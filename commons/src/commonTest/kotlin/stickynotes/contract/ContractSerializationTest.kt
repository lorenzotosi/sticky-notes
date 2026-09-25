// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.contract

import kotlinx.serialization.SerializationException
import stickynotes.domain.Board
import stickynotes.domain.BoardId
import stickynotes.domain.Note
import stickynotes.domain.NoteColor
import stickynotes.domain.NoteId
import stickynotes.domain.NoteStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ContractSerializationTest {
    @Test
    fun `roundtrip serialization preserves board snapshot structure`() {
        val original =
            BoardSnapshot(
                id = "main",
                schemaVersion = 1,
                revision = 5,
                wipLimit = 4,
                notes =
                    listOf(
                        NoteSnapshot(
                            id = "note-1",
                            content = "Task 1",
                            color = NoteColor.PINK,
                            status = NoteStatus.DOING,
                            blockedReason = "Blocked by backend",
                            checklist =
                                listOf(
                                    ChecklistItemSnapshot("item-1", "Step 1", true),
                                ),
                        ),
                    ),
            )

        val encoded = boardJson.encodeToString(BoardSnapshot.serializer(), original)
        val decoded = boardJson.decodeFromString(BoardSnapshot.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `deserialization rejects unknown fields`() {
        val invalidJson =
            """
            {
              "id": "main",
              "schemaVersion": 1,
              "revision": 0,
              "wipLimit": 3,
              "unexpectedProperty": "forbidden",
              "notes": []
            }
            """.trimIndent()

        assertFailsWith<SerializationException> {
            boardJson.decodeFromString(BoardSnapshot.serializer(), invalidJson)
        }
    }

    @Test
    fun `command polymorphic serialization uses type discriminator`() {
        val command: BoardCommand =
            BoardCommand.CreateNote(
                content = "New Note",
                color = NoteColor.GREEN,
                newId = "id-123",
            )

        val encoded = boardJson.encodeToString(BoardCommand.serializer(), command)
        val decoded = boardJson.decodeFromString(BoardCommand.serializer(), encoded)

        assertIs<BoardCommand.CreateNote>(decoded)
        assertEquals("New Note", decoded.content)
        assertEquals(NoteColor.GREEN, decoded.color)
        assertEquals("id-123", decoded.newId)
    }

    @Test
    fun `domain mapping converts correctly and detects invalid state`() {
        val note = Note(NoteId("n-1"), "Content", NoteColor.BLUE, NoteStatus.TODO)
        val domainBoard = Board(BoardId("main"), listOf(note))

        val snapshot = domainBoard.toSnapshot(revision = 2, wipLimit = 5)
        assertEquals("main", snapshot.id)
        assertEquals(2, snapshot.revision)
        assertEquals(5, snapshot.wipLimit)
        assertEquals(1, snapshot.notes.size)

        val rehydrated = snapshot.toDomain()
        assertEquals(domainBoard, rehydrated)
        assertEquals(domainBoard.notes.first().content, rehydrated.notes.first().content)
    }

    @Test
    fun `rehydration rejects invalid snapshot invariant`() {
        val invalidWipSnapshot = BoardSnapshot(wipLimit = 0)
        assertFailsWith<IllegalArgumentException> {
            invalidWipSnapshot.toDomain()
        }

        val duplicateIdSnapshot =
            BoardSnapshot(
                notes =
                    listOf(
                        NoteSnapshot("duplicate", "One", NoteColor.YELLOW, NoteStatus.TODO),
                        NoteSnapshot("duplicate", "Two", NoteColor.YELLOW, NoteStatus.TODO),
                    ),
            )
        assertFailsWith<IllegalArgumentException> {
            duplicateIdSnapshot.toDomain()
        }
    }
}
