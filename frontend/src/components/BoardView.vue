<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { computed } from 'vue'
import NoteCard from './NoteCard.vue'

const props = defineProps({
  board: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: Object,
    default: null
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['retry', 'edit-note', 'delete-note', 'move-note'])

const columns = [
  { status: 'TODO', title: 'Da fare' },
  { status: 'DOING', title: 'In lavorazione' },
  { status: 'DONE', title: 'Completate' }
]

const notesByStatus = computed(() => {
  const grouped = {
    TODO: [],
    DOING: [],
    DONE: []
  }
  if (!props.board || !props.board.notes) {
    return grouped
  }
  for (const note of props.board.notes) {
    if (grouped[note.status]) {
      grouped[note.status].push(note)
    }
  }
  return grouped
})

const doingCount = computed(() => notesByStatus.value.DOING.length)

function handleMoveStatus({ noteId, targetStatus }) {
  const targetColNotes = notesByStatus.value[targetStatus] || []
  const destinationIndex = targetColNotes.length
  emit('move-note', { noteId, targetStatus, destinationIndex })
}

function handleReorder(status, index, { noteId, direction }) {
  const destinationIndex = direction === 'UP' ? index - 1 : index + 1
  emit('move-note', { noteId, targetStatus: status, destinationIndex })
}
</script>

<template>
  <div class="board-view">
    <div
      v-if="loading && !board"
      class="board-view__state-banner"
      role="status"
    >
      Caricamento della bacheca...
    </div>

    <div
      v-else-if="error && !board"
      class="board-view__state-banner board-view__state-banner--error"
      role="alert"
    >
      <p>Impossibile caricare la bacheca: {{ error.message }}</p>
      <button
        type="button"
        class="board-view__retry-button"
        @click="emit('retry')"
      >
        Riprova
      </button>
    </div>

    <div
      v-else-if="board"
      class="board-view__columns"
    >
      <section
        v-for="col in columns"
        :key="col.status"
        :class="['board-column', `board-column--${col.status.toLowerCase()}`]"
        :aria-labelledby="`col-heading-${col.status}`"
      >
        <header class="board-column__header">
          <h2 :id="`col-heading-${col.status}`">
            {{ col.title }}
            <span class="board-column__count">
              <template v-if="col.status === 'DOING'">
                ({{ doingCount }} / {{ board.wipLimit }})
              </template>
              <template v-else>
                ({{ notesByStatus[col.status].length }})
              </template>
            </span>
          </h2>
          <div
            v-if="col.status === 'DOING' && doingCount >= board.wipLimit"
            class="board-column__wip-alert"
            role="status"
          >
            Limite WIP raggiunto
          </div>
        </header>

        <div
          v-if="notesByStatus[col.status].length === 0"
          class="board-column__empty"
        >
          Nessuna nota in questa colonna.
        </div>

        <div
          v-else
          class="board-column__notes"
        >
          <NoteCard
            v-for="(note, index) in notesByStatus[col.status]"
            :key="note.id"
            :note="note"
            :disabled="disabled"
            :is-first="index === 0"
            :is-last="index === notesByStatus[col.status].length - 1"
            @edit="emit('edit-note', note)"
            @delete="emit('delete-note', note.id)"
            @move-status="handleMoveStatus"
            @reorder="(e) => handleReorder(col.status, index, e)"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.board-view__state-banner {
  background-color: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 1.5rem;
  text-align: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.board-view__state-banner--error {
  background-color: var(--color-blocked-bg);
  border-color: #fda4af;
  color: var(--color-blocked-text);
}

.board-view__retry-button {
  margin-top: 0.75rem;
  padding: 0.5rem 1rem;
  background-color: #ffffff;
  border: 1px solid currentColor;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 600;
  color: inherit;
}

.board-view__columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1.5rem;
  align-items: start;
}

.board-column {
  background-color: #f0ece1;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.board-column__header h2 {
  font-size: 1.125rem;
  margin: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.board-column__count {
  font-size: 0.875rem;
  color: #4b5563;
}

.board-column__wip-alert {
  margin-top: 0.5rem;
  padding: 0.25rem 0.5rem;
  background-color: #fee2e2;
  color: #991b1b;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
}

.board-column__empty {
  color: #6b7280;
  font-size: 0.875rem;
  font-style: italic;
  padding: 1rem 0;
  text-align: center;
}

.board-column__notes {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

@media (max-width: 768px) {
  .board-view__columns {
    grid-template-columns: 1fr;
  }
}
</style>
