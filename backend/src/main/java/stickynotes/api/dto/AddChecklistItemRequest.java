// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public record AddChecklistItemRequest(String label, Integer expectedRevision) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AddChecklistItemRequest fromJson(JsonNode body) {
        ChecklistRequestJson.checkFields(body, Set.of("label", "expectedRevision"));
        return new AddChecklistItemRequest(
                ChecklistRequestJson.label(body), ChecklistRequestJson.expectedRevision(body));
    }
}
