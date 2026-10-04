// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

export class ApiError extends Error {
    constructor(status, errorPayload) {
        super(errorPayload?.message || 'API request failed')
        this.name = 'ApiError'
        this.status = status
        this.code = errorPayload?.code || 'INVALID_REQUEST'
        this.field = errorPayload?.field || null
        this.currentRevision = errorPayload?.currentRevision ?? null
    }
}

const DEFAULT_TIMEOUT_MS = 10000

async function request(path, options = {}) {
  const { timeout = DEFAULT_TIMEOUT_MS, expectedRevision, body, ...fetchOptions } = options

  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)

  const headers = {
    Accept: 'application/json',
    ...fetchOptions.headers
  }

  let finalPath = path
  let finalBody = body

  if (fetchOptions.method === 'DELETE' && expectedRevision !== undefined) {
    const separator = finalPath.includes('?') ? '&' : '?'
    finalPath = `${finalPath}${separator}expectedRevision=${encodeURIComponent(expectedRevision)}`
  } else if (body !== undefined && expectedRevision !== undefined) {
    finalBody = { ...body, expectedRevision }
  }

  if (finalBody !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response
  try {
    response = await fetch(finalPath, {
      ...fetchOptions,
      headers,
      body: finalBody !== undefined ? JSON.stringify(finalBody) : undefined,
      signal: controller.signal
    })
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new ApiError(408, {
        code: 'REQUEST_TIMEOUT',
        message: 'Request timed out'
      })
    }
    throw new ApiError(0, {
      code: 'NETWORK_ERROR',
      message: error.message || 'Network error occurred'
    })
  } finally {
    clearTimeout(timeoutId)
  }

  const contentType = response.headers.get('content-type') || ''
  const isJson = contentType.includes('application/json')
  const payload = isJson ? await response.json() : null

  if (!response.ok) {
    throw new ApiError(response.status, payload)
  }

  return payload
}

export const boardClient = {
    getBoard(options = {}) {
        return request('/api/board', {
            method: 'GET',
            ...options
        })
    },

    createNote(content, color, expectedRevision, options = {}) {
        return request('/api/board/notes', {
            method: 'POST',
            body: { content, color },
            expectedRevision,
            ...options
        })
    },

    updateNote(noteId, payload, expectedRevision, options = {}) {
        return request(`/api/board/notes/${encodeURIComponent(noteId)}`, {
            method: 'PATCH',
            body: payload,
            expectedRevision,
            ...options
        })
    },

    deleteNote(noteId, expectedRevision, options = {}) {
        return request(`/api/board/notes/${encodeURIComponent(noteId)}`, {
            method: 'DELETE',
            expectedRevision,
            ...options
        })
    },

    moveNote(noteId, targetStatus, destinationIndex, expectedRevision, options = {}) {
        return request(`/api/board/notes/${encodeURIComponent(noteId)}/move`, {
            method: 'POST',
            body: { targetStatus, destinationIndex },
            expectedRevision,
            ...options
        })
    },

    setWipLimit(limit, expectedRevision, options = {}) {
        return request('/api/board/wip-limit', {
            method: 'PUT',
            body: { limit },
            expectedRevision,
            ...options
        })
    },

    blockNote(noteId, reason, expectedRevision, options = {}) {
        return request(`/api/board/notes/${encodeURIComponent(noteId)}/block`, {
            method: 'PUT',
            body: { reason },
            expectedRevision,
            ...options
        })
    },

    unblockNote(noteId, expectedRevision, options = {}) {
        return request(`/api/board/notes/${encodeURIComponent(noteId)}/block`, {
            method: 'DELETE',
            expectedRevision,
            ...options
        })
    },

    addChecklistItem(noteId, label, expectedRevision, options = {}) {
        return request(`/api/board/notes/${encodeURIComponent(noteId)}/checklist`, {
            method: 'POST',
            body: { label },
            expectedRevision,
            ...options
        })
    },

    updateChecklistItem(noteId, itemId, payload, expectedRevision, options = {}) {
        return request(`/api/board/notes/${encodeURIComponent(noteId)}/checklist/${encodeURIComponent(itemId)}`, {
            method: 'PATCH',
            body: payload,
            expectedRevision,
            ...options
        })
    },

    deleteChecklistItem(noteId, itemId, expectedRevision, options = {}) {
        return request(`/api/board/notes/${encodeURIComponent(noteId)}/checklist/${encodeURIComponent(itemId)}`, {
            method: 'DELETE',
            expectedRevision,
            ...options
        })
    }
}
