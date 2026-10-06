// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import stickynotes.contract.BoardCommand;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.CommandResponse;
import stickynotes.contract.NoteSnapshot;
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
    void updatesNoteFieldsIndependentlyWithoutChangingOtherNotes() {
        FakeBoardRepository repository = new FakeBoardRepository();
        NoteSnapshot untouched =
                new NoteSnapshot("other", "Untouched", NoteColor.BLUE, NoteStatus.TODO, null, List.of());
        repository.board = new BoardSnapshot(
                "main",
                1,
                7,
                3,
                List.of(
                        new NoteSnapshot("note", "Content", NoteColor.YELLOW, NoteStatus.TODO, null, List.of()),
                        untouched));
        BoardService service = new BoardServiceImpl(repository);

        BoardSnapshot contentUpdate = service.updateNote(7, "note", "Changed", null);
        assertEquals("Changed", contentUpdate.getNotes().get(0).getContent());
        assertEquals(NoteColor.YELLOW, contentUpdate.getNotes().get(0).getColor());
        assertEquals(untouched, contentUpdate.getNotes().get(1));

        BoardSnapshot colorUpdate = service.updateNote(8, "note", null, NoteColor.GREEN);
        assertEquals("Changed", colorUpdate.getNotes().get(0).getContent());
        assertEquals(NoteColor.GREEN, colorUpdate.getNotes().get(0).getColor());
        assertEquals(untouched, colorUpdate.getNotes().get(1));
        assertEquals(2, repository.saveCalls);
    }

    @Test
    void updatesItemFieldsIndependentlyWithoutChangingOtherItems() {
        FakeBoardRepository repository = new FakeBoardRepository();
        ChecklistItemSnapshot untouched = new ChecklistItemSnapshot("other", "Untouched", false);
        repository.board = new BoardSnapshot(
                "main",
                1,
                7,
                3,
                List.of(new NoteSnapshot(
                        "note",
                        "Content",
                        NoteColor.YELLOW,
                        NoteStatus.TODO,
                        null,
                        List.of(new ChecklistItemSnapshot("item", "Item", false), untouched))));
        BoardService service = new BoardServiceImpl(repository);

        BoardSnapshot labelUpdate = service.updateItem(7, "note", "item", "Changed", null);
        assertEquals(
                "Changed", labelUpdate.getNotes().get(0).getChecklist().get(0).getLabel());
        assertFalse(labelUpdate.getNotes().get(0).getChecklist().get(0).getCompleted());
        assertEquals(untouched, labelUpdate.getNotes().get(0).getChecklist().get(1));

        BoardSnapshot completionUpdate = service.updateItem(8, "note", "item", null, true);
        assertEquals(
                "Changed",
                completionUpdate.getNotes().get(0).getChecklist().get(0).getLabel());
        assertTrue(completionUpdate.getNotes().get(0).getChecklist().get(0).getCompleted());
        assertEquals(
                untouched, completionUpdate.getNotes().get(0).getChecklist().get(1));
        assertEquals(2, repository.saveCalls);
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
        repository.board = boardAt(1);
        BoardService service = new BoardServiceImpl(repository);

        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> service.createNote(0, "Content", NoteColor.YELLOW));

        assertEquals("REVISION_CONFLICT", error.getCode());
        assertEquals(1, error.getCurrentRevision());
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void rejectsCompareAndSetFailureAsConflict() {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.competingWrite = boardAt(1);
        BoardService service = new BoardServiceImpl(repository);

        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> service.createNote(0, "Content", NoteColor.YELLOW));

        assertEquals("REVISION_CONFLICT", error.getCode());
        assertEquals(1, error.getCurrentRevision());
        assertEquals(1, repository.saveCalls);
        assertEquals(2, repository.loadCalls);
    }

    @Test
    void rejectsRevisionOverflowBeforeSaving() {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = new BoardSnapshot("main", 1, Integer.MAX_VALUE, 3, List.of());
        BoardService service = new BoardServiceImpl(repository);

        BoardServiceException error = assertThrows(
                BoardServiceException.class, () -> service.createNote(Integer.MAX_VALUE, "Content", NoteColor.YELLOW));

        assertEquals("REVISION_OVERFLOW", error.getCode());
        assertEquals(0, repository.saveCalls);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void everyMutationReportsCurrentRevisionWithoutSavingOnStaleRequest(Mutation mutation) {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = boardAt(7);
        BoardSnapshot before = repository.board;

        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> mutation.apply(new BoardServiceImpl(repository), 6));

        assertEquals("REVISION_CONFLICT", error.getCode());
        assertEquals(7, error.getCurrentRevision());
        assertEquals(1, repository.loadCalls);
        assertEquals(0, repository.saveCalls);
        assertEquals(before, repository.board);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void everyMutationReloadsCurrentRevisionAfterCasConflictWithoutRetrying(Mutation mutation) {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = boardAt(7);
        BoardSnapshot competing = boardAt(8);
        repository.competingWrite = competing;

        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> mutation.apply(new BoardServiceImpl(repository), 7));

        assertEquals("REVISION_CONFLICT", error.getCode());
        assertEquals(8, error.getCurrentRevision());
        assertEquals(2, repository.loadCalls);
        assertEquals(1, repository.saveCalls);
        assertEquals(7, repository.receivedRevision);
        assertEquals(7, repository.candidate.getRevision());
        assertEquals(competing, repository.board);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void everyMutationRejectsOverflowBeforeSaving(Mutation mutation) {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = boardAt(Integer.MAX_VALUE);
        BoardSnapshot before = repository.board;

        BoardServiceException error = assertThrows(
                BoardServiceException.class, () -> mutation.apply(new BoardServiceImpl(repository), Integer.MAX_VALUE));

        assertEquals("REVISION_OVERFLOW", error.getCode());
        assertNull(error.getCurrentRevision());
        assertEquals(0, repository.saveCalls);
        assertEquals(before, repository.board);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void everyMutationRejectsCorruptPersistedStateWithoutRepairingOrSaving(Mutation mutation) {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = boardAt(7);
        BoardSnapshot before = repository.board;
        repository.loadFailure = new IllegalArgumentException("Invalid persisted snapshot");

        BoardServiceException error =
                assertThrows(BoardServiceException.class, () -> mutation.apply(new BoardServiceImpl(repository), 7));

        assertEquals("INVALID_SNAPSHOT", error.getCode());
        assertNull(error.getField());
        assertNull(error.getCurrentRevision());
        assertEquals(1, repository.loadCalls);
        assertEquals(0, repository.saveCalls);
        assertEquals(before, repository.board);
    }

    @Test
    void acceptsLastRevisionThatCanBeIncremented() {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = boardAt(Integer.MAX_VALUE - 1);
        BoardSnapshot saved = new BoardServiceImpl(repository).setWipLimit(Integer.MAX_VALUE - 1, 4);

        assertEquals(Integer.MAX_VALUE, saved.getRevision());
        assertEquals(4, saved.getWipLimit());
        assertEquals(1, repository.saveCalls);
        assertEquals(Integer.MAX_VALUE - 1, repository.candidate.getRevision());
        assertEquals(Integer.MAX_VALUE - 1, repository.receivedRevision);
        assertEquals(saved, repository.board);
    }

    @Test
    void reportsConflictWithoutRevisionWhenBoardDisappearsDuringSave() {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.disappearOnSave = true;

        BoardServiceException error = assertThrows(
                BoardServiceException.class,
                () -> new BoardServiceImpl(repository).createNote(0, "Content", NoteColor.YELLOW));

        assertEquals("REVISION_CONFLICT", error.getCode());
        assertNull(error.getCurrentRevision());
        assertEquals(2, repository.loadCalls);
        assertEquals(1, repository.saveCalls);
        assertNull(repository.board);
    }

    @Test
    void reportsDatabaseFailureOnConflictReloadWithoutRetryingSave() {
        FakeBoardRepository repository = new FakeBoardRepository();
        BoardSnapshot competing = boardAt(1);
        repository.competingWrite = competing;
        repository.reloadFailure = new IllegalStateException("mongodb://secret");

        BoardServiceException error = assertThrows(
                BoardServiceException.class,
                () -> new BoardServiceImpl(repository).createNote(0, "Content", NoteColor.YELLOW));

        assertEquals("DATABASE_UNAVAILABLE", error.getCode());
        assertFalse(error.getMessage().contains("mongodb://secret"));
        assertEquals(2, repository.loadCalls);
        assertEquals(1, repository.saveCalls);
        assertEquals(competing, repository.board);
    }

    @Test
    void reportsCorruptSnapshotOnConflictReloadWithoutRepairingOrRetryingSave() {
        FakeBoardRepository repository = new FakeBoardRepository();
        BoardSnapshot competing = boardAt(1);
        repository.competingWrite = competing;
        repository.reloadFailure = new IllegalArgumentException("Invalid persisted snapshot");

        BoardServiceException error = assertThrows(
                BoardServiceException.class,
                () -> new BoardServiceImpl(repository).createNote(0, "Content", NoteColor.YELLOW));

        assertEquals("INVALID_SNAPSHOT", error.getCode());
        assertEquals(2, repository.loadCalls);
        assertEquals(1, repository.saveCalls);
        assertEquals(competing, repository.board);
    }

    @Test
    void missingItemOnDoneNoteTakesPrecedenceOverReadonlyWithoutSaving() {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = new BoardSnapshot(
                "main",
                1,
                7,
                3,
                List.of(new NoteSnapshot(
                        "done",
                        "Done",
                        NoteColor.YELLOW,
                        NoteStatus.DONE,
                        null,
                        List.of(new ChecklistItemSnapshot("existing", "Ready", true)))));
        BoardSnapshot before = repository.board;
        BoardService service = new BoardServiceImpl(repository);

        BoardServiceException update = assertThrows(
                BoardServiceException.class, () -> service.updateItem(7, "done", "absent", "Changed", true));
        BoardServiceException delete =
                assertThrows(BoardServiceException.class, () -> service.deleteItem(7, "done", "absent"));

        assertEquals("ITEM_NOT_FOUND", update.getCode());
        assertEquals("ITEM_NOT_FOUND", delete.getCode());
        assertEquals(0, repository.saveCalls);
        assertEquals(before, repository.board);
    }

    @Test
    void fullWipTakesPrecedenceOverIndexWithoutSaving() {
        FakeBoardRepository repository = new FakeBoardRepository();
        repository.board = new BoardSnapshot(
                "main",
                1,
                7,
                1,
                List.of(
                        new NoteSnapshot("todo", "Todo", NoteColor.YELLOW, NoteStatus.TODO, null, List.of()),
                        new NoteSnapshot("doing", "Doing", NoteColor.BLUE, NoteStatus.DOING, "Waiting", List.of())));
        BoardSnapshot before = repository.board;

        BoardServiceException error = assertThrows(
                BoardServiceException.class,
                () -> new BoardServiceImpl(repository).moveNote(7, "todo", NoteStatus.DOING, 2));

        assertEquals("WIP_LIMIT_REACHED", error.getCode());
        assertEquals(0, repository.saveCalls);
        assertEquals(before, repository.board);
    }

    static Stream<Mutation> mutations() {
        return Stream.of(
                new Mutation("createNote", (s, r) -> s.createNote(r, "New", NoteColor.BLUE)),
                new Mutation("updateNote", (s, r) -> s.updateNote(r, "note", "Changed", NoteColor.GREEN)),
                new Mutation("deleteNote", (s, r) -> s.deleteNote(r, "note")),
                new Mutation("moveNote", (s, r) -> s.moveNote(r, "note", NoteStatus.DOING, 0)),
                new Mutation("setWipLimit", (s, r) -> s.setWipLimit(r, 4)),
                new Mutation("blockNote", (s, r) -> s.blockNote(r, "note", "Waiting")),
                new Mutation("unblockNote", (s, r) -> s.unblockNote(r, "note")),
                new Mutation("addItem", (s, r) -> s.addItem(r, "note", "New item")),
                new Mutation("updateItem", (s, r) -> s.updateItem(r, "note", "item", "Changed", true)),
                new Mutation("deleteItem", (s, r) -> s.deleteItem(r, "note", "item")));
    }

    private static BoardSnapshot boardAt(int revision) {
        return new BoardSnapshot(
                "main",
                1,
                revision,
                3,
                List.of(new NoteSnapshot(
                        "note",
                        "Content",
                        NoteColor.YELLOW,
                        NoteStatus.TODO,
                        null,
                        List.of(new ChecklistItemSnapshot("item", "Item", false)))));
    }

    private record Mutation(String name, BiFunction<BoardService, Integer, BoardSnapshot> call) {
        BoardSnapshot apply(BoardService service, int revision) {
            return call.apply(service, revision);
        }

        @Override
        public String toString() {
            return name;
        }
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
        private RuntimeException reloadFailure;
        private BoardSnapshot competingWrite;
        private BoardSnapshot candidate;
        private boolean disappearOnSave;
        private int receivedRevision;
        private int loadCalls;
        private int saveCalls;

        @Override
        public Optional<BoardSnapshot> load() {
            loadCalls++;
            if (loadCalls > 1 && reloadFailure != null) {
                throw reloadFailure;
            }
            if (loadFailure != null) {
                throw loadFailure;
            }
            return Optional.ofNullable(board);
        }

        @Override
        public Optional<BoardSnapshot> save(BoardSnapshot updated, int expectedRevision) {
            saveCalls++;
            candidate = updated;
            receivedRevision = expectedRevision;
            if (saveFailure != null) {
                throw saveFailure;
            }
            if (competingWrite != null) {
                board = competingWrite;
                competingWrite = null;
                return Optional.empty();
            }
            if (disappearOnSave) {
                board = null;
                return Optional.empty();
            }
            if (board == null || board.getRevision() != expectedRevision) {
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
