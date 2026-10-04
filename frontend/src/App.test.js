// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

describe('App.vue orchestration', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('fetches and renders kanban board with columns on mount', async () => {
    const mockBoard = {
      id: 'main',
      schemaVersion: 1,
      revision: 0,
      wipLimit: 3,
      notes: [
        {
          id: 'note-1',
          content: 'Mounted Kanban Note',
          color: 'YELLOW',
          status: 'TODO',
          blockedReason: null,
          checklist: []
        }
      ]
    }

    fetch.mockResolvedValueOnce({
      ok: true,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => mockBoard
    })

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Sticky Notes Kanban')
    expect(wrapper.find('.board-column--todo').text()).toContain('Mounted Kanban Note')
    expect(wrapper.find('.board-column--doing').exists()).toBe(true)
    expect(wrapper.find('.board-column--done').exists()).toBe(true)
  })

  it('displays error state and retries fetching board', async () => {
    fetch.mockResolvedValueOnce({
      ok: false,
      status: 503,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ code: 'DATABASE_UNAVAILABLE', message: 'Database connection failed' })
    })

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('Database connection failed')

    const mockBoard = {
      id: 'main',
      schemaVersion: 1,
      revision: 1,
      wipLimit: 3,
      notes: []
    }

    fetch.mockResolvedValueOnce({
      ok: true,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => mockBoard
    })

    const retryButton = wrapper.find('.board-view__retry-button')
    await retryButton.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.board-column')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('Database connection failed')
  })
})
