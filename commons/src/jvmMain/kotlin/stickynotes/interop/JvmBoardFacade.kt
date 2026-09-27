// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.interop

import stickynotes.contract.BoardCommand
import stickynotes.contract.BoardSnapshot
import stickynotes.contract.CommandResponse
import stickynotes.domain.BoardEngine
import stickynotes.domain.NoteColor
import stickynotes.domain.NoteStatus

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
}
