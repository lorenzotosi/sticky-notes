// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.contract

import kotlinx.serialization.json.Json
import stickynotes.domain.Board
import stickynotes.domain.BoardId
import stickynotes.domain.Note
import stickynotes.domain.NoteId

const val MIN_WIP_LIMIT = 1
const val MAX_WIP_LIMIT = 20

val boardJson =
    Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        isLenient = false
    }

fun BoardSnapshot.toDomain(): Board {
    require(id.isNotBlank()) { "Board id cannot be blank." }
    require(schemaVersion == 1) { "Unsupported schema version: $schemaVersion" }
    require(revision >= 0) { "Revision cannot be negative." }
    require(wipLimit in MIN_WIP_LIMIT..MAX_WIP_LIMIT) { "WIP limit must be between 1 and 20." }

    val domainNotes =
        notes.map { noteSnapshot ->
            Note(
                id = NoteId(noteSnapshot.id),
                content = noteSnapshot.content,
                color = noteSnapshot.color,
                status = noteSnapshot.status,
            )
        }

    return Board(
        id = BoardId(id),
        notes = domainNotes,
    )
}

fun Board.toSnapshot(
    schemaVersion: Int = 1,
    revision: Int = 0,
    wipLimit: Int = 3,
): BoardSnapshot =
    BoardSnapshot(
        id = id.value,
        schemaVersion = schemaVersion,
        revision = revision,
        wipLimit = wipLimit,
        notes =
            notes.map { note ->
                NoteSnapshot(
                    id = note.id.value,
                    content = note.content,
                    color = note.color,
                    status = note.status,
                    blockedReason = null,
                    checklist = emptyList(),
                )
            },
    )
