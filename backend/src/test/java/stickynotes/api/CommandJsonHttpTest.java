// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import stickynotes.application.BoardService;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.uri=mongodb://127.0.0.1:1/sticky_notes_test")
@ActiveProfiles("test")
class CommandJsonHttpTest {

    private static final String NOTES = "/api/board/notes";
    private static final String NOTE = NOTES + "/note-1";
    private static final String WIP = "/api/board/wip-limit";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private BoardService boardService;

    @Test
    void rejectsUnknownFieldsBeforeCallingTheService() throws Exception {
        assertRejected(
                "POST",
                NOTES,
                "{\"content\":\"Text\",\"expectedRevision\":5,\"limit\":\"private payload\"}",
                "INVALID_REQUEST");
    }

    @ParameterizedTest
    @MethodSource("invalidJson")
    void rejectsInvalidJsonTypesBeforeCallingTheService(String method, String path, String body) throws Exception {
        assertRejected(method, path, body, "INVALID_REQUEST");
    }

    static Stream<Arguments> invalidJson() {
        return Stream.of(
                Arguments.of("POST", NOTES, "{\"content\":23,\"expectedRevision\":5}"),
                Arguments.of("PATCH", NOTE, "{\"content\":null,\"expectedRevision\":5}"),
                Arguments.of(
                        "POST", NOTE + "/move", "{\"targetStatus\":1,\"destinationIndex\":0,\"expectedRevision\":5}"),
                Arguments.of("PUT", NOTE + "/block", "{\"reason\":false,\"expectedRevision\":5}"),
                Arguments.of("POST", NOTES, "[]"),
                Arguments.of("POST", NOTES, "{"));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void invalidFieldsKeepTheirCodes(String method, String path, String body, String code, String field)
            throws Exception {
        var error = assertRejected(method, path, body, code);
        assertEquals(field, error.path("field").stringValue());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of(
                        "POST",
                        NOTES,
                        "{\"content\":\"Text\",\"color\":\"VIOLET\",\"expectedRevision\":5}",
                        "INVALID_COLOR",
                        "color"),
                Arguments.of(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"ARCHIVED\",\"destinationIndex\":0,\"expectedRevision\":5}",
                        "INVALID_STATUS",
                        "targetStatus"),
                Arguments.of("PUT", WIP, "{\"limit\":\"3\",\"expectedRevision\":5}", "INVALID_WIP_LIMIT", "limit"),
                Arguments.of(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DOING\",\"destinationIndex\":0.5,\"expectedRevision\":5}",
                        "INVALID_INDEX",
                        "destinationIndex"),
                Arguments.of(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DOING\",\"destinationIndex\":2147483648,\"expectedRevision\":5}",
                        "INVALID_INDEX",
                        "destinationIndex"));
    }

    @ParameterizedTest
    @MethodSource("invalidRevisions")
    void rejectsInvalidRevisionsBeforeCallingTheService(String method, String path, String body) throws Exception {
        var error = assertRejected(method, path, body, "INVALID_REVISION");
        assertEquals("expectedRevision", error.path("field").stringValue());
    }

    static Stream<Arguments> invalidRevisions() {
        return Stream.of(
                Arguments.of("POST", NOTE + "/move", "{\"targetStatus\":\"DOING\",\"destinationIndex\":0}"),
                Arguments.of(
                        "POST",
                        NOTE + "/move",
                        "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":-1}"),
                Arguments.of("PUT", WIP, "{\"limit\":3,\"expectedRevision\":-1}"),
                Arguments.of("PUT", NOTE + "/block", "{\"reason\":\"Waiting\",\"expectedRevision\":-1}"),
                Arguments.of("DELETE", NOTE + "/block?expectedRevision=-1", null));
    }

    @ParameterizedTest
    @MethodSource("validNoteBodies")
    void validCommandsTreatNullLikeOmittedFields(String creation, String colorUpdate, String contentUpdate)
            throws Exception {
        var note = new NoteSnapshot("note-1", "È", NoteColor.YELLOW, NoteStatus.TODO, null, List.of());
        var board = new BoardSnapshot("main", 1, 6, 3, List.of(note));
        when(boardService.createNote(5, "È", NoteColor.YELLOW)).thenReturn(board);
        when(boardService.updateNote(5, "note-1", null, NoteColor.BLUE)).thenReturn(board);
        when(boardService.updateNote(5, "note-1", "Renamed", null)).thenReturn(board);

        assertEquals(201, send("POST", NOTES, creation).statusCode());
        assertEquals(200, send("PATCH", NOTE, colorUpdate).statusCode());
        assertEquals(200, send("PATCH", NOTE, contentUpdate).statusCode());
        verify(boardService).createNote(5, "È", NoteColor.YELLOW);
        verify(boardService).updateNote(5, "note-1", null, NoteColor.BLUE);
        verify(boardService).updateNote(5, "note-1", "Renamed", null);
    }

    static Stream<Arguments> validNoteBodies() {
        return Stream.of(
                Arguments.of(
                        "{\"content\":\"È\",\"expectedRevision\":5}",
                        "{\"color\":\"BLUE\",\"expectedRevision\":5}",
                        "{\"content\":\"Renamed\",\"expectedRevision\":5}"),
                Arguments.of(
                        "{\"content\":\"È\",\"color\":null,\"expectedRevision\":5}",
                        "{\"color\":\"BLUE\",\"content\":null,\"expectedRevision\":5}",
                        "{\"content\":\"Renamed\",\"color\":null,\"expectedRevision\":5}"));
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

    private JsonNode assertRejected(String method, String path, String body, String code) throws Exception {
        var response = send(method, path, body);
        assertEquals(400, response.statusCode(), response.body());
        var error = JSON.readTree(response.body());
        assertEquals(code, error.path("code").stringValue());
        assertFalse(error.has("currentRevision"));
        assertFalse(response.body().contains("private payload"));
        assertFalse(error.has("trace"));
        verifyNoInteractions(boardService);
        return error;
    }
}
