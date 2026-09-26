// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

import kotlin.collections.plus

fun Board.addChecklistItem(
    noteId: NoteId,
    label: String,
    itemId: ChecklistItemId,
): BoardCommandResult {
    val note = notes.find { it.id == noteId }
    return when {
        note == null -> BoardCommandResult.Failure("NOTE_NOT_FOUND", "id")
        note.status == NoteStatus.DONE -> BoardCommandResult.Failure("NOTE_DONE_READ_ONLY")
        note.checklist.size >= MAX_CHECKLIST_ITEMS -> BoardCommandResult.Failure("CHECKLIST_FULL")
        note.checklist.any { it.id == itemId } -> BoardCommandResult.Failure("DUPLICATE_ID", "itemId")
        else -> {
            val failure = validateChecklistLabel(label)
            failure ?: BoardCommandResult.Success(
                replaceNote(note.copy(checklist = note.checklist + ChecklistItem(itemId, label))),
            )
        }
    }
}

fun Board.updateChecklistItem(
    noteId: NoteId,
    itemId: ChecklistItemId,
    label: String? = null,
    completed: Boolean? = null,
): BoardCommandResult {
    val note = notes.find { it.id == noteId }
    val itemIndex = note?.checklist?.indexOfFirst { it.id == itemId } ?: -1
    return when {
        note == null -> BoardCommandResult.Failure("NOTE_NOT_FOUND", "id")
        note.status == NoteStatus.DONE -> BoardCommandResult.Failure("NOTE_DONE_READ_ONLY")
        itemIndex == -1 -> BoardCommandResult.Failure("ITEM_NOT_FOUND", "itemId")
        else -> applyItemUpdate(note, itemIndex, label, completed)
    }
}

fun Board.deleteChecklistItem(
    noteId: NoteId,
    itemId: ChecklistItemId,
): BoardCommandResult {
    val note = notes.find { it.id == noteId }
    return when {
        note == null -> BoardCommandResult.Failure("NOTE_NOT_FOUND", "id")
        note.status == NoteStatus.DONE -> BoardCommandResult.Failure("NOTE_DONE_READ_ONLY")
        note.checklist.none { it.id == itemId } -> BoardCommandResult.Failure("ITEM_NOT_FOUND", "itemId")
        else -> {
            val nextList = note.checklist.filterNot { it.id == itemId }
            BoardCommandResult.Success(replaceNote(note.copy(checklist = nextList)))
        }
    }
}

private fun Board.applyItemUpdate(
    note: Note,
    itemIndex: Int,
    label: String?,
    completed: Boolean?,
): BoardCommandResult {
    val failure = if (label != null) validateChecklistLabel(label) else null
    if (failure != null) {
        return failure
    }
    val current = note.checklist[itemIndex]
    val updatedItem =
        current.copy(
            label = if (label != null) normalizeChecklistLabel(label) else current.label,
            completed = completed ?: current.completed,
        )
    val nextList = note.checklist.toMutableList().apply { this[itemIndex] = updatedItem }
    return BoardCommandResult.Success(replaceNote(note.copy(checklist = nextList)))
}

private fun Board.replaceNote(updated: Note): Board {
    val updatedList = notes.map { if (it.id == updated.id) updated else it }
    return Board(id = this.id, notes = updatedList, wipLimit = this.wipLimit)
}

private fun validateChecklistLabel(label: String): BoardCommandResult.Failure? =
    try {
        normalizeChecklistLabel(label)
        null
    } catch (_: IllegalArgumentException) {
        BoardCommandResult.Failure("INVALID_LABEL", "label")
    }
