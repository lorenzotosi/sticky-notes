// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.uri=mongodb://127.0.0.1:1/sticky_notes_test"
                + "?connectTimeoutMS=500&serverSelectionTimeoutMS=500")
@ActiveProfiles("test")
class MainTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void livenessIsUpWithoutDatabase() throws Exception {
        final var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health/liveness"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try (final var client = HttpClient.newHttpClient()) {
            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());

            final var body = JsonMapper.builder().build().readTree(response.body());
            assertEquals("UP", body.path("status").asString());
        }
    }
}
