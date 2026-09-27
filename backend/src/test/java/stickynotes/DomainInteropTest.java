// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.CommandResponse;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import stickynotes.interop.JvmBoardFacade;

class DomainInteropTest {

  @Test
  void rejectsMoveToDoingWhenWipLimitIsReached() {
    BoardSnapshot board = JvmBoardFacade.createEmptyBoard("main", 1);

    board = executeSuccess(board, JvmBoardFacade.createNote("n1", "First task", NoteColor.YELLOW));
    board = executeSuccess(board, JvmBoardFacade.createNote("n2", "Second task", NoteColor.BLUE));

    board = executeSuccess(board, JvmBoardFacade.moveNote("n1", NoteStatus.DOING, 0));

    CommandResponse response =
        JvmBoardFacade.execute(board, JvmBoardFacade.moveNote("n2", NoteStatus.DOING, 1));

    CommandResponse.Failure failure = assertInstanceOf(CommandResponse.Failure.class, response);
    assertEquals("WIP_LIMIT_REACHED", failure.getCode());
  }

  @Test
  void rejectsMoveToDoneWhenChecklistIsIncomplete() {
    BoardSnapshot board = JvmBoardFacade.createEmptyBoard("main", 2);

    board =
        executeSuccess(
            board, JvmBoardFacade.createNote("n1", "Task with checklist", NoteColor.YELLOW));
    board = executeSuccess(board, JvmBoardFacade.addItem("n1", "item-1", "Pending task"));
    board = executeSuccess(board, JvmBoardFacade.moveNote("n1", NoteStatus.DOING, 0));

    CommandResponse response =
        JvmBoardFacade.execute(board, JvmBoardFacade.moveNote("n1", NoteStatus.DONE, 0));

    CommandResponse.Failure failure = assertInstanceOf(CommandResponse.Failure.class, response);
    assertEquals("CHECKLIST_INCOMPLETE", failure.getCode());
  }

  @Test
  void rejectsWipLimitBelowCurrentDoingOccupancy() {
    BoardSnapshot board = JvmBoardFacade.createEmptyBoard("main", 3);

    board = executeSuccess(board, JvmBoardFacade.createNote("n1", "Task 1", NoteColor.YELLOW));
    board = executeSuccess(board, JvmBoardFacade.createNote("n2", "Task 2", NoteColor.BLUE));

    board = executeSuccess(board, JvmBoardFacade.moveNote("n1", NoteStatus.DOING, 0));
    board = executeSuccess(board, JvmBoardFacade.moveNote("n2", NoteStatus.DOING, 1));

    CommandResponse response = JvmBoardFacade.execute(board, JvmBoardFacade.setWipLimit(1));

    CommandResponse.Failure failure = assertInstanceOf(CommandResponse.Failure.class, response);
    assertEquals("WIP_BELOW_OCCUPANCY", failure.getCode());
  }

  private BoardSnapshot executeSuccess(
      BoardSnapshot snapshot, stickynotes.contract.BoardCommand command) {
    CommandResponse response = JvmBoardFacade.execute(snapshot, command);
    CommandResponse.Success success = assertInstanceOf(CommandResponse.Success.class, response);
    return success.getBoard();
  }
}
