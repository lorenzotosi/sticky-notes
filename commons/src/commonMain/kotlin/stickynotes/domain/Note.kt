// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

const val MAX_NOTE_LENGTH = 500
const val MAX_BLOCK_REASON_LENGTH = 200

class Note(
    val id: NoteId,
    content: String,
    val color: NoteColor = NoteColor.YELLOW,
    val status: NoteStatus = NoteStatus.TODO,
    blockedReason: String? = null,
    checklist: List<ChecklistItem> = emptyList(),
) {
    val content = normalizeNoteContent(content)
    val blockedReason = blockedReason?.let { normalizeBlockReason(it) }
    private val checklistState: List<ChecklistItem>

    init {
        require(checklist.size <= MAX_CHECKLIST_ITEMS) {
            "Checklist cannot exceed $MAX_CHECKLIST_ITEMS items."
        }
        val ids = mutableSetOf<ChecklistItemId>()
        for (item in checklist) {
            require(ids.add(item.id)) { "Duplicate checklist item ID: ${item.id.value}" }
        }
        checklistState = checklist.toList()
    }

    val checklist: List<ChecklistItem>
        get() = checklistState.toList()

    val isBlocked: Boolean
        get() = blockedReason != null

    val isChecklistComplete: Boolean
        get() = checklistState.all { it.completed }

    fun copy(
        content: String = this.content,
        color: NoteColor = this.color,
        status: NoteStatus = this.status,
        blockedReason: String? = this.blockedReason,
        checklist: List<ChecklistItem> = this.checklist,
    ): Note =
        Note(
            id = this.id,
            content = content,
            color = color,
            status = status,
            blockedReason = blockedReason,
            checklist = checklist,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Note && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

internal fun normalizeNoteContent(value: String): String {
    val normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim()
    require(normalized.isNotEmpty()) { "A note needs content." }
    require(normalized.length <= MAX_NOTE_LENGTH) {
        "A note cannot exceed $MAX_NOTE_LENGTH UTF-16 code units."
    }
    return normalized
}

internal fun normalizeBlockReason(value: String): String {
    val normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim()
    require(normalized.isNotEmpty()) { "Block reason cannot be empty." }
    require(normalized.length <= MAX_BLOCK_REASON_LENGTH) {
        "Block reason cannot exceed $MAX_BLOCK_REASON_LENGTH UTF-16 code units."
    }
    return normalized
}
