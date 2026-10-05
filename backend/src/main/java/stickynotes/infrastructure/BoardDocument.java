// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * MongoDB representation of the complete board aggregate in the {@code boards} collection.
 *
 * <p>Boxed fields retain missing persisted values so {@link BoardDocumentMapper} can reject them
 * explicitly. Constructing a document does not validate its domain state.
 *
 * @param id the board identifier, mapped to MongoDB's {@code _id} field
 * @param schemaVersion the persisted format version; currently only version {@code 1} is supported
 * @param revision the non-negative counter used for conditional saves
 * @param wipLimit the maximum number of notes allowed in {@code DOING}
 * @param notes the notes in canonical column and position order, with their nested checklists
 */
@Document(collection = "boards")
public record BoardDocument(
        @Id String id, Integer schemaVersion, Integer revision, Integer wipLimit, List<NoteDocument> notes) {

    /**
     * Persisted note embedded in the board document.
     *
     * @param id the note identifier, stored as {@code id} rather than {@code _id}
     * @param content the note text
     * @param color the name of a supported domain color
     * @param status the name of a supported domain status
     * @param blockedReason the blocker reason, or {@code null} when the note is unblocked
     * @param checklist the checklist items in their persisted order
     */
    public record NoteDocument(
            @Field("id") String id,
            String content,
            String color,
            String status,
            String blockedReason,
            List<ChecklistItemDocument> checklist) {}

    /**
     * Persisted checklist item embedded in a note.
     *
     * @param id the item identifier, stored as {@code id} rather than {@code _id}
     * @param label the item text
     * @param completed the completion flag; a missing value is rejected during mapping
     */
    public record ChecklistItemDocument(@Field("id") String id, String label, Boolean completed) {}
}
