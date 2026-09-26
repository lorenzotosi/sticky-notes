// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BlockerAndChecklistTest {
    @Test
    fun `blocked DOING note cannot move to other status but can reorder in same column`() {
        val note = Note(NoteId("N1"), "Task", status = NoteStatus.DOING, blockedReason = "Waiting for review")
        val board = Board(BoardId("main"), notes = listOf(note))

        val toDone = board.moveNote(NoteId("N1"), NoteStatus.DONE, 0)
        assertIs<BoardCommandResult.Failure>(toDone)
        assertEquals("NOTE_BLOCKED", toDone.code)

        val toTodo = board.moveNote(NoteId("N1"), NoteStatus.TODO, 0)
        assertIs<BoardCommandResult.Failure>(toTodo)
        assertEquals("NOTE_BLOCKED", toTodo.code)

        val reorder = board.moveNote(NoteId("N1"), NoteStatus.DOING, 0)
        assertIs<BoardCommandResult.Success>(reorder)
    }

    @Test
    fun `block with empty or too long reason is rejected, valid reason blocks, unblock clears`() {
        val note = Note(NoteId("N1"), "Task")
        val board = Board(BoardId("main"), notes = listOf(note))

        val emptyRes = board.blockNote(NoteId("N1"), "   ")
        assertIs<BoardCommandResult.Failure>(emptyRes)
        assertEquals("INVALID_BLOCK_REASON", emptyRes.code)

        val longRes = board.blockNote(NoteId("N1"), "a".repeat(201))
        assertIs<BoardCommandResult.Failure>(longRes)
        assertEquals("INVALID_BLOCK_REASON", longRes.code)

        val validRes = board.blockNote(NoteId("N1"), "Valid reason")
        assertIs<BoardCommandResult.Success>(validRes)
        assertEquals("Valid reason", validRes.board.notes.first().blockedReason)

        val unblockRes = validRes.board.unblockNote(NoteId("N1"))
        assertIs<BoardCommandResult.Success>(unblockRes)
        assertNull(unblockRes.board.notes.first().blockedReason)
    }

    @Test
    fun `DONE note cannot be blocked but allows content and color editing`() {
        val note = Note(NoteId("N1"), "Task", status = NoteStatus.DONE)
        val board = Board(BoardId("main"), notes = listOf(note))

        val blockRes = board.blockNote(NoteId("N1"), "Reason")
        assertIs<BoardCommandResult.Failure>(blockRes)

        val editRes = board.updateNote(NoteId("N1"), content = "New Done Text", color = NoteColor.PINK)
        assertIs<BoardCommandResult.Success>(editRes)
        assertEquals("New Done Text", editRes.board.notes.first().content)
        assertEquals(NoteColor.PINK, editRes.board.notes.first().color)
    }

    @Test
    fun `incomplete checklist prevents moving to DONE`() {
        val item = ChecklistItem(ChecklistItemId("I1"), "Subtask", completed = false)
        val note = Note(NoteId("N1"), "Task", status = NoteStatus.DOING, checklist = listOf(item))
        val board = Board(BoardId("main"), notes = listOf(note))

        val result = board.moveNote(NoteId("N1"), NoteStatus.DONE, 0)
        assertIs<BoardCommandResult.Failure>(result)
        assertEquals("CHECKLIST_INCOMPLETE", result.code)
    }

    @Test
    fun `empty checklist or all completed items allows moving to DONE`() {
        val emptyNote = Note(NoteId("N1"), "Task 1", status = NoteStatus.DOING)
        val completeItem = ChecklistItem(ChecklistItemId("I1"), "Subtask", completed = true)
        val fullNote = Note(NoteId("N2"), "Task 2", status = NoteStatus.DOING, checklist = listOf(completeItem))
        val board = Board(BoardId("main"), notes = listOf(emptyNote, fullNote))

        val res1 = board.moveNote(NoteId("N1"), NoteStatus.DONE, 0)
        assertIs<BoardCommandResult.Success>(res1)

        val res2 = res1.board.moveNote(NoteId("N2"), NoteStatus.DONE, 1)
        assertIs<BoardCommandResult.Success>(res2)
        assertEquals(2, res2.board.notes.count { it.status == NoteStatus.DONE })
    }

    @Test
    fun `DONE note rejects any checklist mutations`() {
        val item = ChecklistItem(ChecklistItemId("I1"), "Done item", completed = true)
        val note = Note(NoteId("N1"), "Task", status = NoteStatus.DONE, checklist = listOf(item))
        val board = Board(BoardId("main"), notes = listOf(note))

        val addRes = board.addChecklistItem(NoteId("N1"), "New", ChecklistItemId("I2"))
        assertIs<BoardCommandResult.Failure>(addRes)
        assertEquals("NOTE_DONE_READ_ONLY", addRes.code)

        val updateRes = board.updateChecklistItem(NoteId("N1"), ChecklistItemId("I1"), label = "Renamed")
        assertIs<BoardCommandResult.Failure>(updateRes)
        assertEquals("NOTE_DONE_READ_ONLY", updateRes.code)

        val deleteRes = board.deleteChecklistItem(NoteId("N1"), ChecklistItemId("I1"))
        assertIs<BoardCommandResult.Failure>(deleteRes)
        assertEquals("NOTE_DONE_READ_ONLY", deleteRes.code)
    }

    @Test
    fun `reopening DONE note to DOING checks wipLimit`() {
        val doingNote = Note(NoteId("DOING_1"), "In progress", status = NoteStatus.DOING)
        val doneNote = Note(NoteId("DONE_1"), "Finished", status = NoteStatus.DONE)
        val board = Board(BoardId("main"), wipLimit = 1, notes = listOf(doingNote, doneNote))

        val reopenFail = board.moveNote(NoteId("DONE_1"), NoteStatus.DOING, 1)
        assertIs<BoardCommandResult.Failure>(reopenFail)
        assertEquals("WIP_LIMIT_REACHED", reopenFail.code)

        val boardWithSpace = Board(BoardId("main"), wipLimit = 2, notes = listOf(doingNote, doneNote))
        val reopenSuccess = boardWithSpace.moveNote(NoteId("DONE_1"), NoteStatus.DOING, 1)
        assertIs<BoardCommandResult.Success>(reopenSuccess)
        assertEquals(NoteStatus.DOING, reopenSuccess.board.notes.first { it.id == NoteId("DONE_1") }.status)
    }

    @Test
    fun `checklist capacity of 20 items is enforced`() {
        val items = (1..20).map { ChecklistItem(ChecklistItemId("I$it"), "Item $it") }
        val note = Note(NoteId("N1"), "Task", checklist = items)
        val board = Board(BoardId("main"), notes = listOf(note))

        val overflowRes = board.addChecklistItem(NoteId("N1"), "Item 21", ChecklistItemId("I21"))
        assertIs<BoardCommandResult.Failure>(overflowRes)
        assertEquals("CHECKLIST_FULL", overflowRes.code)

        val deletedRes = board.deleteChecklistItem(NoteId("N1"), ChecklistItemId("I1"))
        assertIs<BoardCommandResult.Success>(deletedRes)

        val addedRes = deletedRes.board.addChecklistItem(NoteId("N1"), "Item 21", ChecklistItemId("I21"))
        assertIs<BoardCommandResult.Success>(addedRes)
        assertEquals(20, addedRes.board.notes.first().checklist.size)
    }
}
