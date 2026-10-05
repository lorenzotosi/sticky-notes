// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class BoardBootstrapTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final BoardBootstrap bootstrap = new BoardBootstrap(mongoTemplate);
    private final DefaultApplicationArguments arguments = new DefaultApplicationArguments();

    @Test
    void usesInsertOnlyUpsertOnEveryStartup() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(BoardDocument.class)))
                .thenReturn(
                        UpdateResult.acknowledged(0, 0L, new BsonString("main")),
                        UpdateResult.acknowledged(1, 0L, null));

        bootstrap.run(arguments);
        bootstrap.run(arguments);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).upsert(query.capture(), update.capture(), eq(BoardDocument.class));
        for (Query captured : query.getAllValues()) {
            assertEquals(new Document("_id", "main"), captured.getQueryObject());
        }
        Document initialFields = new Document("schemaVersion", 1)
                .append("revision", 0)
                .append("wipLimit", 3)
                .append("notes", List.of());
        for (Update captured : update.getAllValues()) {
            assertEquals(new Document("$setOnInsert", initialFields), captured.getUpdateObject());
        }
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void toleratesConcurrentStartupWithoutRetryOrReset() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(BoardDocument.class)))
                .thenThrow(new DuplicateKeyException("main already exists"));

        assertDoesNotThrow(() -> bootstrap.run(arguments));

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(BoardDocument.class));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void rejectsUnacknowledgedInitialization() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(BoardDocument.class)))
                .thenReturn(UpdateResult.unacknowledged());

        assertThrows(IllegalStateException.class, () -> bootstrap.run(arguments));

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(BoardDocument.class));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void allowsStartupWhenDatabaseIsUnavailableWithoutRetry() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(BoardDocument.class)))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        assertDoesNotThrow(() -> bootstrap.run(arguments));

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(BoardDocument.class));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void propagatesOtherDatabaseErrors() {
        InvalidDataAccessApiUsageException failure = new InvalidDataAccessApiUsageException("Invalid update");
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(BoardDocument.class)))
                .thenThrow(failure);

        assertSame(failure, assertThrows(InvalidDataAccessApiUsageException.class, () -> bootstrap.run(arguments)));

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(BoardDocument.class));
        verifyNoMoreInteractions(mongoTemplate);
    }
}
