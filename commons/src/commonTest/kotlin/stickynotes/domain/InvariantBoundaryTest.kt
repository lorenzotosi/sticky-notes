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

class InvariantBoundaryTest {
    private fun createInitialBoard(wipLimit: Int = 3): BoardSnapshot =
        BoardSnapshot(
            id = "main",
            schemaVersion = 1,
            revision = 0,
            wipLimit = wipLimit,
            notes = emptyList(),
        )

    @Test
    fun rejectsContentBeyondFiveHundredUtf16Units() {
        val board = createInitialBoard()
        val exact500 = "a".repeat(500)
        val res500 = BoardEngine.execute(board, BoardCommand.CreateNote(exact500, NoteColor.YELLOW, "n1"))
        assertIs<CommandResponse.Success>(res500)

        val overflow501 = "a".repeat(501)
        val res501 = BoardEngine.execute(board, BoardCommand.CreateNote(overflow501, NoteColor.YELLOW, "n2"))
        val failure = assertIs<CommandResponse.Failure>(res501)
        assertEquals("INVALID_CONTENT", failure.code)
    }

    @Test
    fun countsNonBmpEmojiAsTwoUtf16CodeUnits() {
        val board = createInitialBoard()
        val emoji = "\uD83D\uDE00"
        val payload498 = "a".repeat(498) + emoji
        val resPass = BoardEngine.execute(board, BoardCommand.CreateNote(payload498, NoteColor.YELLOW, "n1"))
        assertIs<CommandResponse.Success>(resPass)

        val payload500 = "a".repeat(499) + emoji
        val resFail = BoardEngine.execute(board, BoardCommand.CreateNote(payload500, NoteColor.YELLOW, "n2"))
        val failure = assertIs<CommandResponse.Failure>(resFail)
        assertEquals("INVALID_CONTENT", failure.code)
    }

    @Test
    fun preservesInternalWhitespaceAndNormalizesNewlines() {
        val board = createInitialBoard()
        val input = "  Line 1\r\nLine 2\rLine 3  "
        val res = BoardEngine.execute(board, BoardCommand.CreateNote(input, NoteColor.YELLOW, "n1"))
        val success = assertIs<CommandResponse.Success>(res)
        assertEquals("Line 1\nLine 2\nLine 3", success.board.notes.first().content)
    }

    @Test
    fun rejectsIllegalStateTransitionsLeavingBoardUnchanged() {
        val note = NoteSnapshot("n1", "Content", NoteColor.YELLOW, NoteStatus.TODO, null, emptyList())
        val board = createInitialBoard().copy(notes = listOf(note))

        val directToDone = BoardEngine.execute(board, BoardCommand.MoveNote("n1", NoteStatus.DONE, 0))
        val failure = assertIs<CommandResponse.Failure>(directToDone)
        assertEquals("INVALID_TRANSITION", failure.code)

        val doneNote = note.copy(status = NoteStatus.DONE)
        val doneBoard = createInitialBoard().copy(notes = listOf(doneNote))
        val directToTodo = BoardEngine.execute(doneBoard, BoardCommand.MoveNote("n1", NoteStatus.TODO, 0))
        val failureTodo = assertIs<CommandResponse.Failure>(directToTodo)
        assertEquals("INVALID_TRANSITION", failureTodo.code)
    }

    @Test
    fun permitsSameColumnReorderAtWipLimit() {
        val note1 = NoteSnapshot("n1", "A", NoteColor.YELLOW, NoteStatus.DOING, null, emptyList())
        val note2 = NoteSnapshot("n2", "B", NoteColor.YELLOW, NoteStatus.DOING, null, emptyList())
        val board = createInitialBoard(wipLimit = 2).copy(notes = listOf(note1, note2))

        val reorder = BoardEngine.execute(board, BoardCommand.MoveNote("n1", NoteStatus.DOING, 1))
        val success = assertIs<CommandResponse.Success>(reorder)
        assertEquals(listOf("n2", "n1"), success.board.notes.map { it.id })
    }

    @Test
    fun rejectsDestinationIndexOutOfRangeWithoutClamping() {
        val note = NoteSnapshot("n1", "A", NoteColor.YELLOW, NoteStatus.TODO, null, emptyList())
        val board = createInitialBoard().copy(notes = listOf(note))

        val failNegative = BoardEngine.execute(board, BoardCommand.MoveNote("n1", NoteStatus.DOING, -1))
        assertIs<CommandResponse.Failure>(failNegative)

        val failTooHigh = BoardEngine.execute(board, BoardCommand.MoveNote("n1", NoteStatus.DOING, 2))
        assertIs<CommandResponse.Failure>(failTooHigh)
    }

    @Test
    fun rejectsLoweringWipLimitBelowCurrentDoingOccupancy() {
        val note1 = NoteSnapshot("n1", "A", NoteColor.YELLOW, NoteStatus.DOING, null, emptyList())
        val note2 = NoteSnapshot("n2", "B", NoteColor.YELLOW, NoteStatus.DOING, null, emptyList())
        val board = createInitialBoard(wipLimit = 3).copy(notes = listOf(note1, note2))

        val res = BoardEngine.execute(board, BoardCommand.SetWipLimit(1))
        val failure = assertIs<CommandResponse.Failure>(res)
        assertEquals("WIP_BELOW_OCCUPANCY", failure.code)
    }
}
