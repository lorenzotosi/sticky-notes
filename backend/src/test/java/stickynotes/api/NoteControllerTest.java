// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import stickynotes.api.dto.CreateNoteRequest;
import stickynotes.api.dto.CreateNoteResponse;
import stickynotes.api.dto.UpdateNoteRequest;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;

class NoteControllerTest {

    private BoardService boardService;
    private BoardController boardController;
    private NoteController noteController;

    @BeforeEach
    void setUp() {
        boardService = mock(BoardService.class);
        boardController = new BoardController(boardService);
        noteController = new NoteController(boardService);
    }

    @Test
    void getBoardReturnsSnapshot() {
        BoardSnapshot snapshot = new BoardSnapshot("main", 1, 0, 3, Collections.emptyList());
        when(boardService.getBoard()).thenReturn(snapshot);

        ResponseEntity<BoardSnapshot> response = boardController.getBoard();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(snapshot, response.getBody());
    }

    @Test
    void createNoteReturnsCreatedResponse() {
        NoteSnapshot created = new NoteSnapshot(
                "uuid-1", "Note text", NoteColor.YELLOW, NoteStatus.TODO, null, Collections.emptyList());
        BoardSnapshot updated = new BoardSnapshot("main", 1, 1, 3, List.of(created));

        when(boardService.createNote(eq(0), eq("Note text"), any(NoteColor.class)))
                .thenReturn(updated);

        CreateNoteRequest request = new CreateNoteRequest("Note text", NoteColor.YELLOW, 0);
        ResponseEntity<CreateNoteResponse> response = noteController.createNote(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals("uuid-1", response.getBody().createdNoteId());
        assertEquals(1, response.getBody().board().getRevision());
    }

    @Test
    void createNoteRejectsMissingRevision() {
        CreateNoteRequest request = new CreateNoteRequest("Note text", NoteColor.YELLOW, null);
        BoardServiceException ex = assertThrows(BoardServiceException.class, () -> noteController.createNote(request));
        assertEquals("INVALID_REVISION", ex.getCode());
    }

    @Test
    void updateNoteReturnsUpdatedSnapshot() {
        BoardSnapshot updated = new BoardSnapshot("main", 1, 2, 3, Collections.emptyList());
        when(boardService.updateNote(eq(1), eq("note-1"), eq("New text"), any()))
                .thenReturn(updated);

        UpdateNoteRequest request = new UpdateNoteRequest("New text", null, 1);
        ResponseEntity<BoardSnapshot> response = noteController.updateNote("note-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals(2, response.getBody().getRevision());
    }

    @Test
    void updateNoteRejectsPayloadWithNoChanges() {
        UpdateNoteRequest request = new UpdateNoteRequest(null, null, 1);
        BoardServiceException ex =
                assertThrows(BoardServiceException.class, () -> noteController.updateNote("note-1", request));
        assertEquals("INVALID_REQUEST", ex.getCode());
    }

    @Test
    void deleteNoteReturnsUpdatedSnapshot() {
        BoardSnapshot updated = new BoardSnapshot("main", 1, 3, 3, Collections.emptyList());
        when(boardService.deleteNote(eq(2), eq("note-1"))).thenReturn(updated);

        ResponseEntity<BoardSnapshot> response = noteController.deleteNote("note-1", 2);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals(3, response.getBody().getRevision());
    }

    @Test
    void deleteNoteRejectsMissingRevision() {
        BoardServiceException ex =
                assertThrows(BoardServiceException.class, () -> noteController.deleteNote("note-1", null));
        assertEquals("INVALID_REVISION", ex.getCode());
    }
}
