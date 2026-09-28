<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Domain rules

The shared Kotlin module owns the board rules. Java and JavaScript use the same command engine.

## Board invariants

A board has between 1 and 20 work-in-progress slots. The default limit is 3.

A board can contain at most 200 notes. Each note ID must be unique within the board.

The board stores notes in a fixed order. `TODO` notes come first, then `DOING`, then `DONE`. The `destinationIndex` field is zero-based within the target status.

A note can move from `TODO` to `DOING`, from `DOING` to `TODO` or `DONE`, and from `DONE` back to `DOING`. A note can also be reordered within its current column. Direct `TODO` to `DONE` and `DONE` to `TODO` transitions are rejected.

A blocked note cannot change status. A note needs a complete checklist before it can move to `DONE`.

## Text normalization

The domain converts CRLF and CR line endings to LF. It then removes leading and trailing whitespace.

Note content cannot be empty and has a limit of 500 UTF-16 code units. A block reason cannot be empty and has a limit of 200 units.

A checklist label cannot be empty and has a limit of 120 units. Each note can contain at most 20 checklist items.

## Command results

Commands do not change an existing board. A successful command returns a new board. A failed command returns an error code and can name the invalid field.

| Code | Meaning |
| --- | --- |
| `BOARD_FULL` | The board already has 200 notes. |
| `CHECKLIST_FULL` | The note already has 20 checklist items. |
| `CHECKLIST_INCOMPLETE` | The note cannot move to `DONE`. |
| `DUPLICATE_ID` | The board or checklist already uses the supplied ID. |
| `INVALID_BLOCK_REASON` | The block reason is empty or too long. |
| `INVALID_CONTENT` | The note content is empty or too long. |
| `INVALID_INDEX` | The target position is outside the target status. |
| `INVALID_LABEL` | The checklist label is empty or too long. |
| `INVALID_REQUEST` | The JavaScript facade could not decode the input JSON. |
| `INVALID_SNAPSHOT` | The command engine rejected the board snapshot. |
| `INVALID_STATUS` | A `DONE` note cannot be blocked. |
| `INVALID_TRANSITION` | The requested status change is not allowed. |
| `INVALID_WIP_LIMIT` | The WIP limit is outside the range from 1 to 20. |
| `ITEM_NOT_FOUND` | The checklist item does not exist. |
| `NOTE_BLOCKED` | A blocked note cannot change status. |
| `NOTE_DONE_READ_ONLY` | A checklist in `DONE` cannot change. |
| `NOTE_NOT_FOUND` | The note does not exist. |
| `WIP_BELOW_OCCUPANCY` | The new limit is below the current `DOING` count. |
| `WIP_LIMIT_REACHED` | The `DOING` column has no free slot. |

## Snapshots and facades

`BoardSnapshot` is the portable board format. Schema version 1 is the only supported version. Revisions cannot be negative.

`BoardEngine.execute` validates a snapshot and applies one command. It preserves the snapshot schema version and revision.

`JvmBoardFacade` gives Java callers static entry points. `evaluateBoardCommand` gives JavaScript callers a JSON entry point.

See the [generated API reference](api/index.html) for types, commands, and function signatures.
