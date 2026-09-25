// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.contract

import kotlinx.serialization.Serializable
import stickynotes.domain.NoteColor
import stickynotes.domain.NoteStatus

@Serializable
data class ChecklistItemSnapshot(
    val id: String,
    val label: String,
    val completed: Boolean,
)

@Serializable
data class NoteSnapshot(
    val id: String,
    val content: String,
    val color: NoteColor,
    val status: NoteStatus,
    val blockedReason: String? = null,
    val checklist: List<ChecklistItemSnapshot> = emptyList(),
)

@Serializable
data class BoardSnapshot(
    val id: String = "main",
    val schemaVersion: Int = 1,
    val revision: Int = 0,
    val wipLimit: Int = 3,
    val notes: List<NoteSnapshot> = emptyList(),
)
