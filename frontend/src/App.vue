<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { ref } from 'vue'
import { evaluateBoardCommand } from 'sticky-notes-domain'

const text = ref('')
const board = ref({
  id: 'main',
  schemaVersion: 1,
  revision: 0,
  wipLimit: 3,
  notes: []
})
const error = ref('')

const addNote = () => {
  const command = {
    type: 'CREATE_NOTE',
    newId: crypto.randomUUID(),
    content: text.value,
    color: 'YELLOW'
  }

  const result = JSON.parse(
    evaluateBoardCommand(
      JSON.stringify(board.value),
      JSON.stringify(command)
    )
  )

  if (result.type === 'SUCCESS') {
    board.value = result.board
    text.value = ''
    error.value = ''
  } else {
    error.value = 'Non è possibile creare la nota.'
  }
}
</script>

<template>
  <main>
    <h1>Sticky Notes</h1>
    <form @submit.prevent="addNote">
      <label for="note">New note</label>
      <textarea
        id="note"
        v-model="text"
        maxlength="500"
        required
      />
      <button>Create</button>
    </form>

    <p role="alert">
      {{ error }}
    </p>

    <section aria-label="Notes">
      <article
        v-for="note in board.notes"
        :key="note.id"
      >
        {{ note.content }}
      </article>
    </section>
  </main>
</template>
