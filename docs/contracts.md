<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# API Contracts, Precedence Rules, and Error Handling

This document specifies the communication protocol between the Vue frontend and the Spring Boot backend.

---

## 1. Request Protocol & Headers
- **Base URL**: `/api` (same-origin reverse proxy behind Nginx).
- **Content-Type**: `application/json` for all request bodies.
- **Payload Size Limit**: Capped at 64 KiB. Requests exceeding this threshold receive `413 Payload Too Large`.
- **Concurrency Guard**: Every state-mutating HTTP call must supply `expectedRevision: Int` (in JSON body for POST/PUT/PATCH, or as query parameter `?expectedRevision=N` for DELETE).

---

## 2. Error Catalog & HTTP Status Codes

| Code | HTTP Status | Description |
|---|---|---|
| `INVALID_REQUEST` | 400 | Malformed JSON, unparseable body, or no mutable fields provided in PATCH. |
| `INVALID_CONTENT` | 400 | Note content is blank or exceeds 500 UTF-16 code units. |
| `INVALID_COLOR` | 400 | Note color is not one of `YELLOW`, `BLUE`, `GREEN`, `PINK`. |
| `INVALID_STATUS` | 400 | Status not one of `TODO`, `DOING`, `DONE`. |
| `INVALID_INDEX` | 400 | Destination index is negative, fraction, or exceeds target column size. |
| `INVALID_WIP_LIMIT` | 400 | WIP limit is not an integer in range `1..20`. |
| `INVALID_BLOCK_REASON` | 400 | Block reason is blank or exceeds 200 UTF-16 code units. |
| `INVALID_LABEL` | 400 | Checklist item label is blank or exceeds 120 UTF-16 code units. |
| `NOTE_NOT_FOUND` | 404 | Target note ID does not exist in the board. |
| `ITEM_NOT_FOUND` | 404 | Target checklist item ID does not exist on the specified note. |
| `REVISION_CONFLICT` | 409 | `expectedRevision` does not match the database revision (`currentRevision` returned). |
| `WIP_LIMIT_REACHED` | 409 | Cannot move note to `DOING`: active DOING count equals or exceeds `wipLimit`. |
| `WIP_BELOW_OCCUPANCY` | 409 | Cannot reduce WIP limit below current number of notes in `DOING`. |
| `INVALID_TRANSITION` | 409 | Attempted non-adjacent move (e.g. `TODO -> DONE` or `DONE -> TODO`). |
| `NOTE_BLOCKED` | 409 | Blocked note cannot change columns. |
| `CHECKLIST_INCOMPLETE` | 409 | Cannot transition to `DONE`: one or more checklist items are incomplete. |
| `NOTE_DONE_READ_ONLY` | 409 | Cannot block a `DONE` note or mutate its checklist items. |
| `BOARD_FULL` | 409 | Board has reached its maximum capacity of 200 notes. |
| `CHECKLIST_FULL` | 409 | Note has reached its maximum capacity of 20 checklist items. |
| `DATABASE_UNAVAILABLE`| 503 | MongoDB connectivity failure (no sensitive connection strings leaked). |

---

## 3. Server-Side Validation Precedence Order

When processing any mutating command, the backend strictly applies validations in the following deterministic sequence:

1. **Structural Validation**: JSON parsing, unknown field rejection, basic type and enum range checks $\rightarrow$ `400 BadRequest`.
2. **Persistence State Load**: Board is retrieved from MongoDB. If DB is down $\rightarrow$ `503 ServiceUnavailable`.
3. **Concurrency Check**: If `expectedRevision != board.revision` $\rightarrow$ `409 REVISION_CONFLICT` (returns `currentRevision`).
4. **Entity Presence**: Verify that target `NoteId` (and `ChecklistItemId`) exist $\rightarrow$ `404 NotFound`.
5. **Domain Invariant Rules** (evaluated via `BoardEngine`):
    - `DONE` immutability check (`NOTE_DONE_READ_ONLY`).
    - Blocker check (`NOTE_BLOCKED`).
    - Transition validity (`INVALID_TRANSITION`).
    - Checklist completion check for `DONE` (`CHECKLIST_INCOMPLETE`).
    - WIP limit check (`WIP_LIMIT_REACHED` / `WIP_BELOW_OCCUPANCY`).
    - Destination index bound check (`INVALID_INDEX`).
6. **Atomic Persistence (CAS)**: Atomic update with filter `_id="main"` and `revision=expectedRevision`. If matched count is 0 $\rightarrow$ `409 REVISION_CONFLICT`.
