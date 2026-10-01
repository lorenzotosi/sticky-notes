// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import App from './App.vue'

describe('App.vue interaction', () => {
  it('creates and renders a new note when valid text is submitted', async () => {
    const wrapper = mount(App)
    const textarea = wrapper.find('textarea#note')
    const form = wrapper.find('form')

    await textarea.setValue('Preparare il rilascio')
    await form.trigger('submit')

    const notes = wrapper.findAll('section[aria-label="Notes"] article')
    expect(notes).toHaveLength(1)
    expect(notes[0].text()).toBe('Preparare il rilascio')
    expect(textarea.element.value).toBe('')
  })

  it('rejects empty or blank notes on submission', async () => {
    const wrapper = mount(App)
    const textarea = wrapper.find('textarea#note')
    const form = wrapper.find('form')

    await textarea.setValue('   ')
    await form.trigger('submit')

    const notes = wrapper.findAll('section[aria-label="Notes"] article')
    expect(notes).toHaveLength(0)
  })

  it('preserves the board and draft on rejection, then clears the error on success', async () => {
    const wrapper = mount(App)
    const textarea = wrapper.find('#note')
    const form = wrapper.find('form')

    await textarea.setValue('First note')
    await form.trigger('submit')

    const draft = 'a'.repeat(501)
    await textarea.setValue(draft)
    await form.trigger('submit')

    expect(wrapper.findAll('article').map(note => note.text())).toEqual(['First note'])
    expect(wrapper.find('[role="alert"]').text()).not.toBe('')
    expect(textarea.element.value).toBe(draft)

    await textarea.setValue('Second note')
    await form.trigger('submit')

    expect(wrapper.findAll('article').map(note => note.text())).toEqual(['First note', 'Second note'])
    expect(wrapper.find('[role="alert"]').text()).toBe('')
    expect(textarea.element.value).toBe('')
  })
})
