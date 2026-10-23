// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { test, expect } from '@playwright/test'

async function resetBoardViaApi(request) {
  let res = await request.get('/api/board')
  expect(res.ok()).toBeTruthy()
  let board = await res.json()

  for (const note of board.notes) {
    const delRes = await request.delete(
      `/api/board/notes/${note.id}?expectedRevision=${board.revision}`
    )
    expect(delRes.ok()).toBeTruthy()
    board = await delRes.json()
  }

  if (board.wipLimit !== 3) {
    const wipRes = await request.put('/api/board/wip-limit', {
      data: { limit: 3, expectedRevision: board.revision }
    })
    expect(wipRes.ok()).toBeTruthy()
    board = await wipRes.json()
  }

  return board
}

function columnSection(page, status) {
  return page.locator(`.board-column--${status.toLowerCase()}`)
}

function noteCard(page, text) {
  return page.locator('article.note-card', { hasText: text })
}

test.describe('Sticky Notes Kanban E2E Journeys', () => {
  test.beforeEach(async ({ request }) => {
    await resetBoardViaApi(request)
  })

  test('creates, edits, changes color, reloads, and deletes a note on the production bundle', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Sticky Notes Kanban' })).toBeVisible()

    const moduleScriptSrc = await page.locator('script[type="module"]').first().getAttribute('src')
    expect(moduleScriptSrc).toMatch(/^\/assets\/index-.*\.js$/)

    await page.getByRole('button', { name: 'Nuova nota' }).click()
    await page.getByLabel('Contenuto della nota').fill('E2E initial note')
    await page.getByRole('radio', { name: 'Blu' }).check()
    await page.getByRole('button', { name: 'Crea nota' }).click()

    const todoColumn = columnSection(page, 'TODO')
    const createdCard = todoColumn.locator('article.note-card', { hasText: 'E2E initial note' })
    await expect(createdCard).toBeVisible()
    await expect(createdCard.locator('.note-card__color-badge')).toHaveText('Blu')

    await createdCard.getByRole('button', { name: 'Modifica nota' }).click()
    await page.getByLabel('Contenuto della nota').fill('E2E updated note')
    await page.getByRole('radio', { name: 'Verde' }).check()
    await page.getByRole('button', { name: 'Salva modifiche' }).click()

    const updatedCard = todoColumn.locator('article.note-card', { hasText: 'E2E updated note' })
    await expect(updatedCard).toBeVisible()
    await expect(updatedCard.locator('.note-card__color-badge')).toHaveText('Verde')

    await page.reload()
    const persistedCard = columnSection(page, 'TODO').locator('article.note-card', { hasText: 'E2E updated note' })
    await expect(persistedCard).toBeVisible()
    await expect(persistedCard.locator('.note-card__color-badge')).toHaveText('Verde')

    await persistedCard.getByRole('button', { name: 'Elimina nota' }).click()
    const confirmDialog = persistedCard.getByRole('alertdialog', { name: 'Conferma eliminazione' })
    await expect(confirmDialog).toBeVisible()
    await confirmDialog.getByRole('button', { name: 'Conferma eliminazione' }).click()

    await expect(noteCard(page, 'E2E updated note')).toHaveCount(0)
  })

  test('enforces WIP limit when moving a second note into DOING', async ({ page, request }) => {
    let board = await resetBoardViaApi(request)

    const create1 = await request.post('/api/board/notes', {
      data: { content: 'First WIP task', color: 'YELLOW', expectedRevision: board.revision }
    })
    board = (await create1.json()).board

    const create2 = await request.post('/api/board/notes', {
      data: { content: 'Second WIP task', color: 'BLUE', expectedRevision: board.revision }
    })
    board = (await create2.json()).board

    await page.goto('/')

    await page.getByRole('button', { name: 'Modifica limite WIP' }).click()
    await page.getByLabel('Limite massimo (1 - 20)').fill('1')
    await page.getByRole('button', { name: 'Conferma limite' }).click()

    const doingColumn = columnSection(page, 'DOING')
    await expect(doingColumn.locator('h2')).toContainText('(0 / 1)')

    await noteCard(page, 'First WIP task').getByRole('button', { name: 'Sposta in lavorazione' }).click()
    await expect(doingColumn.locator('article.note-card', { hasText: 'First WIP task' })).toBeVisible()
    await expect(doingColumn.getByText('Limite WIP raggiunto')).toBeVisible()

    await noteCard(page, 'Second WIP task').getByRole('button', { name: 'Sposta in lavorazione' }).click()
    await expect(columnSection(page, 'TODO').locator('article.note-card', { hasText: 'Second WIP task' })).toBeVisible()
    await expect(doingColumn.locator('article.note-card', { hasText: 'Second WIP task' })).toHaveCount(0)
    await expect(page.locator('[aria-live="polite"]')).toContainText('WIP_LIMIT_REACHED')
  })

  test('prevents transitions while blocked and allows movement after unblocking', async ({ page, request }) => {
    const board = await resetBoardViaApi(request)
    await request.post('/api/board/notes', {
      data: { content: 'Blocked task item', color: 'PINK', expectedRevision: board.revision }
    })

    await page.goto('/')

    const card = noteCard(page, 'Blocked task item')
    await card.getByRole('button', { name: 'Blocca nota' }).click()
    await page.getByLabel('Motivo del blocco').fill('Waiting for schema approval')
    await page.getByRole('button', { name: 'Conferma blocco' }).click()

    await expect(card.getByText('Bloccata')).toBeVisible()
    await expect(card.getByText('Waiting for schema approval')).toBeVisible()

    const moveBtn = card.getByRole('button', { name: 'Sposta in lavorazione' })
    await expect(moveBtn).toBeDisabled()

    await card.getByRole('button', { name: 'Sblocca nota' }).click()
    await expect(card.getByText('Waiting for schema approval')).toHaveCount(0)
    await expect(moveBtn).toBeEnabled()

    await moveBtn.click()
    await expect(columnSection(page, 'DOING').locator('article.note-card', { hasText: 'Blocked task item' })).toBeVisible()
  })

  test('guards completion with checklist, keeps DONE checklist read-only, and respects WIP on reopen', async ({ page, request }) => {
    const board = await resetBoardViaApi(request)
    const create1 = await request.post('/api/board/notes', {
      data: { content: 'Checklist release note', color: 'YELLOW', expectedRevision: board.revision }
    })
    const created1 = await create1.json()

    await request.post(`/api/board/notes/${created1.createdNoteId}/move`, {
      data: { targetStatus: 'DOING', destinationIndex: 0, expectedRevision: created1.board.revision }
    })

    await page.goto('/')

    const doingColumn = columnSection(page, 'DOING')
    const doneColumn = columnSection(page, 'DONE')
    const cardInDoing = doingColumn.locator('article.note-card', { hasText: 'Checklist release note' })

    await cardInDoing.getByRole('button', { name: 'Gestisci checklist' }).click()
    await page.getByLabel('Nuova voce checklist').fill('Verify container digest')
    await page.getByRole('button', { name: 'Aggiungi' }).click()
    await expect(page.getByRole('list', { name: 'Elenco voci checklist' })).toContainText('Verify container digest')
    await page.getByRole('button', { name: 'Chiudi' }).click()

    const completeBtn = cardInDoing.getByRole('button', { name: 'Completa' })
    await expect(completeBtn).toBeDisabled()

    await cardInDoing.getByRole('checkbox', { name: 'Verify container digest' }).check()
    await expect(completeBtn).toBeEnabled()
    await completeBtn.click()

    const cardInDone = doneColumn.locator('article.note-card', { hasText: 'Checklist release note' })
    await expect(cardInDone).toBeVisible()

    await expect(cardInDone.getByRole('checkbox', { name: 'Verify container digest' })).toBeDisabled()
    await cardInDone.getByRole('button', { name: 'Gestisci checklist' }).click()
    await expect(page.getByText('Questa nota è completata: le voci della checklist sono in sola lettura.')).toBeVisible()
    await page.getByRole('button', { name: 'Chiudi' }).click()

    await page.getByRole('button', { name: 'Nuova nota' }).click()
    await page.getByLabel('Contenuto della nota').fill('Active WIP blocker')
    await page.getByRole('button', { name: 'Crea nota' }).click()

    const blockerInTodo = columnSection(page, 'TODO').locator('article.note-card', { hasText: 'Active WIP blocker' })
    await expect(blockerInTodo).toBeVisible()
    await blockerInTodo.getByRole('button', { name: 'Sposta in lavorazione' }).click()
    await expect(doingColumn.locator('article.note-card', { hasText: 'Active WIP blocker' })).toBeVisible()

    await page.getByRole('button', { name: 'Modifica limite WIP' }).click()
    await page.getByLabel('Limite massimo (1 - 20)').fill('1')
    await page.getByRole('button', { name: 'Conferma limite' }).click()
    await expect(doingColumn.locator('h2')).toContainText('(1 / 1)')

    await cardInDone.getByRole('button', { name: 'Riapri' }).click()
    await expect(cardInDone).toBeVisible()
    await expect(page.locator('[aria-live="polite"]')).toContainText('WIP_LIMIT_REACHED')

    await doingColumn.locator('article.note-card', { hasText: 'Active WIP blocker' })
      .getByRole('button', { name: 'Rimetti da fare' }).click()
    await expect(doingColumn.locator('h2')).toContainText('(0 / 1)')

    await cardInDone.getByRole('button', { name: 'Riapri' }).click()
    await expect(doingColumn.locator('article.note-card', { hasText: 'Checklist release note' })).toBeVisible()
  })

  test('detects concurrent edit conflict across two browser contexts and preserves user draft', async ({ browser, request }) => {
    const board = await resetBoardViaApi(request)
    await request.post('/api/board/notes', {
      data: { content: 'Shared initial text', color: 'YELLOW', expectedRevision: board.revision }
    })

    const contextA = await browser.newContext()
    const contextB = await browser.newContext()
    const pageA = await contextA.newPage()
    const pageB = await contextB.newPage()

    try {
      await pageA.goto('/')
      await pageB.goto('/')

      await expect(noteCard(pageA, 'Shared initial text')).toBeVisible()
      await expect(noteCard(pageB, 'Shared initial text')).toBeVisible()

      await noteCard(pageA, 'Shared initial text').getByRole('button', { name: 'Modifica nota' }).click()
      await noteCard(pageB, 'Shared initial text').getByRole('button', { name: 'Modifica nota' }).click()

      await pageA.getByLabel('Contenuto della nota').fill('Updated by Window A')
      await pageA.getByRole('button', { name: 'Salva modifiche' }).click()
      await expect(noteCard(pageA, 'Updated by Window A')).toBeVisible()

      await pageB.getByLabel('Contenuto della nota').fill('Draft from Window B')
      await pageB.getByRole('button', { name: 'Salva modifiche' }).click()

      const conflictBanner = pageB.locator('.conflict-notice')
      await expect(conflictBanner).toBeVisible()
      await expect(conflictBanner).toContainText('Conflitto di aggiornamento rilevato')
      await expect(conflictBanner.locator('.conflict-notice__draft-content')).toHaveText('Draft from Window B')

      await expect(noteCard(pageB, 'Updated by Window A')).toBeVisible()

      await pageB.getByRole('button', { name: 'Salva modifiche' }).click()
      await expect(noteCard(pageB, 'Draft from Window B')).toBeVisible()
    } finally {
      await contextA.close()
      await contextB.close()
    }
  })
})
