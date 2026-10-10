// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import stickynotes.api.dto.SetWipLimitRequest;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;

@RestController
@RequestMapping("/api/board")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public ResponseEntity<BoardSnapshot> getBoard() {
        return ResponseEntity.ok(boardService.getBoard());
    }

    @PutMapping("/wip-limit")
    public ResponseEntity<BoardSnapshot> setWipLimit(@RequestBody SetWipLimitRequest request) {
        if (request == null || request.expectedRevision() == null) {
            throw new BoardServiceException("INVALID_REVISION", "expectedRevision");
        }
        if (request.limit() == null || request.limit() < 1 || request.limit() > 20) {
            throw new BoardServiceException("INVALID_WIP_LIMIT", "limit");
        }
        BoardSnapshot updated = boardService.setWipLimit(request.expectedRevision(), request.limit());
        return ResponseEntity.ok(updated);
    }
}
