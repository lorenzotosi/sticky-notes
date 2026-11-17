<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# ADR 001: Board as the Sole Aggregate Root and Persistence Document

## Context
The application domain requires rules across notes, such as:
1. WIP limit: the `DOING` column cannot exceed `wipLimit` notes.
2. Notes blocked with a reason cannot transition states.
3. Notes cannot enter `DONE` unless all checklist items are completed.
4. Deterministic ordered list across discrete lifecycle columns.

Notes as independent aggregate roots in a separate `notes` MongoDB collection would put the WIP limit across documents. That would need distributed locking, multi-document ACID transactions, or a saga to keep the writes consistent.

## Decision
We model `Board` as the single aggregate root. The domain stores notes and checklist items inside the aggregate. The persistence adapter stores that aggregate as a single document (`_id: "main"`):
- All state changes pass through `Board` methods.
- Startup creates an empty board with an insert-only upsert. Initialization never overwrites or resets an existing board.
- The board's `revision` field carries the optimistic lock. Each write is one atomic compare-and-swap on that field.
- The board holds at most 200 notes and 20 items per note. The document stays well below the 16 MB BSON limit in MongoDB.

## Consequences
### Positive
- WIP constraints and ordering cannot be bypassed by concurrent updates.
- Local development needs no MongoDB replica set and no multi-document transactions.
- Each mutation returns the complete board state in one response.

### Negative / Trade-offs
- Concurrent updates to distinct notes compete for the same board revision. They return `409 REVISION_CONFLICT`. We accept this trade-off for the single-team, local scope.
