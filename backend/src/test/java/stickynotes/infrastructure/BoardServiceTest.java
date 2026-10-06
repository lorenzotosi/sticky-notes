// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import stickynotes.application.BoardRepository;
import stickynotes.contract.BoardCommand;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.CommandResponse;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import stickynotes.interop.JvmBoardFacade;

class BoardServiceTest {
    @Test
    void jvmFacadeCreatesEverySupportedCommand() {
        BoardSnapshot board = JvmBoardFacade.createEmptyBoard("board", 4);
        BoardCommand.CreateNote create = JvmBoardFacade.createNote("note", "Content", NoteColor.BLUE);
        BoardCommand.UpdateNote update = JvmBoardFacade.updateNote("note", "Updated", NoteColor.GREEN);
        BoardCommand.DeleteNote delete = JvmBoardFacade.deleteNote("note");
        BoardCommand.MoveNote move = JvmBoardFacade.moveNote("note", NoteStatus.DOING, 2);
        BoardCommand.SetWipLimit setWipLimit = JvmBoardFacade.setWipLimit(5);
        BoardCommand.BlockNote block = JvmBoardFacade.blockNote("note", "Waiting");
        BoardCommand.UnblockNote unblock = JvmBoardFacade.unblockNote("note");
        BoardCommand.AddItem addItem = JvmBoardFacade.addItem("note", "item", "First item");
        BoardCommand.UpdateItem updateItem = JvmBoardFacade.updateItem("note", "item", "Updated item", true);
        BoardCommand.DeleteItem deleteItem = JvmBoardFacade.deleteItem("note", "item");

        assertAll(
                () -> assertEquals("board", board.getId()),
                () -> assertEquals(4, board.getWipLimit()),
                () -> assertEquals("note", create.getNewId()),
                () -> assertEquals("Content", create.getContent()),
                () -> assertEquals(NoteColor.BLUE, create.getColor()),
                () -> assertEquals("note", update.getNoteId()),
                () -> assertEquals("Updated", update.getContent()),
                () -> assertEquals(NoteColor.GREEN, update.getColor()),
                () -> assertEquals("note", delete.getNoteId()),
                () -> assertEquals(NoteStatus.DOING, move.getTargetStatus()),
                () -> assertEquals(2, move.getDestinationIndex()),
                () -> assertEquals(5, setWipLimit.getLimit()),
                () -> assertEquals("Waiting", block.getReason()),
                () -> assertEquals("note", unblock.getNoteId()),
                () -> assertEquals("item", addItem.getNewId()),
                () -> assertEquals("First item", addItem.getLabel()),
                () -> assertEquals("Updated item", updateItem.getLabel()),
                () -> assertEquals(true, updateItem.getCompleted()),
                () -> assertEquals("item", deleteItem.getItemId()));
    }

    @Test
    void jvmFacadeExecutesCommands() {
        BoardSnapshot board = JvmBoardFacade.createEmptyBoard();

        CommandResponse response = JvmBoardFacade.execute(board, JvmBoardFacade.createNote("note", "Content"));

        CommandResponse.Success success = assertInstanceOf(CommandResponse.Success.class, response);
        assertEquals("note", success.getBoard().getNotes().get(0).getId());
    }

    @Test
    void serviceExecutesEveryMutationAndSavesEachExactlyOnce() {
        FakeBoardRepository repository = new FakeBoardRepository();
        BoardService service = new BoardServiceImpl(repository);

        BoardSnapshot board = service.createNote(0, "Configure MongoDB", NoteColor.BLUE);
        String noteId = board.getNotes().get(0).getId();
        assertDoesNotThrow(() -> UUID.fromString(noteId));
        assertEquals(NoteColor.BLUE, board.getNotes().get(0).getColor());

        board = service.updateNote(board.getRevision(), noteId, "Configure persistence", NoteColor.GREEN);
        assertEquals("Configure persistence", board.getNotes().get(0).getContent());
        assertEquals(NoteColor.GREEN, board.getNotes().get(0).getColor());

        board = service.moveNote(board.getRevision(), noteId, NoteStatus.DOING, 0);
        assertEquals(NoteStatus.DOING, board.getNotes().get(0).getStatus());

        board = service.setWipLimit(board.getRevision(), 2);
        assertEquals(2, board.getWipLimit());

        board = service.blockNote(board.getRevision(), noteId, "Waiting for credentials");
        assertEquals("Waiting for credentials", board.getNotes().get(0).getBlockedReason());

        board = service.unblockNote(board.getRevision(), noteId);
        assertNull(board.getNotes().get(0).getBlockedReason());

        board = service.addItem(board.getRevision(), noteId, "Create indexes");
        String itemId = board.getNotes().get(0).getChecklist().get(0).getId();
        assertDoesNotThrow(() -> UUID.fromString(itemId));

        board = service.updateItem(board.getRevision(), noteId, itemId, "Create required indexes", true);
        assertEquals(
                "Create required indexes",
                board.getNotes().get(0).getChecklist().get(0).getLabel());
        assertTrue(board.getNotes().get(0).getChecklist().get(0).getCompleted());

        board = service.deleteItem(board.getRevision(), noteId, itemId);
        assertTrue(board.getNotes().get(0).getChecklist().isEmpty());

        board = service.deleteNote(board.getRevision(), noteId);
        assertTrue(board.getNotes().isEmpty());
        assertEquals(10, board.getRevision());
        assertEquals(10, repository.saveCalls);
    }

    @Test
    void rejectsInvalidContentWithoutSaving() {
        FakeBoardRepository repository = new FakeBoardRepository();
        BoardService service = new BoardServiceImpl(repository);

        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> service.createNote(0, "   ", NoteColor.YELLOW));

        assertEquals("INVALID_CONTENT", error.getCode());
        assertEquals("content", error.getField());
        assertEquals(0, repository.saveCalls);
        assertTrue(repository.board.getNotes().isEmpty());
    }

    @Test
    void rejectsStaleRevisionAsConflictWithoutSaving() {
        FakeBoardRepository repository = new FakeBoardRepository();
        BoardService service = new BoardServiceImpl(repository);

        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> service.createNote(1, "Content", NoteColor.YELLOW));

        assertEquals("REVISION_CONFLICT", error.getCode());
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void rejectsCompareAndSetFailureAsConflict() {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.rejectSave = true;
        BoardService service = new BoardServiceImpl(repository);

        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> service.createNote(0, "Content", NoteColor.YELLOW));

        assertEquals("REVISION_CONFLICT", error.getCode());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void rejectsRevisionOverflowBeforeSaving() {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = new BoardSnapshot("main", 1, Integer.MAX_VALUE, 3, List.of());
        BoardService service = new BoardServiceImpl(repository);

        BoardServiceException error = assertThrows(
                BoardServiceException.class, () -> service.createNote(Integer.MAX_VALUE, "Content", NoteColor.YELLOW));

        assertEquals("REVISION_OVERFLOW", error.getCode());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void reportsDatabaseFailuresWithoutLeakingTheCause() {
        FakeBoardRepository loadFailure = new FakeBoardRepository();
        loadFailure.loadFailure = new IllegalStateException("mongodb://secret");
        FakeBoardRepository saveFailure = new FakeBoardRepository();
        saveFailure.saveFailure = new IllegalStateException("mongodb://secret");

        assertAll(
                () -> assertDatabaseUnavailable(new BoardServiceImpl(loadFailure)),
                () -> assertDatabaseUnavailable(new BoardServiceImpl(saveFailure)));
    }

    private static void assertDatabaseUnavailable(BoardService service) {
        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> service.createNote(0, "Content", NoteColor.YELLOW));
        assertEquals("DATABASE_UNAVAILABLE", error.getCode());
        assertFalse(error.getMessage().contains("mongodb://secret"));
    }

    private static final class FakeBoardRepository implements BoardRepository {
        private BoardSnapshot board = JvmBoardFacade.createEmptyBoard();
        private RuntimeException loadFailure;
        private RuntimeException saveFailure;
        private boolean rejectSave;
        private int saveCalls;

        @Override
        public Optional<BoardSnapshot> load() {
            if (loadFailure != null) {
                throw loadFailure;
            }
            return Optional.of(board);
        }

        @Override
        public Optional<BoardSnapshot> save(BoardSnapshot updated, int expectedRevision) {
            saveCalls++;
            if (saveFailure != null) {
                throw saveFailure;
            }
            if (rejectSave || board.getRevision() != expectedRevision) {
                return Optional.empty();
            }

            board = new BoardSnapshot(
                    updated.getId(),
                    updated.getSchemaVersion(),
                    Math.addExact(expectedRevision, 1),
                    updated.getWipLimit(),
                    updated.getNotes());
            return Optional.of(board);
        }
    }
}
