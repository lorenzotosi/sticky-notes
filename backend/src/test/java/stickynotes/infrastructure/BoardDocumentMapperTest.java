// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;
import stickynotes.infrastructure.BoardDocument.ChecklistItemDocument;
import stickynotes.infrastructure.BoardDocument.NoteDocument;

class BoardDocumentMapperTest {

    @Test
    void preservesCompleteSnapshot() {
        BoardSnapshot original =
            new BoardSnapshot(
                "main",
                1,
                7,
                4,
                List.of(
                    new NoteSnapshot(
                        "note-2",
                        "Verificare API",
                        NoteColor.PINK,
                        NoteStatus.TODO,
                        null,
                        List.of()),
                    new NoteSnapshot(
                        "note-1",
                        "Configurare build",
                        NoteColor.YELLOW,
                        NoteStatus.TODO,
                        null,
                        List.of()),
                    new NoteSnapshot(
                        "note-3",
                        "Preparare rilascio",
                        NoteColor.BLUE,
                        NoteStatus.DOING,
                        "Configurazione mancante",
                        List.of(
                            new ChecklistItemSnapshot(
                                "item-2", "Verificare accesso", true),
                            new ChecklistItemSnapshot(
                                "item-1", "Verificare configurazione", false))),
                    new NoteSnapshot(
                        "note-4",
                        "Eseguire controlli",
                        NoteColor.GREEN,
                        NoteStatus.DONE,
                        null,
                        List.of(
                            new ChecklistItemSnapshot(
                                "item-3", "Eseguire test", true)))));

        BoardDocument document = BoardDocumentMapper.toDocument(original);
        BoardSnapshot restored = BoardDocumentMapper.toSnapshot(document);

        assertEquals("BLUE", document.notes().get(2).color());
        assertEquals("DOING", document.notes().get(2).status());
        assertEquals(original, restored);
    }

    @Test
    void rejectsMissingRevision() {
        BoardDocument document =
            new BoardDocument("main", 1, null, 3, List.of());

        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class,
                () -> BoardDocumentMapper.toSnapshot(document));

        assertEquals(
            "Missing persisted field: board.revision",
            error.getMessage());
    }

    @Test
    void rejectsUnknownColor() {
        NoteDocument note =
            new NoteDocument(
                "note-1", "Verificare API", "PURPLE", "TODO", null, List.of());

        BoardDocument document =
            new BoardDocument("main", 1, 7, 3, List.of(note));

        assertThrows(
            IllegalArgumentException.class,
            () -> BoardDocumentMapper.toSnapshot(document));
    }

    @Test
    void rejectsInvalidPersistedStates() {
        ChecklistItemDocument pending =
            new ChecklistItemDocument("item-1", "Eseguire test", false);

        List<BoardDocument> invalidDocuments =
            List.of(
                new BoardDocument("main", 2, 7, 3, List.of()),
                new BoardDocument("main", 1, -1, 3, List.of()),
                new BoardDocument(
                    "main", 1, 7, 3,
                    List.of(
                        note("note-1", "TODO", null, List.of()),
                        note("note-1", "TODO", null, List.of()))),
                new BoardDocument(
                    "main", 1, 7, 3,
                    List.of(note("note-1", "DONE", null, List.of(pending)))),
                new BoardDocument(
                    "main", 1, 7, 3,
                    List.of(
                        note("note-1", "DONE", "Configurazione mancante", List.of()))),
                new BoardDocument(
                    "main", 1, 7, 1,
                    List.of(
                        note("note-1", "DOING", null, List.of()),
                        note("note-2", "DOING", null, List.of()))),
                new BoardDocument(
                    "main", 1, 7, 3,
                    List.of(
                        note("note-1", "DONE", null, List.of()),
                        note("note-2", "TODO", null, List.of()))));

        for (BoardDocument document : invalidDocuments) {
            assertThrows(
                IllegalArgumentException.class,
                () -> BoardDocumentMapper.toSnapshot(document),
                document.toString());
        }
    }

    private static NoteDocument note(
        String id,
        String status,
        String blockedReason,
        List<ChecklistItemDocument> checklist) {
        return new NoteDocument(
            id, "Verificare " + id, "BLUE", status, blockedReason, checklist);
    }
}
