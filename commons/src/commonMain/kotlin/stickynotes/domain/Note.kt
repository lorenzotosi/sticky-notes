// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

package stickynotes.domain

import kotlin.jvm.JvmInline

@JvmInline
value class NoteId(val value: String)

data class NotePosition(val x: Int, val y: Int)

enum class NoteColor { YELLOW, BLUE, GREEN, PINK }

data class Note(
    val id: NoteId,
    val content: String,
    val color: NoteColor = NoteColor.YELLOW,
    val position: NotePosition = NotePosition(0, 0),
) {
    init {
        require(content.isNotBlank()) { "A note needs content." }
        require(content.length <= 500) { "A note cannot exceed 500 characters." }
    }
}
