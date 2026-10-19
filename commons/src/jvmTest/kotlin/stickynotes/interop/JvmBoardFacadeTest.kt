// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.interop

import stickynotes.contract.CommandResponse
import stickynotes.domain.NoteColor
import stickynotes.domain.NoteStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JvmBoardFacadeTest {
    @Test
    fun `facade factories instantiate all commands and execute successfully`() {
        val emptyBoard = JvmBoardFacade.createEmptyBoard("main", 3)
        assertEquals("main", emptyBoard.id)
        assertEquals(3, emptyBoard.wipLimit)

        val createCmd = JvmBoardFacade.createNote("n1", "Task 1", NoteColor.YELLOW)
        val created = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(emptyBoard, createCmd)).board

        val moveCmd = JvmBoardFacade.moveNote("n1", NoteStatus.DOING, 0)
        val moved = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(created, moveCmd)).board

        val blockCmd = JvmBoardFacade.blockNote("n1", "Waiting")
        val blocked = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(moved, blockCmd)).board

        val unblockCmd = JvmBoardFacade.unblockNote("n1")
        val unblocked = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(blocked, unblockCmd)).board

        val addItemCmd = JvmBoardFacade.addItem("n1", "i1", "Check item")
        val withItem = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(unblocked, addItemCmd)).board

        val updateItemCmd = JvmBoardFacade.updateItem("n1", "i1", "New label", true)
        val itemUpdated = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(withItem, updateItemCmd)).board

        val deleteItemCmd = JvmBoardFacade.deleteItem("n1", "i1")
        val itemDeleted = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(itemUpdated, deleteItemCmd)).board

        val updateNoteCmd = JvmBoardFacade.updateNote("n1", "Renamed", NoteColor.BLUE)
        val updatedNote = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(itemDeleted, updateNoteCmd)).board

        val wipLimitCmd = JvmBoardFacade.setWipLimit(5)
        val withWip = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(updatedNote, wipLimitCmd)).board
        assertEquals(5, withWip.wipLimit)

        val deleteNoteCmd = JvmBoardFacade.deleteNote("n1")
        val finished = assertIs<CommandResponse.Success>(JvmBoardFacade.execute(withWip, deleteNoteCmd)).board
        assertEquals(0, finished.notes.size)
    }
}
