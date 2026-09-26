// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.interop

import stickynotes.contract.BoardCommand
import stickynotes.contract.BoardSnapshot
import stickynotes.contract.CommandResponse
import stickynotes.contract.boardJson
import stickynotes.domain.BoardEngine
import stickynotes.domain.normalizeNoteContent

@JsExport
fun evaluateBoardCommand(
    snapshotJson: String,
    commandJson: String,
): String =
    try {
        val snapshot = boardJson.decodeFromString<BoardSnapshot>(snapshotJson)
        val command = boardJson.decodeFromString<BoardCommand>(commandJson)
        val response = BoardEngine.execute(snapshot, command)
        boardJson.encodeToString(response)
    } catch (_: Exception) {
        boardJson.encodeToString(CommandResponse.Failure("INVALID_REQUEST"))
    }

@JsExport
fun validateNoteContent(content: String): String =
    try {
        normalizeNoteContent(content)
        boardJson.encodeToString(ValidationResult(valid = true))
    } catch (_: IllegalArgumentException) {
        boardJson.encodeToString(ValidationResult(valid = false, code = "INVALID_CONTENT", field = "content"))
    }

@kotlinx.serialization.Serializable
private data class ValidationResult(
    val valid: Boolean,
    val code: String? = null,
    val field: String? = null,
)
