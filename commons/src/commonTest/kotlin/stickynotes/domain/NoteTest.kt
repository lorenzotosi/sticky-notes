// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

package stickynotes.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class NoteTest {
    @Test
    fun `blank content is rejected`() {
        assertFailsWith<IllegalArgumentException> { Note(NoteId("note-1"), " ") }
    }
}
