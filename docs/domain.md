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

When updating or deleting a checklist item, note and item presence are checked before `DONE` immutability, so an absent item returns `ITEM_NOT_FOUND`. For movement, blocker, transition, and checklist checks precede WIP; WIP precedes destination index bounds. Reordering within the current column does not check WIP capacity.

## Snapshots and facades

`BoardSnapshot` is the portable board format. Schema version 1 is the only supported version. Rehydration rejects negative revisions, duplicate IDs, non-canonical note order, WIP limits below current occupancy, and `DONE` notes that are blocked or have incomplete checklists. Invalid state is not repaired or replaced with an empty board.

`BoardEngine.execute` validates a snapshot and applies one command. It preserves the snapshot schema version and revision.

`JvmBoardFacade` gives Java callers static entry points. `evaluateBoardCommand` gives JavaScript callers a JSON entry point.

See the generated API reference in `build/docs/api/`, produced by `./gradlew documentation`, for types, commands, and function signatures.
