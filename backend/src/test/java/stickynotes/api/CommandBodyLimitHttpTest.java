// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.uri=mongodb://127.0.0.1:1/sticky_notes_test"
                + "?connectTimeoutMS=500&serverSelectionTimeoutMS=500")
@ActiveProfiles("test")
class CommandBodyLimitHttpTest {

    private static final int LIMIT = 65_536;
    private static final String CHECKLIST_ITEM = "/api/board/notes/note-1/checklist/item-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private BoardService boardService;

    @ParameterizedTest
    @MethodSource("commandRoutes")
    void rejectsOversizedContentLengthBeforeCallingTheService(String method, String path) throws Exception {
        assertTooLarge(send(method, path, paddedBody(LIMIT + 1), false));
    }

    static Stream<Arguments> commandRoutes() {
        return Stream.of(
                Arguments.of("POST", "/api/board/notes"),
                Arguments.of("PUT", "/api/board/wip-limit"),
                Arguments.of("PATCH", CHECKLIST_ITEM),
                Arguments.of("DELETE", CHECKLIST_ITEM + "?expectedRevision=5"));
    }

    @Test
    void rejectsOversizedChunkedBodyInBytesBeforeCallingTheService() throws Exception {
        byte[] body = paddedBody(LIMIT + 1);
        assertEquals(LIMIT, new String(body, StandardCharsets.UTF_8).length());

        assertTooLarge(send("PATCH", CHECKLIST_ITEM, body, true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsExactly64KiBWithContentLengthOrChunkedTransfer(boolean chunked) throws Exception {
        when(boardService.updateItem(5, "note-1", "item-1", "È", null))
                .thenReturn(new BoardSnapshot("main", 1, 6, 3, List.of()));

        var response = send("PATCH", CHECKLIST_ITEM, paddedBody(LIMIT), chunked);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(6, JSON.readTree(response.body()).path("revision").intValue());
        verify(boardService).updateItem(5, "note-1", "item-1", "È", null);
    }

    private static byte[] paddedBody(int bytes) {
        String json = "{\"label\":\"È\",\"expectedRevision\":5}";
        return (json + " ".repeat(bytes - json.getBytes(StandardCharsets.UTF_8).length))
                .getBytes(StandardCharsets.UTF_8);
    }

    private HttpResponse<String> send(String method, String path, byte[] body, boolean chunked) throws Exception {
        var publisher = chunked
                ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))
                : HttpRequest.BodyPublishers.ofByteArray(body);
        assertEquals(chunked ? -1 : body.length, publisher.contentLength());
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private void assertTooLarge(HttpResponse<String> response) {
        assertEquals(413, response.statusCode(), response.body());
        assertEquals(
                "application/json",
                response.headers().firstValue("Content-Type").orElseThrow());
        var error = JSON.readTree(response.body());
        assertEquals("PAYLOAD_TOO_LARGE", error.path("code").stringValue());
        assertEquals("Request body exceeds 64 KiB.", error.path("message").stringValue());
        assertFalse(error.has("field"));
        assertFalse(error.has("currentRevision"));
        verifyNoInteractions(boardService);
    }
}
