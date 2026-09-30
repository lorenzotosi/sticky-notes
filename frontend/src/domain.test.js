// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { evaluateBoardCommand, validateNoteContent } from 'sticky-notes-domain'

describe('Domain JS Facade Interop', () => {
  it('ships the entry points and declarations advertised by the installed package', () => {
    const directory = resolve('node_modules/sticky-notes-domain')
    const manifest = JSON.parse(readFileSync(resolve(directory, 'package.json'), 'utf8'))
    const exported = manifest.exports['.']

    for (const path of [manifest.main, manifest.module, manifest.types, ...Object.values(exported)]) {
      expect(existsSync(resolve(directory, path))).toBe(true)
    }
    const declarations = readFileSync(resolve(directory, manifest.types), 'utf8')
    expect(declarations).toContain('evaluateBoardCommand')
    expect(declarations).toContain('validateNoteContent')
  })

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

  it('rejects invalid enum values and fractional indexes at the JSON boundary', () => {
    const snapshot = JSON.stringify({
      id: 'main', schemaVersion: 1, revision: 0, wipLimit: 3,
      notes: [{ id: 'n1', content: 'Task', color: 'YELLOW', status: 'TODO' }]
    })
    const commands = [
      { type: 'CREATE_NOTE', newId: 'n2', content: 'Task', color: 'RED' },
      { type: 'MOVE_NOTE', noteId: 'n1', targetStatus: 'DOING', destinationIndex: 0.5 },
      { type: 'SET_WIP_LIMIT', limit: 1.5 }
    ]

    for (const command of commands) {
      expect(JSON.parse(evaluateBoardCommand(snapshot, JSON.stringify(command)))).toMatchObject({
        type: 'FAILURE', code: 'INVALID_REQUEST'
      })
    }
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
