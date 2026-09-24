// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

package stickynotes.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class NoteTest {
    @Test
    fun `C01 blank content is rejected without changing an existing note`() {
        val existing = fixtureNote("existing", "still here")

        assertFailsWith<IllegalArgumentException> { fixtureNote("empty", " \r\n ") }
        assertEquals("still here", existing.content)
        assertFailsWith<IllegalArgumentException> { BoardId(" ") }
        assertFailsWith<IllegalArgumentException> { NoteId(" ") }
        assertFailsWith<IllegalArgumentException> { ChecklistItemId(" ") }
    }

    @Test
    fun `C02 content length boundaries are checked after normalization`() {
        assertEquals("a", fixtureNote("one", " a ").content)
        assertEquals(500, fixtureNote("five-hundred", "a".repeat(500)).content.length)
        assertFailsWith<IllegalArgumentException> {
            fixtureNote("five-hundred-and-one", " ${"a".repeat(501)} ")
        }
    }

    @Test
    fun `C03 emoji line endings and whitespace normalize identically`() {
        val note = fixtureNote("note-1", "  first\r\ninside  space\r😀  ")

        assertEquals("first\ninside  space\n😀", note.content)
        assertEquals(2, "😀".length)
        assertEquals(NoteColor.YELLOW, note.color)
        assertEquals(NoteStatus.TODO, note.status)
    }

    @Test
    fun `C04 equality uses identity and board notes are copied`() {
        val id = NoteId("same-id")
        val first = Note(id, "first")
        val second = Note(id, "second", NoteColor.BLUE, NoteStatus.DOING)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        val firstSnapshot = listOf(first.content, first.color, first.status)
        val secondSnapshot = listOf(second.content, second.color, second.status)
        assertNotEquals(firstSnapshot, secondSnapshot)
        assertNotEquals(first, fixtureNote("different-id", "first"))

        val originalNotes = mutableListOf(first)
        val board = Board(BoardId("main"), originalNotes)
        val sameBoard = Board(BoardId("main"), listOf(second))

        originalNotes.clear()

        assertEquals(1, board.notes.size)
        assertEquals(board, sameBoard)
        assertEquals(board.hashCode(), sameBoard.hashCode())
        assertNotEquals(board, Board(BoardId("other")))
    }
}

private fun fixtureNote(
    id: String,
    content: String,
) = Note(NoteId(id), content)
