// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

package stickynotes.domain

import kotlin.jvm.JvmInline

const val MAX_NOTE_LENGTH = 500

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
        require(content.length <= MAX_NOTE_LENGTH) { "A note cannot exceed $MAX_NOTE_LENGTH characters." }
    }
}
