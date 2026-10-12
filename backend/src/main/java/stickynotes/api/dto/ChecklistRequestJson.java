// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api.dto;

import java.util.Set;
import tools.jackson.databind.JsonNode;

final class ChecklistRequestJson {

    private ChecklistRequestJson() {}

    static void checkFields(JsonNode body, Set<String> allowedFields) {
        if (!body.isObject() || !allowedFields.containsAll(body.propertyNames())) {
            throw new IllegalArgumentException("Expected a checklist request object with known fields.");
        }
    }

    static String label(JsonNode body) {
        JsonNode value = body.get("label");
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("label must be a JSON string.");
        }
        return value.stringValue();
    }

    static Boolean completed(JsonNode body) {
        JsonNode value = body.get("completed");
        if (value == null) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("completed must be a JSON boolean.");
        }
        return value.booleanValue();
    }

    static Integer expectedRevision(JsonNode body) {
        JsonNode value = body.get("expectedRevision");
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("expectedRevision must be a JSON integer in the Java int range.");
        }
        return value.intValue();
    }
}
