<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useBoard } from './composables/useBoard.js'
import BoardView from './components/BoardView.vue'
import NoteEditor from './components/NoteEditor.vue'
import WipLimitEditor from './components/WipLimitEditor.vue'
import BlockEditor from './components/BlockEditor.vue'

const {
  board,
  loading,
  saving,
  error,
  fetchBoard,
  createNote,
  updateNote,
  deleteNote,
  moveNote,
  setWipLimit,
  blockNote,
  unblockNote
} = useBoard()

const isEditorOpen = ref(false)
const editingNote = ref(null)
const createButtonRef = ref(null)

const isWipEditorOpen = ref(false)
const isBlockEditorOpen = ref(false)
const blockingNote = ref(null)

const currentDoingCount = computed(() => {
  return (board.value?.notes || []).filter(n => n.status === 'DOING').length
})

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

function openWipEditor() {
  isWipEditorOpen.value = true
}

function closeWipEditor() {
  isWipEditorOpen.value = false
}

function openBlockEditor(note) {
  blockingNote.value = note
  isBlockEditorOpen.value = true
}

function closeBlockEditor() {
  isBlockEditorOpen.value = false
  blockingNote.value = null
}

async function handleMoveNote({ noteId, targetStatus, destinationIndex }) {
  try {
    await moveNote(noteId, targetStatus, destinationIndex)
  } catch {
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

async function handleSaveWipLimit(newLimit) {
  try {
    await setWipLimit(newLimit)
    closeWipEditor()
  } catch {
  }
}

async function handleSaveBlock({ noteId, reason }) {
  try {
    await blockNote(noteId, reason)
    closeBlockEditor()
  } catch {
  }
}

async function handleUnblockNote(noteId) {
  try {
    await unblockNote(noteId)
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
      class="app-modal"
    >
      <div class="app-modal__content">
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

    <div
      v-if="isWipEditorOpen && board"
      class="app-modal"
    >
      <div class="app-modal__content">
        <WipLimitEditor
          :current-limit="board.wipLimit"
          :current-doing-count="currentDoingCount"
          :saving="saving"
          :error-message="error ? error.message : ''"
          @save="handleSaveWipLimit"
          @cancel="closeWipEditor"
        />
      </div>
    </div>

    <div
      v-if="isBlockEditorOpen && blockingNote"
      class="app-modal"
    >
      <div class="app-modal__content">
        <BlockEditor
          :note-id="blockingNote.id"
          :initial-reason="blockingNote.blockedReason || ''"
          :saving="saving"
          :error-message="error ? error.message : ''"
          @save="handleSaveBlock"
          @cancel="closeBlockEditor"
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
      @move-note="handleMoveNote"
      @edit-wip-limit="openWipEditor"
      @block-note="openBlockEditor"
      @unblock-note="handleUnblockNote"
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

.app-modal {
  margin-bottom: 2rem;
}

.app-modal__content {
  max-width: 36rem;
  margin: 0 auto;
}
</style>
