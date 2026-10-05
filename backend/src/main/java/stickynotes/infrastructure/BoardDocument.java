// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "boards")
public record BoardDocument(
    @Id String id,
    Integer schemaVersion,
    Integer revision,
    Integer wipLimit,
    List<NoteDocument> notes) {

    public record NoteDocument(
        @Field("id") String id,
        String content,
        String color,
        String status,
        String blockedReason,
        List<ChecklistItemDocument> checklist) {}

    public record ChecklistItemDocument(
        @Field("id") String id,
        String label,
        Boolean completed) {}
}
