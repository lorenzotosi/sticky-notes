// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import java.util.Optional;
import java.util.UUID;
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

        BoardCommand.CreateNote createCommand = JvmBoardFacade.createNote(id, content);

        CommandResponse response = JvmBoardFacade.execute(board, createCommand);

        return saveBoard(boardRevision, response);
    }

    private BoardSnapshot getBoard() {
        Optional<BoardSnapshot> board = repository.load();
        if (board.isEmpty()) {
            throw new RuntimeException("Board not found");
        } else {
            return board.get();
        }
    }

    @Override
    public BoardSnapshot updateNote(int expectedRevision, String noteId, String content, NoteColor color) {
        BoardSnapshot board = getBoard();

        int boardRevision = getBoardRevision(expectedRevision, board);

        BoardCommand.UpdateNote updateCommand = JvmBoardFacade.updateNote(noteId, content, color);
        CommandResponse response = JvmBoardFacade.execute(board, updateCommand);

        return saveBoard(boardRevision, response);
    }

    private BoardSnapshot saveBoard(int boardRevision, CommandResponse response) {
        if (response instanceof CommandResponse.Success success) {
            BoardSnapshot updated = success.getBoard();
            Optional<BoardSnapshot> saved = repository.save(updated, boardRevision);

            if (saved.isEmpty()) {
                throw new RuntimeException("Failed to save board");
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
            throw new RuntimeException("Revision does not match expected revision");
        }
        return boardRevision;
    }

    @Override
    public BoardSnapshot deleteNote(int expectedRevision, String noteId) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteNote'");
    }

    @Override
    public BoardSnapshot moveNote(int expectedRevision, String noteId, NoteStatus targetStatus, int destinationIndex) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'moveNote'");
    }

    @Override
    public BoardSnapshot setWipLimit(int expectedRevision, int limit) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setWipLimit'");
    }

    @Override
    public BoardSnapshot blockNote(int expectedRevision, String noteId, String reason) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'blockNote'");
    }

    @Override
    public BoardSnapshot unblockNote(int expectedRevision, String noteId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'unblockNote'");
    }

    @Override
    public BoardSnapshot addItem(int expectedRevision, String noteId, String label) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addItem'");
    }

    @Override
    public BoardSnapshot updateItem(
            int expectedRevision, String noteId, String itemId, String label, Boolean completed) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateItem'");
    }

    @Override
    public BoardSnapshot deleteItem(int expectedRevision, String noteId, String itemId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deleteItem'");
    }
}
