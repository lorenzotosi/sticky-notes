// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

const val MAX_BOARD_NOTES = 200

sealed interface BoardCommandResult {
    data class Success(val board: Board) : BoardCommandResult

    data class Failure(val code: String, val field: String? = null) : BoardCommandResult
}

class Board(
    val id: BoardId,
    notes: List<Note> = emptyList(),
) {
    private val noteState: List<Note>

    init {
        require(notes.size <= MAX_BOARD_NOTES) { "Board cannot exceed $MAX_BOARD_NOTES notes." }
        val ids = mutableSetOf<NoteId>()
        for (note in notes) {
            require(ids.add(note.id)) { "Duplicate note ID: ${note.id.value}" }
        }
        noteState = notes.toList()
    }

    val notes: List<Note>
        get() = noteState.toList()

    fun createNote(
        id: NoteId,
        content: String,
        color: NoteColor = NoteColor.YELLOW,
    ): BoardCommandResult {
        val failure =
            when {
                noteState.size >= MAX_BOARD_NOTES -> BoardCommandResult.Failure("BOARD_FULL")
                noteState.any { it.id == id } -> BoardCommandResult.Failure("DUPLICATE_ID", "id")
                else -> validateContent(content)
            }
        if (failure != null) {
            return failure
        }

        val newNote =
            Note(
                id = id,
                content = normalizeNoteContent(content),
                color = color,
                status = NoteStatus.TODO,
            )
        return BoardCommandResult.Success(Board(id = this.id, notes = noteState + newNote))
    }

    fun updateNote(
        id: NoteId,
        content: String? = null,
        color: NoteColor? = null,
    ): BoardCommandResult {
        val existingIndex = noteState.indexOfFirst { it.id == id }
        val failure =
            when {
                existingIndex == -1 -> BoardCommandResult.Failure("NOTE_NOT_FOUND", "id")
                content != null -> validateContent(content)
                else -> null
            }
        if (failure != null) {
            return failure
        }

        val existing = noteState[existingIndex]
        val newContent = if (content != null) normalizeNoteContent(content) else existing.content
        val newColor = color ?: existing.color

        val updatedNote =
            Note(
                id = existing.id,
                content = newContent,
                color = newColor,
                status = existing.status,
            )
        val updatedList =
            noteState.toMutableList().apply {
                this[existingIndex] = updatedNote
            }
        return BoardCommandResult.Success(Board(id = this.id, notes = updatedList))
    }

    fun deleteNote(id: NoteId): BoardCommandResult {
        val existingIndex = noteState.indexOfFirst { it.id == id }
        if (existingIndex == -1) {
            return BoardCommandResult.Failure("NOTE_NOT_FOUND", "id")
        }

        val updatedList =
            noteState.toMutableList().apply {
                removeAt(existingIndex)
            }
        return BoardCommandResult.Success(Board(id = this.id, notes = updatedList))
    }

    private fun validateContent(rawContent: String): BoardCommandResult.Failure? =
        try {
            normalizeNoteContent(rawContent)
            null
        } catch (_: IllegalArgumentException) {
            BoardCommandResult.Failure("INVALID_CONTENT", "content")
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Board && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
