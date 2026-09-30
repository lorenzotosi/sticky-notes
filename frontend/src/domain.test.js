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

    const longRes = JSON.parse(validateNoteContent('a'.repeat(501)))
    expect(longRes.valid).toBe(false)
    expect(longRes.code).toBe('INVALID_CONTENT')
  })

  it('handles malformed JSON and unknown command structures', () => {
    const malformed = evaluateBoardCommand('{ bad json', '{}')
    expect(JSON.parse(malformed)).toMatchObject({
      type: 'FAILURE',
      code: 'INVALID_REQUEST'
    })

    const unknownCmd = evaluateBoardCommand(
      JSON.stringify({ id: 'main', schemaVersion: 1, revision: 0, wipLimit: 3, notes: [] }),
      JSON.stringify({ type: 'UNKNOWN_COMMAND' })
    )
    expect(JSON.parse(unknownCmd)).toMatchObject({
      type: 'FAILURE',
      code: 'INVALID_REQUEST'
    })
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

    const res = JSON.parse(evaluateBoardCommand(JSON.stringify(snapshot), JSON.stringify(command)))
    expect(res.type).toBe('SUCCESS')
    expect(res.board.notes.length).toBe(1)
    expect(res.board.notes[0].content).toBe('Created via JS facade')
  })

  it('propagates invariant rejections identically to JVM target', () => {
    const snapshot = {
      id: 'main',
      schemaVersion: 1,
      revision: 0,
      wipLimit: 1,
      notes: [
        {
          id: 'note-2',
          content: 'Active',
          color: 'YELLOW',
          status: 'TODO',
          blockedReason: null,
          checklist: []
        },
        {
          id: 'note-1',
          content: 'Pending',
          color: 'YELLOW',
          status: 'DOING',
          blockedReason: null,
          checklist: []
        }
      ]
    }

    const moveCommand = {
      type: 'MOVE_NOTE',
      noteId: 'note-2',
      targetStatus: 'DOING',
      destinationIndex: 1
    }

    const res = JSON.parse(evaluateBoardCommand(JSON.stringify(snapshot), JSON.stringify(moveCommand)))
    expect(res.type).toBe('FAILURE')
    expect(res.code).toBe('WIP_LIMIT_REACHED')
  })

  it('rejects done transition when checklist is incomplete', () => {
    const snapshot = {
      id: 'main',
      schemaVersion: 1,
      revision: 0,
      wipLimit: 3,
      notes: [
        {
          id: 'note-1',
          content: 'With task',
          color: 'YELLOW',
          status: 'DOING',
          blockedReason: null,
          checklist: [
            { id: 'item-1', label: 'Incomplete item', completed: false }
          ]
        }
      ]
    }

    const doneCommand = {
      type: 'MOVE_NOTE',
      noteId: 'note-1',
      targetStatus: 'DONE',
      destinationIndex: 0
    }

    const res = JSON.parse(evaluateBoardCommand(JSON.stringify(snapshot), JSON.stringify(doneCommand)))
    expect(res.type).toBe('FAILURE')
    expect(res.code).toBe('CHECKLIST_INCOMPLETE')
  })
})
