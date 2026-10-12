// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import stickynotes.api.dto.AddChecklistItemRequest;
import stickynotes.api.dto.UpdateChecklistItemRequest;
import stickynotes.application.BoardService;
import stickynotes.application.BoardServiceException;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;

class ChecklistControllerTest {

    private BoardService boardService;
    private ChecklistController controller;

    @BeforeEach
    void setUp() {
        boardService = mock(BoardService.class);
        controller = new ChecklistController(boardService);
    }

    @Test
    void creationReturnsTheAppendedItemIdFromTheTargetNote() {
        var board = new BoardSnapshot(
                "main",
                1,
                6,
                3,
                List.of(
                        new NoteSnapshot(
                                "note-1",
                                "Target note",
                                NoteColor.YELLOW,
                                NoteStatus.TODO,
                                null,
                                List.of(
                                        new ChecklistItemSnapshot("existing-item", "Existing item", true),
                                        new ChecklistItemSnapshot("created-item", "New item", false))),
                        new NoteSnapshot(
                                "note-2",
                                "Other note",
                                NoteColor.BLUE,
                                NoteStatus.DOING,
                                null,
                                List.of(new ChecklistItemSnapshot("other-item", "Other item", false)))));
        when(boardService.addItem(5, "note-1", "New item")).thenReturn(board);

        var response = controller.addItem("note-1", new AddChecklistItemRequest("New item", 5));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(board, response.getBody().board());
        assertEquals("created-item", response.getBody().createdItemId());
    }

    @ParameterizedTest
    @MethodSource("updates")
    void updatesPreserveOmittedFieldsAndExplicitFalse(UpdateChecklistItemRequest request) {
        var board = new BoardSnapshot("main", 1, 6, 3, List.of());
        when(boardService.updateItem(5, "note-1", "item-1", request.label(), request.completed()))
                .thenReturn(board);

        var response = controller.updateItem("note-1", "item-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(board, response.getBody());
        verify(boardService).updateItem(5, "note-1", "item-1", request.label(), request.completed());
    }

    static Stream<UpdateChecklistItemRequest> updates() {
        return Stream.of(
                new UpdateChecklistItemRequest("Renamed item", null, 5),
                new UpdateChecklistItemRequest(null, false, 5),
                new UpdateChecklistItemRequest("Renamed item", true, 5));
    }

    @Test
    void deletionReturnsTheSavedBoard() {
        var board = new BoardSnapshot("main", 1, 6, 3, List.of());
        when(boardService.deleteItem(5, "note-1", "item-1")).thenReturn(board);

        var response = controller.deleteItem("note-1", "item-1", 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(board, response.getBody());
        verify(boardService).deleteItem(5, "note-1", "item-1");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = -1)
    void everyCommandRejectsMissingOrNegativeRevisionBeforeCallingTheService(Integer revision) {
        var addFailure = assertThrows(
                BoardServiceException.class,
                () -> controller.addItem("note-1", new AddChecklistItemRequest("Item", revision)));
        var updateFailure = assertThrows(
                BoardServiceException.class,
                () -> controller.updateItem("note-1", "item-1", new UpdateChecklistItemRequest(null, true, revision)));
        var deleteFailure =
                assertThrows(BoardServiceException.class, () -> controller.deleteItem("note-1", "item-1", revision));

        for (var failure : List.of(addFailure, updateFailure, deleteFailure)) {
            assertEquals("INVALID_REVISION", failure.getCode());
            assertEquals("expectedRevision", failure.getField());
        }
        verifyNoInteractions(boardService);
    }

    @Test
    void creationRejectsMissingLabelBeforeCallingTheService() {
        var failure = assertThrows(
                BoardServiceException.class, () -> controller.addItem("note-1", new AddChecklistItemRequest(null, 5)));

        assertEquals("INVALID_LABEL", failure.getCode());
        assertEquals("label", failure.getField());
        verifyNoInteractions(boardService);
    }

    @Test
    void updateRejectsARequestWithNoMutableFieldsBeforeCallingTheService() {
        var failure = assertThrows(
                BoardServiceException.class,
                () -> controller.updateItem("note-1", "item-1", new UpdateChecklistItemRequest(null, null, 5)));

        assertEquals("INVALID_REQUEST", failure.getCode());
        verifyNoInteractions(boardService);
    }
}
