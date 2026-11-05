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
