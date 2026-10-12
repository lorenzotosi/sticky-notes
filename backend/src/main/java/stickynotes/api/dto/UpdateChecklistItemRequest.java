// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public record UpdateChecklistItemRequest(String label, Boolean completed, Integer expectedRevision) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static UpdateChecklistItemRequest fromJson(JsonNode body) {
        ChecklistRequestJson.checkFields(body, Set.of("label", "completed", "expectedRevision"));
        return new UpdateChecklistItemRequest(
                ChecklistRequestJson.label(body),
                ChecklistRequestJson.completed(body),
                ChecklistRequestJson.expectedRevision(body));
    }
}
