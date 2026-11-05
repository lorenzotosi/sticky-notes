<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# API Contracts, Precedence Rules, and Error Handling

The Vue frontend and the Java backend communicate through the REST API under `/api`. The backend exposes board, note, movement, WIP, blocker, and checklist endpoints through `BoardService` and the conditional-write repository described in [architecture](architecture.md#4-concurrency-boundary). The [OpenAPI document](openapi.yaml) defines the request and response schemas.

`GET /actuator/health/liveness` returns `200 {"status":"UP"}` while the application runs, including when MongoDB is unavailable. `GET /actuator/health/readiness` also checks MongoDB and returns `503` when it is unreachable. Health responses omit component details.

## 1. Request Protocol & Headers

- **Base URL**: `/api`, behind the same-origin Nginx proxy.
- **Content-Type**: `application/json` for every request body.
- **JSON structure**:
  - Bodies must be objects containing only declared fields.
  - Numeric strings, fractional integers, and numeric enum ordinals return `400`.
  - Integers must fit a Java `int`.
  - Structural failures use `INVALID_REQUEST`. Invalid indices, WIP limits, and enum names keep their specific codes.
  - For note creation and updates, a missing or null optional field means omitted. Creation defaults to `YELLOW`. PATCH leaves the value unchanged.
  - A note PATCH needs at least one non-null mutable field.
- **Payload size limit**: The API caps POST, PUT, PATCH, and DELETE bodies under `/api` at 65,536 bytes. Chunked requests without `Content-Length` count too. Larger bodies get `413 PAYLOAD_TOO_LARGE` before JSON decoding or any service call. Oversized requests leave the board and revision unchanged.
- **Concurrency guard**: every state-mutating call supplies `expectedRevision` (in the body for POST/PUT/PATCH, as `?expectedRevision=N` for DELETE).

## 2. Error Catalog & HTTP Status Codes

| Code | HTTP Status | Description |
|---|---|---|
| `INVALID_REQUEST` | 400 | Malformed JSON, non-object bodies, unknown fields, wrong JSON types, explicit null in checklist commands, or a PATCH without mutable values. |
| `INVALID_CONTENT` | 400 | Note content is blank or exceeds 500 UTF-16 code units. |
| `INVALID_COLOR` | 400 | Note color is not `YELLOW`, `BLUE`, `GREEN`, or `PINK`. |
| `INVALID_STATUS` | 400 | Unsupported status name, or an attempt to block a note in `DONE`. |
| `INVALID_INDEX` | 400 | Destination index is negative, fractional, or exceeds the target column size. |
| `INVALID_WIP_LIMIT` | 400 | WIP limit is not an integer in `1..20`. |
| `INVALID_BLOCK_REASON` | 400 | Block reason is blank or exceeds 200 UTF-16 code units. |
| `INVALID_LABEL` | 400 | Checklist label is blank or exceeds 120 UTF-16 code units. |
| `INVALID_REVISION` | 400 | `expectedRevision` is missing, negative, or null outside checklist commands. |
| `NOTE_NOT_FOUND` | 404 | Target note does not exist. |
| `ITEM_NOT_FOUND` | 404 | Target checklist item does not exist on the note. |
| `REVISION_CONFLICT` | 409 | `expectedRevision` does not match the stored revision (`currentRevision` returned). |
| `WIP_LIMIT_REACHED` | 409 | Cannot enter `DOING`: active count equals or exceeds `wipLimit`. |
| `WIP_BELOW_OCCUPANCY` | 409 | Cannot reduce the WIP limit below the current `DOING` count. |
| `INVALID_TRANSITION` | 409 | Non-adjacent move, such as `TODO -> DONE` or `DONE -> TODO`. |
| `NOTE_BLOCKED` | 409 | Blocked note cannot change columns. |
| `CHECKLIST_INCOMPLETE` | 409 | Cannot enter `DONE` with incomplete checklist items. |
| `NOTE_DONE_READ_ONLY` | 409 | Cannot mutate the checklist of a `DONE` note. |
| `BOARD_FULL` | 409 | Board reached 200 notes. |
| `CHECKLIST_FULL` | 409 | Note reached 20 checklist items. |
| `PAYLOAD_TOO_LARGE` | 413 | Body exceeds 65,536 bytes. |
| `DATABASE_UNAVAILABLE` | 503 | MongoDB connectivity failure. |
| `INVALID_SNAPSHOT` | 500 | Persisted state cannot be rehydrated as a valid snapshot. |
| `REVISION_OVERFLOW` | 500 | The revision cannot be incremented within the Java `int` range. |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected failure with a neutral message. |

Errors include `field` only when the failure identifies one. `currentRevision` appears only for a revision conflict that can report it. Logs record the error code and HTTP status. They omit request bodies, credentials, exception messages, and stack traces.

## 3. Server-Side Validation Precedence Order

The backend validates a mutating command in this order. The body-size filter runs before step 1.

1. **Structural validation**: JSON parsing, unknown field rejection, scalar types, and enum range → `400`.
2. **Persistence load**: the backend reads the board from MongoDB. An unavailable database returns `503`. The shared domain mapper rehydrates persisted state, and invalid state returns `500 INVALID_SNAPSHOT`. The backend never replaces invalid state with an empty board and never saves it silently.
3. **Concurrency check**: `expectedRevision != board.revision` → `409 REVISION_CONFLICT` with `currentRevision`.
4. **Entity presence**: the target note and checklist item must exist → `404`.
5. **Domain invariants** (`BoardEngine`): `DONE` immutability, blocker, transition validity, checklist completion, WIP limit, and destination index bounds.
6. **Conditional save**: one replacement filtered by `_id="main"` and `revision=expectedRevision`. A match count of zero → `409 REVISION_CONFLICT`.

## 4. Checklist endpoints (R3)

`ChecklistController` exposes:

| Method | Path | Success |
| --- | --- | --- |
| `POST` | `/api/board/notes/{id}/checklist` | `201 {board, createdItemId}` |
| `PATCH` | `/api/board/notes/{id}/checklist/{itemId}` | `200` with the saved board |
| `DELETE` | `/api/board/notes/{id}/checklist/{itemId}` | `200` with the saved board |

POST accepts `label` and `expectedRevision`. PATCH accepts `label` and/or `completed` with `expectedRevision`. Omitted fields stay unchanged. Explicit null values are rejected. DELETE takes `expectedRevision` from the query.

A `{itemId}` that is blank or whitespace-only returns `400 INVALID_REQUEST` with `field: "itemId"` before the service call. Unknown item IDs return `404 ITEM_NOT_FOUND`. The service generates the item UUID. The controller returns it from the appended item on the saved board.

## 5. Snapshot response shape

HTTP snapshots include every field required by OpenAPI, even when the value equals a Kotlin default. An empty board includes `id`, `schemaVersion`, `revision`, `wipLimit`, and `notes`. Each note includes `blockedReason` (JSON null when unblocked) and `checklist` (empty array when it has no items). The shape is the same for GET, direct mutation responses, and snapshots nested in creation responses.
