<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { nextTick, onMounted, ref } from 'vue'

const props = defineProps({
  currentLimit: {
    type: Number,
    required: true
  },
  currentDoingCount: {
    type: Number,
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

const emit = defineEmits(['save', 'cancel'])

const limit = ref(props.currentLimit)
const clientError = ref('')
const inputRef = ref(null)

onMounted(async () => {
  await nextTick()
  if (inputRef.value) {
    inputRef.value.focus()
  }
})

function handleSubmit() {
  clientError.value = ''
  const parsed = Number(limit.value)

  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 20) {
    clientError.value = 'Il limite WIP deve essere un numero intero compreso tra 1 e 20.'
    return
  }

  if (parsed < props.currentDoingCount) {
    clientError.value = `Il nuovo limite (${parsed}) non può essere inferiore al numero attuale di note in lavorazione (${props.currentDoingCount}).`
    return
  }

  emit('save', parsed)
}
</script>

<template>
  <form
    class="wip-limit-editor"
    @submit.prevent="handleSubmit"
  >
    <div class="wip-limit-editor__header">
      <h3 class="wip-limit-editor__title">
        Modifica limite WIP
      </h3>
      <span class="wip-limit-editor__info">
        Note in lavorazione: {{ currentDoingCount }}
      </span>
    </div>

    <div class="wip-limit-editor__field">
      <label
        for="wip-limit-input"
        class="wip-limit-editor__label"
      >
        Limite massimo (1 - 20)
      </label>
      <input
        id="wip-limit-input"
        ref="inputRef"
        v-model="limit"
        type="number"
        min="1"
        max="20"
        step="1"
        class="wip-limit-editor__input"
        :disabled="saving"
      >
    </div>

    <div
      v-if="clientError || errorMessage"
      class="wip-limit-editor__error"
      role="alert"
    >
      {{ clientError || errorMessage }}
    </div>

    <div class="wip-limit-editor__actions">
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
        class="btn btn--primary"
        :disabled="saving"
      >
        {{ saving ? 'Salvataggio...' : 'Conferma limite' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.wip-limit-editor {
  background-color: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 1.25rem;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.wip-limit-editor__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.wip-limit-editor__title {
  margin: 0;
  font-size: 1.125rem;
}

.wip-limit-editor__info {
  font-size: 0.8125rem;
  color: #4b5563;
}

.wip-limit-editor__field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.wip-limit-editor__label {
  font-size: 0.875rem;
  font-weight: 600;
}

.wip-limit-editor__input {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  font-size: 1rem;
}

.wip-limit-editor__error {
  background-color: var(--color-blocked-bg);
  border-left: 3px solid #e11d48;
  padding: 0.5rem;
  font-size: 0.8125rem;
  color: var(--color-blocked-text);
}

.wip-limit-editor__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}
</style>
