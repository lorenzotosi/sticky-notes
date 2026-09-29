// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.contract

import stickynotes.domain.BoardEngine
import stickynotes.domain.NoteColor
import stickynotes.domain.NoteStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SnapshotTest {
    private fun note(
        id: String,
        status: NoteStatus = NoteStatus.TODO,
        blockedReason: String? = null,
        checklist: List<ChecklistItemSnapshot> = emptyList(),
    ) = NoteSnapshot(id, id, NoteColor.YELLOW, status, blockedReason, checklist)

    @Test
    fun `rehydration from DB rejects persisted states that commands cannot create`() {
        val incomplete = listOf(ChecklistItemSnapshot("item", "Item", false))
        val invalidSnapshots =
            listOf(
                BoardSnapshot(notes = listOf(note("duplicate"), note("duplicate"))),
                BoardSnapshot(notes = listOf(note("done", NoteStatus.DONE, checklist = incomplete))),
                BoardSnapshot(notes = listOf(note("done", NoteStatus.DONE, blockedReason = "blocked"))),
                BoardSnapshot(wipLimit = 1, notes = listOf(note("one", NoteStatus.DOING),
                    note("two", NoteStatus.DOING))),
                BoardSnapshot(revision = -1),
                BoardSnapshot(notes = listOf(note("done", NoteStatus.DONE), note("todo"))),
            )

        invalidSnapshots.forEach { snapshot ->
            assertFailsWith<IllegalArgumentException> {
                snapshot.toDomain()
            }

            val result =
                BoardEngine.execute(
                    snapshot,
                    BoardCommand.CreateNote("content", NoteColor.YELLOW, "new-note"),
                )

            val failure = assertIs<CommandResponse.Failure>(result)
            assertEquals("INVALID_SNAPSHOT", failure.code)
        }
    }
}
