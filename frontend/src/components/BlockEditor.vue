<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'

const props = defineProps({
  noteId: {
    type: String,
    required: true
  },
  initialReason: {
    type: String,
    default: ''
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

const emit = defineEmits(['save', 'cancel'])

const reason = ref(props.initialReason)
const clientError = ref('')
const textareaRef = ref(null)
const maxChars = 200

const charCount = computed(() => reason.value.length)

onMounted(async () => {
  await nextTick()
  if (textareaRef.value) {
    textareaRef.value.focus()
  }
})

function handleSubmit() {
  clientError.value = ''
  const trimmed = reason.value.trim()

  if (trimmed.length === 0) {
    clientError.value = 'Il motivo del blocco non può essere vuoto.'
    return
  }

  if (reason.value.length > maxChars) {
    clientError.value = `Il motivo del blocco non può superare ${maxChars} caratteri.`
    return
  }

  emit('save', {
    noteId: props.noteId,
    reason: reason.value
  })
}
</script>

<template>
  <form
    class="block-editor"
    @submit.prevent="handleSubmit"
  >
    <div class="block-editor__header">
      <h3 class="block-editor__title">
        {{ initialReason ? 'Modifica motivo blocco' : 'Blocca nota' }}
      </h3>
      <span
        :class="['block-editor__counter', { 'block-editor__counter--exceeded': charCount > maxChars }]"
        aria-live="polite"
      >
        {{ charCount }} / {{ maxChars }}
      </span>
    </div>

    <div class="block-editor__field">
      <label
        for="block-reason-textarea"
        class="sr-only"
      >
        Motivo del blocco
      </label>
      <textarea
        id="block-reason-textarea"
        ref="textareaRef"
        v-model="reason"
        class="block-editor__textarea"
        placeholder="Specifica il motivo per cui questa nota è bloccata..."
        rows="3"
        :disabled="saving"
      />
    </div>

    <div
      v-if="clientError || errorMessage"
      class="block-editor__error"
      role="alert"
    >
      {{ clientError || errorMessage }}
    </div>

    <div class="block-editor__actions">
      <button
        type="button"
        class="btn btn--cancel"
        :disabled="saving"
        @click="emit('cancel')"
      >
        Annulla
      </button>
      <button
        type="submit"
        class="btn btn--danger"
        :disabled="saving"
      >
        {{ saving ? 'Salvataggio...' : 'Conferma blocco' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.block-editor {
  background-color: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 1.25rem;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.block-editor__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.block-editor__title {
  margin: 0;
  font-size: 1.125rem;
}

.block-editor__counter {
  font-size: 0.8125rem;
  color: #6b7280;
}

.block-editor__counter--exceeded {
  color: #dc2626;
  font-weight: 700;
}

.block-editor__textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 0.75rem;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.9375rem;
  resize: vertical;
}

.block-editor__error {
  background-color: var(--color-blocked-bg);
  border-left: 3px solid #e11d48;
  padding: 0.5rem;
  font-size: 0.8125rem;
  color: var(--color-blocked-text);
}

.block-editor__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}
</style>
