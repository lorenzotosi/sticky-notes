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
import stickynotes.interop.JvmBoardFacade;

public final class BoardServiceImpl implements BoardService {

    private final BoardRepository repository;

    public BoardServiceImpl(BoardRepository repository) {
        this.repository = repository;
    }

    public BoardSnapshot createNote(int expectedRevision, String content, NoteColor color) {

        BoardSnapshot board = getBoard();

        int boardRevision = board.getRevision();

        if (boardRevision != expectedRevision) {
            throw new RuntimeException("Revision does not match expected revision");
        }

        String id = UUID.randomUUID().toString();

        BoardCommand.CreateNote createCommand = JvmBoardFacade.createNote(id, content, color);

        CommandResponse response = JvmBoardFacade.execute(board, createCommand);

        if (response instanceof CommandResponse.Success success) {
            BoardSnapshot updated = success.getBoard();
            Optional<BoardSnapshot> saved = repository.save(updated, boardRevision);

            if (saved.isEmpty()) {
                throw new RuntimeException("Failed to save board");
            }

            return saved.get();
        }

        throw new RuntimeException("Failed to create note");
    }

    private BoardSnapshot getBoard() {
        Optional<BoardSnapshot> board = repository.load();
        if (board.isEmpty()) {
            throw new RuntimeException("Board not found");
        } else {
            return board.get();
        }
    }
}
