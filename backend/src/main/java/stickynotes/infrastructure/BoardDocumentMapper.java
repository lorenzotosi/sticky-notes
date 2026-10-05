// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import java.util.ArrayList;
import java.util.List;
import stickynotes.contract.BoardSnapshot;
import stickynotes.contract.ChecklistItemSnapshot;
import stickynotes.contract.NoteSnapshot;
import stickynotes.contract.SerializationKt;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;

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

    public static BoardSnapshot toSnapshot(BoardDocument document) {
        required(document, "board");

        List<NoteSnapshot> notes = new ArrayList<>();

        for (BoardDocument.NoteDocument note
            : required(document.notes(), "notes")) {
            required(note, "notes[]");

            List<ChecklistItemSnapshot> checklist = new ArrayList<>();

            for (BoardDocument.ChecklistItemDocument item
                : required(note.checklist(), "note.checklist")) {
                required(item, "checklist[]");

                checklist.add(
                    new ChecklistItemSnapshot(
                        required(item.id(), "item.id"),
                        required(item.label(), "item.label"),
                        required(item.completed(), "item.completed")));
            }

            notes.add(
                new NoteSnapshot(
                    required(note.id(), "note.id"),
                    required(note.content(), "note.content"),
                    NoteColor.valueOf(required(note.color(), "note.color")),
                    NoteStatus.valueOf(required(note.status(), "note.status")),
                    note.blockedReason(),
                    checklist));
        }

        BoardSnapshot snapshot =
            new BoardSnapshot(
                required(document.id(), "board.id"),
                required(document.schemaVersion(), "board.schemaVersion"),
                required(document.revision(), "board.revision"),
                required(document.wipLimit(), "board.wipLimit"),
                notes);

        SerializationKt.toDomain(snapshot);

        return snapshot;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(
                "Missing persisted field: " + field);
        }

        return value;
    }
}
