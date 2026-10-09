<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { onMounted, ref } from 'vue'
import { useBoard } from './composables/useBoard.js'
import BoardView from './components/BoardView.vue'
import NoteEditor from './components/NoteEditor.vue'

const {
  board,
  loading,
  saving,
  error,
  fetchBoard,
  createNote,
  updateNote,
  deleteNote
} = useBoard()

const isEditorOpen = ref(false)
const editingNote = ref(null)
const createButtonRef = ref(null)

function handleRetry() {
  fetchBoard().catch(() => {})
}

function openCreateEditor() {
  editingNote.value = null
  isEditorOpen.value = true
}

function openEditEditor(note) {
  editingNote.value = note
  isEditorOpen.value = true
}

function closeEditor() {
  isEditorOpen.value = false
  editingNote.value = null
  if (createButtonRef.value) {
    createButtonRef.value.focus()
  }
}

async function handleSaveNote(payload) {
  try {
    if (editingNote.value) {
      await updateNote(editingNote.value.id, payload)
    } else {
      await createNote(payload.content, payload.color)
    }
    closeEditor()
  } catch {
  }
}

async function handleDeleteNote(noteId) {
  try {
    await deleteNote(noteId)
  } catch {
  }
}

onMounted(() => {
  fetchBoard().catch(() => {})
})
</script>

<template>
  <main class="app-container">
    <header class="app-header">
      <div class="app-header__main">
        <h1>Sticky Notes Kanban</h1>
        <button
          ref="createButtonRef"
          type="button"
          class="app-header__create-btn"
          :disabled="loading || saving || !board"
          @click="openCreateEditor"
        >
          Nuova nota
        </button>
      </div>

      <div
        aria-live="polite"
        class="sr-only"
      >
        <span v-if="loading">Caricamento in corso</span>
        <span v-else-if="saving">Salvataggio in corso</span>
        <span v-else-if="error">{{ error.message }}</span>
      </div>
    </header>

    <div
      v-if="isEditorOpen"
      class="app-editor-modal"
    >
      <div class="app-editor-modal__content">
        <NoteEditor
          :initial-content="editingNote ? editingNote.content : ''"
          :initial-color="editingNote ? editingNote.color : 'YELLOW'"
          :is-editing="Boolean(editingNote)"
          :saving="saving"
          :error-message="error ? error.message : ''"
          @save="handleSaveNote"
          @cancel="closeEditor"
        />
      </div>
    </div>

    <BoardView
      :board="board"
      :loading="loading"
      :error="error"
      :disabled="saving"
      @retry="handleRetry"
      @edit-note="openEditEditor"
      @delete-note="handleDeleteNote"
    />
  </main>
</template>

<style scoped>
.app-container {
  max-width: 80rem;
  margin: 0 auto;
  padding: 1.5rem 1rem;
}

.app-header {
  margin-bottom: 2rem;
}

.app-header__main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.app-header h1 {
  font-size: 1.875rem;
  margin: 0;
  font-weight: 700;
}

.app-header__create-btn {
  background-color: #2563eb;
  color: #ffffff;
  border: 1px solid #2563eb;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-weight: 600;
  font-size: 0.875rem;
  cursor: pointer;
}

.app-header__create-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.app-editor-modal {
  margin-bottom: 2rem;
}

.app-editor-modal__content {
  max-width: 36rem;
  margin: 0 auto;
}
</style>
