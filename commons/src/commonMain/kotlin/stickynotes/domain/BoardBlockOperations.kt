// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

fun Board.blockNote(
    id: NoteId,
    reason: String,
): BoardCommandResult {
    val note = notes.find { it.id == id }
    return when {
        note == null -> BoardCommandResult.Failure("NOTE_NOT_FOUND", "id")
        note.status == NoteStatus.DONE -> BoardCommandResult.Failure("INVALID_STATUS", "status")
        else -> {
            val failure = validateBlockReason(reason)
            failure ?: BoardCommandResult.Success(
                replaceNote(note.copy(blockedReason = normalizeBlockReason(reason))),
            )
        }
    }
}

fun Board.unblockNote(id: NoteId): BoardCommandResult {
    val note = notes.find { it.id == id }
    return if (note == null) {
        BoardCommandResult.Failure("NOTE_NOT_FOUND", "id")
    } else {
        BoardCommandResult.Success(replaceNote(note.copy(blockedReason = null)))
    }
}

private fun Board.replaceNote(updated: Note): Board {
    val updatedList = notes.map { if (it.id == updated.id) updated else it }
    return Board(id = this.id, notes = updatedList, wipLimit = this.wipLimit)
}

private fun validateBlockReason(reason: String): BoardCommandResult.Failure? =
    try {
        normalizeBlockReason(reason)
        null
    } catch (_: IllegalArgumentException) {
        BoardCommandResult.Failure("INVALID_BLOCK_REASON", "reason")
    }
