// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

import stickynotes.contract.BoardCommand
import stickynotes.contract.BoardSnapshot
import stickynotes.contract.CommandResponse
import stickynotes.contract.NoteSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MovementTest {
    @Test
    fun `moveNote rejects transition to DOING when wip limit is reached`() {
        val noteA = Note(NoteId("A"), "Task A", status = NoteStatus.DOING)
        val noteB = Note(NoteId("B"), "Task B", status = NoteStatus.TODO)
        val board = Board(BoardId("main"), wipLimit = 1, notes = listOf(noteA, noteB))

        val result = board.moveNote(NoteId("B"), NoteStatus.DOING, 1)

        assertIs<BoardCommandResult.Failure>(result)
        assertEquals("WIP_LIMIT_REACHED", result.code)
        assertEquals(NoteStatus.TODO, board.notes.first { it.id == NoteId("B") }.status)
    }

    @Test
    fun `moveNote validates all state transition pairs`() {
        fun createTestBoard(): Board {
            val todo = Note(NoteId("TODO_NOTE"), "1", status = NoteStatus.TODO)
            val doing = Note(NoteId("DOING_NOTE"), "2", status = NoteStatus.DOING)
            val done = Note(NoteId("DONE_NOTE"), "3", status = NoteStatus.DONE)
            return Board(BoardId("main"), wipLimit = 3, notes = listOf(todo, doing, done))
        }

        val board = createTestBoard()

        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("TODO_NOTE"), NoteStatus.DOING, 0))
        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("DOING_NOTE"), NoteStatus.TODO, 0))
        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("DOING_NOTE"), NoteStatus.DONE, 0))
        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("DONE_NOTE"), NoteStatus.DOING, 0))

        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("TODO_NOTE"), NoteStatus.TODO, 0))
        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("DOING_NOTE"), NoteStatus.DOING, 0))
        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("DONE_NOTE"), NoteStatus.DONE, 0))

        val invalidTodoDone = board.moveNote(NoteId("TODO_NOTE"), NoteStatus.DONE, 0)
        assertIs<BoardCommandResult.Failure>(invalidTodoDone)
        assertEquals("INVALID_TRANSITION", invalidTodoDone.code)

        val invalidDoneTodo = board.moveNote(NoteId("DONE_NOTE"), NoteStatus.TODO, 0)
        assertIs<BoardCommandResult.Failure>(invalidDoneTodo)
        assertEquals("INVALID_TRANSITION", invalidDoneTodo.code)
    }

    @Test
    fun `reordering in DOING with full WIP succeeds without consuming WIP`() {
        val a = Note(NoteId("A"), "Task A", status = NoteStatus.DOING)
        val b = Note(NoteId("B"), "Task B", status = NoteStatus.DOING)
        val c = Note(NoteId("C"), "Task C", status = NoteStatus.DOING)
        val board = Board(BoardId("main"), wipLimit = 3, notes = listOf(a, b, c))

        val result = board.moveNote(NoteId("A"), NoteStatus.DOING, 2)
        assertIs<BoardCommandResult.Success>(result)
        val reordered = result.board

        assertEquals(listOf(NoteId("B"), NoteId("C"), NoteId("A")), reordered.notes.map { it.id })
    }

    @Test
    fun `setWipLimit validates boundaries and prevents limit below occupancy`() {
        val a = Note(NoteId("A"), "Task A", status = NoteStatus.DOING)
        val b = Note(NoteId("B"), "Task B", status = NoteStatus.DOING)
        val board = Board(BoardId("main"), wipLimit = 3, notes = listOf(a, b))

        val belowOccupancy = board.setWipLimit(1)
        assertIs<BoardCommandResult.Failure>(belowOccupancy)
        assertEquals("WIP_BELOW_OCCUPANCY", belowOccupancy.code)

        val equalOccupancy = board.setWipLimit(2)
        assertIs<BoardCommandResult.Success>(equalOccupancy)
        assertEquals(2, equalOccupancy.board.wipLimit)

        val outOfBoundsLower = board.setWipLimit(0)
        assertIs<BoardCommandResult.Failure>(outOfBoundsLower)
        assertEquals("INVALID_WIP_LIMIT", outOfBoundsLower.code)

        val outOfBoundsUpper = board.setWipLimit(21)
        assertIs<BoardCommandResult.Failure>(outOfBoundsUpper)
        assertEquals("INVALID_WIP_LIMIT", outOfBoundsUpper.code)
    }

    @Test
    fun `destination index bounds are strictly validated`() {
        val a = Note(NoteId("A"), "Task A", status = NoteStatus.TODO)
        val b = Note(NoteId("B"), "Task B", status = NoteStatus.TODO)
        val c = Note(NoteId("C"), "Task C", status = NoteStatus.DOING)
        val board = Board(BoardId("main"), wipLimit = 3, notes = listOf(a, b, c))

        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("A"), NoteStatus.DOING, 0))
        assertIs<BoardCommandResult.Success>(board.moveNote(NoteId("A"), NoteStatus.DOING, 1))

        val negativeIndex = board.moveNote(NoteId("A"), NoteStatus.DOING, -1)
        assertIs<BoardCommandResult.Failure>(negativeIndex)
        assertEquals("INVALID_INDEX", negativeIndex.code)

        val indexTooLarge = board.moveNote(NoteId("A"), NoteStatus.DOING, 2)
        assertIs<BoardCommandResult.Failure>(indexTooLarge)
        assertEquals("INVALID_INDEX", indexTooLarge.code)
    }

    @Test
    fun `BoardEngine executes move command preserving snapshot revision`() {
        val snapshot =
            BoardSnapshot(
                id = "main",
                revision = 7,
                wipLimit = 2,
                notes =
                    listOf(
                        NoteSnapshot("1", "Todo Note", NoteColor.YELLOW, NoteStatus.TODO),
                    ),
            )

        val command =
            BoardCommand.MoveNote(
                noteId = "1",
                targetStatus = NoteStatus.DOING,
                destinationIndex = 0,
            )

        val response = BoardEngine.execute(snapshot, command)
        assertIs<CommandResponse.Success>(response)
        assertEquals(7, response.board.revision)
        assertEquals(NoteStatus.DOING, response.board.notes.first().status)
    }
}
