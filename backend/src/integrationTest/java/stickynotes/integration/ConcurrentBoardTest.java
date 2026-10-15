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
        var expected = new BoardSnapshot("main", 1, 7, 1, List.of(noteA, noteB));

        try (MongoClient mongo = MongoClients.create(uri)) {
            mongo.getDatabase("admin").runCommand(new Document("ping", 1));
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

                var barrier = new CyclicBarrier(2);

                for (var backend : List.of(first, second)) {
                    MongoBoardRepository realRepository =
                            AopTestUtils.getUltimateTargetObject(backend.getBean(MongoBoardRepository.class));

                    var repositorySpy = spy(realRepository);

                    doAnswer(invocation -> {
                                try {
                                    barrier.await(30, SECONDS);
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

                String moveBody = "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":7}";
                var requestA = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + firstPort + "/api/board/notes/note-a/move"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(moveBody))
                        .build();
                var requestB = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + secondPort + "/api/board/notes/note-b/move"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(moveBody))
                        .build();

                var futureA = http.sendAsync(requestA, HttpResponse.BodyHandlers.ofString());
                var futureB = http.sendAsync(requestB, HttpResponse.BodyHandlers.ofString());

                HttpResponse<String> responseA = futureA.join();
                HttpResponse<String> responseB = futureB.join();

                assertTrue((responseA.statusCode() == 200 && responseB.statusCode() == 409)
                        || (responseA.statusCode() == 409 && responseB.statusCode() == 200));

                boolean aWon = responseA.statusCode() == 200;
                BoardSnapshot expectedAfterMove;
                if (aWon) {
                    assertEquals(409, responseB.statusCode());
                    assertEquals(
                            "REVISION_CONFLICT",
                            JSON.readTree(responseB.body()).path("code").stringValue());
                    NoteSnapshot winnerA = new NoteSnapshot(
                            "note-a", "First note", NoteColor.YELLOW, NoteStatus.DOING, null, List.of());
                    expectedAfterMove = new BoardSnapshot("main", 1, 8, 1, List.of(noteB, winnerA));
                    assertEquals(JSON.valueToTree(expectedAfterMove), JSON.readTree(responseA.body()));
                } else {
                    assertEquals(409, responseA.statusCode());
                    assertEquals(
                            "REVISION_CONFLICT",
                            JSON.readTree(responseA.body()).path("code").stringValue());
                    NoteSnapshot winnerB = new NoteSnapshot(
                            "note-b", "Second note", NoteColor.BLUE, NoteStatus.DOING, null, List.of());
                    expectedAfterMove = new BoardSnapshot("main", 1, 8, 1, List.of(noteA, winnerB));
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
        var expected = new BoardSnapshot("main", 1, 7, 1, List.of(noteA));

        try (MongoClient mongo = MongoClients.create(uri)) {
            mongo.getDatabase("admin").runCommand(new Document("ping", 1));
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

                var barrier = new CyclicBarrier(2);

                for (var backend : List.of(first, second)) {
                    MongoBoardRepository realRepository =
                            AopTestUtils.getUltimateTargetObject(backend.getBean(MongoBoardRepository.class));

                    var repositorySpy = spy(realRepository);

                    doAnswer(invocation -> {
                                try {
                                    barrier.await(30, SECONDS);
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

                String editAAlpha = "{\"content\":\"Alpha\",\"expectedRevision\":7}";
                String editABeta = "{\"content\":\"Beta\",\"expectedRevision\":7}";
                var requestA = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + firstPort + "/api/board/notes/note-a"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .method("PATCH", (HttpRequest.BodyPublishers.ofString(editAAlpha)))
                        .build();
                var requestB = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + secondPort + "/api/board/notes/note-a"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(editABeta))
                        .build();

                var futureA = http.sendAsync(requestA, HttpResponse.BodyHandlers.ofString());
                var futureB = http.sendAsync(requestB, HttpResponse.BodyHandlers.ofString());

                HttpResponse<String> responseA = futureA.join();
                HttpResponse<String> responseB = futureB.join();

                String expectedContent = responseA.statusCode() == 200 ? "Alpha" : "Beta";

                if (expectedContent.equals("Alpha")) {
                    assertEquals(409, responseB.statusCode());
                    NoteSnapshot note = repository.load().get().getNotes().get(0);
                    assertEquals(expectedContent, note.getContent());
                } else {
                    assertEquals(409, responseA.statusCode());
                    NoteSnapshot note = repository.load().get().getNotes().get(0);
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
        var expected = new BoardSnapshot("main", 1, 7, 2, List.of(noteA, noteB));

        try (MongoClient mongo = MongoClients.create(uri)) {
            mongo.getDatabase("admin").runCommand(new Document("ping", 1));
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

                var barrier = new CyclicBarrier(2);

                for (var backend : List.of(first, second)) {
                    MongoBoardRepository realRepository =
                            AopTestUtils.getUltimateTargetObject(backend.getBean(MongoBoardRepository.class));

                    var repositorySpy = spy(realRepository);

                    doAnswer(invocation -> {
                                try {
                                    barrier.await(30, SECONDS);
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

                String limitBody = "{\"limit\":1,\"expectedRevision\":7}";

                var requestA = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + firstPort + "/api/board/wip-limit"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(limitBody))
                        .build();

                String moveBody = "{\"targetStatus\":\"DOING\",\"destinationIndex\":1,\"expectedRevision\":7}";

                var requestB = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + secondPort + "/api/board/notes/note-a/move"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(moveBody))
                        .build();

                var futureA = http.sendAsync(requestA, HttpResponse.BodyHandlers.ofString());
                var futureB = http.sendAsync(requestB, HttpResponse.BodyHandlers.ofString());

                HttpResponse<String> responseA = futureA.join();
                HttpResponse<String> responseB = futureB.join();

                if (responseA.statusCode() == 200) {
                    assertEquals(409, responseB.statusCode());
                    assertEquals(repository.load().get().getWipLimit(), 1);
                    assertEquals(
                            repository.load().get().getNotes().stream()
                                    .filter(x -> x.getStatus().equals(NoteStatus.DOING))
                                    .count(),
                            1);
                    assertEquals(
                    repository.load().get().getNotes().stream()
                            .filter(x -> x.getStatus().equals(NoteStatus.TODO))
                            .count(),
                            1);
                } else {
                    assertEquals(200, responseB.statusCode());
                    assertEquals(409, responseA.statusCode());
                    assertEquals(
                            repository.load().get().getNotes().stream()
                                    .filter(x -> x.getStatus().equals(NoteStatus.DOING))
                                    .count(),
                            2);
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
                        "--server.port=0",
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
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }
}
