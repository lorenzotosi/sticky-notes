// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

package stickynotes.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class NoteTest {
    @Test
    fun `identifiers reject blank values`() {
        assertFailsWith<IllegalArgumentException> { BoardId(" ") }
        assertFailsWith<IllegalArgumentException> { NoteId(" ") }
        assertFailsWith<IllegalArgumentException> { ChecklistItemId(" ") }
    }

    @Test
    fun `content boundaries are enforced after normalization`() {
        assertFailsWith<IllegalArgumentException> { Note(NoteId("empty"), " \r\n ") }
        assertEquals("a", Note(NoteId("one"), " a ").content)
        assertEquals(500, Note(NoteId("five-hundred"), "a".repeat(500)).content.length)
        assertFailsWith<IllegalArgumentException> {
            Note(NoteId("five-hundred-and-one"), "a".repeat(501))
        }
    }

    @Test
    fun `normalization is deterministic in UTF-16 code units`() {
        val note = Note(NoteId("note-1"), "  first\r\ninside  space\r😀  ")

        assertEquals("first\ninside  space\n😀", note.content)
        assertEquals(2, "😀".length)
        assertEquals(NoteColor.YELLOW, note.color)
        assertEquals(NoteStatus.TODO, note.status)
    }

    @Test
    fun `note equality uses identity rather than attributes`() {
        val id = NoteId("same-id")
        val first = Note(id, "first")
        val second = Note(id, "second", NoteColor.BLUE, NoteStatus.DOING)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first.content, second.content)
        assertNotEquals(first, Note(NoteId("different-id"), "first"))
    }

    @Test
    fun `board equality uses identity and protects its note collection`() {
        val originalNotes = mutableListOf(Note(NoteId("note-1"), "first"))
        val board = Board(BoardId("main"), originalNotes)
        val sameBoard = Board(BoardId("main"), listOf(Note(NoteId("note-2"), "second")))

        originalNotes.clear()

        assertEquals(1, board.notes.size)
        assertEquals(board, sameBoard)
        assertEquals(board.hashCode(), sameBoard.hashCode())
        assertNotEquals(board, Board(BoardId("other")))
    }
}
