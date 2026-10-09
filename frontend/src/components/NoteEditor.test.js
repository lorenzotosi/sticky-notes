// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import NoteEditor from './NoteEditor.vue'

describe('NoteEditor.vue', () => {
  it('updates character counter when typing content', async () => {
    const wrapper = mount(NoteEditor)
    const textarea = wrapper.find('textarea')

    await textarea.setValue('Valid note content')
    expect(wrapper.find('.note-editor__counter').text()).toBe('18 / 500')
  })

  it('rejects blank note content using shared validator without emitting save', async () => {
    const wrapper = mount(NoteEditor)
    const textarea = wrapper.find('textarea')
    const form = wrapper.find('form')

    await textarea.setValue('   ')
    await form.trigger('submit')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.find('.note-editor__error').text()).toContain('compreso tra 1 e 500 caratteri')
  })

  it('emits save event with selected content and color', async () => {
    const wrapper = mount(NoteEditor)
    const textarea = wrapper.find('textarea')
    const blueRadio = wrapper.find('input[type="radio"][value="BLUE"]')
    const form = wrapper.find('form')

    await textarea.setValue('A blue note')
    await blueRadio.setChecked()
    await form.trigger('submit')

    expect(wrapper.emitted('save')).toHaveLength(1)
    expect(wrapper.emitted('save')[0][0]).toEqual({
      content: 'A blue note',
      color: 'BLUE'
    })
  })

  it('emits cancel event when cancel button is clicked', async () => {
    const wrapper = mount(NoteEditor)
    const cancelButton = wrapper.find('.note-editor__button--cancel')

    await cancelButton.trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
