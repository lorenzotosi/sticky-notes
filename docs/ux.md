<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# User Experience & Interface States Specification

This document defines the required layout, user interactions, accessibility behavior, and state transitions for the Sticky Notes Kanban board. The Vue interface loads and changes persistent board state through the HTTP API. Diagrams and card layouts describe the required design; the [testing guide](testing.md) identifies the implemented verification scope, and [Accessibility verification](accessibility.md) records the verified results of the accessibility and responsive requirements.

---

## 1. Board Layout & Visual Hierarchy

The interface presents a responsive 3-column layout (`TODO`, `DOING`, `DONE`). On desktop screens, columns are side-by-side; on viewports of 768 pixels or less, columns stack vertically and the note cards remain fully visible without horizontal scrolling.


## 2. Interaction States & Transitions

### 2.1 Allowed In-Card Actions by Column State

| Action | `TODO`                                       | `DOING` | `DONE`                                 |
| :--- |:---------------------------------------------| :--- |:---------------------------------------|
| **Edit Text / Color** | Allowed                                      | Allowed | Allowed                                |
| **Reorder Up / Down** | Allowed                                      | Allowed | Allowed                                |
| **Move to DOING** | Allowed (if unblocked and DOING < WIP Limit) | N/A | Allowed (Reopen, if DOING < WIP Limit) |
| **Move to TODO** | N/A                                          | Allowed (if unblocked) | Rejected (`INVALID_TRANSITION`)        |
| **Move to DONE** | Rejected (`INVALID_TRANSITION`)              | Allowed (if unblocked & checklist complete) | N/A                                    |
| **Block / Unblock** | Allowed                                      | Allowed | Controls unavailable; blocking returns `INVALID_STATUS`. |
| **Edit Checklist** | Allowed                                      | Allowed | Read-Only                              |
| **Delete Note** | Allowed (with confirmation)                  | Allowed (with confirmation) | Allowed (with confirmation)            |

---
