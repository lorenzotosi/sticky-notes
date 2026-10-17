// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { describe, expect, it, vi } from 'vitest'
import { useBoard } from './useBoard.js'
import { ApiError } from '../api/boardClient.js'

describe('useBoard composable', () => {
  function createMockClient() {
    return {
      getBoard: vi.fn(),
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
      moveNote: vi.fn(),
      setWipLimit: vi.fn(),
      blockNote: vi.fn(),
      unblockNote: vi.fn(),
      addChecklistItem: vi.fn(),
      updateChecklistItem: vi.fn(),
      deleteChecklistItem: vi.fn()
    }
  }

  it('loads board snapshot and manages loading state', async () => {
    const client = createMockClient()
    const snapshot = { id: 'main', schemaVersion: 1, revision: 0, wipLimit: 3, notes: [] }
    client.getBoard.mockResolvedValueOnce(snapshot)

    const { board, loading, fetchBoard } = useBoard(client)
    expect(loading.value).toBe(false)
    expect(board.value).toBeNull()

    const promise = fetchBoard()
    expect(loading.value).toBe(true)

    await promise
    expect(loading.value).toBe(false)
    expect(board.value).toEqual(snapshot)
  })

  it('preserves draft upon network or HTTP failure', async () => {
    const client = createMockClient()
    client.getBoard.mockResolvedValueOnce({
      id: 'main',
      schemaVersion: 1,
      revision: 0,
      wipLimit: 3,
      notes: []
    })
    client.createNote.mockRejectedValueOnce(
      new ApiError(503, { code: 'DATABASE_UNAVAILABLE', message: 'DB down' })
    )

    const { draft, setDraftField, createNote, fetchBoard, saving, error } = useBoard(client)
    await fetchBoard()

    setDraftField('content', 'Important draft text')
    setDraftField('color', 'BLUE')

    await expect(createNote('Important draft text', 'BLUE')).rejects.toThrow()
    expect(saving.value).toBe(false)
    expect(error.value.code).toBe('DATABASE_UNAVAILABLE')
    expect(draft.value.content).toBe('Important draft text')
    expect(draft.value.color).toBe('BLUE')
  })

  it('blocks second concurrent mutating command while saving is active', async () => {
    const client = createMockClient()
    client.getBoard.mockResolvedValueOnce({
      id: 'main',
      schemaVersion: 1,
      revision: 0,
      wipLimit: 3,
      notes: []
    })

    let resolveSave
    client.createNote.mockReturnValueOnce(new Promise((resolve) => {
      resolveSave = resolve
    }))

    const { fetchBoard, createNote, saving } = useBoard(client)
    await fetchBoard()

    const firstCall = createNote('Note 1', 'YELLOW')
    expect(saving.value).toBe(true)

    await createNote('Note 2', 'YELLOW')
    expect(client.createNote).toHaveBeenCalledTimes(1)

    resolveSave({
      board: { id: 'main', schemaVersion: 1, revision: 1, wipLimit: 3, notes: [] },
      createdNoteId: 'uuid-1'
    })
    await firstCall
    expect(saving.value).toBe(false)
  })

  it('rejects command via Kotlin JS prevalidation before network call', async () => {
    const client = createMockClient()
    client.getBoard.mockResolvedValueOnce({
      id: 'main',
      schemaVersion: 1,
      revision: 0,
      wipLimit: 1,
      notes: [
        {
          id: 'note-2',
          content: 'Todo 2',
          color: 'YELLOW',
          status: 'TODO',
          blockedReason: null,
          checklist: []
        },
        {
          id: 'note-1',
          content: 'Doing 1',
          color: 'YELLOW',
          status: 'DOING',
          blockedReason: null,
          checklist: []
        }
      ]
    })

    const { fetchBoard, moveNote, error } = useBoard(client)
    await fetchBoard()

    await moveNote('note-2', 'DOING', 0)
    expect(client.moveNote).not.toHaveBeenCalled()
    expect(error.value.code).toBe('WIP_LIMIT_REACHED')
  })

  it('re-fetches board snapshot when encountering REVISION_CONFLICT', async () => {
    const client = createMockClient()
    const initialBoard = { id: 'main', schemaVersion: 1, revision: 1, wipLimit: 3, notes: [] }
    const updatedBoard = { id: 'main', schemaVersion: 1, revision: 2, wipLimit: 3, notes: [] }

    client.getBoard
      .mockResolvedValueOnce(initialBoard)
      .mockResolvedValueOnce(updatedBoard)

    client.createNote.mockRejectedValueOnce(
      new ApiError(409, { code: 'REVISION_CONFLICT', message: 'Conflict', currentRevision: 2 })
    )

    const { board, fetchBoard, createNote, error } = useBoard(client)
    await fetchBoard()
    expect(board.value.revision).toBe(1)

    await expect(createNote('Some note', 'YELLOW')).rejects.toThrow()
    expect(error.value.code).toBe('REVISION_CONFLICT')
    expect(board.value.revision).toBe(2)
  })

  it('populates conflictState and preserves draft when editing a note concurrently deleted', async () => {
    const client = createMockClient()
    const initialBoard = {
      id: 'main',
      schemaVersion: 1,
      revision: 1,
      wipLimit: 3,
      notes: [{ id: 'note-1', content: 'Old', color: 'YELLOW', status: 'TODO', blockedReason: null, checklist: [] }]
    }
    const boardWithoutNote = {
      id: 'main',
      schemaVersion: 1,
      revision: 2,
      wipLimit: 3,
      notes: []
    }

    client.getBoard
      .mockResolvedValueOnce(initialBoard)
      .mockResolvedValueOnce(boardWithoutNote)

    client.updateNote.mockRejectedValueOnce(
      new ApiError(409, { code: 'REVISION_CONFLICT', message: 'Conflict', currentRevision: 2 })
    )

    const { fetchBoard, updateNote, conflictState, draft } = useBoard(client)
    await fetchBoard()

    await expect(updateNote('note-1', { content: 'Updated in window 2', color: 'PINK' })).rejects.toThrow()

    expect(draft.value.content).toBe('Updated in window 2')
    expect(conflictState.value).not.toBeNull()
    expect(conflictState.value.targetDeleted).toBe(true)
    expect(conflictState.value.preservedText).toBe('Updated in window 2')
  })

  it('reconciles board state upon request timeout without automatic duplicate resend', async () => {
    const client = createMockClient()
    const initialBoard = { id: 'main', schemaVersion: 1, revision: 1, wipLimit: 3, notes: [] }
    const savedBoardOnServer = {
      id: 'main',
      schemaVersion: 1,
      revision: 2,
      wipLimit: 3,
      notes: [{ id: 'n-1', content: 'Saved before timeout', color: 'YELLOW', status: 'TODO', blockedReason: null, checklist: [] }]
    }

    client.getBoard
      .mockResolvedValueOnce(initialBoard)
      .mockResolvedValueOnce(savedBoardOnServer)

    client.createNote.mockRejectedValueOnce(
      new ApiError(408, { code: 'REQUEST_TIMEOUT', message: 'Request timed out' })
    )

    const { board, fetchBoard, createNote } = useBoard(client)
    await fetchBoard()

    await expect(createNote('Saved before timeout', 'YELLOW')).rejects.toThrow()
    expect(client.createNote).toHaveBeenCalledTimes(1)
    expect(board.value.revision).toBe(2)
    expect(board.value.notes).toHaveLength(1)
  })

  it('ignores stale out-of-order fetchBoard responses with lower revision', async () => {
    const client = createMockClient()
    const newerBoard = { id: 'main', schemaVersion: 1, revision: 5, wipLimit: 3, notes: [] }
    const staleBoard = { id: 'main', schemaVersion: 1, revision: 3, wipLimit: 3, notes: [] }

    client.getBoard
      .mockResolvedValueOnce(newerBoard)
      .mockResolvedValueOnce(staleBoard)

    const { board, fetchBoard } = useBoard(client)
    await fetchBoard()
    expect(board.value.revision).toBe(5)

    await fetchBoard()
    expect(board.value.revision).toBe(5)
  })
})
