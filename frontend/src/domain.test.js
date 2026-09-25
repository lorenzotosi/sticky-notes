// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { describe, expect, it } from 'vitest'
import { evaluateBoardCommand, validateNoteContent } from 'sticky-notes-domain'

describe('Domain JS Facade Interop', () => {
  it('validates note content correctly', () => {
    const validRes = JSON.parse(validateNoteContent('Valid content'))
    expect(validRes.valid).toBe(true)

    const invalidRes = JSON.parse(validateNoteContent('   '))
    expect(invalidRes.valid).toBe(false)
    expect(invalidRes.code).toBe('INVALID_CONTENT')
  })

  it('evaluates board command via engine', () => {
    const snapshot = {
      id: 'main',
      schemaVersion: 1,
      revision: 0,
      wipLimit: 3,
      notes: []
    }
    const command = {
      type: 'CREATE_NOTE',
      newId: 'note-1',
      content: 'Created via JS facade',
      color: 'YELLOW'
    }

    const resJson = evaluateBoardCommand(JSON.stringify(snapshot), JSON.stringify(command))
    const res = JSON.parse(resJson)

    expect(res.type).toBe('SUCCESS')
    expect(res.board.notes.length).toBe(1)
    expect(res.board.notes[0].content).toBe('Created via JS facade')
  })
})
