// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import stickynotes.Main;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import stickynotes.infrastructure.BoardDocumentMapper;
import stickynotes.infrastructure.MongoBoardRepository;
import stickynotes.interop.JvmBoardFacade;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistHttpIntegrationTest {

    private static final String DEFAULT_URI =
            "mongodb://127.0.0.1:27018/?serverSelectionTimeoutMS=1000&connectTimeoutMS=1000&socketTimeoutMS=1000";
    private static final String NOTE = "/api/board/notes/note-1";
    private static final String CHECKLIST = NOTE + "/checklist";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String database;
    private MongoClient mongo;
    private MongoTemplate template;
    private ConfigurableApplicationContext backend;
    private MongoBoardRepository repository;
    private HttpClient http;
    private int port;

    @BeforeAll
    void start() {
        database = "sticky_notes_http_it_" + UUID.randomUUID();
        try {
            mongo = MongoClients.create(mongoUri());
            mongo.getDatabase("admin").runCommand(new Document("ping", 1));
        } catch (RuntimeException failure) {
            if (mongo != null) {
                mongo.close();
                mongo = null;
            }
            throw new IllegalStateException("MongoDB is not reachable for HTTP integration tests.", failure);
        }
        template = new MongoTemplate(mongo, database);
        startBackend();
        http = HttpClient.newHttpClient();
    }

    @BeforeEach
    void resetBoard() {
        seed(board(NoteStatus.TODO, List.of(new ChecklistItemSnapshot("item-1", "Item", false))));
    }

    @AfterAll
    void stop() {
        try {
            if (http != null) {
                http.close();
            }
            if (backend != null) {
                backend.close();
            }
        } finally {
            if (mongo != null) {
                try {
                    mongo.getDatabase(database).drop();
                } finally {
                    mongo.close();
                }
            }
        }
    }

    @Test
    void emptyBoardContainsEverySnapshotField() throws Exception {
        seed(JvmBoardFacade.createEmptyBoard());

        assertStored(getBoard(), 0);
    }

    @Test
    void checklistCommandsPersistAndSurviveBackendRestart() throws Exception {
        var existing = IntStream.range(0, 19)
                .mapToObj(index -> new ChecklistItemSnapshot("item-" + index, "Existing " + index, true))
                .toList();
        var other = new NoteSnapshot(
                "note-2",
                "Other note",
                NoteColor.BLUE,
                NoteStatus.DOING,
                null,
                List.of(new ChecklistItemSnapshot("other-item", "Other item", false)));
        seed(new BoardSnapshot(
                "main",
                1,
                5,
                3,
                List.of(
                        new NoteSnapshot("note-1", "Target note", NoteColor.YELLOW, NoteStatus.TODO, null, existing),
                        other)));

        var created = response(
                send(
                        "POST",
                        CHECKLIST,
                        JSON.writeValueAsString(Map.of("label", "  Added\r\nitem  ", "expectedRevision", 5))),
                201);
        String itemId = created.path("createdItemId").stringValue();
        assertEquals(itemId, UUID.fromString(itemId).toString());
        JsonNode board = created.path("board");
        assertStored(board, 6);
        assertEquals("Added\nitem", item(board, itemId).path("label").stringValue());
        assertFalse(item(board, itemId).path("completed").booleanValue());
        assertEquals(
                itemId,
                note(board, "note-1").path("checklist").get(19).path("id").stringValue());
        assertEquals(20, note(board, "note-1").path("checklist").size());
        assertEquals(JSON.valueToTree(other), note(board, "note-2"));

        assertRejected("POST", CHECKLIST, "{\"label\":\"Extra\",\"expectedRevision\":6}", 409, "CHECKLIST_FULL");

        board = response(
                send("PATCH", CHECKLIST + "/" + itemId, "{\"label\":\"Renamed\",\"expectedRevision\":6}"), 200);
        assertStored(board, 7);
        assertEquals("Renamed", item(board, itemId).path("label").stringValue());
        assertFalse(item(board, itemId).path("completed").booleanValue());

        board = response(send("PATCH", CHECKLIST + "/" + itemId, "{\"completed\":true,\"expectedRevision\":7}"), 200);
        assertStored(board, 8);
        assertEquals("Renamed", item(board, itemId).path("label").stringValue());
        assertTrue(item(board, itemId).path("completed").booleanValue());

        board = response(send("PATCH", CHECKLIST + "/" + itemId, "{\"completed\":false,\"expectedRevision\":8}"), 200);
        assertStored(board, 9);
        assertEquals("Renamed", item(board, itemId).path("label").stringValue());
        assertFalse(item(board, itemId).path("completed").booleanValue());

        Document persisted = storedDocument();
        backend.close();
        startBackend();
        assertStored(board, 9);
        assertEquals(persisted, storedDocument());

        board = response(send("DELETE", CHECKLIST + "/" + itemId + "?expectedRevision=9", null), 200);
        assertStored(board, 10);
        assertEquals(JSON.valueToTree(existing), note(board, "note-1").path("checklist"));
        assertEquals(JSON.valueToTree(other), note(board, "note-2"));
    }

    @Test
    void staleRevisionPreservesTheStoredDocument() throws Exception {
        var error = assertRejected(
                "PATCH",
                CHECKLIST + "/item-1",
                "{\"completed\":true,\"expectedRevision\":4}",
                409,
                "REVISION_CONFLICT");

        assertEquals(5, error.path("currentRevision").intValue());
        assertFalse(error.has("field"));
    }

    @Test
    void completionAndReopeningRespectChecklistAndWip() throws Exception {
        var initialItem = new ChecklistItemSnapshot("item-1", "Item", false);
        seed(new BoardSnapshot(
                "main",
                1,
                5,
                1,
                List.of(
                        new NoteSnapshot(
                                "note-1", "Note", NoteColor.YELLOW, NoteStatus.TODO, null, List.of(initialItem)),
                        new NoteSnapshot("note-2", "Other note", NoteColor.BLUE, NoteStatus.TODO, null, List.of()))));

        var board = response(
                send(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":5}"),
                200);
        assertStored(board, 6);
        assertEquals("DOING", note(board, "note-1").path("status").stringValue());
        assertEquals(JSON.valueToTree(initialItem), item(board, "item-1"));

        assertRejected(
                "POST",
                NOTE + "/move",
                "{\"targetStatus\":\"DONE\",\"destinationIndex\":0,\"expectedRevision\":6}",
                409,
                "CHECKLIST_INCOMPLETE");

        board = response(send("PATCH", CHECKLIST + "/item-1", "{\"completed\":true,\"expectedRevision\":6}"), 200);
        assertStored(board, 7);
        assertTrue(item(board, "item-1").path("completed").booleanValue());

        board = response(
                send(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DONE\",\"destinationIndex\":0,\"expectedRevision\":7}"),
                200);
        assertStored(board, 8);
        assertEquals("DONE", note(board, "note-1").path("status").stringValue());
        assertTrue(item(board, "item-1").path("completed").booleanValue());

        assertRejected("POST", CHECKLIST, "{\"label\":\"New\",\"expectedRevision\":8}", 409, "NOTE_DONE_READ_ONLY");
        assertRejected(
                "PATCH",
                CHECKLIST + "/item-1",
                "{\"label\":\"Renamed\",\"expectedRevision\":8}",
                409,
                "NOTE_DONE_READ_ONLY");
        var error = assertRejected(
                "PATCH",
                CHECKLIST + "/item-1",
                "{\"completed\":false,\"expectedRevision\":8}",
                409,
                "NOTE_DONE_READ_ONLY");
        assertEquals(
                "Reopen the note in DOING before changing its checklist.",
                error.path("message").stringValue());
        assertRejected("DELETE", CHECKLIST + "/item-1?expectedRevision=8", null, 409, "NOTE_DONE_READ_ONLY");

        board = response(
                send(
                        "POST",
                        "/api/board/notes/note-2/move",
                        "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":8}"),
                200);
        assertStored(board, 9);
        assertEquals("DOING", note(board, "note-2").path("status").stringValue());

        assertRejected(
                "POST",
                NOTE + "/move",
                "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":9}",
                409,
                "WIP_LIMIT_REACHED");

        board = response(
                send(
                        "POST",
                        "/api/board/notes/note-2/move",
                        "{\"targetStatus\":\"TODO\",\"destinationIndex\":0,\"expectedRevision\":9}"),
                200);
        assertStored(board, 10);
        assertEquals("TODO", note(board, "note-2").path("status").stringValue());

        board = response(
                send(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":10}"),
                200);
        assertStored(board, 11);
        assertEquals("DOING", note(board, "note-1").path("status").stringValue());
        assertTrue(item(board, "item-1").path("completed").booleanValue());

        board = response(send("PATCH", CHECKLIST + "/item-1", "{\"completed\":false,\"expectedRevision\":11}"), 200);
        assertStored(board, 12);
        assertEquals("DOING", note(board, "note-1").path("status").stringValue());
        assertFalse(item(board, "item-1").path("completed").booleanValue());
    }

    @Test
    void emptyChecklistAllowsCompletion() throws Exception {
        seed(board(NoteStatus.DOING, List.of()));

        var board = response(
                send(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DONE\",\"destinationIndex\":0,\"expectedRevision\":5}"),
                200);

        assertStored(board, 6);
        assertEquals("DONE", note(board, "note-1").path("status").stringValue());
        assertEquals(0, note(board, "note-1").path("checklist").size());
    }

    @ParameterizedTest
    @MethodSource("invalidLabels")
    void invalidLabelPreservesTheStoredDocument(String method, String label) throws Exception {
        String path = "POST".equals(method) ? CHECKLIST : CHECKLIST + "/item-1";
        String body = JSON.writeValueAsString(Map.of("label", label, "expectedRevision", 5));

        var error = assertRejected(method, path, body, 400, "INVALID_LABEL");

        assertEquals("label", error.path("field").stringValue());
    }

    static Stream<Arguments> invalidLabels() {
        return Stream.of(Arguments.of("POST", "   "), Arguments.of("PATCH", "a".repeat(121)));
    }

    @Test
    void missingItemPreservesTheStoredDocument() throws Exception {
        var error = assertRejected(
                "PATCH", CHECKLIST + "/missing", "{\"completed\":true,\"expectedRevision\":5}", 404, "ITEM_NOT_FOUND");

        assertEquals("itemId", error.path("field").stringValue());
    }

    private void startBackend() {
        backend = new SpringApplication(Main.class)
                .run(
                        "--spring.profiles.active=test",
                        "--spring.mongodb.uri=" + mongoUri(),
                        "--spring.mongodb.database=" + database,
                        "--server.port=0",
                        "--server.address=127.0.0.1",
                        "--spring.main.banner-mode=off");
        port = backend.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        repository = backend.getBean(MongoBoardRepository.class);
    }

    private static String mongoUri() {
        String uri = System.getenv("TEST_MONGODB_URI");
        return uri == null || uri.isBlank() ? DEFAULT_URI : uri;
    }

    private void seed(BoardSnapshot board) {
        template.dropCollection("boards");
        template.insert(BoardDocumentMapper.toDocument(board));
    }

    private static BoardSnapshot board(NoteStatus status, List<ChecklistItemSnapshot> items) {
        return new BoardSnapshot(
                "main", 1, 5, 3, List.of(new NoteSnapshot("note-1", "Note", NoteColor.YELLOW, status, null, items)));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .method(
                        method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode response(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private JsonNode getBoard() throws Exception {
        return response(send("GET", "/api/board", null), 200);
    }

    private void assertStored(JsonNode board, int revision) throws Exception {
        assertEquals(revision, board.path("revision").intValue());
        assertEquals(board, JSON.valueToTree(repository.load().orElseThrow()));
        assertEquals(board, getBoard());
    }

    private Document storedDocument() {
        Document document = template.getCollection("boards")
                .find(new Document("_id", "main"))
                .first();
        assertNotNull(document);
        return document;
    }

    private JsonNode assertRejected(String method, String path, String body, int status, String code) throws Exception {
        Document stored = storedDocument();
        JsonNode board = getBoard();
        var error = response(send(method, path, body), status);
        assertEquals(code, error.path("code").stringValue());
        assertEquals(stored, storedDocument());
        assertEquals(board, getBoard());
        return error;
    }

    private static JsonNode note(JsonNode board, String noteId) {
        return board.path("notes")
                .valueStream()
                .filter(value -> noteId.equals(value.path("id").stringValue()))
                .findFirst()
                .orElseThrow();
    }

    private static JsonNode item(JsonNode board, String itemId) {
        return note(board, "note-1")
                .path("checklist")
                .valueStream()
                .filter(value -> itemId.equals(value.path("id").stringValue()))
                .findFirst()
                .orElseThrow();
    }
}
