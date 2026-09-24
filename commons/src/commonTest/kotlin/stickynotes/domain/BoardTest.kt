// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BoardTest {
    @Test
    fun `createNote adds note at end with TODO status and default yellow color`() {
        val board = Board(BoardId("main"))
        val result = board.createNote(NoteId("note-1"), "First note")

        assertIs<BoardCommandResult.Success>(result)
        val updatedBoard = result.board

        assertEquals(1, updatedBoard.notes.size)
        val created = updatedBoard.notes.first()
        assertEquals(NoteId("note-1"), created.id)
        assertEquals("First note", created.content)
        assertEquals(NoteColor.YELLOW, created.color)
        assertEquals(NoteStatus.TODO, created.status)
        assertEquals(0, board.notes.size, "Original board must remain immutable")
    }

    @Test
    fun `updateNote modifies content and color preserving id and status`() {
        val emptyBoard = Board(BoardId("main"))
        val createdResult = emptyBoard.createNote(NoteId("note-1"), "Old content")
        val initialBoard = (createdResult as BoardCommandResult.Success).board

        val updateResult =
            initialBoard.updateNote(
                id = NoteId("note-1"),
                content = "New content",
                color = NoteColor.BLUE,
            )

        assertIs<BoardCommandResult.Success>(updateResult)
        val updatedBoard = updateResult.board

        assertEquals(1, updatedBoard.notes.size)
        val updated = updatedBoard.notes.first()
        assertEquals(NoteId("note-1"), updated.id)
        assertEquals("New content", updated.content)
        assertEquals(NoteColor.BLUE, updated.color)
        assertEquals(NoteStatus.TODO, updated.status)
    }

    @Test
    fun `deleteNote removes note and compacts ordering`() {
        var board = Board(BoardId("main"))
        board = (board.createNote(NoteId("1"), "Note 1") as BoardCommandResult.Success).board
        board = (board.createNote(NoteId("2"), "Note 2") as BoardCommandResult.Success).board
        board = (board.createNote(NoteId("3"), "Note 3") as BoardCommandResult.Success).board

        val deleteResult = board.deleteNote(NoteId("2"))
        assertIs<BoardCommandResult.Success>(deleteResult)
        val compactBoard = deleteResult.board

        assertEquals(2, compactBoard.notes.size)
        assertEquals(listOf(NoteId("1"), NoteId("3")), compactBoard.notes.map { it.id })
    }

    @Test
    fun `createNote and updateNote reject invalid content with INVALID_CONTENT`() {
        val board = Board(BoardId("main"))
        val emptyCreate = board.createNote(NoteId("note-1"), "   \r\n  ")
        assertIs<BoardCommandResult.Failure>(emptyCreate)
        assertEquals("INVALID_CONTENT", emptyCreate.code)
        assertEquals("content", emptyCreate.field)

        val longCreate = board.createNote(NoteId("note-1"), "a".repeat(501))
        assertIs<BoardCommandResult.Failure>(longCreate)
        assertEquals("INVALID_CONTENT", longCreate.code)

        val validRes = board.createNote(NoteId("note-1"), "Valid")
        val validBoard = (validRes as BoardCommandResult.Success).board
        val invalidUpdate = validBoard.updateNote(NoteId("note-1"), content = "")
        assertIs<BoardCommandResult.Failure>(invalidUpdate)
        assertEquals("INVALID_CONTENT", invalidUpdate.code)
    }

    @Test
    fun `createNote rejects duplicate note id with DUPLICATE_ID`() {
        val res = Board(BoardId("main")).createNote(NoteId("note-1"), "First")
        val board = (res as BoardCommandResult.Success).board
        val duplicateResult = board.createNote(NoteId("note-1"), "Second")

        assertIs<BoardCommandResult.Failure>(duplicateResult)
        assertEquals("DUPLICATE_ID", duplicateResult.code)
        assertEquals("id", duplicateResult.field)
    }

    @Test
    fun `updateNote and deleteNote return NOTE_NOT_FOUND when note does not exist`() {
        val board = Board(BoardId("main"))

        val updateNotFound = board.updateNote(NoteId("missing"), content = "Content")
        assertIs<BoardCommandResult.Failure>(updateNotFound)
        assertEquals("NOTE_NOT_FOUND", updateNotFound.code)

        val deleteNotFound = board.deleteNote(NoteId("missing"))
        assertIs<BoardCommandResult.Failure>(deleteNotFound)
        assertEquals("NOTE_NOT_FOUND", deleteNotFound.code)
    }

    @Test
    fun `createNote enforces 200 notes limit with BOARD_FULL and allows creation after deletion`() {
        var board = Board(BoardId("main"))
        for (i in 1..200) {
            val res = board.createNote(NoteId("n-$i"), "Content $i")
            assertIs<BoardCommandResult.Success>(res)
            board = res.board
        }

        val overflow = board.createNote(NoteId("n-201"), "Overflow")
        assertIs<BoardCommandResult.Failure>(overflow)
        assertEquals("BOARD_FULL", overflow.code)

        val deleteRes = board.deleteNote(NoteId("n-100"))
        assertIs<BoardCommandResult.Success>(deleteRes)
        board = deleteRes.board

        val retryCreate = board.createNote(NoteId("n-201"), "Now allowed")
        assertIs<BoardCommandResult.Success>(retryCreate)
        assertEquals(200, retryCreate.board.notes.size)
    }
}
