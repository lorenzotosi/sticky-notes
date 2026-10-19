<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Accessibility verification

The R3 gate requires the Kanban interface to be operable with the keyboard alone, to name every control, to keep the four note colours readable, and to stay usable at 200% zoom and at a 360 pixel viewport. The required behaviour is stated in [UI and interaction design](ux.md#5-accessibility--responsive-behavior). This document records the method, the observed results, and the corrections applied to the interface.

## Verification environment

| Item | Value |
| --- | --- |
| Interface | Vue application served by the Vite development server on `http://localhost:5173` |
| API and data | Spring Boot backend on `127.0.0.1:8080`, MongoDB on `127.0.0.1:27019` |
| Browser | Google Chrome 155.0.8059.85 on macOS 15.8.1, x86_64 |
| Board under test | `YELLOW` in `TODO` with a 197 character text containing a 42 character unbroken token, `GREEN` blocked in `TODO`, `BLUE` in `DOING` with one completed checklist item, `PINK` in `DONE` |

Contrast ratios use the WCAG relative luminance formula applied to the colours read from the rendered page with `getComputedStyle`. Normal text requires 4.5:1, large text 3:1, and a focus indicator or a control boundary 3:1. Reflow was measured with the same application loaded in a 360 pixel and in a 640 pixel wide frame; 640 pixels is the CSS width of a 1280 pixel viewport at 200% zoom.

## Keyboard reachability and focus indicator

The board exposes 32 interactive elements. Seven of them are disabled: the reorder buttons at the first and the last position of a column, and the movement control of the blocked note. They are not focusable. The remaining 25 are buttons, checkboxes, and form fields, and all of them:

- are reachable in document order without a pointer;
- expose an accessible name, either visible text, an `aria-label`, or an associated label;
- render a visible focus indicator of 2 pixels in `#1d4ed8` with a 2 pixel offset when focused with the keyboard.

The focus colour keeps a ratio of 5.88:1 on the yellow note, 4.96:1 on the blue note, 5.48:1 on the green note, 4.65:1 on the pink note, 6.10:1 on the application background, 5.68:1 on a column, and 6.70:1 on a white panel, so the indicator stays readable on every surface of the interface.

## Focus management

| Interaction | Observed result |
| --- | --- |
| Opening the note editor | Focus moves to the note text area |
| Closing the note editor | Focus returns to the control that opened it |
| Opening the WIP limit editor | Focus moves to the limit input |
| Closing the WIP limit editor | Focus returns to the "Modifica limite WIP" button |
| Opening the checklist editor | Focus moves to the new item field |
| Renaming a checklist item | Focus moves to the rename field, which carries the accessible name "Nuova etichetta della voce" |
| Cancelling a rename | Focus returns to the "Rinomina voce" button |
| Saving a rename | While the request is in flight the rename button is disabled and cannot take the focus; when the request settles the focus returns to the "Rinomina voce" button |
| Requesting a deletion | Focus moves to "Conferma eliminazione" inside the confirmation marked as `alertdialog` |
| Cancelling a deletion | Focus returns to the "Elimina nota" button |
| Confirming a deletion | Focus moves to the first remaining note of the same column, or to the "Nuova nota" button when the column becomes empty |

The editors are inline panels rather than overlays. Opening one moves the focus into its first field, so the keyboard user lands on the form to fill in. The application exposes a live region that reports loading, saving, and error states; it does not announce the opening of an editor.

## Names and labels

- Every input has a label: visible for the WIP limit, visually hidden for the note content, the block reason, and the new checklist item.
- The four note colours are a radio group inside a `fieldset` with a `legend`, so the group and each option are named.
- Icon-only controls, the reorder arrows, the checklist checkbox, and the conflict notice dismissal carry an `aria-label`.
- The note colour is always available as text, and the blocked state always shows its reason, so no information depends on colour alone.
- The document language is `it`, matching the language of the interface.

## Contrast

| Element | Foreground | Background | Ratio | Minimum | Result |
| --- | --- | --- | --- | --- | --- |
| Note text, all four colours | `#1e1e1e` | `#fff385`, `#c4e1fd`, `#c8f3cd`, `#fbcada` | 14.62, 12.33, 13.62, 11.56 | 4.5 | pass |
| Note colour badge, 12 px | `#1e1e1e` at 80% opacity | the four note colours | 8.00, 7.18, 7.63, 6.91 | 4.5 | pass |
| Completed checklist label, 13 px | `#4b5563` | the four note colours | 6.63, 5.59, 6.17, 5.24 | 4.5 | pass |
| Column counter and empty column message, 14 px | `#4b5563` | `#f0ece1` | 6.40 | 4.5 | pass |
| Primary button label, 14 px | `#ffffff` | `#2563eb` | 5.17 | 4.5 | pass |
| Success button label, 12 px | `#ffffff` | `#047857` | 5.48 | 4.5 | pass |
| Warning button label, 14 px | `#9f1239` | `#fff1f2` | 7.30 | 4.5 | pass |
| Disabled button label, 14 px | `#4b5563` | `#e5e7eb` | 6.10 | 4.5 | pass |
| WIP limit alert, 12 px bold | `#991b1b` | `#fee2e2` | 6.80 | 4.5 | pass |
| Blocked badge | `#ffffff` | `#be123c` | 6.29 | 4.5 | pass |
| Conflict notice | `#78350f` | `#fffbeb` | 8.75 | 4.5 | pass |
| Checklist counters, 11 px and 13 px | `#4b5563` | `#ffffff` | 7.56 | 4.5 | pass |
| Focus indicator | `#1d4ed8` | the four note colours, application background, column, white panel | 5.88, 4.96, 5.48, 4.65, 6.10, 5.68, 6.70 | 3 | pass |

## Reflow, zoom, and long text

| Viewport | Result |
| --- | --- |
| 360 pixel wide | One column of 328 pixels, no horizontal scroll, no element outside the viewport, no clipped text |
| 640 pixel wide, the CSS width of a 1280 pixel viewport at 200% zoom | One column, no horizontal scroll, no clipped text |

The 197 character note with a 42 character unbroken token wraps over five lines at 360 pixels. Its content box is 260 pixels wide with a scroll width of 260 pixels, so the text wraps instead of being truncated or causing horizontal scrolling.

## Corrections applied

| Finding | Evidence | Correction | Files |
| --- | --- | --- | --- |
| Disabled buttons showed their label at 1.99:1, because the label was faded to 45% opacity | Automated audit reported the primary button contrast | Disabled buttons use the background `#e5e7eb` with the label colour `#4b5563` | `frontend/src/style.css`, `frontend/src/components/NoteEditor.vue` |
| Completed checklist labels reached 4.47:1 on the pink note | Computed styles of the rendered cards | Opacity replaced by the colour `#4b5563` | `frontend/src/components/NoteCard.vue`, `frontend/src/components/ChecklistEditor.vue` |
| The checklist character counter was 2.54:1 | Computed styles of the open checklist editor | Colour changed to `#4b5563` | `frontend/src/components/ChecklistEditor.vue` |
| The empty column message was 4.10:1 | Stylesheet of the board view | Colour changed to `#4b5563` | `frontend/src/components/BoardView.vue` |
| Success buttons showed their label at 3.77:1 | Computed styles of the rendered buttons | Background changed to `#047857` | `frontend/src/style.css` |
| Only the shared button class had a focus outline, so the other controls relied on the user agent default | Stylesheet inspection | A `:focus-visible` rule now covers links, buttons, inputs, selects, and text areas | `frontend/src/style.css` |
| The accessible name "Modifica motivo blocco" did not contain the visible label "Modifica blocco" | Automated audit | The name matches the visible label | `frontend/src/components/NoteCard.vue` |
| The document language was `en` while the interface is Italian | Document inspection | The document declares `lang="it"` | `frontend/index.html` |
| Focus was lost when the deletion confirmation replaced the card footer | Browser observation | Focus enters the confirmation and returns to the trigger when it is cancelled | `frontend/src/components/NoteCard.vue` |
| Closing the WIP limit, block, and checklist editors returned focus nowhere, and closing the note editor always returned focus to the "Nuova nota" button | Browser observation | The control that opened the editor is captured from the click event and refocused when the editor closes, independently of the way the browser handles the focus of a clicked button | `frontend/src/App.vue` |
| The checklist rename field had no accessible name and received no focus | Browser observation | The field is named and focused | `frontend/src/components/ChecklistEditor.vue` |
| Saving a rename left the focus on the document body, because the rename button is disabled while the request is in flight | Browser observation | The focus returns to the rename button when the request settles | `frontend/src/components/ChecklistEditor.vue` |

After the corrections the automated accessibility audit of the running interface reports no finding and scores the accessibility category with the maximum value.

## Limits

- No assistive technology was run. The verification used the accessibility tree exposed by the browser, the computed styles of the rendered interface, and an automated accessibility audit.
- Contrast was measured for the single light theme of the interface.
- Reflow was measured in frames of 360 and 640 CSS pixels instead of a resized browser window.
- Focus movement is covered by component tests for the deletion confirmation, the closing of an editor, and the checklist rename, including the rename saved while the request is in flight. The remaining focus behaviour was observed in the browser only.
