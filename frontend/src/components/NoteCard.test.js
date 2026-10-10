// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import NoteCard from './NoteCard.vue'

describe('NoteCard.vue', () => {
  const sampleNote = {
    id: 'note-1',
    content: 'Standard note',
    color: 'YELLOW',
    status: 'TODO',
    blockedReason: null,
    checklist: []
  }

  it('renders note content safely without executing HTML tags', () => {
    const note = {
      ...sampleNote,
      content: '<script>alert("xss")</script><b>Bold Text</b>'
    }

    const wrapper = mount(NoteCard, { props: { note } })
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('b').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>alert("xss")</script><b>Bold Text</b>')
  })

  it('displays color badge and blocker banner when present', () => {
    const note = {
      ...sampleNote,
      color: 'PINK',
      status: 'DOING',
      blockedReason: 'Waiting for database'
    }

    const wrapper = mount(NoteCard, { props: { note } })
    expect(wrapper.text()).toContain('Rosa')
    expect(wrapper.text()).toContain('Bloccata')
    expect(wrapper.text()).toContain('Waiting for database')
  })

  it('emits edit event when edit button is clicked', async () => {
    const wrapper = mount(NoteCard, { props: { note: sampleNote } })
    const editBtn = wrapper.find('button[aria-label="Modifica nota"]')

    await editBtn.trigger('click')
    expect(wrapper.emitted('edit')).toHaveLength(1)
    expect(wrapper.emitted('edit')[0][0]).toEqual(sampleNote)
  })

  it('requires confirmation before emitting delete event', async () => {
    const wrapper = mount(NoteCard, { props: { note: sampleNote } })
    const deleteBtn = wrapper.find('button[aria-label="Elimina nota"]')

    await deleteBtn.trigger('click')
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
    expect(wrapper.emitted('delete')).toBeUndefined()

    const confirmBtn = wrapper.find('.note-card__btn--danger')
    await confirmBtn.trigger('click')

    expect(wrapper.emitted('delete')).toHaveLength(1)
    expect(wrapper.emitted('delete')[0][0]).toBe('note-1')
  })

  it('cancels deletion when cancel confirmation button is clicked', async () => {
    const wrapper = mount(NoteCard, { props: { note: sampleNote } })
    await wrapper.find('button[aria-label="Elimina nota"]').trigger('click')

    const cancelBtn = wrapper.find('.note-card__btn--cancel')
    await cancelBtn.trigger('click')

    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    expect(wrapper.emitted('delete')).toBeUndefined()
  })

  it('emits move-status event on button click for TODO note', async () => {
    const wrapper = mount(NoteCard, { props: { note: sampleNote } })
    const moveBtn = wrapper.find('button[aria-label="Sposta in lavorazione"]')
    expect(moveBtn.exists()).toBe(true)

    await moveBtn.trigger('click')
    expect(wrapper.emitted('move-status')).toHaveLength(1)
    expect(wrapper.emitted('move-status')[0][0]).toEqual({
      noteId: 'note-1',
      targetStatus: 'DOING'
    })
  })

  it('disables move button if note is blocked', () => {
    const note = { ...sampleNote, blockedReason: 'Blocked reason' }
    const wrapper = mount(NoteCard, { props: { note } })
    const moveBtn = wrapper.find('button[aria-label="Sposta in lavorazione"]')
    expect(moveBtn.attributes('disabled')).toBeDefined()
  })

  it('emits reorder events and disables boundary buttons', async () => {
    const wrapper = mount(NoteCard, {
      props: {
        note: sampleNote,
        isFirst: true,
        isLast: false
      }
    })

    const upBtn = wrapper.find('button[aria-label="Sposta su"]')
    const downBtn = wrapper.find('button[aria-label="Sposta giù"]')

    expect(upBtn.attributes('disabled')).toBeDefined()
    expect(downBtn.attributes('disabled')).toBeUndefined()

    await downBtn.trigger('click')
    expect(wrapper.emitted('reorder')).toHaveLength(1)
    expect(wrapper.emitted('reorder')[0][0]).toEqual({
      noteId: 'note-1',
      direction: 'DOWN'
    })
  })
})
