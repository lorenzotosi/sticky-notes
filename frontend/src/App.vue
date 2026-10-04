<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

<script setup>
import { onMounted } from 'vue'
import { useBoard } from './composables/useBoard.js'
import BoardView from './components/BoardView.vue'

const { board, loading, error, fetchBoard } = useBoard()

function handleRetry() {
  fetchBoard().catch(() => {})
}

onMounted(() => {
  fetchBoard().catch(() => {})
})
</script>

<template>
  <main class="app-container">
    <header class="app-header">
      <h1>Sticky Notes Kanban</h1>
      <div
        aria-live="polite"
        class="sr-only"
      >
        <span v-if="loading">Caricamento in corso</span>
        <span v-else-if="error">{{ error.message }}</span>
      </div>
    </header>

    <BoardView
      :board="board"
      :loading="loading"
      :error="error"
      @retry="handleRetry"
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

.app-header h1 {
  font-size: 1.875rem;
  margin: 0;
  font-weight: 700;
}
</style>
