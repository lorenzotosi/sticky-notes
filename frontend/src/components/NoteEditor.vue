<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { validateNoteContent } from 'sticky-notes-domain'

const props = defineProps({
  initialContent: {
    type: String,
    default: ''
  },
  initialColor: {
    type: String,
    default: 'YELLOW'
  },
  isEditing: {
    type: Boolean,
    default: false
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

const content = ref(props.initialContent)
const selectedColor = ref(props.initialColor)
const clientValidationError = ref('')
const textareaRef = ref(null)

const availableColors = [
  { value: 'YELLOW', label: 'Giallo' },
  { value: 'BLUE', label: 'Blu' },
  { value: 'GREEN', label: 'Verde' },
  { value: 'PINK', label: 'Rosa' }
]

const charCount = computed(() => content.value.length)
const maxChars = 500

onMounted(async () => {
  await nextTick()
  if (textareaRef.value) {
    textareaRef.value.focus()
  }
})

function handleInput() {
  clientValidationError.value = ''
}

function handleSubmit() {
  clientValidationError.value = ''
  const validation = JSON.parse(validateNoteContent(content.value))
  if (!validation.valid) {
    clientValidationError.value = 'Il contenuto della nota deve essere compreso tra 1 e 500 caratteri.'
    return
  }

  emit('save', {
    content: content.value,
    color: selectedColor.value
  })
}
</script>

<template>
  <form
    class="note-editor"
    @submit.prevent="handleSubmit"
  >
    <div class="note-editor__header">
      <h3 class="note-editor__title">
        {{ isEditing ? 'Modifica nota' : 'Nuova nota' }}
      </h3>
      <span
        :class="['note-editor__counter', { 'note-editor__counter--exceeded': charCount > maxChars }]"
        aria-live="polite"
      >
        {{ charCount }} / {{ maxChars }}
      </span>
    </div>

    <div class="note-editor__field">
      <label
        for="note-editor-content"
        class="sr-only"
      >
        Contenuto della nota
      </label>
      <textarea
        id="note-editor-content"
        ref="textareaRef"
        v-model="content"
        class="note-editor__textarea"
        placeholder="Scrivi qui il contenuto della nota..."
        rows="4"
        :disabled="saving"
        @input="handleInput"
      />
    </div>

    <fieldset class="note-editor__colors">
      <legend class="note-editor__colors-legend">
        Colore della nota
      </legend>
      <div class="note-editor__color-options">
        <label
          v-for="color in availableColors"
          :key="color.value"
          :class="['note-editor__color-label', `note-editor__color-label--${color.value.toLowerCase()}`]"
        >
          <input
            v-model="selectedColor"
            type="radio"
            name="note-color"
            :value="color.value"
            :disabled="saving"
          >
          <span>{{ color.label }}</span>
        </label>
      </div>
    </fieldset>

    <div
      v-if="clientValidationError || errorMessage"
      class="note-editor__error"
      role="alert"
    >
      {{ clientValidationError || errorMessage }}
    </div>

    <div class="note-editor__actions">
      <button
        type="button"
        class="note-editor__button note-editor__button--cancel"
        :disabled="saving"
        @click="emit('cancel')"
      >
        Annulla
      </button>
      <button
        type="submit"
        class="note-editor__button note-editor__button--save"
        :disabled="saving"
      >
        {{ saving ? 'Salvataggio...' : (isEditing ? 'Salva modifiche' : 'Crea nota') }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.note-editor {
  background-color: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 1.25rem;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.note-editor__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.note-editor__title {
  margin: 0;
  font-size: 1.125rem;
}

.note-editor__counter {
  font-size: 0.8125rem;
  color: #6b7280;
}

.note-editor__counter--exceeded {
  color: #dc2626;
  font-weight: 700;
}

.note-editor__textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 0.75rem;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.9375rem;
  resize: vertical;
}

.note-editor__colors {
  border: 0;
  padding: 0;
  margin: 0;
}

.note-editor__colors-legend {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.note-editor__color-options {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.note-editor__color-label {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.625rem;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  cursor: pointer;
  font-size: 0.8125rem;
}

.note-editor__color-label--yellow { background-color: var(--color-yellow); }
.note-editor__color-label--blue { background-color: var(--color-blue); }
.note-editor__color-label--green { background-color: var(--color-green); }
.note-editor__color-label--pink { background-color: var(--color-pink); }

.note-editor__error {
  background-color: var(--color-blocked-bg);
  border-left: 3px solid #e11d48;
  padding: 0.5rem;
  font-size: 0.8125rem;
  color: var(--color-blocked-text);
}

.note-editor__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

.note-editor__button {
  padding: 0.5rem 1rem;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  cursor: pointer;
  font-weight: 600;
  font-size: 0.875rem;
}

.note-editor__button--cancel {
  background-color: #ffffff;
}

.note-editor__button--save {
  background-color: #1e293b;
  color: #ffffff;
  border-color: #1e293b;
}

.note-editor__button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
