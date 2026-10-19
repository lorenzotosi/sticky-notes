// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import ChecklistEditor from './ChecklistEditor.vue'

describe('ChecklistEditor.vue', () => {
  const sampleNote = {
    id: 'note-1',
    content: 'Test note',
    color: 'YELLOW',
    status: 'DOING',
    blockedReason: null,
    checklist: [
      { id: 'item-1', label: 'Item 1', completed: false }
    ]
  }

  it('renders items and emits toggle-item event', async () => {
    const wrapper = mount(ChecklistEditor, {
      props: { note: sampleNote }
    })

    expect(wrapper.text()).toContain('Item 1')
    const checkbox = wrapper.find('input[type="checkbox"]')
    await checkbox.trigger('change')

    expect(wrapper.emitted('toggle-item')).toHaveLength(1)
    expect(wrapper.emitted('toggle-item')[0][0]).toEqual({
      noteId: 'note-1',
      itemId: 'item-1',
      completed: true
    })
  })

  it('rejects empty label on add', async () => {
    const wrapper = mount(ChecklistEditor, {
      props: { note: sampleNote }
    })

    await wrapper.find('input[id="checklist-new-item"]').setValue('   ')
    await wrapper.find('form.checklist-editor__add-form').trigger('submit')

    expect(wrapper.text()).toContain('non può essere vuota')
    expect(wrapper.emitted('add-item')).toBeUndefined()
  })

  it('disables item additions and modifications for DONE notes', () => {
    const doneNote = {
      ...sampleNote,
      status: 'DONE',
      checklist: [{ id: 'item-1', label: 'Item 1', completed: true }]
    }

    const wrapper = mount(ChecklistEditor, {
      props: { note: doneNote }
    })

    expect(wrapper.text()).toContain('sola lettura')
    expect(wrapper.find('form.checklist-editor__add-form').exists()).toBe(false)
    expect(wrapper.find('input[type="checkbox"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('button[aria-label="Rimuovi voce"]').exists()).toBe(false)
  })

  it('focuses the rename field and returns focus to the rename button', async () => {
    const wrapper = mount(ChecklistEditor, {
      props: { note: sampleNote },
      attachTo: document.body
    })

    const renameButton = wrapper.find('button[aria-label="Rinomina voce"]')
    renameButton.element.focus()
    await renameButton.trigger('click')
    await wrapper.vm.$nextTick()

    const renameInput = wrapper.find('.checklist-editor__input--inline')
    expect(renameInput.attributes('aria-label')).toBe('Nuova etichetta della voce')
    expect(document.activeElement).toBe(renameInput.element)

    await wrapper.find('.btn--cancel').trigger('click')
    await wrapper.vm.$nextTick()

    expect(document.activeElement).toBe(wrapper.find('button[aria-label="Rinomina voce"]').element)

    wrapper.unmount()
  })

  it('restores focus to the rename button when the rename request settles', async () => {
    const Host = defineComponent({
      setup() {
        const saving = ref(false)
        const note = ref(sampleNote)
        const onUpdateItemLabel = () => {
          saving.value = true
        }
        return { saving, note, onUpdateItemLabel }
      },
      render() {
        return h(ChecklistEditor, {
          note: this.note,
          saving: this.saving,
          'onUpdate-item-label': this.onUpdateItemLabel
        })
      }
    })

    const wrapper = mount(Host, { attachTo: document.body })

    await wrapper.find('button[aria-label="Rinomina voce"]').trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.find('.checklist-editor__input--inline').setValue('Voce rinominata')
    await wrapper.find('.checklist-editor__edit-inline .btn--success').trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.saving).toBe(true)
    expect(document.activeElement).not.toBe(wrapper.find('button[aria-label="Rinomina voce"]').element)

    wrapper.vm.saving = false
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(document.activeElement).toBe(wrapper.find('button[aria-label="Rinomina voce"]').element)

    wrapper.unmount()
  })
})
