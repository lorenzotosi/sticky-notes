// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import stickynotes.application.BoardRepository;
import stickynotes.contract.BoardSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.interop.JvmBoardFacade;

class BoardServiceTest {
    @Test
    void createsNoteAndSavesExactlyOnce() {
        // Arrange: empty board at revision 0.
        FakeBoardRepository repository = new FakeBoardRepository();
        BoardService service = new BoardServiceImpl(repository);

        // Act: the caller asks to create a note using revision 0.
        BoardSnapshot saved = service.createNote(0, "Configure MongoDB", NoteColor.YELLOW);

        // Assert: complete board saved once, then returned with revision 1.
        assertEquals(1, repository.saveCalls);
        assertEquals(0, repository.receivedRevision);
        assertEquals(0, repository.candidate.getRevision());
        assertEquals(1, saved.getRevision());
        assertEquals(1, saved.getNotes().size());
        assertEquals("Configure MongoDB", saved.getNotes().get(0).getContent());
        assertDoesNotThrow(() -> UUID.fromString(saved.getNotes().get(0).getId()));
        assertSame(repository.board, saved);
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
        assertEquals(0, repository.board.getRevision());
        assertTrue(repository.board.getNotes().isEmpty());
    }

    private static final class FakeBoardRepository implements BoardRepository {
        private BoardSnapshot board = JvmBoardFacade.createEmptyBoard();
        private BoardSnapshot candidate;
        private int receivedRevision;
        private int saveCalls;

        @Override
        public Optional<BoardSnapshot> load() {
            return Optional.of(board);
        }

        @Override
        public Optional<BoardSnapshot> save(BoardSnapshot updated, int expectedRevision) {
            saveCalls++;
            candidate = updated;
            receivedRevision = expectedRevision;

            if (board.getRevision() != expectedRevision) {
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
