// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import java.util.ArrayList;
import java.util.List;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.contract.SerializationKt;

public final class BoardDocumentMapper {

    private BoardDocumentMapper() {}

    public static BoardDocument toDocument(BoardSnapshot snapshot) {
        SerializationKt.toDomain(snapshot);

        List<BoardDocument.NoteDocument> notes = new ArrayList<>();

        for (NoteSnapshot note : snapshot.getNotes()) {
            List<BoardDocument.ChecklistItemDocument> checklist =
                new ArrayList<>();

            for (ChecklistItemSnapshot item : note.getChecklist()) {
                checklist.add(
                    new BoardDocument.ChecklistItemDocument(
                        item.getId(),
                        item.getLabel(),
                        item.getCompleted()));
            }

            notes.add(
                new BoardDocument.NoteDocument(
                    note.getId(),
                    note.getContent(),
                    note.getColor().name(),
                    note.getStatus().name(),
                    note.getBlockedReason(),
                    checklist));
        }

        return new BoardDocument(
            snapshot.getId(),
            snapshot.getSchemaVersion(),
            snapshot.getRevision(),
            snapshot.getWipLimit(),
            notes);
    }
}
