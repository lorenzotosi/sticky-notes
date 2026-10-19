<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { ref } from 'vue'

const props = defineProps({
  conflict: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['dismiss', 'create-from-draft'])

const copied = ref(false)

async function copyPreservedText() {
  if (!props.conflict?.preservedText) return
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(props.conflict.preservedText)
    }
    copied.value = true
  } catch {
    copied.value = false
  }
}
</script>

<template>
  <div
    class="conflict-notice"
    role="alert"
    aria-live="assertive"
  >
    <div class="conflict-notice__header">
      <strong class="conflict-notice__title">
        {{ conflict.targetDeleted ? 'Elemento eliminato in un\'altra sessione' : 'Conflitto di aggiornamento rilevato' }}
      </strong>
      <button
        type="button"
        class="btn btn--sm btn--cancel"
        aria-label="Chiudi avviso conflitto"
        @click="emit('dismiss')"
      >
        Chiudi
      </button>
    </div>

    <p class="conflict-notice__message">
      {{ conflict.message }}
    </p>

    <div
      v-if="conflict.preservedText"
      class="conflict-notice__draft-box"
    >
      <span class="conflict-notice__draft-label">Testo in bozza conservato:</span>
      <pre class="conflict-notice__draft-content">{{ conflict.preservedText }}</pre>

      <div class="conflict-notice__actions">
        <button
          type="button"
          class="btn btn--sm btn--secondary"
          @click="copyPreservedText"
        >
          {{ copied ? 'Testo copiato' : 'Copia testo' }}
        </button>

        <button
          v-if="conflict.targetDeleted"
          type="button"
          class="btn btn--sm btn--primary"
          @click="emit('create-from-draft', { content: conflict.preservedText, color: conflict.preservedColor || 'YELLOW' }, $event)"
        >
          Crea come nuova nota
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.conflict-notice {
  background-color: #fffbeb;
  border: 1px solid #f59e0b;
  border-left: 4px solid #d97706;
  border-radius: 6px;
  padding: 1rem;
  margin-bottom: 1.5rem;
  color: #78350f;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.conflict-notice__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conflict-notice__title {
  font-size: 0.9375rem;
}

.conflict-notice__message {
  margin: 0;
  font-size: 0.875rem;
}

.conflict-notice__draft-box {
  background-color: #ffffff;
  border: 1px solid #fcd34d;
  border-radius: 4px;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.conflict-notice__draft-label {
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
}

.conflict-notice__draft-content {
  margin: 0;
  font-family: inherit;
  font-size: 0.875rem;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
}

.conflict-notice__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}
</style>
