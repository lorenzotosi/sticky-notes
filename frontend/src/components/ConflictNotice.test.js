// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConflictNotice from './ConflictNotice.vue'

describe('ConflictNotice.vue', () => {
  it('displays conflict message and preserved draft text', () => {
    const wrapper = mount(ConflictNotice, {
      props: {
        conflict: {
          code: 'REVISION_CONFLICT',
          targetDeleted: false,
          preservedText: 'My unsaved note text',
          preservedColor: 'BLUE',
          message: 'La bacheca è stata aggiornata da un\'altra finestra.'
        }
      }
    })

    expect(wrapper.text()).toContain('Conflitto di aggiornamento rilevato')
    expect(wrapper.text()).toContain('My unsaved note text')
    expect(wrapper.find('button').text()).toBe('Chiudi')
  })

  it('offers explicit note creation when target note was deleted concurrently', async () => {
    const wrapper = mount(ConflictNotice, {
      props: {
        conflict: {
          code: 'NOTE_NOT_FOUND',
          targetDeleted: true,
          preservedText: 'Orphaned draft content',
          preservedColor: 'GREEN',
          message: 'Elemento eliminato'
        }
      }
    })

    const createBtn = wrapper.findAll('button').find(b => b.text().includes('Crea come nuova nota'))
    expect(createBtn).toBeDefined()

    await createBtn.trigger('click')
    expect(wrapper.emitted('create-from-draft')).toHaveLength(1)
    expect(wrapper.emitted('create-from-draft')[0][0]).toEqual({
      content: 'Orphaned draft content',
      color: 'GREEN'
    })
  })
})
