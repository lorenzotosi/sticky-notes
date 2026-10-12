// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
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
import stickynotes.application.BoardService;
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
class ChecklistRequestHttpTest {

    private static final String CHECKLIST = "/api/board/notes/note-1/checklist";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private BoardService boardService;

    @BeforeEach
    void setUp() {
        var board = new BoardSnapshot(
                "main",
                1,
                6,
                3,
                List.of(new NoteSnapshot(
                        "note-1",
                        "Note",
                        NoteColor.YELLOW,
                        NoteStatus.TODO,
                        null,
                        List.of(new ChecklistItemSnapshot("item-1", "Item", false)))));
        when(boardService.addItem(anyInt(), anyString(), anyString())).thenReturn(board);
        when(boardService.updateItem(anyInt(), anyString(), anyString(), any(), any()))
                .thenReturn(board);
        when(boardService.deleteItem(anyInt(), anyString(), anyString())).thenReturn(board);
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void rejectsInvalidUpdateJsonBeforeCallingTheService(String body) throws Exception {
        assertRejection(send("PATCH", CHECKLIST + "/item-1", body), "INVALID_REQUEST");
    }

    static Stream<String> invalidUpdates() {
        return Stream.of(
                "{\"completed\":\"true\",\"expectedRevision\":5}",
                "{\"completed\":\"false\",\"expectedRevision\":5}",
                "{\"completed\":1,\"expectedRevision\":5}",
                "{\"completed\":0,\"expectedRevision\":5}",
                "{\"completed\":[],\"expectedRevision\":5}",
                "{\"completed\":{},\"expectedRevision\":5}",
                "{\"label\":\"Item\",\"completed\":null,\"expectedRevision\":5}",
                "{\"label\":null,\"completed\":true,\"expectedRevision\":5}",
                "{\"label\":23,\"completed\":true,\"expectedRevision\":5}",
                "{\"label\":false,\"completed\":true,\"expectedRevision\":5}",
                "{\"label\":[],\"completed\":true,\"expectedRevision\":5}",
                "{\"label\":{},\"completed\":true,\"expectedRevision\":5}",
                "{\"completed\":true,\"expectedRevision\":\"5\"}",
                "{\"completed\":true,\"expectedRevision\":5.5}",
                "{\"completed\":true,\"expectedRevision\":5.0}",
                "{\"completed\":true,\"expectedRevision\":true}",
                "{\"completed\":true,\"expectedRevision\":null}",
                "{\"completed\":true,\"expectedRevision\":2147483648}",
                "{\"completed\":true,\"expectedRevision\":5,\"status\":\"DONE\"}",
                "{\"expectedRevision\":5}",
                "null",
                "[]",
                "true",
                "\"text\"",
                "{");
    }

    @ParameterizedTest
    @MethodSource("invalidCreations")
    void rejectsInvalidCreationJsonBeforeCallingTheService(String body) throws Exception {
        assertRejection(send("POST", CHECKLIST, body), "INVALID_REQUEST");
    }

    static Stream<String> invalidCreations() {
        return Stream.of(
                "{\"label\":null,\"expectedRevision\":5}",
                "{\"label\":23,\"expectedRevision\":5}",
                "{\"label\":true,\"expectedRevision\":5}",
                "{\"label\":[],\"expectedRevision\":5}",
                "{\"label\":{},\"expectedRevision\":5}",
                "{\"label\":\"Item\",\"expectedRevision\":\"5\"}",
                "{\"label\":\"Item\",\"expectedRevision\":5.5}",
                "{\"label\":\"Item\",\"expectedRevision\":5.0}",
                "{\"label\":\"Item\",\"expectedRevision\":false}",
                "{\"label\":\"Item\",\"expectedRevision\":null}",
                "{\"label\":\"Item\",\"expectedRevision\":2147483648}",
                "{\"label\":\"Item\",\"expectedRevision\":5,\"completed\":true}",
                "null",
                "[]",
                "false",
                "\"text\"",
                "{");
    }

    @ParameterizedTest
    @MethodSource("validUpdates")
    void acceptsTypedUpdatesAndPreservesOmittedFields(String body, String label, Boolean completed) throws Exception {
        var response = send("PATCH", CHECKLIST + "/item-1", body);

        assertEquals(200, response.statusCode());
        assertEquals(6, JSON.readTree(response.body()).path("revision").intValue());
        verify(boardService).updateItem(5, "note-1", "item-1", label, completed);
    }

    static Stream<Arguments> validUpdates() {
        return Stream.of(
                Arguments.of("{\"label\":\"Renamed\",\"expectedRevision\":5}", "Renamed", null),
                Arguments.of("{\"completed\":true,\"expectedRevision\":5}", null, true),
                Arguments.of("{\"completed\":false,\"expectedRevision\":5}", null, false),
                Arguments.of("{\"label\":\"Renamed\",\"completed\":false,\"expectedRevision\":5}", "Renamed", false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, Integer.MAX_VALUE})
    void acceptsTypedCreationAndReturnsTheCreatedItemId(int revision) throws Exception {
        var response = send("POST", CHECKLIST, "{\"label\":\"Item\",\"expectedRevision\":%d}".formatted(revision));

        assertEquals(201, response.statusCode());
        assertEquals(
                "item-1", JSON.readTree(response.body()).path("createdItemId").stringValue());
        verify(boardService).addItem(revision, "note-1", "Item");
    }

    @Test
    void rejectsMissingCreationLabelBeforeCallingTheService() throws Exception {
        assertRejection(send("POST", CHECKLIST, "{\"expectedRevision\":5}"), "INVALID_LABEL");
    }

    @Test
    void acceptsAnIntegerDeletionRevision() throws Exception {
        var response = send("DELETE", CHECKLIST + "/item-1?expectedRevision=5", null);

        assertEquals(200, response.statusCode());
        verify(boardService).deleteItem(5, "note-1", "item-1");
    }

    @ParameterizedTest
    @MethodSource("missingOrNegativeRevisions")
    void rejectsMissingOrNegativeRevisionBeforeCallingTheService(String method, String path, String body)
            throws Exception {
        assertRejection(send(method, path, body), "INVALID_REVISION");
    }

    static Stream<Arguments> missingOrNegativeRevisions() {
        return Stream.of(
                Arguments.of("POST", CHECKLIST, "{\"label\":\"Item\"}"),
                Arguments.of("POST", CHECKLIST, "{\"label\":\"Item\",\"expectedRevision\":-1}"),
                Arguments.of("PATCH", CHECKLIST + "/item-1", "{\"completed\":true}"),
                Arguments.of("PATCH", CHECKLIST + "/item-1", "{\"completed\":true,\"expectedRevision\":-1}"),
                Arguments.of("DELETE", CHECKLIST + "/item-1", null),
                Arguments.of("DELETE", CHECKLIST + "/item-1?expectedRevision=-1", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "5.5", "5.0", "2147483648", ""})
    void rejectsInvalidDeletionRevisionAsBadRequest(String revision) throws Exception {
        assertRejection(send("DELETE", CHECKLIST + "/item-1?expectedRevision=" + revision, null), "INVALID_REVISION");
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

    private void assertRejection(HttpResponse<String> response, String code) {
        assertEquals(400, response.statusCode(), response.body());
        assertEquals(code, JSON.readTree(response.body()).path("code").stringValue());
        verifyNoInteractions(boardService);
    }
}
