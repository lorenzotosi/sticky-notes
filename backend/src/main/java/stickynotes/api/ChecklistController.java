// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

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
import stickynotes.api.dto.AddChecklistItemRequest;
import stickynotes.api.dto.AddChecklistItemResponse;
import stickynotes.api.dto.UpdateChecklistItemRequest;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.NoteSnapshot;

@RestController
@RequestMapping("/api/board/notes/{id}/checklist")
public class ChecklistController {

    private final BoardService boardService;

    public ChecklistController(BoardService boardService) {
        this.boardService = boardService;
    }

    @PostMapping
    public ResponseEntity<AddChecklistItemResponse> addItem(
            @PathVariable("id") String noteId, @RequestBody AddChecklistItemRequest request) {
        int expectedRevision = requireRevision(request.expectedRevision());
        if (request.label() == null) {
            throw new BoardServiceException("INVALID_LABEL", "label");
        }

        BoardSnapshot updated = boardService.addItem(expectedRevision, noteId, request.label());
        NoteSnapshot note = updated.getNotes().stream()
                .filter(candidate -> candidate.getId().equals(noteId))
                .findFirst()
                .orElseThrow();
        String createdItemId = note.getChecklist().getLast().getId();

        return ResponseEntity.status(HttpStatus.CREATED).body(new AddChecklistItemResponse(updated, createdItemId));
    }

    @PatchMapping("/{itemId}")
    public ResponseEntity<BoardSnapshot> updateItem(
            @PathVariable("id") String noteId,
            @PathVariable("itemId") String itemId,
            @RequestBody UpdateChecklistItemRequest request) {
        int expectedRevision = requireRevision(request.expectedRevision());
        if (request.label() == null && request.completed() == null) {
            throw new BoardServiceException("INVALID_REQUEST", null);
        }
        requireItemId(itemId);

        BoardSnapshot updated =
                boardService.updateItem(expectedRevision, noteId, itemId, request.label(), request.completed());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<BoardSnapshot> deleteItem(
            @PathVariable("id") String noteId,
            @PathVariable("itemId") String itemId,
            @RequestParam(name = "expectedRevision", required = false) Integer expectedRevision) {
        int revision = requireRevision(expectedRevision);
        requireItemId(itemId);
        BoardSnapshot updated = boardService.deleteItem(revision, noteId, itemId);
        return ResponseEntity.ok(updated);
    }

    private static void requireItemId(String itemId) {
        // Match the shared Kotlin domain's definition of a blank identifier.
        if (itemId.chars().allMatch(value -> Character.isWhitespace(value) || Character.isSpaceChar(value))) {
            throw new BoardServiceException("INVALID_REQUEST", "itemId");
        }
    }

    private static int requireRevision(Integer expectedRevision) {
        if (expectedRevision == null || expectedRevision < 0) {
            throw new BoardServiceException("INVALID_REVISION", "expectedRevision");
        }
        return expectedRevision;
    }
}
