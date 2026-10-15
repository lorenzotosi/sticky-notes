<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# API Contracts, Precedence Rules, and Error Handling

This document specifies the accepted communication protocol between the Vue frontend and the Java backend. The Spring Boot backend exposes board, note, movement, WIP, blocker, and checklist endpoints through `BoardService` and a [board repository with conditional writes](architecture.md#4-concurrency-boundary). The validation requirements below describe the accepted contract; the [testing guide](testing.md) identifies the layers exercised by each test suite.

`GET /actuator/health/liveness` returns HTTP `200` with `{"status":"UP"}` when the application is running, including when MongoDB is unavailable. The aggregate endpoint is `GET /actuator/health`; `GET /actuator/health/readiness` includes application readiness and MongoDB connectivity, returning HTTP `503` when MongoDB is unavailable. Health responses omit component and error details. See [backend runtime configuration](development.md#backend-runtime) for startup and health-check instructions.

After MongoDB restarts with its data intact, readiness returns to `200 UP` and reads and writes resume without restarting the backend. Reconnection can take several seconds. Commands rejected during the outage leave the board and revision unchanged; structural errors still return `400` before database access. The [manual recovery procedure](testing.md#mongodb-outage-and-recovery-r3) verifies these boundaries.

---

## 1. Request Protocol & Headers
- **Base URL**: `/api` (same-origin reverse proxy behind Nginx).
- **Content-Type**: `application/json` for all request bodies.
- **JSON Structure**: POST, PUT, and PATCH command bodies must be objects containing only declared fields. Non-null values retain their JSON types: numeric strings, fractional integer tokens, and numeric enum ordinals return `400`. Integers must fit a Java `int`. Structural failures use `INVALID_REQUEST`; invalid indices, WIP limits, and enum names retain their field-specific codes. For note creation and updates, null means omitted: a missing or null creation color defaults to `YELLOW`, and PATCH fields stay unchanged. A note PATCH needs at least one non-null mutable field. Outside checklist commands, controllers check required values and reject missing, null, or negative revisions with `INVALID_REVISION`. Checklist commands reject explicit null values with `INVALID_REQUEST`.
- **Payload Size Limit**: POST, PUT, PATCH, and DELETE bodies under `/api` are capped at 64 KiB (65,536 bytes), including JSON whitespace. Exactly 65,536 bytes is allowed. Larger bodies receive HTTP `413` with `PAYLOAD_TOO_LARGE` before JSON decoding or any service call. The filter checks the received bytes, including chunked requests without `Content-Length`; UTF-8 characters can occupy more than one byte. Oversized requests leave the persisted board and revision unchanged.
- **Concurrency Guard**: Every state-mutating HTTP call must supply `expectedRevision: Int` (in JSON body for POST/PUT/PATCH, or as query parameter `?expectedRevision=N` for DELETE).

---

## 2. Error Catalog & HTTP Status Codes

| Code | HTTP Status | Description |
|---|---|---|
| `INVALID_REQUEST` | 400 | Malformed JSON, non-object bodies, unknown fields, wrong JSON types, explicit null in checklist commands, or no mutable values provided in PATCH. |
| `INVALID_CONTENT` | 400 | Note content is blank or exceeds 500 UTF-16 code units. |
| `INVALID_COLOR` | 400 | Note color is not one of `YELLOW`, `BLUE`, `GREEN`, `PINK`. |
| `INVALID_STATUS` | 400 | Unsupported status name, or an attempt to block a note in DONE. |
| `INVALID_INDEX` | 400 | Destination index is negative, fraction, or exceeds target column size. |
| `INVALID_WIP_LIMIT` | 400 | WIP limit is not an integer in range `1..20`. |
| `INVALID_BLOCK_REASON` | 400 | Block reason is blank or exceeds 200 UTF-16 code units. |
| `INVALID_LABEL` | 400 | Checklist item label is blank or exceeds 120 UTF-16 code units. |
| `INVALID_REVISION` | 400 | expectedRevision is missing or negative, or null outside checklist commands; wrong JSON token types and checklist nulls use INVALID_REQUEST. |
| `NOTE_NOT_FOUND` | 404 | Target note ID does not exist in the board. |
| `ITEM_NOT_FOUND` | 404 | Target checklist item ID does not exist on the specified note. |
| `REVISION_CONFLICT` | 409 | `expectedRevision` does not match the database revision (`currentRevision` returned). |
| `WIP_LIMIT_REACHED` | 409 | Cannot move note to `DOING`: active DOING count equals or exceeds `wipLimit`. |
| `WIP_BELOW_OCCUPANCY` | 409 | Cannot reduce WIP limit below current number of notes in `DOING`. |
| `INVALID_TRANSITION` | 409 | Attempted non-adjacent move (e.g. `TODO -> DONE` or `DONE -> TODO`). |
| `NOTE_BLOCKED` | 409 | Blocked note cannot change columns. |
| `CHECKLIST_INCOMPLETE` | 409 | Cannot transition to `DONE`: one or more checklist items are incomplete. |
| `NOTE_DONE_READ_ONLY` | 409 | Cannot mutate checklist items on a `DONE` note. Reopen in `DOING` first, respecting WIP capacity. |
| `BOARD_FULL` | 409 | Board has reached its maximum capacity of 200 notes. |
| `CHECKLIST_FULL` | 409 | Note has reached its maximum capacity of 20 checklist items. |
| `PAYLOAD_TOO_LARGE` | 413 | Command request body exceeds 65,536 bytes. |
| `DATABASE_UNAVAILABLE`| 503 | MongoDB connectivity failure (no sensitive connection strings leaked). |
| `INVALID_SNAPSHOT` | 500 | Persisted board state cannot be rehydrated as a valid domain snapshot. |
| `REVISION_OVERFLOW` | 500 | The board revision cannot be incremented within the Java int range. |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected failure with a neutral message and no exception details. |

Server failures are logged with the error code and HTTP status. Database unavailability uses `WARN`; other failures use `ERROR`, with the exception type for unexpected errors. Logs omit request bodies, credentials, exception messages, and stack traces.

---

## 3. Server-Side Validation Precedence Order

When processing any mutating command, the backend strictly applies validations in the following deterministic sequence:

The HTTP body-size filter runs before the sequence below. An oversized body returns `413 PAYLOAD_TOO_LARGE` even when its JSON would otherwise be malformed or MongoDB is unavailable.

1. **Structural Validation**: JSON parsing, unknown field rejection, basic type and enum range checks $\rightarrow$ `400 BadRequest`.
2. **Persistence State Load**: Board is retrieved from MongoDB. If DB is down $\rightarrow$ `503 ServiceUnavailable`.
   Persisted snapshots are rehydrated only through the shared domain mapper. It rejects unsupported schema versions, negative revisions, duplicate IDs, non-canonical note order, `DONE` notes that are blocked or have incomplete checklists, and WIP limits below current occupancy. Invalid persisted state produces `500 INVALID_SNAPSHOT`; it is never replaced with an empty board or saved silently.
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

## 4. Checklist endpoints (R3)

`ChecklistController` exposes `POST /api/board/notes/{id}/checklist`, `PATCH /api/board/notes/{id}/checklist/{itemId}`, and `DELETE /api/board/notes/{id}/checklist/{itemId}`. Request and response schemas are defined in [OpenAPI](openapi.yaml).

POST accepts `label` and `expectedRevision` and returns HTTP `201` with `{board, createdItemId}`. The service generates the item UUID; the controller obtains that ID from the appended item on the target note in the saved board.

PATCH accepts `label` and/or `completed` with `expectedRevision`, and returns HTTP `200` with the saved board. Omitted fields stay unchanged. At the Java service boundary, a null argument represents an omitted field; `Boolean` preserves the distinction between an omitted completion value and `false`. Explicit JSON null values are rejected. DELETE takes `expectedRevision` from the query and returns HTTP `200` with the saved board.

PATCH and DELETE require a non-blank `itemId`. Whitespace-only values return HTTP `400 INVALID_REQUEST` with `field: "itemId"` before calling the service. Whitespace follows the shared Kotlin domain definition, including non-breaking spaces. Other unknown item IDs produce `404 ITEM_NOT_FOUND` through the service.

The two checklist request records use Jackson creator methods to inspect the original JSON types before constructing the typed request. Checklist bodies must be objects with only the declared fields. A present `label` must be a JSON string, `completed` a JSON boolean, and `expectedRevision` an integer token within the Java int range. Numeric strings, floating-point tokens, explicit null values, and unknown fields return HTTP `400 INVALID_REQUEST` without calling the service.

The controller rejects missing or negative revisions with `INVALID_REVISION`, a missing creation label with `INVALID_LABEL`, and a PATCH with no mutable fields with `INVALID_REQUEST`. An invalid query parameter type returns HTTP `400`; for `expectedRevision`, its code is `INVALID_REVISION`. Domain checks and conditional persistence use the existing service flow. The [checklist HTTP tests](testing.md#checklist-http-tests-r3) exercise the Spring HTTP server, JSON decoding, and error mapping with a mocked service. Domain combinations and conditional-save behavior remain in the [application service tests](testing.md#backend-application-service-unit-tests). The [MongoDB HTTP integration tests](testing.md#checklist-http-and-mongodb-integration-tests-r3) exercise persisted commands and document preservation on rejection. The completion and reopening journey is exercised as one continuous HTTP scenario against the real database, including rejection at full WIP and success after freeing a slot.

Checklist error messages explain invalid labels, missing items, checklist capacity, and incomplete completion. `NOTE_DONE_READ_ONLY` asks the caller to reopen the note before changing its checklist; it does not forbid movement from `DONE` to `DOING`. Error responses include `field` only when the failure identifies one and `currentRevision` only when available for a revision conflict. A stale request returns the loaded revision; a failed conditional save returns the revision observed by the conflict reload. Unavailable metadata is omitted. Infrastructure errors return neutral messages without exception details.

## 5. Snapshot response shape

HTTP snapshots include every field required by OpenAPI even when its value equals a Kotlin constructor default. An empty board includes `id`, `schemaVersion`, `revision`, `wipLimit`, and `notes`. Each note includes `blockedReason`, using JSON null when unblocked, and `checklist`, using an empty array when it has no items. The response shape is the same for GET, direct mutation responses, and snapshots nested in creation responses.

The server uses Jackson for these HTTP responses. Its converter configuration prevents Kotlin Serialization's default-value omission from changing the shape of direct snapshot responses. The shared domain retains its own JSON configuration for JVM/JS commands and snapshots.
