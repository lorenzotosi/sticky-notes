// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, boardClient } from './boardClient.js'

describe('boardClient', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('fetches board snapshot successfully', async () => {
    const mockBoard = { id: 'main', revision: 0, wipLimit: 3, notes: [] }
    fetch.mockResolvedValueOnce({
      ok: true,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => mockBoard
    })

    const result = await boardClient.getBoard()
    expect(result).toEqual(mockBoard)
    expect(fetch).toHaveBeenCalledWith('/api/board', expect.objectContaining({
      method: 'GET'
    }))
  })

  it('attaches expectedRevision to request body on mutation', async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ board: { revision: 1 }, createdNoteId: 'uuid-1' })
    })

    await boardClient.createNote('Test content', 'YELLOW', 0)
    expect(fetch).toHaveBeenCalledWith('/api/board/notes', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ content: 'Test content', color: 'YELLOW', expectedRevision: 0 })
    }))
  })

  it('attaches expectedRevision as query parameter on DELETE', async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ id: 'main', revision: 1, wipLimit: 3, notes: [] })
    })

    await boardClient.deleteNote('note-1', 4)
    expect(fetch).toHaveBeenCalledWith('/api/board/notes/note-1?expectedRevision=4', expect.objectContaining({
      method: 'DELETE'
    }))
  })

  it('throws ApiError on HTTP error status with parsed payload', async () => {
    fetch.mockResolvedValueOnce({
      ok: false,
      status: 409,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({
        code: 'REVISION_CONFLICT',
        message: 'Conflict',
        currentRevision: 5
      })
    })

    await expect(boardClient.createNote('Conflict', 'YELLOW', 2)).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'REVISION_CONFLICT',
      currentRevision: 5
    })
  })

  it('translates fetch network abort into timeout ApiError', async () => {
    const abortErr = new Error('The operation was aborted')
    abortErr.name = 'AbortError'
    fetch.mockRejectedValueOnce(abortErr)

    await expect(boardClient.getBoard()).rejects.toMatchObject({
      name: 'ApiError',
      status: 408,
      code: 'REQUEST_TIMEOUT'
    })
  })
})
