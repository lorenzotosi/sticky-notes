// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import stickynotes.api.dto.BlockNoteRequest;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;

@RestController
@RequestMapping("/api/board/notes")
public class BlockController {

    private final BoardService boardService;

    public BlockController(BoardService boardService) {
        this.boardService = boardService;
    }

    @PutMapping("/{id}/block")
    public ResponseEntity<BoardSnapshot> blockNote(
            @PathVariable("id") String noteId, @RequestBody BlockNoteRequest request) {
        if (request == null || request.expectedRevision() == null) {
            throw new BoardServiceException("INVALID_REVISION", "expectedRevision");
        }
        if (request.reason() == null
                || request.reason().isBlank()
                || request.reason().length() > 200) {
            throw new BoardServiceException("INVALID_BLOCK_REASON", "reason");
        }
        BoardSnapshot updated = boardService.blockNote(request.expectedRevision(), noteId, request.reason());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/block")
    public ResponseEntity<BoardSnapshot> unblockNote(
            @PathVariable("id") String noteId, @RequestParam("expectedRevision") Integer expectedRevision) {
        if (expectedRevision == null) {
            throw new BoardServiceException("INVALID_REVISION", "expectedRevision");
        }
        BoardSnapshot updated = boardService.unblockNote(expectedRevision, noteId);
        return ResponseEntity.ok(updated);
    }
}
