// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReplaceOptions;
import org.springframework.data.mongodb.core.query.Query;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;

class MongoBoardRepositoryTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final MongoBoardRepository repository = new MongoBoardRepository(mongoTemplate);

    @Test
    void loadsValidatedSnapshot() {
        BoardSnapshot expected = snapshot(7);
        when(mongoTemplate.findById("main", BoardDocument.class)).thenReturn(BoardDocumentMapper.toDocument(expected));

        assertEquals(Optional.of(expected), repository.load());

        verify(mongoTemplate).findById("main", BoardDocument.class);
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void returnsEmptyWhenBoardIsMissing() {
        when(mongoTemplate.findById("main", BoardDocument.class)).thenReturn(null);

        assertEquals(Optional.empty(), repository.load());

        verify(mongoTemplate).findById("main", BoardDocument.class);
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void rejectsInvalidPersistedBoardWithoutRepairingIt() {
        when(mongoTemplate.findById("main", BoardDocument.class))
                .thenReturn(new BoardDocument("main", 1, -1, 4, List.of()));

        assertThrows(IllegalArgumentException.class, repository::load);

        verify(mongoTemplate).findById("main", BoardDocument.class);
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void wrapsLoadFailureAtTheRepositoryBoundary() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Database unavailable");
        when(mongoTemplate.findById("main", BoardDocument.class)).thenThrow(failure);

        IllegalStateException error = assertThrows(IllegalStateException.class, repository::load);
        assertSame(failure, error.getCause());

        verify(mongoTemplate).findById("main", BoardDocument.class);
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void savesCompleteSnapshotWithExpectedRevisionAndWithoutUpsert() {
        BoardSnapshot updated = snapshot(7);
        BoardSnapshot expected = snapshot(8);
        replacementReturns(UpdateResult.acknowledged(1, 1L, null));

        assertEquals(Optional.of(expected), repository.save(updated, 7));
        assertEquals(7, updated.getRevision());

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<BoardDocument> document = ArgumentCaptor.forClass(BoardDocument.class);
        ArgumentCaptor<ReplaceOptions> options = ArgumentCaptor.forClass(ReplaceOptions.class);
        verify(mongoTemplate).replace(query.capture(), document.capture(), options.capture(), eq("boards"));
        assertEquals(
                new Document("_id", "main").append("revision", 7),
                query.getValue().getQueryObject());
        assertEquals(BoardDocumentMapper.toDocument(expected), document.getValue());
        assertFalse(options.getValue().isUpsert());
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void returnsConflictWithoutRetryWhenRevisionDoesNotMatch() {
        replacementReturns(UpdateResult.acknowledged(0, 0L, null));

        assertEquals(Optional.empty(), repository.save(snapshot(7), 7));

        verify(mongoTemplate)
                .replace(any(Query.class), any(BoardDocument.class), any(ReplaceOptions.class), eq("boards"));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void rejectsUnacknowledgedWrite() {
        replacementReturns(UpdateResult.unacknowledged());

        assertThrows(IllegalStateException.class, () -> repository.save(snapshot(7), 7));

        verify(mongoTemplate)
                .replace(any(Query.class), any(BoardDocument.class), any(ReplaceOptions.class), eq("boards"));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void rejectsRevisionOverflowBeforeAccessingDatabase() {
        assertThrows(ArithmeticException.class, () -> repository.save(snapshot(Integer.MAX_VALUE), Integer.MAX_VALUE));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejectsDifferentBoardBeforeAccessingDatabase() {
        BoardSnapshot updated = new BoardSnapshot("other", 1, 7, 4, List.of());

        assertThrows(IllegalArgumentException.class, () -> repository.save(updated, 7));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejectsSnapshotRevisionMismatchBeforeAccessingDatabase() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(snapshot(7), 6));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejectsNegativeRevisionBeforeAccessingDatabase() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(snapshot(-1), -1));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejectsInvalidSnapshotBeforeAccessingDatabase() {
        BoardSnapshot updated = new BoardSnapshot("main", 1, 7, 0, List.of());

        assertThrows(IllegalArgumentException.class, () -> repository.save(updated, 7));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void wrapsSaveFailureAtTheRepositoryBoundaryWithoutRetry() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Database unavailable");
        when(mongoTemplate.replace(any(Query.class), any(BoardDocument.class), any(ReplaceOptions.class), eq("boards")))
                .thenThrow(failure);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> repository.save(snapshot(7), 7));
        assertSame(failure, error.getCause());

        verify(mongoTemplate)
                .replace(any(Query.class), any(BoardDocument.class), any(ReplaceOptions.class), eq("boards"));
        verifyNoMoreInteractions(mongoTemplate);
    }

    private void replacementReturns(UpdateResult result) {
        when(mongoTemplate.replace(any(Query.class), any(BoardDocument.class), any(ReplaceOptions.class), eq("boards")))
                .thenReturn(result);
    }

    private static BoardSnapshot snapshot(int revision) {
        return new BoardSnapshot(
                "main",
                1,
                revision,
                4,
                List.of(
                        new NoteSnapshot("note-2", "Verify API", NoteColor.BLUE, NoteStatus.TODO, null, List.of()),
                        new NoteSnapshot(
                                "note-1",
                                "Configure build",
                                NoteColor.PINK,
                                NoteStatus.DOING,
                                "Missing configuration",
                                List.of(new ChecklistItemSnapshot("item-1", "Verify configuration", false)))));
    }
}
