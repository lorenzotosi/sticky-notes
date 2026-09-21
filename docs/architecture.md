<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# System Architecture & Domain Boundaries

This document defines the architectural structure, module layers, and Domain-Driven Design patterns implemented across Kotlin Multiplatform, Spring Boot, and Vue 3.

---

## 1. Domain Aggregate Model

The Kanban workflow is encapsulated inside a single Bounded Context. `Board` serves as the sole **Aggregate Root**, maintaining direct ownership and operational integrity over `Note` entities and nested `ChecklistItem` value entities.

```mermaid
classDiagram
    class Board {
        +BoardId id
        +Int revision
        +Int wipLimit
        +List~Note~ notes
        +createNote(id, content, color) Result
        +updateNote(id, content, color) Result
        +deleteNote(id) Result
        +moveNote(id, targetStatus, destinationIndex) Result
        +setWipLimit(limit) Result
        +blockNote(id, reason) Result
        +unblockNote(id) Result
        +addChecklistItem(noteId, itemId, label) Result
        +updateChecklistItem(noteId, itemId, label, completed) Result
        +deleteChecklistItem(noteId, itemId) Result
    }

    class Note {
        +NoteId id
        +String content
        +NoteColor color
        +NoteStatus status
        +String? blockedReason
        +List~ChecklistItem~ checklist
    }

    class ChecklistItem {
        +ChecklistItemId id
        +String label
        +Boolean completed
    }

    class NoteStatus {
        <<enumeration>>
        TODO
        DOING
        DONE
    }

    class NoteColor {
        <<enumeration>>
        YELLOW
        BLUE
        GREEN
        PINK
    }

    Board "1" *-- "0..200" Note : owns & guards invariants
    Note "1" *-- "0..20" ChecklistItem : contains
    Note --> NoteStatus
    Note --> NoteColor
```

### Invariant Enforcement Rules
- No Direct Sub-Entity Mutation: External layers cannot mutate `Note` or `ChecklistItem` properties directly. Every state modification passes through `Board` aggregate methods.

- Consistency Boundary: Invariants spanning multiple cards (such as ensuring the number of `DOING` notes does not exceed `wipLimit`) are evaluated synchronously within `Board`.

## 2. Layered Architecture & Module Dependencies
The codebase applies Clean/Hexagonal Architecture dependency rules. The domain core remains completely independent of frameworks, web libraries, and databases.

```mermaid
graph TD
    subgraph FrontendModule ["frontend (Vue 3 / Vite)"]
        VueViews["UI Views & Components"] --> UseBoard["Composable: useBoard"]
        UseBoard --> BoardClient["API Client: fetch"]
        UseBoard -.->|Pre-validation| JsFacade["BoardJsFacade (@JsExport)"]
    end

    subgraph CommonsModule ["commons (Kotlin Multiplatform)"]
        JsFacade --> BoardEngine["BoardEngine"]
        JvmFacade["JvmBoardFacade"] --> BoardEngine
        BoardEngine --> DomainModel["Domain Aggregate: Board, Note, Value Objects"]
    end

    subgraph BackendModule ["backend (Java Spring Boot)"]
        Controller["HTTP Controllers: /api/board/*"] --> BoardService["Application: BoardService"]
        BoardService --> JvmFacade
        BoardService --> BoardRepositoryPort["Application Port: BoardRepository"]
        MongoRepo["Infrastructure: MongoBoardRepository"] -.->|implements| BoardRepositoryPort
    end

    BoardClient -->|HTTP REST + JSON| Controller
    MongoRepo -->|MongoDB Driver / BSON| MongoDb[(MongoDB Document: _id='main')]
```

### Strict Dependency Constraints
- `commons`: Depends only on the Kotlin standard library and `kotlinx.serialization`. It contains no imports or annotations from Spring, MongoDB, Jackson, or Vue.
- `backend`: References `commons` via `JvmBoardFacade`. The domain is isolated from persistence documents; `BoardRepository` bridges application commands to MongoDB.
- `frontend`: Interacts with the backend via REST endpoints and optionally invokes `BoardJsFacade` for client-side pre-validation previews before network dispatch.

## 3. Concurrency Boundary & Conflict Prevention
Concurreny is managed via Optimistic Concurrency Control anchored to the `Board` aggregate:
- The persistent MongoDB document (`_id: "main"`) contains the current `revision` counter.
- Command executions read the board snapshot at revision $R$, evaluate business invariants in memory, and commit via an atomic compare and swap update:$$\text{Filter: } \{\_id: \text{"main"}, revision: R\} \implies \text{Set: } \{\text{snapshot}, revision: R + 1\}$$
- If another request advanced the revision in the interim, the filter returns zero matched documents (`matchedCount == 0`), throwing a `409 REVISION_CONFLICT` without partial writes or lost updates.
