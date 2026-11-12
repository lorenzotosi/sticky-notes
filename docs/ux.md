<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# User Experience & Interface States Specification

The Vue app loads and changes board state through the HTTP API.

## 1. Board Layout & Visual Hierarchy

The board shows a 3-column layout: `TODO`, `DOING`, `DONE`. On desktop, columns sit side by side. On viewports of 768 pixels or less, columns stack vertically. Note cards stay fully visible without horizontal scrolling.

## 2. Interaction States & Transitions

### 2.1 Allowed In-Card Actions by Column State

| Action | `TODO`                                       | `DOING` | `DONE`                                 |
| :--- |:---------------------------------------------| :--- |:---------------------------------------|
| **Edit Text / Color** | Allowed                                      | Allowed | Allowed                                |
| **Reorder Up / Down** | Allowed                                      | Allowed | Allowed                                |
| **Move to DOING** | Allowed (if unblocked and DOING < WIP limit) | N/A | Allowed (Reopen, if DOING < WIP limit) |
| **Move to TODO** | N/A                                          | Allowed (if unblocked) | Rejected (`INVALID_TRANSITION`)        |
| **Move to DONE** | Rejected (`INVALID_TRANSITION`)              | Allowed (if unblocked and checklist complete) | N/A                                    |
| **Block / Unblock** | Allowed                                      | Allowed | Controls unavailable. Blocking returns `INVALID_STATUS`. |
| **Edit Checklist** | Allowed                                      | Allowed | Read-only                              |
| **Delete Note** | Allowed (with confirmation)                  | Allowed (with confirmation) | Allowed (with confirmation)            |

## 3. Request States and Conflict Handling

The interface never changes the board before the server confirms a command. While a command runs, the controls that would send another command stay disabled. A polite live region reports loading, saving, and error states.

On `409 REVISION_CONFLICT`, the app:

- keeps the note text or checklist input in a local draft buffer;
- reloads the board from the server;
- shows an assertive `aria-live` notice that another window changed the board and that the input was preserved;
- leaves resubmission to the user.

Automatic retry is forbidden. The user reviews the reloaded board and submits the draft again against the new `currentRevision`.

## 4. Accessibility and Responsive Behaviour

### 4.1 Keyboard, Names, and Focus

All actions use buttons or form controls. No operation requires drag and drop or a pointer. Every control is reachable in document order, exposes an accessible name through visible text, an `aria-label`, or an associated label, and shows a visible focus indicator. A disabled control is not focusable.

The four note colours form a radio group inside a `fieldset` with a `legend`. Icon-only controls, the reorder arrows, the checklist checkbox, and the conflict-notice dismissal carry an `aria-label`.

Focus behaviour:

- Opening an editor moves focus into its first field. Closing the editor returns focus to the control that opened it.
- Cancelling a deletion returns focus to the delete control. Confirming it moves focus to the first remaining note of the column, or to the "Nuova nota" button when the column is empty.
- Saving a checklist rename returns focus to the rename button when the request settles.

### 4.2 Colour, Contrast, and Messages

The four note colours (`YELLOW`, `BLUE`, `GREEN`, `PINK`) are always available as text, and a blocked note always shows its reason, so no state depends on colour alone. Note text, colour badges, checkboxes, counters, buttons, alerts, and the conflict notice meet the WCAG AA ratio of 4.5:1 for normal text. The focus indicator meets 3:1.

### 4.3 Reflow and Zoom

At 360 pixels the board shows its stacked column without horizontal scrolling or clipped text. At 200% zoom, or 640 CSS pixels, the layout stays usable without clipping. Long text wraps instead of being truncated.
