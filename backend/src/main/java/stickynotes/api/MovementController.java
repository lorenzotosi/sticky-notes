// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import stickynotes.api.dto.MoveNoteRequest;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;

@RestController
@RequestMapping("/api/board/notes")
public class MovementController {

    private final BoardService boardService;

    public MovementController(BoardService boardService) {
        this.boardService = boardService;
    }

    @PostMapping("/{id}/move")
    public ResponseEntity<BoardSnapshot> moveNote(
            @PathVariable("id") String noteId, @RequestBody MoveNoteRequest request) {
        if (request == null || request.expectedRevision() == null) {
            throw new BoardServiceException("INVALID_REVISION", "expectedRevision");
        }
        if (request.targetStatus() == null) {
            throw new BoardServiceException("INVALID_STATUS", "targetStatus");
        }
        if (request.destinationIndex() == null || request.destinationIndex() < 0) {
            throw new BoardServiceException("INVALID_INDEX", "destinationIndex");
        }
        BoardSnapshot updated = boardService.moveNote(
                request.expectedRevision(), noteId, request.targetStatus(), request.destinationIndex());
        return ResponseEntity.ok(updated);
    }
}
