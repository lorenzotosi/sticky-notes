<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Testing

`commons/src/commonTest` contains domain scenarios shared by the JVM and ChromeHeadless targets. `browserDomainTest` is an alias for `jsBrowserTest`. The backend also has application service and persistence unit tests, and an HTTP startup and liveness test.

This guide explains how to execute the implemented tests and which [domain rules](domain.md) they cover. [R1 evidence](evidence/R1.md) records the observed domain and consumer results.

Install JDK 21 and Google Chrome before running the browser tests. Point `CHROME_BIN` to the Chrome executable if Karma cannot find it:

```sh
# macOS
export CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# Linux (after installing Google Chrome)
export CHROME_BIN="$(command -v google-chrome)"

./gradlew :commons:jvmTest :commons:browserDomainTest
```

On Windows PowerShell, set `$env:CHROME_BIN` to the full path of `chrome.exe` (normally under `C:\Program Files\Google\Chrome\Application\`) and run `.\gradlew.bat :commons:jvmTest :commons:browserDomainTest`. Set the Gradle JVM in IntelliJ IDEA to JDK 21.

CI uses the Chrome installation on the [GitHub Actions Ubuntu runner](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md). The quality workflow checks `google-chrome --version`, exports `CHROME_BIN`, and runs `fullBuild`, which includes both common test targets. Compare `commons/build/test-results/jvmTest/` and `commons/build/test-results/jsBrowserTest/`: both must show the shared scenarios executed, with no skipped tests. The workflow uploads these reports even when a test fails.

To check that both targets detect a core regression, temporarily change an expected value in a shared domain test to an incorrect value. Run `./gradlew :commons:jvmTest :commons:browserDomainTest --rerun-tasks --continue` and confirm that test fails in both reports. Restore the assertion and rerun the command; both targets must pass.

## Domain Invariant Coverage Matrix

The shared multiplatform suite (`commons/src/commonTest`) and frontend interop suite (`frontend/src/domain.test.js`) cover the implemented shared-domain boundaries and edge cases:

| Domain Rule & Boundary | Expected Behavior | Verification Target |
| :--- | :--- | :--- |
| **Empty or Whitespace Content** | Reject with `INVALID_CONTENT` without mutating state. | `NoteTest`, `BoardTest`, `domain.test.js` |
| **Content Length Boundaries (1, 500, 501)** | Allow 1 and 500 UTF-16 units; reject 501 after trimming and newline normalization. | `NoteTest`, `InvariantBoundaryTest`, `domain.test.js` |
| **Astral Plane Unicode & Internal Spaces** | Non-BMP emoji counts as 2 UTF-16 units; preserve internal whitespace across JVM/JS. | `NoteTest`, `InvariantBoundaryTest` |
| **Entity Identity & Defensive Copies** | Equality based strictly on identifier; mutating external collections does not affect the board. | `NoteTest` |
| **Note Creation & Deletion Lifecycle** | New notes enter `TODO` as `YELLOW`; deletion removes target and compacts column order. | `BoardTest` |
| **Identifier Uniqueness & Existence** | Reject duplicate IDs on creation; return `NOTE_NOT_FOUND` on missing target note. | `BoardTest` |
| **WIP Capacity on State Transition** | Moving into `DOING` rejected with `WIP_LIMIT_REACHED` if `DOING` count equals limit. | `MovementTest`, `domain.test.js` |
| **State Transition Rules** | Allow only adjacent transitions (`TODO` <-> `DOING` <-> `DONE`); reject illegal moves with `INVALID_TRANSITION`. | `MovementTest`, `InvariantBoundaryTest` |
| **In-Column Reordering** | Reordering notes within the same column succeeds without consuming or checking WIP. | `MovementTest`, `InvariantBoundaryTest` |
| **WIP Limit Configuration Range** | Limit must be between 1 and 20; reject values below current active `DOING` occupancy. | `MovementTest`, `InvariantBoundaryTest` |
| **Blocker Protection** | Blocked notes reject all column changes (`NOTE_BLOCKED`) while permitting in-column reordering. | `BlockerAndChecklistTest` |
| **Blocker Reason Boundaries** | Mandatory reason between 1 and 200 UTF-16 units; unblocking clears the reason. | `BlockerAndChecklistTest` |
| **DONE Note Constraints** | Notes in `DONE` cannot be blocked, but allow text and color updates. | `BlockerAndChecklistTest` |
| **Checklist Completion Guard** | Moving to `DONE` rejected with `CHECKLIST_INCOMPLETE` if any item is incomplete. | `BlockerAndChecklistTest`, `domain.test.js` |
| **Empty & Completed Checklist Transition** | Empty checklist or fully completed items allow transition to `DONE`. | `BlockerAndChecklistTest` |
| **DONE Checklist Immutability** | All checklist mutations on `DONE` notes are rejected with `NOTE_DONE_READ_ONLY`. | `BlockerAndChecklistTest` |
| **Reopening Completed Notes** | Moving from `DONE` back to `DOING` verifies available WIP capacity. | `BlockerAndChecklistTest` |
| **Capacity Boundaries** | Board cap of 200 notes (`BOARD_FULL`) and note cap of 20 items (`CHECKLIST_FULL`). | `BoardTest`, `BlockerAndChecklistTest` |
| **Destination Index Range** | Reject indices outside `0..targetSize` with `INVALID_INDEX` without automatic clamping. | `MovementTest`, `InvariantBoundaryTest` |
| **JavaScript Facade & Serialization Failures** | Reject malformed JSON and unknown command structures with `INVALID_REQUEST`. | `domain.test.js` |

## Verified Defect Regressions

To confirm that the test suite detects logical defects across the core model and interop layers, three deliberate regressions were introduced and verified:

1. **WIP Condition Weakening (`<=` instead of `<`)**:
    - *Mutation*: Allowing transition when active `DOING` count is less than or equal to `wipLimit`.
    - *Detection*: Caught by `MovementTest` and `domain.test.js` (`WIP_LIMIT_REACHED`).

2. **Checklist Validation Bypass on DONE**:
    - *Mutation*: Bypassing checklist completion check when transitioning into `DONE`.
    - *Detection*: Caught by `BlockerAndChecklistTest` and `domain.test.js` (`CHECKLIST_INCOMPLETE`).

3. **Premature State Mutation on Rejection**:
    - *Mutation*: Mutating the note's status before completing invariant checks.
    - *Detection*: Caught by `InvariantBoundaryTest` and `BlockerAndChecklistTest` verifying that the board snapshot remains strictly unchanged upon failure.

## Backend startup and liveness

Run the Spring Boot startup test with JDK 21 from the repository root:

```sh
./gradlew :backend:test --tests stickynotes.MainTest
```

`MainTest.livenessIsUpWithoutDatabase` uses `@SpringBootTest` with a random HTTP port and the `test` profile. It overrides the MongoDB URI with `mongodb://127.0.0.1:1/sticky_notes_test` and short connection timeouts so the database is unreachable independently of the development URI.

The test runs the startup bootstrap against the unreachable database, sends a real HTTP request to `/actuator/health/liveness`, checks status `200`, parses the JSON body, and checks that `status` is `UP`. This verifies that a bootstrap connectivity failure allows Spring Boot startup and HTTP liveness; it does not verify note persistence or database-aware readiness.

Inspect `backend/build/reports/tests/test/index.html` or `backend/build/test-results/test/TEST-stickynotes.MainTest.xml`. A passing run must show the test executed with zero failures, errors, or skips. `:backend:check`, the repository-wide `check`, and `fullBuild` include the backend test suite. GitHub Actions retains the backend test reports.

## Backend application service unit tests

Run the application service tests from the repository root:

```sh
./gradlew :backend:test --tests stickynotes.application.BoardServiceTest
```

`BoardServiceTest` calls every Java entry point exposed by `JvmBoardFacade` and executes every `BoardService` mutation against an in-memory fake `BoardRepository`. The successful scenarios cover note, movement, WIP, blocker, and checklist operations, including generated UUIDs, partial note and checklist updates, preservation of untouched entities, one conditional save per command, and revision increments. Domain rejection is checked without a save.

Parameterized scenarios exercise every mutation with a stale revision, a competing write during conditional save, revision overflow, and corrupt persisted state. They verify conflict metadata, a single read on stale requests, one additional read after a failed CAS, no save retry or overwrite of the competing snapshot, and zero save calls on overflow or invalid state. Boundary coverage includes the last revision that can be incremented, a board disappearing during CAS, and database or snapshot validation failures during the conflict reload. The service translates these failures to stable `BoardServiceException` codes without exposing infrastructure details.

Service and shared-domain regressions verify that a missing checklist item on a `DONE` note produces `ITEM_NOT_FOUND` before the read-only check, and that a full WIP column produces `WIP_LIMIT_REACHED` before an out-of-range destination index. Both preserve the original board. `BlockerAndChecklistTest` and `MovementTest` execute the shared regressions on JVM and ChromeHeadless. The service tests do not exercise MongoDB or HTTP error mapping.

## Backend persistence unit tests

Run the mapper, repository, and bootstrap tests with JDK 21 from the repository root:

```sh
./gradlew :backend:test --tests 'stickynotes.infrastructure.*'
```

`BoardDocumentMapperTest` verifies complete snapshot round trips, including note and checklist order, and rejects missing fields, unknown colors, unsupported schemas, negative revisions, duplicate note IDs, invalid `DONE` states, non-canonical note order, and WIP limits below occupancy.

`MongoBoardRepositoryTest` uses Mockito to simulate `MongoTemplate` without starting Spring or connecting to a database. It covers:

- Loading an existing board, an absent board, invalid persisted state, and database failures. Invalid state is rejected without a repair write; a database failure is propagated rather than treated as an absent board.
- Saving a complete snapshot with revision incremented once. The test captures the replacement query and checks both `_id="main"` and the expected revision, the complete replacement document, the `boards` collection, and disabled upsert.
- An unmatched revision returning an empty result without a retry, and an unacknowledged write being rejected.
- Wrong board IDs, mismatched or negative revisions, revision overflow, and invalid domain state being rejected before any database interaction.
- A save failure being propagated without a retry.

`BoardBootstrapTest` simulates MongoDB responses and captures the upsert query and update. It checks that initial creation and repeated startup use only the `_id="main"` filter and `$setOnInsert` fields, so the bootstrap never requests a replacement or unconditional field update. It also covers duplicate-key tolerance without retry, rejection of unacknowledged initialization, startup continuation on database resource failure, and propagation of other database errors.

Inspect `backend/build/reports/tests/test/index.html` or the XML files under `backend/build/test-results/test/`. A passing run must show all three persistence test classes executed with zero failures, errors, or skips. `:backend:check` includes these tests and the backend formatting check.

These unit tests verify the adapter's decisions and arguments to `MongoTemplate`. They do not exercise BSON conversion, atomic replacement in a real MongoDB server, or restart behavior. Within R2, database integration tests must verify those boundaries and board initialization; HTTP tests must verify the API responses specified in [contracts](contracts.md).

## Other test scopes

- `frontend/src/App.test.js` exercises the real generated Kotlin/JS package through the Vue note form: creation and rendering, blank-content rejection, and oversized-content rejection with board/draft preservation and recovery after a valid submission. The oversized test submits programmatically so HTML `maxlength` cannot mask a missing domain check.
- `backend/src/test/java/stickynotes/DomainInteropTest.java` verifies Java access to the shared JVM facade.
- `backend/src/test/java/stickynotes/MainTest.java` verifies Spring Boot startup and HTTP liveness, as described [above](#backend-startup-and-liveness).
