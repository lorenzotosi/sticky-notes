<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useBoard } from './composables/useBoard.js'
import BoardView from './components/BoardView.vue'
import NoteEditor from './components/NoteEditor.vue'
import WipLimitEditor from './components/WipLimitEditor.vue'
import BlockEditor from './components/BlockEditor.vue'
import ChecklistEditor from './components/ChecklistEditor.vue'
import ConflictNotice from './components/ConflictNotice.vue'

const {
  board,
  loading,
  saving,
  error,
  conflictState,
  clearConflict,
  fetchBoard,
  createNote,
  updateNote,
  deleteNote,
  moveNote,
  setWipLimit,
  blockNote,
  unblockNote,
  addChecklistItem,
  updateChecklistItem,
  deleteChecklistItem
} = useBoard()

const isEditorOpen = ref(false)
const editingNote = ref(null)
const createButtonRef = ref(null)
const editorTriggerRef = ref(null)
const appRootRef = ref(null)

const isWipEditorOpen = ref(false)
const isBlockEditorOpen = ref(false)
const blockingNote = ref(null)

const isChecklistEditorOpen = ref(false)
const checklistNote = ref(null)

const draftOverride = ref(null)

const currentDoingCount = computed(() => {
  return (board.value?.notes || []).filter(n => n.status === 'DOING').length
})

function handleRetry() {
  fetchBoard().catch(() => {})
}

function rememberEditorTrigger(event) {
  const clicked = event?.currentTarget
  if (clicked) {
    editorTriggerRef.value = clicked
    return
  }
  const active = document.activeElement
  editorTriggerRef.value = active && active !== document.body ? active : null
}

function restoreEditorFocus() {
  const trigger = editorTriggerRef.value
  editorTriggerRef.value = null
  if (trigger && trigger.isConnected) {
    trigger.focus()
    return
  }
  if (createButtonRef.value) {
    createButtonRef.value.focus()
  }
}

function focusAfterDelete(status) {
  const root = appRootRef.value
  const column = root && status
    ? root.querySelector(`.board-column--${status.toLowerCase()}`)
    : null
  const nextEditButton = column?.querySelector('button[aria-label="Modifica nota"]')
  if (nextEditButton) {
    nextEditButton.focus()
    return
  }
  if (createButtonRef.value) {
    createButtonRef.value.focus()
  }
}

function openCreateEditor() {
  rememberEditorTrigger()
  editingNote.value = null
  isEditorOpen.value = true
  draftOverride.value = null
}

function openEditEditor(note, event) {
  rememberEditorTrigger(event)
  editingNote.value = note
  isEditorOpen.value = true
}

function closeEditor() {
  isEditorOpen.value = false
  editingNote.value = null
  draftOverride.value = null
  restoreEditorFocus()
}

function openWipEditor(event) {
  rememberEditorTrigger(event)
  isWipEditorOpen.value = true
}

function closeWipEditor() {
  isWipEditorOpen.value = false
  restoreEditorFocus()
}

function openBlockEditor(note, event) {
  rememberEditorTrigger(event)
  blockingNote.value = note
  isBlockEditorOpen.value = true
}

function closeBlockEditor() {
  isBlockEditorOpen.value = false
  blockingNote.value = null
  restoreEditorFocus()
}

function openChecklistEditor(note, event) {
  rememberEditorTrigger(event)
  checklistNote.value = note
  isChecklistEditorOpen.value = true
}

function closeChecklistEditor() {
  isChecklistEditorOpen.value = false
  checklistNote.value = null
  restoreEditorFocus()
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
  const deletedNote = board.value?.notes?.find(note => note.id === noteId)
  try {
    await deleteNote(noteId)
    await nextTick()
    focusAfterDelete(deletedNote?.status)
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

const activeChecklistNote = computed(() => {
  if (!checklistNote.value || !board.value) return null
  return board.value.notes.find(n => n.id === checklistNote.value.id) || checklistNote.value
})

async function handleAddChecklistItem({ noteId, label }) {
  try {
    await addChecklistItem(noteId, label)
  } catch {
  }
}

async function handleToggleChecklistItem({ noteId, itemId, completed }) {
  try {
    await updateChecklistItem(noteId, itemId, { completed })
  } catch {
  }
}

async function handleUpdateChecklistItemLabel({ noteId, itemId, label }) {
  try {
    await updateChecklistItem(noteId, itemId, { label })
  } catch {
  }
}

async function handleDeleteChecklistItem({ noteId, itemId }) {
  try {
    await deleteChecklistItem(noteId, itemId)
  } catch {
  }
}

function handleCreateFromConflictDraft({ content, color }, event) {
  isBlockEditorOpen.value = false
  isChecklistEditorOpen.value = false
  editingNote.value = null
  draftOverride.value = { content, color }
  clearConflict()
  rememberEditorTrigger(event)
  isEditorOpen.value = true
}

onMounted(() => {
  fetchBoard().catch(() => {})
})
</script>

<template>
  <main
    ref="appRootRef"
    class="app-container"
  >
    <header class="app-header">
      <div class="app-header__main">
        <h1>Sticky Notes Kanban</h1>
        <button
          ref="createButtonRef"
          type="button"
          class="btn btn--primary"
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

      <ConflictNotice
        v-if="conflictState"
        :conflict="conflictState"
        @dismiss="clearConflict"
        @create-from-draft="handleCreateFromConflictDraft"
      />
    </header>

    <div
      v-if="isEditorOpen"
      class="app-modal"
    >
      <div class="app-modal__content">
        <NoteEditor
          :key="editingNote ? editingNote.id : (draftOverride ? 'draft-recovery' : 'new')"
          :initial-content="editingNote ? editingNote.content : (draftOverride ? draftOverride.content : '')"
          :initial-color="editingNote ? editingNote.color : (draftOverride ? draftOverride.color : 'YELLOW')"
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

    <div
      v-if="isChecklistEditorOpen && activeChecklistNote"
      class="app-modal"
    >
      <div class="app-modal__content">
        <ChecklistEditor
          :note="activeChecklistNote"
          :saving="saving"
          :error-message="error ? error.message : ''"
          @close="closeChecklistEditor"
          @add-item="handleAddChecklistItem"
          @toggle-item="handleToggleChecklistItem"
          @update-item-label="handleUpdateChecklistItemLabel"
          @delete-item="handleDeleteChecklistItem"
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
      @manage-checklist="openChecklistEditor"
      @toggle-checklist-item="handleToggleChecklistItem"
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

.app-modal {
  margin-bottom: 2rem;
}

.app-modal__content {
  max-width: 36rem;
  margin: 0 auto;
}
</style>
