// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

package stickynotes.domain

data class BoardId(val value: String) {
    init {
        require(value.isNotBlank()) { "Board ID cannot be blank." }
    }
}

data class NoteId(val value: String) {
    init {
        require(value.isNotBlank()) { "Note ID cannot be blank." }
    }
}

data class ChecklistItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Checklist item ID cannot be blank." }
    }
}

enum class NoteStatus {
    TODO,
    DOING,
    DONE,
}

enum class NoteColor {
    YELLOW,
    BLUE,
    GREEN,
    PINK,
}
