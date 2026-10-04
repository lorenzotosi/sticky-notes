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
  const draft = ref({
    content: '',
    color: 'YELLOW',
    blockReason: '',
    checklistLabel: ''
  })

  function clearError() {
    error.value = null
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

  async function fetchBoard() {
    loading.value = true
    try {
      board.value = await client.getBoard()
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

  async function executeMutating(action, preflightCommand = null) {
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
    error.value = null

    try {
      await action()
    } catch (err) {
      if (err.status === 409 && err.code === 'REVISION_CONFLICT') {
        try {
          const freshBoard = await client.getBoard()
          board.value = freshBoard
        } catch {
        }
      }
      error.value = err
      throw err
    } finally {
      saving.value = false
    }
  }

  async function createNote(content, color = 'YELLOW') {
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

    await executeMutating(async () => {
      const response = await client.createNote(content, color, board.value?.revision ?? 0)
      board.value = response.board
      resetDraft()
    }, preflight)
  }

  async function updateNote(noteId, payload) {
    const preflight = {
      type: 'UPDATE_NOTE',
      noteId,
      content: payload.content ?? null,
      color: payload.color ?? null
    }

    await executeMutating(async () => {
      board.value = await client.updateNote(noteId, payload, board.value?.revision ?? 0)
    }, preflight)
  }

  async function deleteNote(noteId) {
    const preflight = {
      type: 'DELETE_NOTE',
      noteId
    }

    await executeMutating(async () => {
      board.value = await client.deleteNote(noteId, board.value?.revision ?? 0)
    }, preflight)
  }

  async function moveNote(noteId, targetStatus, destinationIndex) {
    const preflight = {
      type: 'MOVE_NOTE',
      noteId,
      targetStatus,
      destinationIndex
    }

    await executeMutating(async () => {
      board.value = await client.moveNote(
        noteId,
        targetStatus,
        destinationIndex,
        board.value?.revision ?? 0
      )
    }, preflight)
  }

  async function setWipLimit(limit) {
    const preflight = {
      type: 'SET_WIP_LIMIT',
      limit
    }

    await executeMutating(async () => {
      board.value = await client.setWipLimit(limit, board.value?.revision ?? 0)
    }, preflight)
  }

  async function blockNote(noteId, reason) {
    const preflight = {
      type: 'BLOCK_NOTE',
      noteId,
      reason
    }

    await executeMutating(async () => {
      board.value = await client.blockNote(noteId, reason, board.value?.revision ?? 0)
    }, preflight)
  }

  async function unblockNote(noteId) {
    const preflight = {
      type: 'UNBLOCK_NOTE',
      noteId
    }

    await executeMutating(async () => {
      board.value = await client.unblockNote(noteId, board.value?.revision ?? 0)
    }, preflight)
  }

  async function addChecklistItem(noteId, label) {
    const previewId = 'preview-item'
    const preflight = {
      type: 'ADD_ITEM',
      noteId,
      label,
      newId: previewId
    }

    await executeMutating(async () => {
      const response = await client.addChecklistItem(
        noteId,
        label,
        board.value?.revision ?? 0
      )
      board.value = response.board
    }, preflight)
  }

  async function updateChecklistItem(noteId, itemId, payload) {
    const preflight = {
      type: 'UPDATE_ITEM',
      noteId,
      itemId,
      label: payload.label ?? null,
      completed: payload.completed ?? null
    }

    await executeMutating(async () => {
      board.value = await client.updateChecklistItem(
        noteId,
        itemId,
        payload,
        board.value?.revision ?? 0
      )
    }, preflight)
  }

  async function deleteChecklistItem(noteId, itemId) {
    const preflight = {
      type: 'DELETE_ITEM',
      noteId,
      itemId
    }

    await executeMutating(async () => {
      board.value = await client.deleteChecklistItem(
        noteId,
        itemId,
        board.value?.revision ?? 0
      )
    }, preflight)
  }

  return {
    board,
    loading,
    saving,
    error,
    draft,
    clearError,
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
