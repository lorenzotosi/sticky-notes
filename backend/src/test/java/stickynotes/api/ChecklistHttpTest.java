// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.uri=mongodb://127.0.0.1:1/sticky_notes_test")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ChecklistHttpTest {

    private static final String CHECKLIST = "/api/board/notes/note-1/checklist";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private BoardService boardService;

    @ParameterizedTest
    @MethodSource("invalidJson")
    void rejectsInvalidJsonBeforeCallingTheService(String method, String body) throws Exception {
        String path = "POST".equals(method) ? CHECKLIST : CHECKLIST + "/item-1";

        assertRejection(send(method, path, body), "INVALID_REQUEST");
    }

    static Stream<Arguments> invalidJson() {
        return Stream.of(
                Arguments.of("POST", "{\"label\":23,\"expectedRevision\":5}"),
                Arguments.of("POST", "{\"label\":null,\"expectedRevision\":5}"),
                Arguments.of("POST", "{\"label\":\"Item\",\"completed\":true,\"expectedRevision\":5}"),
                Arguments.of("PATCH", "{\"completed\":\"true\",\"expectedRevision\":5}"),
                Arguments.of("PATCH", "{\"completed\":null,\"expectedRevision\":5}"),
                Arguments.of("PATCH", "{\"completed\":true,\"expectedRevision\":\"5\"}"),
                Arguments.of("PATCH", "{\"completed\":true,\"expectedRevision\":5.0}"),
                Arguments.of("PATCH", "{\"completed\":true,\"expectedRevision\":2147483648}"),
                Arguments.of("PATCH", "{\"completed\":true,\"expectedRevision\":null}"),
                Arguments.of("PATCH", "{\"completed\":true,\"expectedRevision\":5,\"status\":\"DONE\"}"),
                Arguments.of("PATCH", "{\"expectedRevision\":5}"),
                Arguments.of("PATCH", "[]"),
                Arguments.of("PATCH", "{"));
    }

    @ParameterizedTest
    @MethodSource("partialUpdates")
    void passesOmittedFieldsAndExplicitFalseToTheService(String body, String label, Boolean completed)
            throws Exception {
        when(boardService.updateItem(anyInt(), anyString(), anyString(), any(), any()))
                .thenReturn(new BoardSnapshot("main", 1, 6, 3, List.of()));

        var response = send("PATCH", CHECKLIST + "/item-1", body);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(6, JSON.readTree(response.body()).path("revision").intValue());
        verify(boardService).updateItem(5, "note-1", "item-1", label, completed);
    }

    static Stream<Arguments> partialUpdates() {
        return Stream.of(
                Arguments.of("{\"label\":\"Renamed\",\"expectedRevision\":5}", "Renamed", null),
                Arguments.of("{\"completed\":false,\"expectedRevision\":5}", null, false),
                Arguments.of("{\"label\":\"Renamed\",\"completed\":true,\"expectedRevision\":5}", "Renamed", true));
    }

    @ParameterizedTest
    @MethodSource("invalidRevisions")
    void rejectsInvalidRevisionBeforeCallingTheService(String method, String path, String body) throws Exception {
        var error = assertRejection(send(method, path, body), "INVALID_REVISION");

        assertEquals("expectedRevision", error.path("field").stringValue());
    }

    static Stream<Arguments> invalidRevisions() {
        return Stream.of(
                Arguments.of("POST", CHECKLIST, "{\"label\":\"Item\"}"),
                Arguments.of("PATCH", CHECKLIST + "/item-1", "{\"completed\":true,\"expectedRevision\":-1}"),
                Arguments.of("DELETE", CHECKLIST + "/item-1", null),
                Arguments.of("DELETE", CHECKLIST + "/item-1?expectedRevision=5.5", null));
    }

    @ParameterizedTest
    @MethodSource("blankItemIds")
    void rejectsBlankItemIdBeforeCallingTheService(String method, String encodedId) throws Exception {
        boolean deletion = "DELETE".equals(method);
        String path = CHECKLIST + "/" + encodedId + (deletion ? "?expectedRevision=5" : "");
        String body = deletion ? null : "{\"completed\":false,\"expectedRevision\":5}";

        var error = assertRejection(send(method, path, body), "INVALID_REQUEST");

        assertEquals("itemId", error.path("field").stringValue());
    }

    static Stream<Arguments> blankItemIds() {
        return Stream.of(Arguments.of("PATCH", "%20"), Arguments.of("DELETE", "%C2%A0"));
    }

    @Test
    void rejectsMissingCreationLabelBeforeCallingTheService() throws Exception {
        var error = assertRejection(send("POST", CHECKLIST, "{\"expectedRevision\":5}"), "INVALID_LABEL");

        assertEquals("label", error.path("field").stringValue());
    }

    @ParameterizedTest
    @MethodSource("infrastructureErrors")
    void infrastructureErrorsHaveNeutralMessages(
            RuntimeException failure, int status, String code, String message, CapturedOutput output) throws Exception {
        failure.initCause(new RuntimeException("mongodb://private-user:private-password@db/board"));
        when(boardService.addItem(5, "note-1", "private note content")).thenThrow(failure);

        var response = send("POST", CHECKLIST, "{\"label\":\"private note content\",\"expectedRevision\":5}");

        assertEquals(status, response.statusCode(), response.body());
        var error = JSON.readTree(response.body());
        assertEquals(code, error.path("code").stringValue());
        assertEquals(message, error.path("message").stringValue());
        assertFalse(error.has("field"));
        assertFalse(error.has("currentRevision"));
        assertFalse(response.body().contains("private diagnostic detail"));
        assertTrue(output.getAll().contains("code=" + code + ", status=" + status));
        assertFalse((output.getAll() + response.body()).contains("private diagnostic detail"));
        assertFalse((output.getAll() + response.body()).contains("private note content"));
        assertFalse((output.getAll() + response.body()).contains("private-user"));
        assertFalse((output.getAll() + response.body()).contains("private-password"));
    }

    static Stream<Arguments> infrastructureErrors() {
        return Stream.of(
                Arguments.of(
                        new BoardServiceException("DATABASE_UNAVAILABLE", null),
                        503,
                        "DATABASE_UNAVAILABLE",
                        "Database service is temporarily unavailable."),
                Arguments.of(
                        new BoardServiceException("INVALID_SNAPSHOT", null),
                        500,
                        "INVALID_SNAPSHOT",
                        "INVALID_SNAPSHOT"),
                Arguments.of(
                        new RuntimeException("private diagnostic detail"),
                        500,
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred"));
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

    private JsonNode assertRejection(HttpResponse<String> response, String code) {
        assertEquals(400, response.statusCode(), response.body());
        var error = JSON.readTree(response.body());
        assertEquals(code, error.path("code").stringValue());
        verifyNoInteractions(boardService);
        return error;
    }
}
