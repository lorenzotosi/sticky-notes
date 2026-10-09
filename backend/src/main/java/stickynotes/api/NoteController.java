// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import stickynotes.api.dto.CreateNoteRequest;
import stickynotes.api.dto.CreateNoteResponse;
import stickynotes.api.dto.UpdateNoteRequest;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;

@RestController
@RequestMapping("/api/board/notes")
public class NoteController {

    private final BoardService boardService;

    public NoteController(BoardService boardService) {
        this.boardService = boardService;
    }

    @PostMapping
    public ResponseEntity<CreateNoteResponse> createNote(@RequestBody CreateNoteRequest request) {
        if (request.expectedRevision() == null || request.expectedRevision() < 0) {
            throw new BoardServiceException("INVALID_REVISION", "expectedRevision");
        }
        if (request.content() == null) {
            throw new BoardServiceException("INVALID_CONTENT", "content");
        }

        NoteColor color = request.color() != null ? request.color() : NoteColor.YELLOW;
        BoardSnapshot updatedBoard = boardService.createNote(request.expectedRevision(), request.content(), color);

        List<NoteSnapshot> notes = updatedBoard.getNotes();
        NoteSnapshot createdNote = notes.getLast();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateNoteResponse(updatedBoard, createdNote.getId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BoardSnapshot> updateNote(
            @PathVariable("id") String noteId, @RequestBody UpdateNoteRequest request) {
        if (request.expectedRevision() == null || request.expectedRevision() < 0) {
            throw new BoardServiceException("INVALID_REVISION", "expectedRevision");
        }
        if (request.content() == null && request.color() == null) {
            throw new BoardServiceException("INVALID_REQUEST", null);
        }

        BoardSnapshot updatedBoard =
                boardService.updateNote(request.expectedRevision(), noteId, request.content(), request.color());
        return ResponseEntity.ok(updatedBoard);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BoardSnapshot> deleteNote(
            @PathVariable("id") String noteId,
            @RequestParam(name = "expectedRevision", required = false) Integer expectedRevision) {
        if (expectedRevision == null || expectedRevision < 0) {
            throw new BoardServiceException("INVALID_REVISION", "expectedRevision");
        }

        BoardSnapshot updatedBoard = boardService.deleteNote(expectedRevision, noteId);
        return ResponseEntity.ok(updatedBoard);
    }
}
