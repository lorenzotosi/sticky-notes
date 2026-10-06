// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import stickynotes.application.BoardRepository;
import stickynotes.contract.BoardCommand;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.CommandResponse;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import stickynotes.interop.JvmBoardFacade;

public final class BoardServiceImpl implements BoardService {

    private final BoardRepository repository;

    public BoardServiceImpl(BoardRepository repository) {
        this.repository = repository;
    }

    public BoardSnapshot createNote(int expectedRevision, String content, NoteColor color) {

        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        String id = UUID.randomUUID().toString();

        BoardCommand.CreateNote createCommand = JvmBoardFacade.createNote(id, content, color);

        CommandResponse response = JvmBoardFacade.execute(board, createCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot updateNote(int expectedRevision, String noteId, String content, NoteColor color) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.UpdateNote updateCommand = JvmBoardFacade.updateNote(noteId, content, color);
        CommandResponse response = JvmBoardFacade.execute(board, updateCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot deleteNote(int expectedRevision, String noteId) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.DeleteNote deleteCommand = JvmBoardFacade.deleteNote(noteId);
        CommandResponse response = JvmBoardFacade.execute(board, deleteCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot moveNote(int expectedRevision, String noteId, NoteStatus targetStatus, int destinationIndex) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.MoveNote moveCommand = JvmBoardFacade.moveNote(noteId, targetStatus, destinationIndex);
        CommandResponse response = JvmBoardFacade.execute(board, moveCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot setWipLimit(int expectedRevision, int limit) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.SetWipLimit setWipLimitCommand = JvmBoardFacade.setWipLimit(limit);
        CommandResponse response = JvmBoardFacade.execute(board, setWipLimitCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot blockNote(int expectedRevision, String noteId, String reason) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.BlockNote blockNoteCommand = JvmBoardFacade.blockNote(noteId, reason);
        CommandResponse response = JvmBoardFacade.execute(board, blockNoteCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot unblockNote(int expectedRevision, String noteId) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.UnblockNote unblockNoteCommand = JvmBoardFacade.unblockNote(noteId);
        CommandResponse response = JvmBoardFacade.execute(board, unblockNoteCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot addItem(int expectedRevision, String noteId, String label) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        String itemId = UUID.randomUUID().toString();

        BoardCommand.AddItem addItemCommand = JvmBoardFacade.addItem(noteId, itemId, label);
        CommandResponse response = JvmBoardFacade.execute(board, addItemCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot updateItem(
            int expectedRevision, String noteId, String itemId, String label, Boolean completed) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.UpdateItem updateItemCommand = JvmBoardFacade.updateItem(noteId, itemId, label, completed);
        CommandResponse response = JvmBoardFacade.execute(board, updateItemCommand);

        return saveBoard(boardRevision, response);
    }

    @Override
    public BoardSnapshot deleteItem(int expectedRevision, String noteId, String itemId) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.DeleteItem deleteItemCommand = JvmBoardFacade.deleteItem(noteId, itemId);
        CommandResponse response = JvmBoardFacade.execute(board, deleteItemCommand);

        return saveBoard(boardRevision, response);
    }

    private BoardSnapshot getBoard() {
        Optional<BoardSnapshot> board;
        try {
            board = repository.load();
        } catch (DataAccessException | IllegalStateException exception) {
            throw new BoardServiceException("DATABASE_UNAVAILABLE", null);
        }
        if (board.isEmpty()) {
            throw new RuntimeException("Board not found");
        } else {
            return board.get();
        }
    }

    private BoardSnapshot saveBoard(int boardRevision, CommandResponse response) {
        if (response instanceof CommandResponse.Success success) {
            BoardSnapshot updated = success.getBoard();
            Optional<BoardSnapshot> saved;
            try {
                saved = repository.save(updated, boardRevision);
            } catch (ArithmeticException exception) {
                throw new BoardServiceException("REVISION_OVERFLOW", null);
            } catch (DataAccessException | IllegalStateException exception) {
                throw new BoardServiceException("DATABASE_UNAVAILABLE", null);
            }

            if (saved.isEmpty()) {
                throw new BoardServiceException("REVISION_CONFLICT", null);
            }
            return saved.get();
        } else if (response instanceof CommandResponse.Failure failure) {
            throw new BoardServiceException(failure.getCode(), failure.getField());
        }

        throw new RuntimeException("Unknown response type");
    }

    private int getBoardRevision(int expectedRevision, BoardSnapshot board) {
        int boardRevision = board.getRevision();

        if (boardRevision != expectedRevision) {
            throw new BoardServiceException("REVISION_CONFLICT", null);
        }
        return boardRevision;
    }
}
