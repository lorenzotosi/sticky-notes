<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Kanban Domain Requirements

## 1. System Scope & Boundaries

- **Deployment & Access**: The application targets a single shared board in a trusted single-team environment, with Docker Compose, no authentication, and no multiple boards. The [deployment guide](deployment.md) describes what the repository delivers today.

## 2. Functional Requirements

### REQ-01: Note Creation and Defaults
- A new note starts in the `TODO` column. It gets the default color `YELLOW`, no block, an empty checklist, and the last position in `TODO`.
- The backend generates the note identifier (`NoteId`) as a unique UUID.

### REQ-02: Content Length and Normalization
- The code trims leading and trailing whitespace and normalizes `CRLF` and `CR` line breaks to `LF`. Content must then be between 1 and 500 UTF-16 code units.
- Empty or blank content returns `INVALID_CONTENT`.
- Non-BMP emojis count as 2 UTF-16 code units. Length validation is deterministic across the JVM and browser runtimes.

### REQ-03: Board and Note Capacity Limits
- A board holds at most 200 notes across all columns. Creating one more note returns `BOARD_FULL`.
- A note holds at most 20 checklist items. Adding more returns `CHECKLIST_FULL`.

### REQ-04: Note Editing and Color Assignment
- You can update the content and color of any note. Its identity (`NoteId`) and position do not change.
- Allowed colors are `YELLOW`, `BLUE`, `GREEN`, and `PINK`. Any other value returns `INVALID_COLOR`.
- A note in `DONE` can still have its text and color updated.

### REQ-05: Allowed State Transitions
- Notes move exclusively between adjacent lifecycle stages:
    - `TODO` $\rightarrow$ `DOING`
    - `DOING` $\rightarrow$ `TODO`
    - `DOING` $\rightarrow$ `DONE`
    - `DONE` $\rightarrow$ `DOING`
- Direct transitions `TODO` $\rightarrow$ `DONE` and `DONE` $\rightarrow$ `TODO` return `INVALID_TRANSITION`.

### REQ-06: Work In Progress Enforcement
- Entering `DOING` from `TODO`, or reopening from `DONE`, requires fewer `DOING` notes than the board's `wipLimit`.
- If the `DOING` count $\ge$ `wipLimit`, the move returns `WIP_LIMIT_REACHED`.
- Blocked notes in `DOING` continue to occupy WIP capacity.

### REQ-07: In-Column Reordering
- You can reorder notes within the same column. WIP capacity does not change.
- A movement command gives `destinationIndex` in the target column, after the moving note is removed. Range: `0..targetColumnSize`. An out-of-bounds index returns `INVALID_INDEX`. The backend does not clamp it.

### REQ-08: Configurable WIP Limit
- The board's `wipLimit` is an integer between 1 and 20 (default: 3).
- Reducing `wipLimit` below the number of notes in `DOING` returns `WIP_BELOW_OCCUPANCY`.

### REQ-09: Note Blocking and Reasons
- You can block a note in `TODO` or `DOING` with a mandatory reason, 1..200 normalized UTF-16 code units.
- A blocked note cannot change state. The command returns `NOTE_BLOCKED`.
- You can unblock it, update the reason, or reorder it within its column.
- Notes in `DONE` cannot be blocked.

### REQ-10: Completion Checklist Integrity
- Each checklist item contains a mandatory label (1..120 UTF-16 code units) and a boolean `completed` state.
- The backend generates the checklist item identifiers (`ChecklistItemId`).

### REQ-11: Guarded Transition to DONE
- A note moves from `DOING` to `DONE` only when it is not blocked and every checklist item is `completed`.
- If a checklist item is incomplete, the move to `DONE` returns `CHECKLIST_INCOMPLETE`.
- An empty checklist counts as complete.

### REQ-12: Immutability of DONE Checklists & Reopening
- Once a note enters `DONE`, its checklist is read-only. Adding, updating, removing, or toggling items returns `NOTE_DONE_READ_ONLY`.
- To change the checklist of a note in `DONE`, move it back to `DOING` first. The WIP limit applies.
