// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

describe('App.vue CRUD orchestration', () => {
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
  })

  it('opens note editor and submits note creation', async () => {
    const initialBoard = {
      id: 'main',
      schemaVersion: 1,
      revision: 0,
      wipLimit: 3,
      notes: []
    }
    const updatedBoard = {
      id: 'main',
      schemaVersion: 1,
      revision: 1,
      wipLimit: 3,
      notes: [
        {
          id: 'note-created-1',
          content: 'Brand new note',
          color: 'BLUE',
          status: 'TODO',
          blockedReason: null,
          checklist: []
        }
      ]
    }

    fetch
      .mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => initialBoard
      })
      .mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ board: updatedBoard, createdNoteId: 'note-created-1' })
      })

    const wrapper = mount(App)
    await flushPromises()

    await wrapper.find('.app-header__create-btn').trigger('click')
    expect(wrapper.find('.note-editor').exists()).toBe(true)

    await wrapper.find('.note-editor__textarea').setValue('Brand new note')
    await wrapper.find('input[type="radio"][value="BLUE"]').setChecked()
    await wrapper.find('.note-editor').trigger('submit')
    await flushPromises()

    expect(wrapper.find('.note-editor').exists()).toBe(false)
    expect(wrapper.find('.board-column--todo').text()).toContain('Brand new note')
  })

  it('deletes a note after confirmation', async () => {
    const initialBoard = {
      id: 'main',
      schemaVersion: 1,
      revision: 1,
      wipLimit: 3,
      notes: [
        {
          id: 'note-to-delete',
          content: 'Note to be removed',
          color: 'YELLOW',
          status: 'TODO',
          blockedReason: null,
          checklist: []
        }
      ]
    }
    const emptyBoard = {
      id: 'main',
      schemaVersion: 1,
      revision: 2,
      wipLimit: 3,
      notes: []
    }

    fetch
      .mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => initialBoard
      })
      .mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => emptyBoard
      })

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('Note to be removed')

    await wrapper.find('button[aria-label="Elimina nota"]').trigger('click')
    await wrapper.find('.note-card__btn--danger').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Note to be removed')
  })
})
