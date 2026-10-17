// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { ref } from 'vue'
import { boardClient } from '../api/boardClient.js'
import { evaluateBoardCommand, validateNoteContent } from 'sticky-notes-domain'

export function useBoard(client = boardClient) {
  const board = ref(null)
  const loading = ref(false)
  const saving = ref(false)
  const error = ref(null)
  const conflictState = ref(null)
  const draft = ref({
    content: '',
    color: 'YELLOW',
    blockReason: '',
    checklistLabel: ''
  })

  function clearError() {
    error.value = null
  }

  function clearConflict() {
    conflictState.value = null
  }

  function setDraftField(field, value) {
    draft.value[field] = value
  }

  function resetDraft() {
    draft.value = {
      content: '',
      color: 'YELLOW',
      blockReason: '',
      checklistLabel: ''
    }
  }

  function applySnapshotIfNewer(incoming) {
    if (!incoming) return
    if (!board.value || incoming.revision >= board.value.revision) {
      board.value = incoming
    }
  }

  async function fetchBoard() {
    loading.value = true
    try {
      const snapshot = await client.getBoard()
      applySnapshotIfNewer(snapshot)
      clearError()
    } catch (err) {
      error.value = err
      throw err
    } finally {
      loading.value = false
    }
  }

  function prevalidateCommand(command) {
    if (!board.value) {
      return { valid: true }
    }
    try {
      const resultJson = evaluateBoardCommand(
        JSON.stringify(board.value),
        JSON.stringify(command)
      )
      const parsed = JSON.parse(resultJson)
      if (parsed.type === 'FAILURE') {
        return {
          valid: false,
          code: parsed.code,
          field: parsed.field || null
        }
      }
      return { valid: true }
    } catch {
      return { valid: true }
    }
  }

  async function reconcileBoardOnConflictOrTimeout(err, context = {}) {
    const isRevisionConflict = err.status === 409 && err.code === 'REVISION_CONFLICT'
    const isTargetMissing = err.status === 404 && (err.code === 'NOTE_NOT_FOUND' || err.code === 'ITEM_NOT_FOUND')
    const isTimeout = err.code === 'REQUEST_TIMEOUT'

    if (!isRevisionConflict && !isTargetMissing && !isTimeout) {
      return
    }

    try {
      const latest = await client.getBoard()
      applySnapshotIfNewer(latest)
    } catch {
    }

    const targetNoteExists = context.noteId
      ? Boolean(board.value?.notes?.some(n => n.id === context.noteId))
      : true

    const targetDeleted = isTargetMissing || (Boolean(context.noteId) && !targetNoteExists)

    if (isRevisionConflict || targetDeleted) {
      conflictState.value = {
        code: targetDeleted ? (err.code === 'ITEM_NOT_FOUND' ? 'ITEM_NOT_FOUND' : 'NOTE_NOT_FOUND') : 'REVISION_CONFLICT',
        targetDeleted,
        noteId: context.noteId || null,
        preservedText: context.preservedText || '',
        preservedColor: context.preservedColor || 'YELLOW',
        message: targetDeleted
          ? 'L\'elemento su cui stavi lavorando è stato eliminato in un\'altra finestra.'
          : 'La bacheca è stata aggiornata da un\'altra finestra. Controlla il nuovo stato e riprova.'
      }
    }
  }

  async function executeMutating(action, preflightCommand = null, context = {}) {
    if (saving.value) {
      return
    }

    if (preflightCommand) {
      const validation = prevalidateCommand(preflightCommand)
      if (!validation.valid) {
        const preflightError = new Error(`Preflight rejected: ${validation.code}`)
        preflightError.code = validation.code
        preflightError.field = validation.field
        error.value = preflightError
        return
      }
    }

    saving.value = true
    clearError()
    clearConflict()

    try {
      await action()
    } catch (err) {
      await reconcileBoardOnConflictOrTimeout(err, context)
      error.value = err
      throw err
    } finally {
      saving.value = false
    }
  }

  async function createNote(content, color = 'YELLOW') {
    draft.value.content = content
    draft.value.color = color

    const contentValidation = JSON.parse(validateNoteContent(content))
    if (!contentValidation.valid) {
      const valError = new Error(`Invalid content: ${contentValidation.code}`)
      valError.code = contentValidation.code
      valError.field = contentValidation.field
      error.value = valError
      return
    }

    const previewId = 'preview-note'
    const preflight = {
      type: 'CREATE_NOTE',
      content,
      color,
      newId: previewId
    }

    await executeMutating(
      async () => {
        const response = await client.createNote(content, color, board.value?.revision ?? 0)
        applySnapshotIfNewer(response.board)
        resetDraft()
      },
      preflight,
      { preservedText: content, preservedColor: color }
    )
  }

  async function updateNote(noteId, payload) {
    if (payload.content !== undefined) {
      draft.value.content = payload.content
    }
    if (payload.color !== undefined) {
      draft.value.color = payload.color
    }

    const preflight = {
      type: 'UPDATE_NOTE',
      noteId,
      content: payload.content ?? null,
      color: payload.color ?? null
    }

    await executeMutating(
      async () => {
        const updated = await client.updateNote(noteId, payload, board.value?.revision ?? 0)
        applySnapshotIfNewer(updated)
        resetDraft()
      },
      preflight,
      {
        noteId,
        preservedText: payload.content ?? draft.value.content,
        preservedColor: payload.color ?? draft.value.color
      }
    )
  }

  async function deleteNote(noteId) {
    const preflight = {
      type: 'DELETE_NOTE',
      noteId
    }

    await executeMutating(
      async () => {
        const updated = await client.deleteNote(noteId, board.value?.revision ?? 0)
        applySnapshotIfNewer(updated)
      },
      preflight,
      { noteId }
    )
  }

  async function moveNote(noteId, targetStatus, destinationIndex) {
    const preflight = {
      type: 'MOVE_NOTE',
      noteId,
      targetStatus,
      destinationIndex
    }

    await executeMutating(
      async () => {
        const updated = await client.moveNote(
          noteId,
          targetStatus,
          destinationIndex,
          board.value?.revision ?? 0
        )
        applySnapshotIfNewer(updated)
      },
      preflight,
      { noteId }
    )
  }

  async function setWipLimit(limit) {
    const preflight = {
      type: 'SET_WIP_LIMIT',
      limit
    }

    await executeMutating(
      async () => {
        const updated = await client.setWipLimit(limit, board.value?.revision ?? 0)
        applySnapshotIfNewer(updated)
      },
      preflight
    )
  }

  async function blockNote(noteId, reason) {
    draft.value.blockReason = reason

    const preflight = {
      type: 'BLOCK_NOTE',
      noteId,
      reason
    }

    await executeMutating(
      async () => {
        const updated = await client.blockNote(noteId, reason, board.value?.revision ?? 0)
        applySnapshotIfNewer(updated)
        draft.value.blockReason = ''
      },
      preflight,
      { noteId, preservedText: reason }
    )
  }

  async function unblockNote(noteId) {
    const preflight = {
      type: 'UNBLOCK_NOTE',
      noteId
    }

    await executeMutating(
      async () => {
        const updated = await client.unblockNote(noteId, board.value?.revision ?? 0)
        applySnapshotIfNewer(updated)
      },
      preflight,
      { noteId }
    )
  }

  async function addChecklistItem(noteId, label) {
    draft.value.checklistLabel = label
    const previewId = 'preview-item'
    const preflight = {
      type: 'ADD_ITEM',
      noteId,
      label,
      newId: previewId
    }

    await executeMutating(
      async () => {
        const response = await client.addChecklistItem(
          noteId,
          label,
          board.value?.revision ?? 0
        )
        applySnapshotIfNewer(response.board)
        draft.value.checklistLabel = ''
      },
      preflight,
      { noteId, preservedText: label }
    )
  }

  async function updateChecklistItem(noteId, itemId, payload) {
    if (payload.label !== undefined) {
      draft.value.checklistLabel = payload.label
    }

    const preflight = {
      type: 'UPDATE_ITEM',
      noteId,
      itemId,
      label: payload.label ?? null,
      completed: payload.completed ?? null
    }

    await executeMutating(
      async () => {
        const updated = await client.updateChecklistItem(
          noteId,
          itemId,
          payload,
          board.value?.revision ?? 0
        )
        applySnapshotIfNewer(updated)
        if (payload.label !== undefined) {
          draft.value.checklistLabel = ''
        }
      },
      preflight,
      { noteId, itemId, preservedText: payload.label ?? '' }
    )
  }

  async function deleteChecklistItem(noteId, itemId) {
    const preflight = {
      type: 'DELETE_ITEM',
      noteId,
      itemId
    }

    await executeMutating(
      async () => {
        const updated = await client.deleteChecklistItem(
          noteId,
          itemId,
          board.value?.revision ?? 0
        )
        applySnapshotIfNewer(updated)
      },
      preflight,
      { noteId, itemId }
    )
  }

  return {
    board,
    loading,
    saving,
    error,
    conflictState,
    draft,
    clearError,
    clearConflict,
    setDraftField,
    resetDraft,
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
  }
}
