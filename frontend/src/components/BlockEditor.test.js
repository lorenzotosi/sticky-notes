// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BlockEditor from './BlockEditor.vue'

describe('BlockEditor.vue', () => {
  it('rejects empty block reasons', async () => {
    const wrapper = mount(BlockEditor, {
      props: { noteId: 'note-1', initialReason: '' }
    })

    await wrapper.find('textarea').setValue('   ')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('non può essere vuoto')
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('submits valid block reason maintaining text', async () => {
    const wrapper = mount(BlockEditor, {
      props: { noteId: 'note-1', initialReason: '' }
    })

    await wrapper.find('textarea').setValue('Attesa dipendenza DB')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('save')).toHaveLength(1)
    expect(wrapper.emitted('save')[0][0]).toEqual({
      noteId: 'note-1',
      reason: 'Attesa dipendenza DB'
    })
  })
})
