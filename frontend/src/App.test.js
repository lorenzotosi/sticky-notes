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

    await textarea.setValue('Promemoria esame SPE')
    await form.trigger('submit')

    const notes = wrapper.findAll('section[aria-label="Notes"] article')
    expect(notes).toHaveLength(1)
    expect(notes[0].text()).toBe('Promemoria esame SPE')
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
})
