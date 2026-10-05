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

/**
 * Converts between shared board snapshots and MongoDB documents using shared domain validation.
 *
 * <p>Conversions preserve note and checklist order, schema version, revision, and all note fields.
 * Invalid state is rejected rather than repaired or replaced with an empty board.
 */
public final class BoardDocumentMapper {

    private BoardDocumentMapper() {}

    /**
     * Validates a candidate snapshot and converts it to its persisted representation.
     *
     * <p>Domain validation runs before copying any fields. Enum values are stored by name and the
     * snapshot revision is preserved; the repository owns revision increments.
     *
     * @param snapshot the complete board snapshot to persist
     * @return the corresponding MongoDB document
     * @throws IllegalArgumentException if the snapshot violates the shared snapshot or domain rules
     */
    public static BoardDocument toDocument(BoardSnapshot snapshot) {
        SerializationKt.toDomain(snapshot);

        List<BoardDocument.NoteDocument> notes = new ArrayList<>();

        for (NoteSnapshot note : snapshot.getNotes()) {
            List<BoardDocument.ChecklistItemDocument> checklist = new ArrayList<>();

            for (ChecklistItemSnapshot item : note.getChecklist()) {
                checklist.add(
                        new BoardDocument.ChecklistItemDocument(item.getId(), item.getLabel(), item.getCompleted()));
            }

            notes.add(new BoardDocument.NoteDocument(
                    note.getId(),
                    note.getContent(),
                    note.getColor().name(),
                    note.getStatus().name(),
                    note.getBlockedReason(),
                    checklist));
        }

        return new BoardDocument(
                snapshot.getId(), snapshot.getSchemaVersion(), snapshot.getRevision(), snapshot.getWipLimit(), notes);
    }

    /**
     * Converts a persisted document to a snapshot and validates it through the shared domain.
     *
     * <p>Required fields and nested entries must be present, and color and status names must be
     * supported. A {@code null} blocker reason is permitted for an unblocked note.
     *
     * @param document the complete persisted board document
     * @return the validated snapshot with its original revision and collection order
     * @throws IllegalArgumentException if the document is missing required data, contains an
     *     unsupported enum value, or violates the shared snapshot or domain rules
     */
    public static BoardSnapshot toSnapshot(BoardDocument document) {
        required(document, "board");

        List<NoteSnapshot> notes = new ArrayList<>();

        for (BoardDocument.NoteDocument note : required(document.notes(), "notes")) {
            required(note, "notes[]");

            List<ChecklistItemSnapshot> checklist = new ArrayList<>();

            for (BoardDocument.ChecklistItemDocument item : required(note.checklist(), "note.checklist")) {
                required(item, "checklist[]");

                checklist.add(new ChecklistItemSnapshot(
                        required(item.id(), "item.id"),
                        required(item.label(), "item.label"),
                        required(item.completed(), "item.completed")));
            }

            notes.add(new NoteSnapshot(
                    required(note.id(), "note.id"),
                    required(note.content(), "note.content"),
                    NoteColor.valueOf(required(note.color(), "note.color")),
                    NoteStatus.valueOf(required(note.status(), "note.status")),
                    note.blockedReason(),
                    checklist));
        }

        BoardSnapshot snapshot = new BoardSnapshot(
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
            throw new IllegalArgumentException("Missing persisted field: " + field);
        }

        return value;
    }
}
