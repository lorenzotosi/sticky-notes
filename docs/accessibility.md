<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Accessibility verification

The Kanban interface must be operable with the keyboard alone, name every control, keep the four note colours readable, and stay usable at 200% zoom and at a 360 pixel viewport. The required behaviour is in [UI and interaction design](ux.md).

## Verification environment

The interface ran from the Vite development server on `http://localhost:5173`, with the backend on `127.0.0.1:8080` and MongoDB on `127.0.0.1:27019`. The review used Google Chrome on macOS with a board containing all four colours, a blocked note, a note in `DOING` with a completed checklist item, a note in `DONE`, and a long note with an unbroken token.

Contrast ratios use the WCAG relative luminance formula applied to the colours read from the rendered page with `getComputedStyle`. Reflow was measured at 360 pixels and at 640 pixels, the CSS width of a 1280 pixel viewport at 200% zoom.

## Keyboard reachability and focus indicator

All interactive elements are reachable in document order without a pointer, and each one exposes an accessible name through visible text, an `aria-label`, or an associated label. Disabled controls, such as the reorder buttons at the ends of a column and the movement control of a blocked note, are not focusable. Every focusable control shows a visible focus indicator with a contrast of at least 3:1 on the note colours, the application background, the columns, and the white panels.

## Focus management

| Interaction | Observed result |
| --- | --- |
| Opening an editor | Focus moves into its first field |
| Closing an editor | Focus returns to the control that opened it |
| Cancelling a deletion | Focus returns to the delete control |
| Confirming a deletion | Focus moves to the first remaining note of the column, or to the "Nuova nota" button when the column is empty |
| Saving a checklist rename | Focus returns to the rename button when the request settles |

The editors are inline panels, and a live region reports loading, saving, and error states.

## Names and labels

- Every input has a label, visible or visually hidden.
- The four note colours are a radio group inside a `fieldset` with a `legend`.
- Icon-only controls, the reorder arrows, the checklist checkbox, and the conflict notice dismissal carry an `aria-label`.
- Note colour is always available as text, and a blocked note always shows its reason, so no information depends on colour alone.
- The document language is `it`.

## Contrast

All note text, colour badges, checklist labels, counters, buttons, alerts, and the conflict notice meet the WCAG AA ratio of 4.5:1 for normal text. The focus indicator meets 3:1. Measurements used the rendered styles of the interface.

## Reflow, zoom, and long text

At 360 pixels the board shows one column of 328 pixels without horizontal scrolling or clipped text. At 640 pixels, the CSS width at 200% zoom, it shows one column without clipping. The long note wraps instead of being truncated.

## Limits

- No assistive technology was run; the verification used the browser accessibility tree, computed styles, and an automated audit.
- Contrast was measured for the single light theme.
- Reflow was measured in frames of 360 and 640 CSS pixels instead of a resized browser window.
