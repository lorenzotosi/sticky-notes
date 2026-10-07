// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import stickynotes.Main;
import stickynotes.application.BoardServiceException;
import stickynotes.application.BoardServiceImpl;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.CommandResponse;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import stickynotes.infrastructure.BoardBootstrap;
import stickynotes.infrastructure.BoardDocumentMapper;
import stickynotes.infrastructure.MongoBoardRepository;
import stickynotes.interop.JvmBoardFacade;

class MongoBoardRepositoryIntegrationTest {

    private String database;
    private MongoClient client;
    private MongoTemplate template;
    private MongoBoardRepository repository;

    @BeforeEach
    void setUp() {
        database = "sticky_notes_it_" + UUID.randomUUID();
        client = MongoClients.create(mongoUri());
        template = new MongoTemplate(client, database);
        repository = new MongoBoardRepository(template);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                client.getDatabase(database).drop();
            } finally {
                client.close();
            }
        }
    }

    @Test
    void repeatedBootstrapCreatesOneEmptyBoard() {
        var bootstrap = new BoardBootstrap(template);
        var arguments = new DefaultApplicationArguments();

        bootstrap.run(arguments);
        bootstrap.run(arguments);

        assertEquals(JvmBoardFacade.createEmptyBoard(), repository.load().orElseThrow());
        assertEquals(1L, template.getCollection("boards").countDocuments());
    }

    @Test
    void completeSnapshotSurvivesBackendRestart() {
        BoardSnapshot expected = completeSnapshot(1);

        try (var first = startBackend()) {
            var firstRepository = first.getBean(MongoBoardRepository.class);

            assertEquals(expected, firstRepository.save(completeSnapshot(0), 0).orElseThrow());
            assertEquals(expected, firstRepository.load().orElseThrow());
        }

        try (var restarted = startBackend()) {
            var restartedRepository = restarted.getBean(MongoBoardRepository.class);

            assertEquals(expected, restartedRepository.load().orElseThrow());
        }
    }

    @Test
    void correctRevisionSavesAndStaleRevisionPreservesWinner() {
        BoardSnapshot initial = completeSnapshot(7);
        template.insert(BoardDocumentMapper.toDocument(initial));
        var service = new BoardServiceImpl(repository);

        BoardSnapshot saved = service.updateNote(7, "note-2", "Updated content", NoteColor.GREEN);

        assertEquals(8, saved.getRevision());
        assertEquals(saved, repository.load().orElseThrow());

        assertTrue(repository.save(initial, 7).isEmpty());

        BoardServiceException conflict =
                assertThrows(BoardServiceException.class, () -> service.updateNote(7, "note-2", "Stale content", null));

        assertEquals("REVISION_CONFLICT", conflict.getCode());
        assertEquals(Integer.valueOf(8), conflict.getCurrentRevision());
        assertEquals(saved, repository.load().orElseThrow());
    }

    @Test
    void revisionOverflowPreservesPersistedSnapshot() {
        BoardSnapshot initial = completeSnapshot(Integer.MAX_VALUE);
        template.insert(BoardDocumentMapper.toDocument(initial));

        assertThrows(ArithmeticException.class, () -> repository.save(initial, Integer.MAX_VALUE));

        var service = new BoardServiceImpl(repository);
        BoardServiceException failure = assertThrows(
                BoardServiceException.class,
                () -> service.updateNote(Integer.MAX_VALUE, "note-2", "Valid content", null));

        assertEquals("REVISION_OVERFLOW", failure.getCode());
        assertEquals(initial, repository.load().orElseThrow());
    }

    @Test
    void concurrentSavesAllowExactlyOneNoteIntoLastWipSlot() throws Exception {
        BoardSnapshot initial = new BoardSnapshot(
                "main",
                1,
                7,
                1,
                List.of(
                        new NoteSnapshot("note-a", "First note", NoteColor.YELLOW, NoteStatus.TODO, null, List.of()),
                        new NoteSnapshot("note-b", "Second note", NoteColor.BLUE, NoteStatus.TODO, null, List.of())));

        template.insert(BoardDocumentMapper.toDocument(initial));

        try (MongoClient otherClient = MongoClients.create(mongoUri());
                var workers = Executors.newFixedThreadPool(2)) {

            var otherRepository = new MongoBoardRepository(new MongoTemplate(otherClient, database));

            BoardSnapshot candidateA = moveToDoing(repository.load().orElseThrow(), "note-a");
            BoardSnapshot candidateB = moveToDoing(otherRepository.load().orElseThrow(), "note-b");

            var barrier = new CyclicBarrier(2);

            var first = workers.submit(() -> {
                barrier.await(10, SECONDS);
                return repository.save(candidateA, 7);
            });

            var second = workers.submit(() -> {
                barrier.await(10, SECONDS);
                return otherRepository.save(candidateB, 7);
            });

            var resultA = first.get(20, SECONDS);
            var resultB = second.get(20, SECONDS);

            int successfulSaves = (resultA.isPresent() ? 1 : 0) + (resultB.isPresent() ? 1 : 0);

            assertEquals(1, successfulSaves);

            BoardSnapshot winner = resultA.or(() -> resultB).orElseThrow();
            BoardSnapshot persisted = repository.load().orElseThrow();

            assertEquals(winner, persisted);
            assertEquals(8, persisted.getRevision());
            assertEquals(
                    1L,
                    persisted.getNotes().stream()
                            .filter(note -> note.getStatus() == NoteStatus.DOING)
                            .count());
        }
    }

    private String mongoUri() {
        String uri = System.getenv("TEST_MONGODB_URI");

        if (uri == null || uri.isBlank()) {
            /*throw new IllegalStateException(
            "Set TEST_MONGODB_URI before running integration tests");*/
            return "mongodb://127.0.0.1:27018";
        }

        return uri;
    }

    private ConfigurableApplicationContext startBackend() {
        var application = new SpringApplication(Main.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        return application.run(
                "--spring.profiles.active=test",
                "--spring.mongodb.uri=" + mongoUri(),
                "--spring.mongodb.database=" + database,
                "--spring.main.banner-mode=off");
    }

    private static BoardSnapshot moveToDoing(BoardSnapshot snapshot, String noteId) {
        var response = JvmBoardFacade.execute(snapshot, JvmBoardFacade.moveNote(noteId, NoteStatus.DOING, 0));

        return assertInstanceOf(CommandResponse.Success.class, response).getBoard();
    }

    private static BoardSnapshot completeSnapshot(int revision) {
        return new BoardSnapshot(
                "main",
                1,
                revision,
                3,
                List.of(
                        new NoteSnapshot("note-2", "Verify API", NoteColor.PINK, NoteStatus.TODO, null, List.of()),
                        new NoteSnapshot(
                                "note-1", "Configure build", NoteColor.YELLOW, NoteStatus.TODO, null, List.of()),
                        new NoteSnapshot(
                                "note-3",
                                "Prepare release",
                                NoteColor.BLUE,
                                NoteStatus.DOING,
                                "Missing configuration",
                                List.of(
                                        new ChecklistItemSnapshot("item-2", "Verify access", true),
                                        new ChecklistItemSnapshot("item-1", "Verify configuration", false))),
                        new NoteSnapshot(
                                "note-4",
                                "Run checks",
                                NoteColor.GREEN,
                                NoteStatus.DONE,
                                null,
                                List.of(new ChecklistItemSnapshot("item-3", "Run tests", true)))));
    }
}
