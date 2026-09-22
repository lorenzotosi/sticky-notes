// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

package stickynotes.domain

class Board(
    val id: BoardId,
    notes: List<Note> = emptyList(),
) {
    private val noteState = notes.toList()

    val notes: List<Note>
        get() = noteState.toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Board && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
