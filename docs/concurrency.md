<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Concurrent commands and the board revision

The board is one consistency boundary: moving a note, editing its content, and changing the WIP limit all compete for the same persisted revision. The accepted behavior is defined in the [API contracts](contracts.md#3-server-side-validation-precedence-order) and [board aggregate decision](adr/001-board-aggregate.md). The R3 HTTP tests exercise this boundary through two backend instances sharing MongoDB.

## Conditional replacement

Every mutating request supplies `expectedRevision`. The application follows this sequence:

1. `BoardServiceImpl` loads the persisted snapshot and checks its revision against the request.
2. `JvmBoardFacade` executes the command through the shared domain. A successful candidate retains the loaded revision; domain validation alone does not reserve WIP capacity in the database.
3. `MongoBoardRepository.save()` validates the candidate and performs one complete replacement filtered by `_id="main"` and the expected revision. The replacement advances the revision once and does not use upsert.
4. A replacement that matches no document returns an empty result. The service reloads the current revision and raises `REVISION_CONFLICT`, mapped to HTTP `409` with `currentRevision` when available. It does not replay the command or retry the save.

For a board at revision 7 with WIP limit 1 and two `TODO` notes, both commands may independently validate an available slot:

| Step | Request A | Request B | Persisted revision |
| --- | --- | --- | --- |
| Read | Loads revision 7 | Loads revision 7 | 7 |
| Validate | Prepares A entering `DOING` | Prepares B entering `DOING` | 7 |
| First replacement | Matches revision 7 and saves revision 8 | Candidate still carries revision 7 | 8 |
| Second replacement | Receives HTTP `200` | Cannot match revision 7; receives HTTP `409` | 8 |

Either request may win. The atomic boundary is the conditional MongoDB replacement, not the earlier read and validation. MongoDB rechecks the write predicate when performing the single-document write; see [MongoDB atomicity guarantees](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/).

Without the revision predicate, both complete replacements can succeed. The later replacement can overwrite an acknowledged command while still leaving a valid board with only one note in `DOING` and revision 8. Verification must therefore inspect request outcomes as well as stored state.

## Independent backend instances

A Java `synchronized` block coordinates callers sharing the same monitor. Separate application objects can use different monitors, and separate processes cannot share an ordinary Java monitor. A local lock therefore cannot replace the database predicate when two backend instances write the same board.

`ConcurrentBoardTest` starts two Spring application contexts in one JVM, with separate HTTP ports and MongoDB clients. Each service receives a spy of its real repository. The test unwraps the Spring proxy before creating the spy and replaces the service's private repository reference with `ReflectionTestUtils`; this fixture depends on that field name and is confined to the test.

A shared `CyclicBarrier` pauses both calls at the beginning of `save()`, after service and domain validation and before either real save proceeds. Once both arrive, the spies call the real repository methods. The barrier controls the overlap, while MongoDB decides which conditional replacement succeeds. A barrier placed only before sending HTTP requests would not guarantee that both services validate revision 7.

The tests exercise independent service instances and real database writes. They do not launch separate JVM processes or exercise database failover, network failures, or the full R3 gate. Execution procedures and the revision-filter mutation check are in the [testing guide](testing.md#concurrent-http-commands-and-mongodb-r3); recorded results are in [R3 evidence](evidence/R3.md#concurrent-commands-and-conditional-writes).
