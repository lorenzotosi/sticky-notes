// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BoardView from './BoardView.vue'

describe('BoardView.vue', () => {
  it('shows loading banner when loading and board is null', () => {
    const wrapper = mount(BoardView, {
      props: {
        board: null,
        loading: true,
        error: null
      }
    })
    expect(wrapper.text()).toContain('Caricamento della bacheca...')
  })

  it('shows error banner with retry button on error', async () => {
    const wrapper = mount(BoardView, {
      props: {
        board: null,
        loading: false,
        error: { message: 'Network offline' }
      }
    })
    expect(wrapper.text()).toContain('Network offline')

    const button = wrapper.find('.board-view__retry-button')
    await button.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('groups notes into canonical columns and displays WIP indicator', () => {
    const board = {
      id: 'main',
      schemaVersion: 1,
      revision: 2,
      wipLimit: 2,
      notes: [
        { id: '1', content: 'Todo 1', color: 'YELLOW', status: 'TODO', blockedReason: null, checklist: [] },
        { id: '2', content: 'Doing 1', color: 'BLUE', status: 'DOING', blockedReason: null, checklist: [] },
        { id: '3', content: 'Doing 2', color: 'GREEN', status: 'DOING', blockedReason: null, checklist: [] },
        { id: '4', content: 'Done 1', color: 'PINK', status: 'DONE', blockedReason: null, checklist: [] }
      ]
    }

    const wrapper = mount(BoardView, { props: { board } })
    const todoSection = wrapper.find('.board-column--todo')
    const doingSection = wrapper.find('.board-column--doing')
    const doneSection = wrapper.find('.board-column--done')

    expect(todoSection.findAll('.note-card')).toHaveLength(1)
    expect(doingSection.findAll('.note-card')).toHaveLength(2)
    expect(doneSection.findAll('.note-card')).toHaveLength(1)

    expect(doingSection.text()).toContain('(2 / 2)')
    expect(doingSection.text()).toContain('Limite WIP raggiunto')
  })

  it('emits move-note with correct destinationIndex on status move and reorder', async () => {
    const board = {
      id: 'main',
      schemaVersion: 1,
      revision: 2,
      wipLimit: 3,
      notes: [
        { id: '1', content: 'Todo 1', color: 'YELLOW', status: 'TODO', blockedReason: null, checklist: [] },
        { id: '2', content: 'Todo 2', color: 'YELLOW', status: 'TODO', blockedReason: null, checklist: [] }
      ]
    }

    const wrapper = mount(BoardView, { props: { board } })
    const firstCard = wrapper.findAllComponents({ name: 'NoteCard' })[0]

    await firstCard.find('button[aria-label="Sposta in lavorazione"]').trigger('click')
    expect(wrapper.emitted('move-note')).toHaveLength(1)
    expect(wrapper.emitted('move-note')[0][0]).toEqual({
      noteId: '1',
      targetStatus: 'DOING',
      destinationIndex: 0
    })

    await firstCard.find('button[aria-label="Sposta giù"]').trigger('click')
    expect(wrapper.emitted('move-note')).toHaveLength(2)
    expect(wrapper.emitted('move-note')[1][0]).toEqual({
      noteId: '1',
      targetStatus: 'TODO',
      destinationIndex: 1
    })
  })
})
