<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { ref } from 'vue'

const text = ref('')
const notes = ref([])
const addNote = () => {
  if (text.value.trim()) notes.value.push({ id: crypto.randomUUID(), text: text.value.trim() })
  text.value = ''
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
    <section aria-label="Notes">
      <article
        v-for="note in notes"
        :key="note.id"
      >
        {{ note.text }}
      </article>
    </section>
  </main>
</template>
