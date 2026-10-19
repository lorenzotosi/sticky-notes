<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps({
  note: {
    type: Object,
    required: true
  },
  saving: {
    type: Boolean,
    default: false
  },
  errorMessage: {
    type: String,
    default: ''
  }
})

const emit = defineEmits([
  'close',
  'add-item',
  'toggle-item',
  'update-item-label',
  'delete-item'
])

const newLabel = ref('')
const clientError = ref('')
const editingItemId = ref(null)
const editingItemLabel = ref('')
const newLabelInputRef = ref(null)

const maxItems = 20
const maxLabelLength = 120

const isDone = computed(() => props.note.status === 'DONE')
const checklist = computed(() => props.note.checklist || [])
const isFull = computed(() => checklist.value.length >= maxItems)
const completedCount = computed(() => checklist.value.filter(i => i.completed).length)

onMounted(async () => {
  await nextTick()
  if (newLabelInputRef.value && !isDone.value && !isFull.value) {
    newLabelInputRef.value.focus()
  }
})

function handleAddItem() {
  clientError.value = ''
  const trimmed = newLabel.value.trim()

  if (isDone.value) {
    clientError.value = 'Le note completate sono di sola lettura.'
    return
  }

  if (isFull.value) {
    clientError.value = `Raggiunta la capienza massima di ${maxItems} voci.`
    return
  }

  if (trimmed.length === 0) {
    clientError.value = 'La voce della checklist non può essere vuota.'
    return
  }

  if (newLabel.value.length > maxLabelLength) {
    clientError.value = `La voce non può superare ${maxLabelLength} caratteri.`
    return
  }

  emit('add-item', {
    noteId: props.note.id,
    label: newLabel.value
  })

  watch(
    () => (props.note.checklist || []).length,
    (newLen, oldLen) => {
      if (newLen > oldLen && !props.errorMessage) {
        newLabel.value = ''
      }
    }
  )
}

function startRename(item) {
  if (isDone.value) return
  editingItemId.value = item.id
  editingItemLabel.value = item.label
}

function cancelRename() {
  editingItemId.value = null
  editingItemLabel.value = ''
}

function saveRename(item) {
  clientError.value = ''
  const trimmed = editingItemLabel.value.trim()

  if (trimmed.length === 0) {
    clientError.value = 'La voce della checklist non può essere vuota.'
    return
  }

  if (editingItemLabel.value.length > maxLabelLength) {
    clientError.value = `La voce non può superare ${maxLabelLength} caratteri.`
    return
  }

  if (trimmed !== item.label) {
    emit('update-item-label', {
      noteId: props.note.id,
      itemId: item.id,
      label: editingItemLabel.value
    })
  }
  cancelRename()
}

function handleToggle(item) {
  if (isDone.value || props.saving) return
  emit('toggle-item', {
    noteId: props.note.id,
    itemId: item.id,
    completed: !item.completed
  })
}

function handleDelete(itemId) {
  if (isDone.value || props.saving) return
  emit('delete-item', {
    noteId: props.note.id,
    itemId
  })
}
</script>

<template>
  <div class="checklist-editor">
    <div class="checklist-editor__header">
      <h3 class="checklist-editor__title">
        Gestione Checklist
      </h3>
      <span class="checklist-editor__count">
        {{ completedCount }} / {{ checklist.length }} completati (max {{ maxItems }})
      </span>
    </div>

    <div
      v-if="isDone"
      class="checklist-editor__readonly-alert"
      role="status"
    >
      Questa nota è completata: le voci della checklist sono in sola lettura.
    </div>

    <div
      v-if="clientError || errorMessage"
      class="checklist-editor__error"
      role="alert"
    >
      {{ clientError || errorMessage }}
    </div>

    <form
      v-if="!isDone"
      class="checklist-editor__add-form"
      @submit.prevent="handleAddItem"
    >
      <div class="checklist-editor__add-field">
        <label
          for="checklist-new-item"
          class="sr-only"
        >
          Nuova voce checklist
        </label>
        <input
          id="checklist-new-item"
          ref="newLabelInputRef"
          v-model="newLabel"
          type="text"
          placeholder="Aggiungi una voce (max 120 caratteri)..."
          class="checklist-editor__input"
          :disabled="saving || isFull"
        >
        <span class="checklist-editor__char-counter">
          {{ newLabel.length }} / {{ maxLabelLength }}
        </span>
      </div>
      <button
        type="submit"
        class="btn btn--secondary"
        :disabled="saving || isFull || newLabel.trim().length === 0"
      >
        Aggiungi
      </button>
    </form>

    <div
      v-if="checklist.length === 0"
      class="checklist-editor__empty"
    >
      Nessuna voce presente nella checklist.
    </div>

    <ul
      v-else
      class="checklist-editor__list"
      aria-label="Elenco voci checklist"
    >
      <li
        v-for="item in checklist"
        :key="item.id"
        class="checklist-editor__item"
      >
        <input
          type="checkbox"
          :checked="item.completed"
          :disabled="saving || isDone"
          :aria-label="`Segna ${item.label} come ${item.completed ? 'da fare' : 'completata'}`"
          @change="handleToggle(item)"
        >

        <div
          v-if="editingItemId === item.id"
          class="checklist-editor__edit-inline"
        >
          <input
            v-model="editingItemLabel"
            type="text"
            class="checklist-editor__input checklist-editor__input--inline"
            :disabled="saving"
            @keyup.enter="saveRename(item)"
            @keyup.esc="cancelRename"
          >
          <button
            type="button"
            class="btn btn--sm btn--success"
            :disabled="saving"
            @click="saveRename(item)"
          >
            Salva
          </button>
          <button
            type="button"
            class="btn btn--sm btn--cancel"
            :disabled="saving"
            @click="cancelRename"
          >
            Annulla
          </button>
        </div>

        <span
          v-else
          :class="['checklist-editor__item-label', { 'checklist-editor__item-label--completed': item.completed }]"
        >
          {{ item.label }}
        </span>

        <div
          v-if="!isDone && editingItemId !== item.id"
          class="checklist-editor__item-actions"
        >
          <button
            type="button"
            class="btn btn--sm btn--secondary"
            :disabled="saving"
            aria-label="Rinomina voce"
            @click="startRename(item)"
          >
            Rinomina
          </button>
          <button
            type="button"
            class="btn btn--sm btn--danger-ghost"
            :disabled="saving"
            aria-label="Rimuovi voce"
            @click="handleDelete(item.id)"
          >
            Rimuovi
          </button>
        </div>
      </li>
    </ul>

    <div class="checklist-editor__footer">
      <button
        type="button"
        class="btn btn--secondary"
        @click="emit('close')"
      >
        Chiudi
      </button>
    </div>
  </div>
</template>

<style scoped>
.checklist-editor {
  background-color: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 1.25rem;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.checklist-editor__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.checklist-editor__title {
  margin: 0;
  font-size: 1.125rem;
}

.checklist-editor__count {
  font-size: 0.8125rem;
  color: #4b5563;
}

.checklist-editor__readonly-alert {
  background-color: #f3f4f6;
  border-left: 3px solid #6b7280;
  padding: 0.5rem;
  font-size: 0.8125rem;
  color: #374151;
}

.checklist-editor__error {
  background-color: var(--color-blocked-bg);
  border-left: 3px solid #e11d48;
  padding: 0.5rem;
  font-size: 0.8125rem;
  color: var(--color-blocked-text);
}

.checklist-editor__add-form {
  display: flex;
  gap: 0.5rem;
}

.checklist-editor__add-field {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
}

.checklist-editor__input {
  width: 100%;
  padding: 0.5rem 3.5rem 0.5rem 0.75rem;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  font-size: 0.875rem;
}

.checklist-editor__input--inline {
  padding: 0.25rem 0.5rem;
}

.checklist-editor__char-counter {
  position: absolute;
  right: 0.5rem;
  font-size: 0.6875rem;
  color: #9ca3af;
}

.checklist-editor__empty {
  font-size: 0.875rem;
  color: #6b7280;
  font-style: italic;
  padding: 0.5rem 0;
}

.checklist-editor__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 16rem;
  overflow-y: auto;
}

.checklist-editor__item {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.375rem;
  border-radius: 4px;
  background-color: #fafafa;
}

.checklist-editor__item-label {
  flex: 1;
  font-size: 0.875rem;
  word-break: break-word;
}

.checklist-editor__item-label--completed {
  text-decoration: line-through;
  opacity: 0.6;
}

.checklist-editor__edit-inline {
  flex: 1;
  display: flex;
  gap: 0.375rem;
  align-items: center;
}

.checklist-editor__item-actions {
  display: flex;
  gap: 0.25rem;
}

.checklist-editor__footer {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--border-color);
  padding-top: 0.75rem;
}
</style>
