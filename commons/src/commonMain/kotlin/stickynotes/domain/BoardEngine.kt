// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

import stickynotes.contract.BoardCommand
import stickynotes.contract.BoardSnapshot
import stickynotes.contract.CommandResponse
import stickynotes.contract.toDomain
import stickynotes.contract.toSnapshot

object BoardEngine {
    fun execute(
        snapshot: BoardSnapshot,
        command: BoardCommand,
    ): CommandResponse {
        val board =
            try {
                snapshot.toDomain()
            } catch (_: IllegalArgumentException) {
                return CommandResponse.Failure("INVALID_SNAPSHOT")
            }

        val result =
            when (command) {
                is BoardCommand.CreateNote ->
                    board.createNote(
                        id = NoteId(command.newId),
                        content = command.content,
                        color = command.color,
                    )
                is BoardCommand.UpdateNote ->
                    board.updateNote(
                        id = NoteId(command.noteId),
                        content = command.content,
                        color = command.color,
                    )
                is BoardCommand.DeleteNote ->
                    board.deleteNote(
                        id = NoteId(command.noteId),
                    )
                is BoardCommand.MoveNote ->
                    board.moveNote(
                        id = NoteId(command.noteId),
                        targetStatus = command.targetStatus,
                        destinationIndex = command.destinationIndex,
                    )
                is BoardCommand.SetWipLimit ->
                    board.setWipLimit(
                        limit = command.limit,
                    )
                else -> BoardCommandResult.Failure("INVALID_REQUEST")
            }

        return when (result) {
            is BoardCommandResult.Success -> {
                val updatedSnapshot =
                    result.board.toSnapshot(
                        schemaVersion = snapshot.schemaVersion,
                        revision = snapshot.revision,
                        wipLimit = result.board.wipLimit,
                    )
                CommandResponse.Success(updatedSnapshot)
            }
            is BoardCommandResult.Failure ->
                CommandResponse.Failure(
                    code = result.code,
                    field = result.field,
                )
        }
    }
}
