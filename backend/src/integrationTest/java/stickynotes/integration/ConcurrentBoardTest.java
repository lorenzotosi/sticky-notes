// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import stickynotes.Main;
import stickynotes.application.BoardServiceImpl;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import stickynotes.infrastructure.BoardDocumentMapper;
import stickynotes.infrastructure.MongoBoardRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ConcurrentBoardTest {

    private static final int SCHEMA_VERSION = 1;
    private static final int INITIAL_REVISION = 7;
    private static final int UPDATED_REVISION = 8;
    private static final int SINGLE_SLOT_WIP_LIMIT = 1;
    private static final int TWO_SLOT_WIP_LIMIT = 2;
    private static final int CONCURRENT_REQUEST_COUNT = 2;
    private static final int FIRST_NOTE_INDEX = 0;
    private static final int DOING_START_INDEX = 0;
    private static final int DOING_APPEND_INDEX = 1;
    private static final int MONGO_PING_COMMAND_VALUE = 1;
    private static final int RANDOM_SERVER_PORT = 0;
    private static final long SAVE_BARRIER_TIMEOUT_SECONDS = 30;
    private static final long DOING_COUNT_AFTER_LIMIT_CHANGE = 1;
    private static final long TODO_COUNT_AFTER_LIMIT_CHANGE = 1;
    private static final long DOING_COUNT_AFTER_MOVE = 2;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration WIP_CHANGE_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final String DEFAULT_URI =
            "mongodb://127.0.0.1:27018/?serverSelectionTimeoutMS=1000&connectTimeoutMS=1000&socketTimeoutMS=1000";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void lastWipSlotAllowsExactlyOneMove() throws Exception {
        String uri = mongoUri();
        String database = "sticky_notes_concurrent_" + UUID.randomUUID();

        NoteSnapshot noteA =
                new NoteSnapshot("note-a", "First note", NoteColor.YELLOW, NoteStatus.TODO, null, List.of());
        NoteSnapshot noteB =
                new NoteSnapshot("note-b", "Second note", NoteColor.BLUE, NoteStatus.TODO, null, List.of());
        var expected = new BoardSnapshot(
                "main", SCHEMA_VERSION, INITIAL_REVISION, SINGLE_SLOT_WIP_LIMIT, List.of(noteA, noteB));

        try (MongoClient mongo = MongoClients.create(uri)) {
            mongo.getDatabase("admin").runCommand(new Document("ping", MONGO_PING_COMMAND_VALUE));
            try (var first = startBackend(uri, database);
                    var second = startBackend(uri, database);
                    var http = HttpClient.newHttpClient()) {
                int firstPort = first.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
                int secondPort = second.getEnvironment().getRequiredProperty("local.server.port", Integer.class);

                assertNotEquals(firstPort, secondPort);
                assertNotSame(first.getBean(MongoClient.class), second.getBean(MongoClient.class));

                var template = new MongoTemplate(mongo, database);
                template.dropCollection("boards");
                template.insert(BoardDocumentMapper.toDocument(expected));

                assertEquals(JSON.valueToTree(expected), getBoard(http, firstPort));
                assertEquals(JSON.valueToTree(expected), getBoard(http, secondPort));

                var repository = new MongoBoardRepository(template);

                var barrier = new CyclicBarrier(CONCURRENT_REQUEST_COUNT);

                for (var backend : List.of(first, second)) {
                    MongoBoardRepository realRepository =
                            AopTestUtils.getUltimateTargetObject(backend.getBean(MongoBoardRepository.class));

                    var repositorySpy = spy(realRepository);

                    doAnswer(invocation -> {
                                try {
                                    barrier.await(SAVE_BARRIER_TIMEOUT_SECONDS, SECONDS);
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw exception;
                                }

                                return invocation.callRealMethod();
                            })
                            .when(repositorySpy)
                            .save(any(BoardSnapshot.class), anyInt());

                    ReflectionTestUtils.setField(backend.getBean(BoardServiceImpl.class), "repository", repositorySpy);
                }

                String moveBody = "{\"targetStatus\":\"DOING\",\"destinationIndex\":%d,\"expectedRevision\":%d}"
                        .formatted(DOING_START_INDEX, INITIAL_REVISION);
                var requestA = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + firstPort + "/api/board/notes/note-a/move"))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(moveBody))
                        .build();
                var requestB = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + secondPort + "/api/board/notes/note-b/move"))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(moveBody))
                        .build();

                var futureA = http.sendAsync(requestA, HttpResponse.BodyHandlers.ofString());
                var futureB = http.sendAsync(requestB, HttpResponse.BodyHandlers.ofString());

                HttpResponse<String> responseA = futureA.join();
                HttpResponse<String> responseB = futureB.join();

                assertTrue((responseA.statusCode() == HttpStatus.OK.value()
                                && responseB.statusCode() == HttpStatus.CONFLICT.value())
                        || (responseA.statusCode() == HttpStatus.CONFLICT.value()
                                && responseB.statusCode() == HttpStatus.OK.value()));

                boolean aWon = responseA.statusCode() == HttpStatus.OK.value();
                BoardSnapshot expectedAfterMove;
                if (aWon) {
                    assertEquals(HttpStatus.CONFLICT.value(), responseB.statusCode());
                    assertEquals(
                            "REVISION_CONFLICT",
                            JSON.readTree(responseB.body()).path("code").stringValue());
                    NoteSnapshot winnerA = new NoteSnapshot(
                            "note-a", "First note", NoteColor.YELLOW, NoteStatus.DOING, null, List.of());
                    expectedAfterMove = new BoardSnapshot(
                            "main", SCHEMA_VERSION, UPDATED_REVISION, SINGLE_SLOT_WIP_LIMIT, List.of(noteB, winnerA));
                    assertEquals(JSON.valueToTree(expectedAfterMove), JSON.readTree(responseA.body()));
                } else {
                    assertEquals(HttpStatus.CONFLICT.value(), responseA.statusCode());
                    assertEquals(
                            "REVISION_CONFLICT",
                            JSON.readTree(responseA.body()).path("code").stringValue());
                    NoteSnapshot winnerB = new NoteSnapshot(
                            "note-b", "Second note", NoteColor.BLUE, NoteStatus.DOING, null, List.of());
                    expectedAfterMove = new BoardSnapshot(
                            "main", SCHEMA_VERSION, UPDATED_REVISION, SINGLE_SLOT_WIP_LIMIT, List.of(noteA, winnerB));
                    assertEquals(JSON.valueToTree(expectedAfterMove), JSON.readTree(responseB.body()));
                }

                assertEquals(expectedAfterMove, repository.load().orElseThrow());
                assertEquals(JSON.valueToTree(expectedAfterMove), getBoard(http, firstPort));
                assertEquals(JSON.valueToTree(expectedAfterMove), getBoard(http, secondPort));

            } finally {
                mongo.getDatabase(database).drop();
            }
        }
    }

    @Test
    void concurrentEditsPreserveTheAcknowledgedWinner() throws Exception {
        String uri = mongoUri();
        String database = "sticky_notes_concurrent_" + UUID.randomUUID();

        NoteSnapshot noteA = new NoteSnapshot("note-a", "notaA", NoteColor.YELLOW, NoteStatus.TODO, null, List.of());
        var expected =
                new BoardSnapshot("main", SCHEMA_VERSION, INITIAL_REVISION, SINGLE_SLOT_WIP_LIMIT, List.of(noteA));

        try (MongoClient mongo = MongoClients.create(uri)) {
            mongo.getDatabase("admin").runCommand(new Document("ping", MONGO_PING_COMMAND_VALUE));
            try (var first = startBackend(uri, database);
                    var second = startBackend(uri, database);
                    var http = HttpClient.newHttpClient()) {
                int firstPort = first.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
                int secondPort = second.getEnvironment().getRequiredProperty("local.server.port", Integer.class);

                assertNotEquals(firstPort, secondPort);
                assertNotSame(first.getBean(MongoClient.class), second.getBean(MongoClient.class));

                var template = new MongoTemplate(mongo, database);
                template.dropCollection("boards");
                template.insert(BoardDocumentMapper.toDocument(expected));

                assertEquals(JSON.valueToTree(expected), getBoard(http, firstPort));
                assertEquals(JSON.valueToTree(expected), getBoard(http, secondPort));

                var repository = new MongoBoardRepository(template);

                var barrier = new CyclicBarrier(CONCURRENT_REQUEST_COUNT);

                for (var backend : List.of(first, second)) {
                    MongoBoardRepository realRepository =
                            AopTestUtils.getUltimateTargetObject(backend.getBean(MongoBoardRepository.class));

                    var repositorySpy = spy(realRepository);

                    doAnswer(invocation -> {
                                try {
                                    barrier.await(SAVE_BARRIER_TIMEOUT_SECONDS, SECONDS);
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw exception;
                                }

                                return invocation.callRealMethod();
                            })
                            .when(repositorySpy)
                            .save(any(BoardSnapshot.class), anyInt());

                    ReflectionTestUtils.setField(backend.getBean(BoardServiceImpl.class), "repository", repositorySpy);
                }

                String editAAlpha = "{\"content\":\"Alpha\",\"expectedRevision\":%d}".formatted(INITIAL_REVISION);
                String editABeta = "{\"content\":\"Beta\",\"expectedRevision\":%d}".formatted(INITIAL_REVISION);
                var requestA = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + firstPort + "/api/board/notes/note-a"))
                        .header("Content-Type", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .method("PATCH", (HttpRequest.BodyPublishers.ofString(editAAlpha)))
                        .build();
                var requestB = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + secondPort + "/api/board/notes/note-a"))
                        .header("Content-Type", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(editABeta))
                        .build();

                var futureA = http.sendAsync(requestA, HttpResponse.BodyHandlers.ofString());
                var futureB = http.sendAsync(requestB, HttpResponse.BodyHandlers.ofString());

                HttpResponse<String> responseA = futureA.join();
                HttpResponse<String> responseB = futureB.join();

                String expectedContent = responseA.statusCode() == HttpStatus.OK.value() ? "Alpha" : "Beta";

                if (expectedContent.equals("Alpha")) {
                    assertEquals(HttpStatus.CONFLICT.value(), responseB.statusCode());
                    NoteSnapshot note = repository.load().get().getNotes().get(FIRST_NOTE_INDEX);
                    assertEquals(expectedContent, note.getContent());
                } else {
                    assertEquals(HttpStatus.CONFLICT.value(), responseA.statusCode());
                    NoteSnapshot note = repository.load().get().getNotes().get(FIRST_NOTE_INDEX);
                    assertEquals(expectedContent, note.getContent());
                }

            } finally {
                mongo.getDatabase(database).drop();
            }
        }
    }

    @Test
    void wipLimitChangeAndMoveShareOneRevisionBoundary() throws Exception {
        String uri = mongoUri();
        String database = "sticky_notes_concurrent_" + UUID.randomUUID();

        NoteSnapshot noteA = new NoteSnapshot("note-a", "notaA", NoteColor.YELLOW, NoteStatus.TODO, null, List.of());
        NoteSnapshot noteB = new NoteSnapshot("note-b", "notaB", NoteColor.YELLOW, NoteStatus.DOING, null, List.of());
        var expected =
                new BoardSnapshot("main", SCHEMA_VERSION, INITIAL_REVISION, TWO_SLOT_WIP_LIMIT, List.of(noteA, noteB));

        try (MongoClient mongo = MongoClients.create(uri)) {
            mongo.getDatabase("admin").runCommand(new Document("ping", MONGO_PING_COMMAND_VALUE));
            try (var first = startBackend(uri, database);
                    var second = startBackend(uri, database);
                    var http = HttpClient.newHttpClient()) {
                int firstPort = first.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
                int secondPort = second.getEnvironment().getRequiredProperty("local.server.port", Integer.class);

                assertNotEquals(firstPort, secondPort);
                assertNotSame(first.getBean(MongoClient.class), second.getBean(MongoClient.class));

                var template = new MongoTemplate(mongo, database);
                template.dropCollection("boards");
                template.insert(BoardDocumentMapper.toDocument(expected));

                assertEquals(JSON.valueToTree(expected), getBoard(http, firstPort));
                assertEquals(JSON.valueToTree(expected), getBoard(http, secondPort));

                var repository = new MongoBoardRepository(template);

                var barrier = new CyclicBarrier(CONCURRENT_REQUEST_COUNT);

                for (var backend : List.of(first, second)) {
                    MongoBoardRepository realRepository =
                            AopTestUtils.getUltimateTargetObject(backend.getBean(MongoBoardRepository.class));

                    var repositorySpy = spy(realRepository);

                    doAnswer(invocation -> {
                                try {
                                    barrier.await(SAVE_BARRIER_TIMEOUT_SECONDS, SECONDS);
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw exception;
                                }

                                return invocation.callRealMethod();
                            })
                            .when(repositorySpy)
                            .save(any(BoardSnapshot.class), anyInt());

                    ReflectionTestUtils.setField(backend.getBean(BoardServiceImpl.class), "repository", repositorySpy);
                }

                String limitBody =
                        "{\"limit\":%d,\"expectedRevision\":%d}".formatted(SINGLE_SLOT_WIP_LIMIT, INITIAL_REVISION);

                var requestA = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + firstPort + "/api/board/wip-limit"))
                        .timeout(WIP_CHANGE_REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(limitBody))
                        .build();

                String moveBody = "{\"targetStatus\":\"DOING\",\"destinationIndex\":%d,\"expectedRevision\":%d}"
                        .formatted(DOING_APPEND_INDEX, INITIAL_REVISION);

                var requestB = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + secondPort + "/api/board/notes/note-a/move"))
                        .timeout(WIP_CHANGE_REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(moveBody))
                        .build();

                var futureA = http.sendAsync(requestA, HttpResponse.BodyHandlers.ofString());
                var futureB = http.sendAsync(requestB, HttpResponse.BodyHandlers.ofString());

                HttpResponse<String> responseA = futureA.join();
                HttpResponse<String> responseB = futureB.join();

                if (responseA.statusCode() == HttpStatus.OK.value()) {
                    assertEquals(HttpStatus.CONFLICT.value(), responseB.statusCode());
                    assertEquals(repository.load().get().getWipLimit(), SINGLE_SLOT_WIP_LIMIT);
                    assertEquals(
                            repository.load().get().getNotes().stream()
                                    .filter(x -> x.getStatus().equals(NoteStatus.DOING))
                                    .count(),
                            DOING_COUNT_AFTER_LIMIT_CHANGE);
                    assertEquals(
                            repository.load().get().getNotes().stream()
                                    .filter(x -> x.getStatus().equals(NoteStatus.TODO))
                                    .count(),
                            TODO_COUNT_AFTER_LIMIT_CHANGE);
                } else {
                    assertEquals(HttpStatus.OK.value(), responseB.statusCode());
                    assertEquals(HttpStatus.CONFLICT.value(), responseA.statusCode());
                    assertEquals(
                            repository.load().get().getNotes().stream()
                                    .filter(x -> x.getStatus().equals(NoteStatus.DOING))
                                    .count(),
                            DOING_COUNT_AFTER_MOVE);
                }

            } finally {
                mongo.getDatabase(database).drop();
            }
        }
    }

    private static ConfigurableApplicationContext startBackend(String uri, String database) {
        return new SpringApplication(Main.class)
                .run(
                        "--spring.profiles.active=test",
                        "--spring.mongodb.uri=" + uri,
                        "--spring.mongodb.database=" + database,
                        "--server.port=" + RANDOM_SERVER_PORT,
                        "--server.address=127.0.0.1",
                        "--spring.main.banner-mode=off");
    }

    private static String mongoUri() {
        String uri = System.getenv("TEST_MONGODB_URI");
        return uri == null || uri.isBlank() ? DEFAULT_URI : uri;
    }

    private static JsonNode getBoard(HttpClient http, int port) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/board"))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.OK.value(), response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }
}
