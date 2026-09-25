// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

const val MAX_BOARD_NOTES = 200
const val DEFAULT_WIP_LIMIT = 3
const val MIN_WIP_LIMIT = 1
const val MAX_WIP_LIMIT = 20

sealed interface BoardCommandResult {
    data class Success(val board: Board) : BoardCommandResult

    data class Failure(val code: String, val field: String? = null) : BoardCommandResult
}

class Board(
    val id: BoardId,
    notes: List<Note> = emptyList(),
    val wipLimit: Int = DEFAULT_WIP_LIMIT,
) {
    private val noteState: List<Note>

    init {
        require(wipLimit in MIN_WIP_LIMIT..MAX_WIP_LIMIT) {
            "WIP limit must be between $MIN_WIP_LIMIT and $MAX_WIP_LIMIT."
        }
        require(notes.size <= MAX_BOARD_NOTES) {
            "Board cannot exceed $MAX_BOARD_NOTES notes."
        }
        val ids = mutableSetOf<NoteId>()
        for (note in notes) {
            require(ids.add(note.id)) { "Duplicate note ID: ${note.id.value}" }
        }
        noteState = orderCanonical(notes)
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
        return BoardCommandResult.Success(
            Board(id = this.id, notes = noteState + newNote, wipLimit = this.wipLimit),
        )
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
        return BoardCommandResult.Success(
            Board(id = this.id, notes = updatedList, wipLimit = this.wipLimit),
        )
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
        return BoardCommandResult.Success(
            Board(id = this.id, notes = updatedList, wipLimit = this.wipLimit),
        )
    }

    fun moveNote(
        id: NoteId,
        targetStatus: NoteStatus,
        destinationIndex: Int,
    ): BoardCommandResult {
        val note = noteState.find { it.id == id }
        val failure =
            when {
                note == null -> BoardCommandResult.Failure("NOTE_NOT_FOUND", "id")
                else -> validateMove(note, targetStatus, destinationIndex, noteState, wipLimit)
            }
        if (failure != null) {
            return failure
        }

        val resolvedNote = requireNotNull(note)
        val nextNotes = reorderNotes(resolvedNote, targetStatus, destinationIndex, noteState)
        return BoardCommandResult.Success(
            Board(id = this.id, notes = nextNotes, wipLimit = this.wipLimit),
        )
    }

    fun setWipLimit(limit: Int): BoardCommandResult {
        val doingCount = noteState.count { it.status == NoteStatus.DOING }
        val failure =
            when {
                limit !in MIN_WIP_LIMIT..MAX_WIP_LIMIT -> BoardCommandResult.Failure("INVALID_WIP_LIMIT", "limit")
                limit < doingCount -> BoardCommandResult.Failure("WIP_BELOW_OCCUPANCY", "limit")
                else -> null
            }
        if (failure != null) {
            return failure
        }
        return BoardCommandResult.Success(Board(id = this.id, notes = noteState, wipLimit = limit))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Board && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

private fun reorderNotes(
    note: Note,
    targetStatus: NoteStatus,
    destinationIndex: Int,
    noteState: List<Note>,
): List<Note> {
    val todoList = noteState.filter { it.status == NoteStatus.TODO && it.id != note.id }.toMutableList()
    val doingList = noteState.filter { it.status == NoteStatus.DOING && it.id != note.id }.toMutableList()
    val doneList = noteState.filter { it.status == NoteStatus.DONE && it.id != note.id }.toMutableList()

    val movedNote = Note(id = note.id, content = note.content, color = note.color, status = targetStatus)
    val targetList =
        when (targetStatus) {
            NoteStatus.TODO -> todoList
            NoteStatus.DOING -> doingList
            NoteStatus.DONE -> doneList
        }
    targetList.add(destinationIndex, movedNote)
    return todoList + doingList + doneList
}

private fun validateMove(
    note: Note,
    targetStatus: NoteStatus,
    destinationIndex: Int,
    noteState: List<Note>,
    wipLimit: Int,
): BoardCommandResult.Failure? {
    val destinationSize = noteState.count { it.status == targetStatus && it.id != note.id }
    val isEnteringDoing = note.status != NoteStatus.DOING && targetStatus == NoteStatus.DOING
    val doingCount = noteState.count { it.status == NoteStatus.DOING }

    return when {
        !isValidTransition(note.status, targetStatus) -> {
            BoardCommandResult.Failure("INVALID_TRANSITION", "targetStatus")
        }
        destinationIndex !in 0..destinationSize -> {
            BoardCommandResult.Failure("INVALID_INDEX", "destinationIndex")
        }
        isEnteringDoing && doingCount >= wipLimit -> {
            BoardCommandResult.Failure("WIP_LIMIT_REACHED")
        }
        else -> null
    }
}

private fun isValidTransition(
    current: NoteStatus,
    target: NoteStatus,
): Boolean =
    when (current) {
        NoteStatus.TODO -> target == NoteStatus.TODO || target == NoteStatus.DOING
        NoteStatus.DOING -> true
        NoteStatus.DONE -> target == NoteStatus.DONE || target == NoteStatus.DOING
    }

private fun validateContent(rawContent: String): BoardCommandResult.Failure? =
    try {
        normalizeNoteContent(rawContent)
        null
    } catch (_: IllegalArgumentException) {
        BoardCommandResult.Failure("INVALID_CONTENT", "content")
    }

private fun orderCanonical(inputNotes: List<Note>): List<Note> {
    val todo = inputNotes.filter { it.status == NoteStatus.TODO }
    val doing = inputNotes.filter { it.status == NoteStatus.DOING }
    val done = inputNotes.filter { it.status == NoteStatus.DONE }
    return todo + doing + done
}
