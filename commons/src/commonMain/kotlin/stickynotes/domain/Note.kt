// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

const val MAX_NOTE_LENGTH = 500

class Note(
    val id: NoteId,
    content: String,
    val color: NoteColor = NoteColor.YELLOW,
    val status: NoteStatus = NoteStatus.TODO,
) {
    val content = normalizeNoteContent(content)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Note && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

internal fun normalizeNoteContent(value: String): String {
    val normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim()
    require(normalized.isNotEmpty()) { "A note needs content." }
    require(normalized.length <= MAX_NOTE_LENGTH) {
        "A note cannot exceed $MAX_NOTE_LENGTH UTF-16 code units."
    }
    return normalized
}
