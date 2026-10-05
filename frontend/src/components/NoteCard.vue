<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { computed } from 'vue'

const props = defineProps({
  note: {
    type: Object,
    required: true
  }
})

const completedItemsCount = computed(() => {
  return (props.note.checklist || []).filter(item => item.completed).length
})

const totalItemsCount = computed(() => {
  return (props.note.checklist || []).length
})

const colorLabels = {
  YELLOW: 'Giallo',
  BLUE: 'Blu',
  GREEN: 'Verde',
  PINK: 'Rosa'
}

const colorLabel = computed(() => colorLabels[props.note.color] || props.note.color)
</script>

<template>
  <article
    :class="['note-card', `note-card--${note.color.toLowerCase()}`]"
    :aria-labelledby="`note-title-${note.id}`"
  >
    <header class="note-card__header">
      <span class="note-card__color-badge">
        {{ colorLabel }}
      </span>
      <span
        v-if="note.blockedReason"
        class="note-card__blocked-badge"
        role="status"
      >
        Bloccata
      </span>
    </header>

    <div
      :id="`note-title-${note.id}`"
      class="note-card__content"
    >
      {{ note.content }}
    </div>

    <div
      v-if="note.blockedReason"
      class="note-card__block-alert"
      role="alert"
    >
      <strong>Motivo blocco:</strong> {{ note.blockedReason }}
    </div>

    <div
      v-if="totalItemsCount > 0"
      class="note-card__checklist"
    >
      <div class="note-card__checklist-header">
        Checklist ({{ completedItemsCount }}/{{ totalItemsCount }})
      </div>
      <ul
        class="note-card__checklist-items"
        aria-label="Voci checklist"
      >
        <li
          v-for="item in note.checklist"
          :key="item.id"
          :class="['note-card__checklist-item', { 'note-card__checklist-item--completed': item.completed }]"
        >
          <input
            type="checkbox"
            :checked="item.completed"
            disabled
            :aria-label="item.label"
          >
          <span>{{ item.label }}</span>
        </li>
      </ul>
    </div>
  </article>
</template>

<style scoped>
.note-card {
  border-radius: 6px;
  padding: 1rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  word-break: break-word;
}

.note-card--yellow {
  background-color: var(--color-yellow);
  border: 1px solid #f6e05e;
}

.note-card--blue {
  background-color: var(--color-blue);
  border: 1px solid #90cdf4;
}

.note-card--green {
  background-color: var(--color-green);
  border: 1px solid #9ae6b4;
}

.note-card--pink {
  background-color: var(--color-pink);
  border: 1px solid #fbb6ce;
}

.note-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.75rem;
}

.note-card__color-badge {
  text-transform: uppercase;
  font-weight: 700;
  letter-spacing: 0.05em;
  opacity: 0.8;
}

.note-card__blocked-badge {
  background-color: #be123c;
  color: #ffffff;
  padding: 0.125rem 0.375rem;
  border-radius: 3px;
  font-weight: 700;
  font-size: 0.6875rem;
}

.note-card__content {
  font-size: 0.9375rem;
  white-space: pre-wrap;
  color: var(--text-primary);
}

.note-card__block-alert {
  background-color: #fff1f2;
  border-left: 3px solid #e11d48;
  padding: 0.5rem;
  font-size: 0.8125rem;
  color: #881337;
}

.note-card__checklist {
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  padding-top: 0.5rem;
}

.note-card__checklist-header {
  font-size: 0.75rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.note-card__checklist-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.note-card__checklist-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8125rem;
}

.note-card__checklist-item--completed span {
  text-decoration: line-through;
  opacity: 0.65;
}
</style>
