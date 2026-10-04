// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import NoteCard from './NoteCard.vue'

describe('NoteCard.vue', () => {
  it('renders note content safely without executing HTML tags', () => {
    const note = {
      id: 'note-1',
      content: '<script>alert("xss")</script><b>Bold Text</b>',
      color: 'YELLOW',
      status: 'TODO',
      blockedReason: null,
      checklist: []
    }

    const wrapper = mount(NoteCard, { props: { note } })
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('b').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>alert("xss")</script><b>Bold Text</b>')
  })

  it('displays color badge and blocker banner when present', () => {
    const note = {
      id: 'note-2',
      content: 'Blocked work item',
      color: 'PINK',
      status: 'DOING',
      blockedReason: 'Waiting for database',
      checklist: []
    }

    const wrapper = mount(NoteCard, { props: { note } })
    expect(wrapper.text()).toContain('Rosa')
    expect(wrapper.text()).toContain('Bloccata')
    expect(wrapper.text()).toContain('Waiting for database')
  })

  it('renders checklist items with completion progress', () => {
    const note = {
      id: 'note-3',
      content: 'Note with checklist',
      color: 'GREEN',
      status: 'DOING',
      blockedReason: null,
      checklist: [
        { id: 'item-1', label: 'Item 1', completed: true },
        { id: 'item-2', label: 'Item 2', completed: false }
      ]
    }

    const wrapper = mount(NoteCard, { props: { note } })
    expect(wrapper.text()).toContain('Checklist (1/2)')
    const items = wrapper.findAll('.note-card__checklist-item')
    expect(items).toHaveLength(2)
    expect(items[0].classes()).toContain('note-card__checklist-item--completed')
    expect(items[1].classes()).not.toContain('note-card__checklist-item--completed')
  })
})
