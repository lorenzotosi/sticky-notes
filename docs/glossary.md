<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Ubiquitous Language & Glossary

Consistent terminology applied across requirements, domain entities, API contracts, and user interface.

| Term | Domain Meaning                                                                                                | Code Representation |
|---|---------------------------------------------------------------------------------------------------------------|---|
| **Board** | Aggregate root managing the collection of notes, WIP constraints, and canonical ordering.                     | `stickynotes.domain.Board` |
| **Note** | Domain entity representing a sticky note task card with content, color, status, and checklist.                | `stickynotes.domain.Note` |
| **NoteId** | Value object encapsulating a non-empty unique string identifier for a note.                                   | `stickynotes.domain.NoteId` |
| **NoteStatus** | Discrete progression column: `TODO`, `DOING`, or `DONE`.                                                      | `stickynotes.domain.NoteStatus` |
| **NoteColor** | Visual sticky note tint: `YELLOW`, `BLUE`, `GREEN`, or `PINK`.                                                | `stickynotes.domain.NoteColor` |
| **WIP Limit** | Work-In-Progress cap: maximum allowed notes concurrently present in the `DOING` column.                       | `board.wipLimit: Int` |
| **Blocker** | Operational impediment preventing state change on a note, accompanied by a non-empty reason.                  | `note.blockedReason: String?` |
| **Checklist Item**| Subtask within a note holding a descriptive label and a completion flag.                                      | `stickynotes.domain.ChecklistItem` |
| **Revision** | Non-negative monotonic integer counter on `Board` used for optimistic concurrency control (compare and swap). | `board.revision: Int` |
| **Destination Index** | Zero-based target insertion index for a note within a column after removal from source.                       | `command.destinationIndex: Int` |
