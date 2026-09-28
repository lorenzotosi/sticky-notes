// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.contract

import kotlinx.serialization.Serializable
import stickynotes.domain.NoteColor
import stickynotes.domain.NoteStatus

/** Serialized checklist item used at the module boundary. */
@Serializable
data class ChecklistItemSnapshot(
    val id: String,
    val label: String,
    val completed: Boolean,
)

/** Serialized note used at the module boundary. */
@Serializable
data class NoteSnapshot(
    val id: String,
    val content: String,
    val color: NoteColor,
    val status: NoteStatus,
    val blockedReason: String? = null,
    val checklist: List<ChecklistItemSnapshot> = emptyList(),
)

/**
 * Portable board state.
 *
 * Schema version 1 is supported. [revision] must be zero or greater.
 */
@Serializable
data class BoardSnapshot(
    val id: String = "main",
    val schemaVersion: Int = 1,
    val revision: Int = 0,
    val wipLimit: Int = 3,
    val notes: List<NoteSnapshot> = emptyList(),
)
