// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

const val MAX_CHECKLIST_LABEL_LENGTH = 120
const val MAX_CHECKLIST_ITEMS = 20

class ChecklistItem(
    val id: ChecklistItemId,
    label: String,
    val completed: Boolean = false,
) {
    val label = normalizeChecklistLabel(label)

    fun copy(
        id: ChecklistItemId = this.id,
        label: String = this.label,
        completed: Boolean = this.completed,
    ): ChecklistItem = ChecklistItem(id = id, label = label, completed = completed)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is ChecklistItem && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

internal fun normalizeChecklistLabel(value: String): String {
    val normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim()
    require(normalized.isNotEmpty()) { "Checklist item label cannot be empty." }
    require(normalized.length <= MAX_CHECKLIST_LABEL_LENGTH) {
        "Checklist item label cannot exceed $MAX_CHECKLIST_LABEL_LENGTH UTF-16 code units."
    }
    return normalized
}
