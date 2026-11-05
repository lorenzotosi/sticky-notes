<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Ubiquitous Language & Glossary

One term per concept across requirements, domain entities, API contracts, and user interface.

| Term | Domain Meaning                                                                                                | Code Representation |
|---|---|---|
| **Board** | Aggregate root that holds the notes, the WIP constraints, and the note order.                     | `stickynotes.domain.Board` |
| **Note** | Domain entity for a sticky note card: content, color, status, checklist.                | `stickynotes.domain.Note` |
| **NoteId** | Value object with a non-empty unique string identifier for a note.                                   | `stickynotes.domain.NoteId` |
| **NoteStatus** | Progression column: `TODO`, `DOING`, or `DONE`.                                                      | `stickynotes.domain.NoteStatus` |
| **NoteColor** | Sticky note tint: `YELLOW`, `BLUE`, `GREEN`, or `PINK`.                                                | `stickynotes.domain.NoteColor` |
| **WIP Limit** | Cap: the maximum number of notes in `DOING` at once.                       | `board.wipLimit: Int` |
| **Blocker** | Impediment that blocks a state change on a note, with a non-empty reason.                  | `note.blockedReason: String?` |
| **Checklist Item**| Subtask of a note, with a label and a completion flag.                                      | `stickynotes.domain.ChecklistItem` |
| **Revision** | Non-negative counter in the board snapshot. Conditional MongoDB writes check it, and each saved command increments it once. | `board.revision: Int` |
| **Destination Index** | Zero-based target insertion index for a note within a column after removal from source.                       | `command.destinationIndex: Int` |
