// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import stickynotes.application.BoardRepository;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.uri=mongodb://127.0.0.1:1/sticky_notes_test"
                + "?connectTimeoutMS=500&serverSelectionTimeoutMS=500")
@ActiveProfiles("test")
class ChecklistErrorHttpTest {

    private static final String NOTE = "/api/board/notes/note-1";
    private static final String CHECKLIST = NOTE + "/checklist";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private BoardRepository repository;

    private BoardSnapshot initial;

    @BeforeEach
    void setUp() {
        initial = board(NoteStatus.TODO, List.of(new ChecklistItemSnapshot("item-1", "Item", false)));
        when(repository.load()).thenAnswer(invocation -> Optional.of(initial));
    }

    @ParameterizedTest
    @MethodSource("invalidLabels")
    void invalidLabelsReturnBadRequestWithoutSaving(String method, String label) throws Exception {
        String path = "POST".equals(method) ? CHECKLIST : CHECKLIST + "/item-1";
        String body = JSON.writeValueAsString(Map.of("label", label, "expectedRevision", 5));

        assertFailure(
                send(method, path, body),
                400,
                "INVALID_LABEL",
                "label",
                null,
                "Checklist item label is blank or exceeds 120 UTF-16 code units.");
    }

    static Stream<Arguments> invalidLabels() {
        return Stream.of(
                Arguments.of("POST", "   "),
                Arguments.of("POST", "a".repeat(121)),
                Arguments.of("PATCH", "   "),
                Arguments.of("PATCH", "a".repeat(121)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PATCH", "DELETE"})
    void missingNotesReturnNotFoundWithoutSaving(String method) throws Exception {
        String path = "/api/board/notes/missing/checklist";
        if (!"POST".equals(method)) {
            path += "/item-1";
        }
        if ("DELETE".equals(method)) {
            path += "?expectedRevision=5";
        }

        assertFailure(
                send(method, path, "DELETE".equals(method) ? null : "{\"label\":\"Item\",\"expectedRevision\":5}"),
                404,
                "NOTE_NOT_FOUND",
                "id",
                null,
                "Target note not found.");
    }

    @ParameterizedTest
    @MethodSource("missingItems")
    void missingItemsTakePrecedenceOverDoneImmutability(String method, NoteStatus status) throws Exception {
        initial = board(status, List.of(new ChecklistItemSnapshot("item-1", "Item", true)));
        String path = CHECKLIST + "/missing" + ("DELETE".equals(method) ? "?expectedRevision=5" : "");
        String body = "DELETE".equals(method) ? null : "{\"completed\":false,\"expectedRevision\":5}";

        assertFailure(
                send(method, path, body), 404, "ITEM_NOT_FOUND", "itemId", null, "Target checklist item not found.");
    }

    static Stream<Arguments> missingItems() {
        return Stream.of(
                Arguments.of("PATCH", NoteStatus.TODO),
                Arguments.of("DELETE", NoteStatus.TODO),
                Arguments.of("PATCH", NoteStatus.DONE),
                Arguments.of("DELETE", NoteStatus.DONE));
    }

    @ParameterizedTest
    @MethodSource("doneMutations")
    void doneChecklistMutationsReturnConflictWithoutSaving(String method, String path, String body) throws Exception {
        initial = board(NoteStatus.DONE, List.of(new ChecklistItemSnapshot("item-1", "Item", true)));

        assertFailure(
                send(method, path, body),
                409,
                "NOTE_DONE_READ_ONLY",
                null,
                null,
                "Reopen the note in DOING before changing its checklist.");
    }

    static Stream<Arguments> doneMutations() {
        return Stream.of(
                Arguments.of("POST", CHECKLIST, "{\"label\":\"New item\",\"expectedRevision\":5}"),
                Arguments.of("PATCH", CHECKLIST + "/item-1", "{\"label\":\"Renamed\",\"expectedRevision\":5}"),
                Arguments.of("PATCH", CHECKLIST + "/item-1", "{\"completed\":false,\"expectedRevision\":5}"),
                Arguments.of("DELETE", CHECKLIST + "/item-1?expectedRevision=5", null));
    }

    @Test
    void fullChecklistReturnsConflictWithoutSaving() throws Exception {
        initial = board(
                NoteStatus.TODO,
                IntStream.range(0, 20)
                        .mapToObj(index -> new ChecklistItemSnapshot("item-" + index, "Item " + index, false))
                        .toList());

        assertFailure(
                send("POST", CHECKLIST, "{\"label\":\"Extra item\",\"expectedRevision\":5}"),
                409,
                "CHECKLIST_FULL",
                null,
                null,
                "Note has reached its limit of 20 checklist items.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PATCH", "DELETE"})
    void staleRevisionReturnsCurrentRevisionBeforeEntityChecks(String method) throws Exception {
        String path = "/api/board/notes/missing/checklist";
        if (!"POST".equals(method)) {
            path += "/missing";
        }
        if ("DELETE".equals(method)) {
            path += "?expectedRevision=4";
        }
        String body = "DELETE".equals(method) ? null : "{\"label\":\"Item\",\"expectedRevision\":4}";

        assertFailure(send(method, path, body), 409, "REVISION_CONFLICT", null, 5, "Revision conflict detected.");
    }

    @Test
    void incompleteChecklistReturnsConflictWithoutSaving() throws Exception {
        initial = board(NoteStatus.DOING, List.of(new ChecklistItemSnapshot("item-1", "Item", false)));

        assertFailure(
                send(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DONE\",\"destinationIndex\":0,\"expectedRevision\":5}"),
                409,
                "CHECKLIST_INCOMPLETE",
                null,
                null,
                "Complete all checklist items before moving the note to DONE.");
    }

    @Test
    void failedConditionalSaveReturnsTheRevisionFromTheConflictReload() throws Exception {
        var winner = new BoardSnapshot("main", 1, 6, 3, initial.getNotes());
        when(repository.load()).thenReturn(Optional.of(initial)).thenReturn(Optional.of(winner));
        when(repository.save(any(), eq(5))).thenReturn(Optional.empty());

        var response = send("POST", CHECKLIST, "{\"label\":\"New item\",\"expectedRevision\":5}");

        assertEquals(409, response.statusCode(), response.body());
        var error = JSON.readTree(response.body());
        assertEquals("REVISION_CONFLICT", error.path("code").stringValue());
        assertEquals(6, error.path("currentRevision").intValue());
        assertFalse(error.has("field"));
        verify(repository).save(any(), eq(5));
        verify(repository, times(2)).load();
    }

    @Test
    void reopeningAtFullWipReturnsConflictWithoutSaving() throws Exception {
        initial = new BoardSnapshot(
                "main",
                1,
                5,
                1,
                List.of(
                        new NoteSnapshot(
                                "occupied", "Occupied slot", NoteColor.BLUE, NoteStatus.DOING, null, List.of()),
                        new NoteSnapshot(
                                "note-1",
                                "Done note",
                                NoteColor.YELLOW,
                                NoteStatus.DONE,
                                null,
                                List.of(new ChecklistItemSnapshot("item-1", "Item", true)))));

        assertFailure(
                send(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":5}"),
                409,
                "WIP_LIMIT_REACHED",
                null,
                null,
                "Cannot move note to DOING: column capacity reached.");
    }

    @ParameterizedTest
    @MethodSource("repositoryFailures")
    void infrastructureErrorsDoNotExposeExceptionDetails(
            RuntimeException failure, int status, String code, String message) throws Exception {
        when(repository.load()).thenThrow(failure);
        var response = send("POST", CHECKLIST, "{\"label\":\"Item\",\"expectedRevision\":5}");

        assertFailure(response, status, code, null, null, message);
        assertFalse(response.body().contains("private diagnostic detail"));
    }

    static Stream<Arguments> repositoryFailures() {
        return Stream.of(
                Arguments.of(
                        new IllegalStateException("private diagnostic detail"),
                        503,
                        "DATABASE_UNAVAILABLE",
                        "Database service is temporarily unavailable."),
                Arguments.of(
                        new RuntimeException("private diagnostic detail"),
                        500,
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred"));
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
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private void assertFailure(
            HttpResponse<String> response, int status, String code, String field, Integer revision, String message) {
        assertEquals(status, response.statusCode(), response.body());
        var error = JSON.readTree(response.body());
        assertEquals(code, error.path("code").stringValue());
        assertEquals(message, error.path("message").stringValue());
        if (field == null) {
            assertFalse(error.has("field"));
        } else {
            assertEquals(field, error.path("field").stringValue());
        }
        if (revision == null) {
            assertFalse(error.has("currentRevision"));
        } else {
            assertEquals(revision.intValue(), error.path("currentRevision").intValue());
        }
        verify(repository, never()).save(any(), anyInt());
    }
}
