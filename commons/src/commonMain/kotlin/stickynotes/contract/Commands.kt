// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import stickynotes.domain.NoteColor
import stickynotes.domain.NoteStatus

@Serializable
sealed interface BoardCommand {
    @Serializable
    @SerialName("CREATE_NOTE")
    data class CreateNote(
        val content: String,
        val color: NoteColor = NoteColor.YELLOW,
        val newId: String,
    ) : BoardCommand

    @Serializable
    @SerialName("UPDATE_NOTE")
    data class UpdateNote(
        val noteId: String,
        val content: String? = null,
        val color: NoteColor? = null,
    ) : BoardCommand

    @Serializable
    @SerialName("DELETE_NOTE")
    data class DeleteNote(
        val noteId: String,
    ) : BoardCommand

    @Serializable
    @SerialName("MOVE_NOTE")
    data class MoveNote(
        val noteId: String,
        val targetStatus: NoteStatus,
        val destinationIndex: Int,
    ) : BoardCommand

    @Serializable
    @SerialName("SET_WIP_LIMIT")
    data class SetWipLimit(
        val limit: Int,
    ) : BoardCommand

    @Serializable
    @SerialName("BLOCK_NOTE")
    data class BlockNote(
        val noteId: String,
        val reason: String,
    ) : BoardCommand

    @Serializable
    @SerialName("UNBLOCK_NOTE")
    data class UnblockNote(
        val noteId: String,
    ) : BoardCommand

    @Serializable
    @SerialName("ADD_ITEM")
    data class AddItem(
        val noteId: String,
        val label: String,
        val newId: String,
    ) : BoardCommand

    @Serializable
    @SerialName("UPDATE_ITEM")
    data class UpdateItem(
        val noteId: String,
        val itemId: String,
        val label: String? = null,
        val completed: Boolean? = null,
    ) : BoardCommand

    @Serializable
    @SerialName("DELETE_ITEM")
    data class DeleteItem(
        val noteId: String,
        val itemId: String,
    ) : BoardCommand
}

@Serializable
sealed interface CommandResponse {
    @Serializable
    @SerialName("SUCCESS")
    data class Success(val board: BoardSnapshot) : CommandResponse

    @Serializable
    @SerialName("FAILURE")
    data class Failure(val code: String, val field: String? = null) : CommandResponse
}
