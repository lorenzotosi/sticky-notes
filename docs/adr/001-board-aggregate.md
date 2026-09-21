<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# ADR 001: Board as the Sole Aggregate Root and Persistence Document

## Context
The application domain requires enforcing cross-card rules, such as:
1. Work-In-Progress limit: the `DOING` column cannot exceed `wipLimit` cards.
2. Notes blocked with a reason cannot transition states.
3. Notes cannot enter `DONE` unless all checklist items are completed.
4. Deterministic ordered list across discrete lifecycle columns.

If individual notes were modeled as independent aggregate roots stored in a separate `notes` MongoDB collection, cross-card rules like WIP limits would require distributed locking, multi-document ACID transactions, or complex saga patterns across separate document writes to prevent race conditions.

## Decision
We model `Board` as the single **Aggregate Root** and persist it as a **single document** (`_id: "main"`) containing an embedded array of notes and checklist items:
- All mutative operations pass through `Board` aggregate methods.
- Concurrency is guarded using optimistic locking with an atomic single-document compare and swap update on the board's `revision` field.
- The collection size is constrained to a maximum of 200 notes and 20 items per note, bounding the document size well below MongoDB's 16 MB BSON threshold.

## Consequences
### Positive
- **Guaranteed Consistency**: WIP constraints and ordering cannot be bypassed by concurrent updates.
- **Transactional Simplicity**: No need for MongoDB replica sets or multi-document transactions in local development.
- **Single Source of Truth**: The complete canonical board state is returned atomically upon mutation.

### Negative / Trade-offs
- **Write Contention**: Concurrent updates to distinct notes compete for the same board revision lock, resulting in `409 REVISION_CONFLICT` rejections. This trade-off is accepted given the single-team, local collaborative scope.
