// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import stickynotes.api.dto.BlockNoteRequest;
import stickynotes.api.dto.MoveNoteRequest;
import stickynotes.api.dto.SetWipLimitRequest;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;
import stickynotes.domain.NoteStatus;

class KanbanCommandsTest {

    private BoardService boardService;
    private MovementController movementController;
    private BlockController blockController;
    private BoardController boardController;

    @BeforeEach
    void setUp() {
        boardService = mock(BoardService.class);
        movementController = new MovementController(boardService);
        blockController = new BlockController(boardService);
        boardController = new BoardController(boardService);
    }

    @Test
    void moveNoteSuccessfullyUpdatesBoard() {
        BoardSnapshot updated = new BoardSnapshot("main", 1, 1, 3, Collections.emptyList());
        when(boardService.moveNote(eq(0), eq("note-1"), eq(NoteStatus.DOING), eq(0)))
                .thenReturn(updated);

        MoveNoteRequest request = new MoveNoteRequest(NoteStatus.DOING, 0, 0);
        ResponseEntity<BoardSnapshot> response = movementController.moveNote("note-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals(1, response.getBody().getRevision());
        verify(boardService).moveNote(0, "note-1", NoteStatus.DOING, 0);
    }

    @Test
    void moveNoteRejectsMissingRevision() {
        MoveNoteRequest request = new MoveNoteRequest(NoteStatus.DOING, 0, null);
        BoardServiceException ex =
                assertThrows(BoardServiceException.class, () -> movementController.moveNote("note-1", request));
        assertEquals("INVALID_REVISION", ex.getCode());
    }

    @Test
    void moveNoteRejectsNegativeIndex() {
        MoveNoteRequest request = new MoveNoteRequest(NoteStatus.DOING, -1, 0);
        BoardServiceException ex =
                assertThrows(BoardServiceException.class, () -> movementController.moveNote("note-1", request));
        assertEquals("INVALID_INDEX", ex.getCode());
    }

    @Test
    void moveNotePropagatesDomainExceptionOnWipLimitReached() {
        when(boardService.moveNote(anyInt(), anyString(), any(), anyInt()))
                .thenThrow(new BoardServiceException("WIP_LIMIT_REACHED", null));

        MoveNoteRequest request = new MoveNoteRequest(NoteStatus.DOING, 0, 0);
        BoardServiceException ex =
                assertThrows(BoardServiceException.class, () -> movementController.moveNote("note-1", request));
        assertEquals("WIP_LIMIT_REACHED", ex.getCode());
    }

    @Test
    void setWipLimitSuccessfullyUpdatesBoard() {
        BoardSnapshot updated = new BoardSnapshot("main", 1, 2, 5, Collections.emptyList());
        when(boardService.setWipLimit(eq(1), eq(5))).thenReturn(updated);

        SetWipLimitRequest request = new SetWipLimitRequest(5, 1);
        ResponseEntity<BoardSnapshot> response = boardController.setWipLimit(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals(5, response.getBody().getWipLimit());
        verify(boardService).setWipLimit(1, 5);
    }

    @Test
    void setWipLimitRejectsInvalidLimit() {
        SetWipLimitRequest request = new SetWipLimitRequest(25, 1);
        BoardServiceException ex =
                assertThrows(BoardServiceException.class, () -> boardController.setWipLimit(request));
        assertEquals("INVALID_WIP_LIMIT", ex.getCode());
    }

    @Test
    void blockNoteSuccessfullyUpdatesBoard() {
        BoardSnapshot updated = new BoardSnapshot("main", 1, 3, 3, Collections.emptyList());
        when(boardService.blockNote(eq(2), eq("note-1"), eq("Waiting on review")))
                .thenReturn(updated);

        BlockNoteRequest request = new BlockNoteRequest("Waiting on review", 2);
        ResponseEntity<BoardSnapshot> response = blockController.blockNote("note-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals(3, response.getBody().getRevision());
        verify(boardService).blockNote(2, "note-1", "Waiting on review");
    }

    @Test
    void blockNoteRejectsBlankReason() {
        BlockNoteRequest request = new BlockNoteRequest("   ", 2);
        BoardServiceException ex =
                assertThrows(BoardServiceException.class, () -> blockController.blockNote("note-1", request));
        assertEquals("INVALID_BLOCK_REASON", ex.getCode());
    }

    @Test
    void unblockNoteSuccessfullyUpdatesBoard() {
        BoardSnapshot updated = new BoardSnapshot("main", 1, 4, 3, Collections.emptyList());
        when(boardService.unblockNote(eq(3), eq("note-1"))).thenReturn(updated);

        ResponseEntity<BoardSnapshot> response = blockController.unblockNote("note-1", 3);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals(4, response.getBody().getRevision());
        verify(boardService).unblockNote(3, "note-1");
    }

    @Test
    void unblockNoteRejectsMissingRevision() {
        BoardServiceException ex =
                assertThrows(BoardServiceException.class, () -> blockController.unblockNote("note-1", null));
        assertEquals("INVALID_REVISION", ex.getCode());
    }
}
