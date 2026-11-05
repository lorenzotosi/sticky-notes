<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Concurrent commands and the board revision

All commands on the board compete for the same persisted revision: moving a note, editing its content, and changing the WIP limit. The behavior is defined in [API contracts](contracts.md#3-server-side-validation-precedence-order) and [ADR 001](adr/001-board-aggregate.md).

## Conditional replacement

Every mutating request supplies `expectedRevision`. The application:

1. loads the persisted snapshot and compares its revision with the request.
2. executes the command through the shared domain. A successful candidate keeps the loaded revision.
3. replaces the complete document with one write filtered by `_id="main"` and the expected revision, advancing the revision by one.
4. treats a non-matching write as `409 REVISION_CONFLICT`, reloads the current revision, and does not replay the command or retry the save.

For a board at revision 7 with WIP limit 1 and two `TODO` notes, both commands may validate an available slot:

| Step | Request A | Request B | Persisted revision |
| --- | --- | --- | --- |
| Read | Loads revision 7 | Loads revision 7 | 7 |
| Validate | Prepares A entering `DOING` | Prepares B entering `DOING` | 7 |
| First replacement | Matches revision 7, saves revision 8 | Candidate still carries revision 7 | 8 |
| Second replacement | Receives HTTP `200` | Cannot match revision 7. Receives HTTP `409` | 8 |

Either request may win. Only the conditional MongoDB replacement is atomic. The earlier read and validation are not. See [MongoDB atomicity guarantees](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/).

## Independent backend instances

A Java monitor coordinates only the callers that share it. It cannot replace the database predicate when two backend instances write the same board. `ConcurrentBoardTest` covers that case with two Spring application contexts in one JVM, separate HTTP ports, and separate MongoDB clients. A shared `CyclicBarrier` pauses both calls at the start of `save()`. Both services validate revision 7 before either write proceeds. MongoDB decides which conditional replacement succeeds.

The tests cover independent service instances and real database writes. They do not launch separate JVM processes or exercise database failover. The execution commands are in the [testing guide](testing.md#concurrent-http-commands-and-mongodb-r3). The recorded results are in [R3 evidence](evidence/R3.md#concurrent-commands-and-conditional-writes).
