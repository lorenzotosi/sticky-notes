// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

@file:Suppress("TooManyFunctions")

package stickynotes.interop

import stickynotes.contract.BoardCommand
import stickynotes.contract.BoardSnapshot
import stickynotes.contract.CommandResponse
import stickynotes.domain.BoardEngine
import stickynotes.domain.NoteColor
import stickynotes.domain.NoteStatus

/** Static entry points for Java consumers of the shared domain. */
object JvmBoardFacade {
    @JvmStatic
    fun execute(
        snapshot: BoardSnapshot,
        command: BoardCommand,
    ): CommandResponse = BoardEngine.execute(snapshot, command)

    @JvmStatic
    @JvmOverloads
    fun createEmptyBoard(
        id: String = "main",
        wipLimit: Int = 3,
    ): BoardSnapshot =
        BoardSnapshot(
            id = id,
            schemaVersion = 1,
            revision = 0,
            wipLimit = wipLimit,
            notes = emptyList(),
        )

    @JvmStatic
    @JvmOverloads
    fun createNote(
        newId: String,
        content: String,
        color: NoteColor = NoteColor.YELLOW,
    ): BoardCommand.CreateNote = BoardCommand.CreateNote(content = content, color = color, newId = newId)

    @JvmStatic
    fun moveNote(
        noteId: String,
        targetStatus: NoteStatus,
        destinationIndex: Int,
    ): BoardCommand.MoveNote =
        BoardCommand.MoveNote(
            noteId = noteId,
            targetStatus = targetStatus,
            destinationIndex = destinationIndex,
        )

    @JvmStatic
    fun setWipLimit(limit: Int): BoardCommand.SetWipLimit = BoardCommand.SetWipLimit(limit = limit)

    @JvmStatic
    fun addItem(
        noteId: String,
        newId: String,
        label: String,
    ): BoardCommand.AddItem = BoardCommand.AddItem(noteId = noteId, label = label, newId = newId)

    @JvmStatic
    @JvmOverloads
    fun updateNote(
        noteId: String,
        content: String? = null,
        color: NoteColor? = null,
    ): BoardCommand.UpdateNote = BoardCommand.UpdateNote(noteId = noteId, content = content, color = color)

    @JvmStatic
    fun deleteNote(noteId: String): BoardCommand.DeleteNote = BoardCommand.DeleteNote(noteId = noteId)

    @JvmStatic
    fun blockNote(
        noteId: String,
        reason: String,
    ): BoardCommand.BlockNote = BoardCommand.BlockNote(noteId = noteId, reason = reason)

    @JvmStatic
    fun unblockNote(noteId: String): BoardCommand.UnblockNote = BoardCommand.UnblockNote(noteId = noteId)

    @JvmStatic
    fun updateItem(
        noteId: String,
        itemId: String,
        label: String? = null,
        completed: Boolean? = null,
    ): BoardCommand.UpdateItem =
        BoardCommand.UpdateItem(
            noteId = noteId,
            itemId = itemId,
            label = label,
            completed = completed,
        )

    @JvmStatic
    fun deleteItem(
        noteId: String,
        itemId: String,
    ): BoardCommand.DeleteItem = BoardCommand.DeleteItem(noteId = noteId, itemId = itemId)
}
