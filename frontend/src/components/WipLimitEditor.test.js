// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WipLimitEditor from './WipLimitEditor.vue'

describe('WipLimitEditor.vue', () => {
  it('validates limits against range and current doing occupancy', async () => {
    const wrapper = mount(WipLimitEditor, {
      props: {
        currentLimit: 3,
        currentDoingCount: 2
      }
    })

    const input = wrapper.find('input[type="number"]')

    await input.setValue(1)
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('non può essere inferiore al numero attuale di note in lavorazione')
    expect(wrapper.emitted('save')).toBeUndefined()

    await input.setValue(2)
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('save')).toHaveLength(1)
    expect(wrapper.emitted('save')[0][0]).toBe(2)
  })

  it('rejects limits outside 1..20', async () => {
    const wrapper = mount(WipLimitEditor, {
      props: {
        currentLimit: 3,
        currentDoingCount: 0
      }
    })

    const input = wrapper.find('input[type="number"]')
    await input.setValue(0)
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('compreso tra 1 e 20')
    expect(wrapper.emitted('save')).toBeUndefined()
  })
})
