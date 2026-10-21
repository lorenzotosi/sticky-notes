<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Testing

`commons/src/commonTest` contains domain scenarios shared by the JVM and ChromeHeadless targets. `browserDomainTest` is an alias for `jsBrowserTest`. The backend also has application service and persistence unit tests, real MongoDB integration tests, and an HTTP startup and liveness test.

This guide explains how to execute the implemented tests and which [domain rules](domain.md) they cover. [R1 evidence](evidence/R1.md) records the observed domain and consumer results; [R3 evidence](evidence/R3.md) records the results of the complete Kanban behaviour, including the checklist HTTP results, the concurrent commands, and the browser review of the interface.

## Test design

Prefer a few readable tests that each catch a distinct, plausible regression. Before adding a case, check whether an existing test already protects that behavior.

- Keep domain rule combinations in the shared domain suite and orchestration checks in `BoardServiceTest`. HTTP tests focus on routing, JSON types, omitted fields, response shape, and error metadata. MongoDB integration tests focus on stored state and restart behavior, with representative rejected commands.
- Use representative invalid inputs and meaningful boundaries. Preserve distinctions such as omitted fields, explicit null, `false`, decimals, and integer overflow; do not enumerate equivalent values or multiply every case across every endpoint and layer.
- Keep setup, action, and assertions easy to follow. Use small fixtures and local helpers when they remove repetition; avoid generic test infrastructure for a handful of cases.
- Add another case when it covers an uncovered failure or a known regression, rather than to increase the test count or mirror implementation details.

Install JDK 21 and Google Chrome before running the browser tests. Point `CHROME_BIN` to the Chrome executable if Karma cannot find it:

```sh
# macOS
export CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# Linux (after installing Google Chrome)
export CHROME_BIN="$(command -v google-chrome)"

./gradlew :commons:jvmTest :commons:browserDomainTest
```

On Windows PowerShell, set `$env:CHROME_BIN` to the full path of `chrome.exe` (normally under `C:\Program Files\Google\Chrome\Application\`) and run `.\gradlew.bat :commons:jvmTest :commons:browserDomainTest`. Set the Gradle JVM in IntelliJ IDEA to JDK 21.

CI uses the Chrome installation on the [GitHub Actions Ubuntu runner](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md). The quality workflow checks `google-chrome --version`, exports `CHROME_BIN`, and runs `check` or `fullBuild` according to the event; both include the common test targets. Compare `commons/build/test-results/jvmTest/` and `commons/build/test-results/jsBrowserTest/`: both must show the shared scenarios executed, with no skipped tests. The workflow uploads these reports even when a test fails.

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

`MainTest.livenessIsUpWithoutDatabase` uses `@SpringBootTest` with a random HTTP port and the `test` profile. It overrides the MongoDB URI with the unreachable loopback port 1, independently of the development URI. The application-wide five-second MongoDB timeouts still apply.

The test runs the startup bootstrap against the unreachable database, sends a real HTTP request to `/actuator/health/liveness`, checks status `200`, parses the JSON body, and checks that `status` is `UP`. Its URI contains credential fixtures, and captured startup output must omit both the username and password. This verifies that a bootstrap connectivity failure allows Spring Boot startup and HTTP liveness; it does not verify note persistence or database-aware readiness.

Inspect `backend/build/reports/tests/test/index.html` or `backend/build/test-results/test/TEST-stickynotes.MainTest.xml`. A passing run must show the test executed with zero failures, errors, or skips. `:backend:check`, the repository-wide `check`, and `fullBuild` include the backend test suite. GitHub Actions retains the backend test reports.

## Backend application service unit tests

Run the application service tests from the repository root:

```sh
./gradlew :backend:test --tests stickynotes.application.BoardServiceTest
```

`BoardServiceTest` calls every Java entry point exposed by `JvmBoardFacade` and executes every `BoardService` mutation against an in-memory fake `BoardRepository`. The successful scenarios cover note, movement, WIP, blocker, and checklist operations, including generated UUIDs, partial note and checklist updates, preservation of untouched entities, one conditional save per command, and revision increments. Domain rejection is checked without a save.

Parameterized scenarios exercise every mutation with a stale revision, a competing write during conditional save, revision overflow, and corrupt persisted state. They verify conflict metadata, a single read on stale requests, one additional read after a failed CAS, no save retry or overwrite of the competing snapshot, and zero save calls on overflow or invalid state. Boundary coverage includes the last revision that can be incremented, a board disappearing during CAS, and database or snapshot validation failures during the conflict reload. The service translates these failures to stable `BoardServiceException` codes without exposing infrastructure details.

Service and shared-domain regressions verify that a missing checklist item on a `DONE` note produces `ITEM_NOT_FOUND` before the read-only check, and that a full WIP column produces `WIP_LIMIT_REACHED` before an out-of-range destination index. Both preserve the original board. `BlockerAndChecklistTest` and `MovementTest` execute the shared regressions on JVM and ChromeHeadless. The service tests do not exercise MongoDB or HTTP error mapping.

## Checklist HTTP tests (R3)

Run the checklist HTTP tests from the repository root:

```sh
./gradlew :backend:test --tests stickynotes.api.ChecklistHttpTest
```

`ChecklistHttpTest` starts the Spring HTTP server on a random port and sends requests using the JDK HTTP client. It replaces `BoardService` with a Mockito bean and configures an unreachable test MongoDB URI, so no database is needed.

Representative rejected payloads cover wrong JSON types, numeric strings, decimal revisions, integer overflow, explicit null values, unknown fields, non-object bodies, malformed JSON, missing or negative revisions, and a PATCH without mutable fields. These cases verify HTTP `400` and zero service calls. Successful partial and combined updates verify the values passed to the service, including explicit `false` and null Java arguments for omitted fields.

The blank-item-ID regression sends URL-encoded ordinary and non-breaking spaces through PATCH and DELETE. It checks `400 INVALID_REQUEST`, the `itemId` error field, and zero service calls.

Simulated infrastructure failures verify neutral `503` and `500` responses and omission of unavailable error metadata. Captured console output must contain the error code and HTTP status and omit note content, credentials, and exception details. Domain combinations and conditional-save behavior remain in the shared domain and application service suites. These HTTP tests do not prove persistence; the [MongoDB HTTP suite](#checklist-http-and-mongodb-integration-tests-r3) exercises the real service and database.

## Command JSON validation (R3)

Run the command JSON and checklist HTTP suites with JDK 21:

```sh
./gradlew :backend:test \
  --tests stickynotes.api.CommandJsonHttpTest \
  --tests stickynotes.api.ChecklistHttpTest \
  --max-workers=1 --no-configuration-cache
```

`CommandJsonHttpTest` uses the real Spring HTTP server and a mocked `BoardService`. It rejects unknown fields, malformed or non-object bodies, wrong scalar types, numeric enum ordinals, fractional indices, and invalid revisions before a service call. Invalid enum names, indices, and WIP limits retain their documented codes and fields. Successful note commands verify that omitted and null optional fields have the same effect: default creation color and unchanged PATCH fields. A PATCH containing only null mutable fields is rejected. Existing checklist cases retain their stricter null checks and omission-versus-false coverage.

`ChecklistHttpIntegrationTest.invalidCommandJsonPreservesTheStoredDocument` verifies an unknown-field rejection through HTTP, the real service, and MongoDB. The rejected command preserves the complete raw MongoDB document and GET snapshot, including the revision. Run it through the existing MongoDB lifecycle:

```sh
./gradlew :backend:integrationTest \
  --tests 'stickynotes.integration.ChecklistHttpIntegrationTest.invalidCommandJsonPreservesTheStoredDocument' \
  --max-workers=1 --no-configuration-cache
```

## HTTP command body-size limit (R3)

Run the focused HTTP tests with JDK 21:

```sh
./gradlew :backend:test --tests stickynotes.api.CommandBodyLimitHttpTest
```

`CommandBodyLimitHttpTest` sends requests through the real Spring HTTP server with a mocked `BoardService`. It verifies that oversized POST, PUT, PATCH, and DELETE bodies return `413 PAYLOAD_TOO_LARGE` without calling the service. A chunked request with no `Content-Length` contains 65,537 UTF-8 bytes but only 65,536 characters, protecting byte counting from a character-count regression. A valid JSON command padded with whitespace to exactly 65,536 bytes succeeds with both known-length and chunked transfer, preserving the decoded non-ASCII label.

`ChecklistHttpIntegrationTest.oversizedChunkedCommandPreservesTheStoredDocument` exercises the same filter with the real service and MongoDB. Its otherwise valid command would toggle an item; an oversized body instead returns `413`, and both the complete raw MongoDB document and a fresh GET remain identical, including the revision. Run it with the [MongoDB integration lifecycle](#backend-mongodb-integration-tests-r2):

```sh
./gradlew :backend:integrationTest \
  --tests 'stickynotes.integration.ChecklistHttpIntegrationTest.oversizedChunkedCommandPreservesTheStoredDocument'
```

The focused HTTP suite covers the transport and size permutations; the MongoDB suite uses one representative rejection to prove persistence preservation. Results are recorded in [R3 acceptance evidence](evidence/R3.md).

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

`BoardBootstrapTest` simulates MongoDB responses and captures the upsert query and update. It checks that initial creation and repeated startup use only the `_id="main"` filter and `$setOnInsert` fields, so the bootstrap never requests a replacement or unconditional field update. It also covers duplicate-key tolerance without retry, rejection of unacknowledged initialization, startup continuation on database resource failure or a translated MongoDB operation timeout, and propagation of other database errors.

Inspect `backend/build/reports/tests/test/index.html` or the XML files under `backend/build/test-results/test/`. A passing run must show all three persistence test classes executed with zero failures, errors, or skips. `:backend:check` includes these tests and the backend formatting check.

These unit tests verify the adapter's decisions and arguments to `MongoTemplate`. They do not exercise BSON conversion, atomic replacement in a real MongoDB server, or restart behavior; the integration suite below exercises those boundaries. Within R2, HTTP tests must verify the API responses specified in [contracts](contracts.md).

## Backend MongoDB integration tests (R2)

With JDK 21 and a running Docker engine with Compose and Linux containers, run from the repository root:

```sh
./gradlew :backend:integrationTest --rerun-tasks
```

The integration task depends on `testMongoUp` and finalizes with `testMongoDown`, including when a test fails. `testMongoUp` uses `compose.test.yaml` to pull the pinned MongoDB image when missing, create the test network and container, and wait up to 120 seconds for MongoDB's health check. It starts only `mongo-test`, not the containerized test runner. Docker must already be installed and running; no custom Dockerfile is needed for the MongoDB image.

The Compose project is `sticky-notes-tests`. MongoDB is exposed only on `127.0.0.1:27018`, which must be free. Its data directories use temporary memory-backed storage, not development data volumes; stopping or removing the container discards that data.

IDE runs delegated to Gradle inherit the automatic MongoDB lifecycle. With the IDE's direct JUnit runner, first execute `./gradlew testMongoUp`, then run `MongoBoardRepositoryIntegrationTest` with JDK 21 and execute `./gradlew testMongoDown` afterward. Its default URI is `mongodb://127.0.0.1:27018` when `TEST_MONGODB_URI` is unset or blank. To use another isolated test server, set that environment variable in the IDE run configuration or Gradle process. Each test creates a unique `sticky_notes_it_…` database and drops only that database after the test. Never supply a development or production database server.

The five tests in `backend/src/integrationTest/java/stickynotes/integration/MongoBoardRepositoryIntegrationTest.java` verify:

- Repeated bootstrap creates exactly one empty board without overwriting it.
- A complete snapshot, including ordering, colors, blockers, and checklists, survives closing and restarting the Spring application context against the same database.
- A correct revision saves once, while a stale revision preserves the winning snapshot.
- Revision overflow leaves the persisted snapshot unchanged.
- Two independent MongoDB connections synchronized with a barrier compete for the last WIP slot; exactly one CAS succeeds and the revision increases once.

These tests exercise the persistence adapter and bootstrap against a real MongoDB server. They do not send HTTP requests or restart MongoDB itself. Inspect `backend/build/reports/tests/integrationTest/index.html` or `backend/build/test-results/integrationTest/TEST-stickynotes.integration.MongoBoardRepositoryIntegrationTest.xml`: a passing run must show five tests with zero failures, errors, or skips. Unavailable MongoDB fails the suite rather than skipping it.

The suite is included in `:backend:check`, `fullTest`, `check`, and `fullBuild`. These Gradle entry points start and clean up MongoDB through the integration task lifecycle. [GitHub Actions](ci-cd.md#workflow-execution-policy) uses the same Gradle graph and retains an unconditional Compose cleanup step as a safeguard.

To run the suite itself inside Docker instead of using the host JVM or IDE, use the existing `tests` service. On a POSIX shell, map the host user so generated files remain writable:

```sh
TEST_UID="$(id -u)" TEST_GID="$(id -g)" \
  docker compose -f compose.test.yaml up --exit-code-from tests tests
```

In this workflow, Compose starts MongoDB and waits for its health check before starting the test runner. The runner's Gradle command excludes `:testMongoUp` and `:testMongoDown` with `-x`: container lifecycle belongs to the host's Compose process, not to the JDK container. No Docker CLI or host Docker socket is needed inside the runner. The command returns the test container's exit code; remove both services afterward with the cleanup command below.

After a direct IDE or containerized test run, remove the isolated test environment when it is no longer needed. Gradle integration runs already perform this cleanup through their finalizer:

```sh
./gradlew testMongoDown
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew` for the Gradle commands.

## Concurrent HTTP commands and MongoDB (R3)

`ConcurrentBoardTest` starts two independent Spring application contexts in one JVM, each with a random loopback HTTP port and its own MongoDB client. Both use the same unique `sticky_notes_concurrent_…` database. Each test seeds the board after startup and compares the initial `GET /api/board` responses with that snapshot.

Repository spies share a `CyclicBarrier` at the start of `save()`. Both services must have loaded and validated their commands before either spy calls the real MongoDB save. The fixture unwraps Spring repository proxies before creating the spies and injects them into the services with `ReflectionTestUtils`. Reads, domain execution, and conditional replacements remain real. The [concurrency guide](concurrency.md) explains why this overlap exercises the database boundary.

The three scenarios are:

- `lastWipSlotAllowsExactlyOneMove`: revision 7, WIP limit 1, and two `TODO` notes compete for the last slot. The test requires one HTTP `200` and one `409 REVISION_CONFLICT`, compares the successful response and stored board with the complete winning snapshot at revision 8, and checks fresh GETs through both backends.
- `concurrentEditsPreserveTheAcknowledgedWinner`: two PATCH requests edit the same note from revision 7 to `Alpha` or `Beta`. The test checks the opposing `409` response and persisted winning content; its final assertions compare the content rather than the complete snapshot.
- `wipLimitChangeAndMoveShareOneRevisionBoundary`: a board at revision 7 has WIP limit 2, A in `TODO`, and B in `DOING`. Lowering the limit to 1 competes with moving A into `DOING`; both commands are valid from the seed. When the limit request succeeds, the test checks the opposing `409`, limit 1, and one note in each of `TODO` and `DOING`. When the movement succeeds, it checks HTTP `200`/`409` and two notes in `DOING`.

Run all three tests with JDK 21 and a running Docker engine:

```sh
./gradlew :backend:integrationTest \
  --tests stickynotes.integration.ConcurrentBoardTest \
  --rerun-tasks --no-build-cache --no-daemon
```

The integration task uses the existing isolated MongoDB container and cleanup lifecycle. Each test closes both backend contexts and the HTTP client before dropping its database. Inspect `backend/build/test-results/integrationTest/TEST-stickynotes.integration.ConcurrentBoardTest.xml` and `backend/build/reports/tests/integrationTest/index.html`. A complete class run must show all three methods with zero failures, errors, or skips. A report filtered to one method proves only that scenario.

These tests exercise HTTP, service orchestration, the shared JVM domain, and MongoDB through two application contexts in one JVM. They do not exercise separate JVM processes or database failover. [R3 evidence](evidence/R3.md#concurrent-commands-and-conditional-writes) records the executed concurrency scenarios; the mutation procedure above is a separate, repeatable check.

### Revision-filter mutation check

Use the last-slot test to verify that removing the concurrency guard is detected:

```sh
./gradlew :backend:integrationTest \
  --tests stickynotes.integration.ConcurrentBoardTest.lastWipSlotAllowsExactlyOneMove \
  --rerun-tasks --no-build-cache --no-daemon
```

1. Run the command with the original implementation and retain the passing report.
2. In `MongoBoardRepository.save()`, temporarily remove only `.and("revision").is(expectedRevision)` from the replacement query. Keep the `_id="main"` predicate and the replacement logic unchanged.
3. Run the same command. Both validated candidates can now be saved, so the test must fail because the expected `409` is replaced by another HTTP `200`. A compilation error, timeout, or unavailable database does not establish this result.
4. Restore the revision predicate and rerun the command. The test must pass again. Restore the predicate even if the experiment fails unexpectedly, and do not commit the temporary mutation.
5. Preserve the reports before the next run overwrites them. Record the source revision, execution environment, command or IDE configuration, observed failure, and restored passing result in `docs/evidence/R3.md`.

## Checklist HTTP and MongoDB integration tests (R3)

With JDK 21 and a running Docker engine, run the complete backend integration suite:

```sh
./gradlew :backend:integrationTest --rerun-tasks
```

To run only the checklist HTTP suite:

```sh
./gradlew :backend:integrationTest --tests stickynotes.integration.ChecklistHttpIntegrationTest --rerun-tasks
```

`ChecklistHttpIntegrationTest` uses the same isolated MongoDB container and Gradle lifecycle as the [persistence suite](#backend-mongodb-integration-tests-r2). It starts the real Spring HTTP server on a random loopback port and creates a unique `sticky_notes_http_it_…` database for the class. Each test seeds a valid board in that database; class cleanup closes the HTTP client and backend, then drops only that database. MongoDB unavailability fails the suite.

`allDocumentedEndpointsReturnTheirContractStatusAndPersist` runs every documented board command in one HTTP sequence. It checks the expected `200` or `201` status, required null and empty snapshot fields, each revision increment, the MongoDB repository snapshot, and a fresh GET after every mutation. `staleRevisionTakesPrecedenceOverMissingNote` verifies that a valid command targeting a missing note returns `409 REVISION_CONFLICT` when its revision is stale, before entity lookup could return `404 NOTE_NOT_FOUND`; the rejected request leaves the stored document unchanged.

One sequence creates the twentieth item on the target note when another note follows it, rejects an additional item, renames and toggles the created item, restarts the backend, and deletes it. It checks the generated UUID, label normalization, revision increments, and preservation of unrelated data. Successful HTTP snapshots are compared with a MongoDB repository read and a fresh GET. The backend restart also preserves the raw MongoDB document. A separate empty-board regression checks complete snapshot fields, including constructor defaults.

`completionAndReopeningRespectChecklistAndWip` exercises a continuous HTTP journey from `TODO` to `DOING`, rejects incomplete completion, completes the item, and moves the note to `DONE`. It rejects addition, renaming, toggling, and deletion while in `DONE`. Another note occupies the only WIP slot; reopening is rejected until that note moves back to `TODO`. Reopening then preserves the completed checklist, and an explicit `false` update succeeds in `DOING`. A separate case completes a note with an empty checklist.

Representative rejected labels, a missing item, and a stale revision check HTTP status, codes, and metadata through the real service. Each rejection compares the complete raw MongoDB document and a fresh GET with their pre-request values. Other malformed-input and domain combinations stay in the focused HTTP, service, and shared-domain suites.

Inspect `backend/build/reports/tests/integrationTest/index.html` and `backend/build/test-results/integrationTest/TEST-stickynotes.integration.ChecklistHttpIntegrationTest.xml`. The Compose `tests` service runs the persistence and checklist suites. These tests cover HTTP, application orchestration, the shared JVM domain, and real MongoDB. They do not exercise the Vue interface or restart MongoDB. Recorded results and reproduction details are in [R3 acceptance evidence](evidence/R3.md).

## MongoDB outage and recovery (R3)

This is a manual integration check, separate from `:backend:check`. Use JDK 21, Docker, and free loopback ports 27019 and 8081. The test Compose MongoDB uses memory-backed storage, so use a separate container for this restart check.

1. Start an isolated MongoDB container using the image pinned in `compose.test.yaml`:

   ```sh
   docker run -d --name sticky-notes-recovery -p 127.0.0.1:27019:27017 \
     mongo@sha256:f071cbf97a6e52fb7f07ce9ca5d862ad79876bf0eba9ba97758337d740449638
   ```

   Wait until `docker exec sticky-notes-recovery mongosh --quiet --eval 'db.adminCommand({ping:1}).ok'` returns `1`. In another terminal, start the backend:

   ```sh
   MONGODB_URI=mongodb://127.0.0.1:27019/sticky_notes_recovery PORT=8081 \
     ./gradlew :backend:bootRun --args='--spring.profiles.active=local'
   ```

2. Wait for `/actuator/health/readiness` to return `200 UP`, create a note, and save the HTTP and MongoDB state:

   ```sh
   curl -i http://127.0.0.1:8081/actuator/health/readiness
   curl -i -H 'Content-Type: application/json' -d '{"content":"Recovery check","expectedRevision":0}' \
     http://127.0.0.1:8081/api/board/notes
   curl -fsS http://127.0.0.1:8081/api/board > /tmp/sticky-notes-before.json
   docker exec sticky-notes-recovery mongosh --quiet sticky_notes_recovery \
     --eval 'EJSON.stringify(db.boards.findOne({_id:"main"}))' > /tmp/sticky-notes-before.bson.json
   ```

3. Run `docker stop sticky-notes-recovery`. Use `curl -i --max-time 8` to check liveness (`200 UP`), readiness (`503 DOWN`), and `/api/board` (`503 DATABASE_UNAVAILABLE`). A PUT to `/api/board/wip-limit` with `{"limit":4,"expectedRevision":1}` must also return `503`; malformed JSON must return `400`. Health bodies contain only `status`.

4. Run `docker start sticky-notes-recovery`. Retry readiness for up to thirty seconds until it returns `200 UP`, keeping the backend running. Compare both saved states:

   ```sh
   curl -fsS http://127.0.0.1:8081/api/board > /tmp/sticky-notes-after.json
   docker exec sticky-notes-recovery mongosh --quiet sticky_notes_recovery \
     --eval 'EJSON.stringify(db.boards.findOne({_id:"main"}))' > /tmp/sticky-notes-after.bson.json
   cmp /tmp/sticky-notes-before.json /tmp/sticky-notes-after.json
   cmp /tmp/sticky-notes-before.bson.json /tmp/sticky-notes-after.bson.json
   curl -i -X PUT -H 'Content-Type: application/json' -d '{"limit":4,"expectedRevision":1}' \
     http://127.0.0.1:8081/api/board/wip-limit
   ```

   Both comparisons must match; the final write must return `200` with revision `2` and WIP limit `4`.

5. Stop this backend with Ctrl-C and remove only the isolated check container and its temporary volumes with `docker rm -f -v sticky-notes-recovery`. Record the observed result separately from automated test reports.

## Compose delivery stack (R4)

The delivery stack is exercised manually with Docker Compose. It is not part of `./gradlew check`, because the procedure starts real containers and needs a running Docker engine.

Build the images and start the stack:

```sh
./gradlew buildImages
scripts/start-local.sh
```

The script waits until every service reports healthy. Check the result:

```sh
docker compose ps
```

Every service must show `healthy`. Only the web service may show a host port, and it must be the loopback interface; the API and MongoDB show their internal port without a published port.

The configuration can be checked without starting anything:

```sh
docker compose -f compose.yaml config
```

The command must succeed and must contain exactly one published port, the one of the web service.

Use the stack only through `127.0.0.1:8080`:

```sh
curl -fsS http://127.0.0.1:8080/api/board
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' http://127.0.0.1:8080/api/unknown
```

The first command returns the board snapshot through the reverse proxy. The second answers with a JSON error and not with the frontend document, so an API failure never becomes a page of the interface.

Check that the data survives a stop and a start:

```sh
curl -fsS -X POST -H 'Content-Type: application/json' \
  -d '{"content":"Persistence check","expectedRevision":0}' \
  http://127.0.0.1:8080/api/board/notes
scripts/stop-local.sh
docker volume ls | grep sticky-notes_mongo-data
scripts/start-local.sh
curl -fsS http://127.0.0.1:8080/api/board
```

The note created before the stop is still there after the start, and the named volume is listed after the stop: a normal stop never removes the data. Delete the note afterwards, with the identifier of the creation response and the revision of the last read.

The development override is checked with the same images:

```sh
scripts/stop-local.sh
scripts/start-local.sh --dev
docker compose -f compose.yaml -f compose.dev.yaml ps
```

The API must be published on `127.0.0.1:8080`, the port the Vite development proxy targets, MongoDB on `127.0.0.1:27017`, and the web container must show no published port.

This procedure exercises the containers, the reverse proxy, the health checks, and the volume. It does not exercise the browser journeys, which the end-to-end suite of the delivery gate covers, and it does not prove that the published images start on a machine without a source checkout.

## Other test scopes

- `frontend/src/App.test.js` mounts the Kanban interface with mocked HTTP responses and verifies loading, note creation, deletion confirmation, and movement. It does not exercise a real server or database.
- `frontend/src/composables/useBoard.test.js` uses a mocked API client and the real Kotlin/JS prevalidation to check loading, draft preservation, concurrent-command prevention, WIP rejection before dispatch, and board refresh on revision conflict. `NoteEditor.test.js` checks blank content with the shared validator; `domain.test.js` covers the facade's JSON and domain boundaries.
- `backend/src/test/java/stickynotes/DomainInteropTest.java` verifies Java access to the shared JVM facade.
- `backend/src/test/java/stickynotes/MainTest.java` verifies Spring Boot startup and HTTP liveness, as described [above](#backend-startup-and-liveness).
- `backend/src/test/java/stickynotes/container/BackendImagePolicyTest.java` verifies the runtime image contract: a base image pinned to a digest, a single copied artifact, an unprivileged user, and an entrypoint in exec form with a declared stop signal.

## Code coverage and architectural boundary checks

Coverage reports and domain boundary checks run as part of `./gradlew check` and `./gradlew fullBuild`:

- **Shared domain (`:commons`)**: JaCoCo measures line coverage on the JVM target (`:commons:jvmJacocoReport`) and enforces a minimum 80% line coverage threshold (`:commons:jvmJacocoCoverageVerification`). Reports are generated at `commons/build/reports/jacoco/jvmJacocoReport/html/index.html` and `commons/build/reports/jacoco/jvmJacocoReport/jvmJacocoReport.xml`. Note that JVM JaCoCo coverage measures the shared Kotlin domain executed on the JVM; multi-runtime execution in headless Chrome is verified separately by `:commons:browserDomainTest` (`jsBrowserTest`).
- **Backend (`:backend`)**: JaCoCo measures unit and HTTP test coverage (`:backend:jacocoTestReport`) and enforces a minimum 80% line coverage threshold (`:backend:jacocoTestCoverageVerification`), excluding only the `stickynotes.Main` bootstrap entry point. Reports are generated at `backend/build/reports/jacoco/test/html/index.html` and `backend/build/reports/jacoco/test/jacocoTestReport.xml`.
- **Frontend (`:frontend`)**: Vitest with `@vitest/coverage-v8` measures coverage across maintained source files under `frontend/src/**/*.{js,vue}`, excluding `src/main.js`, test files, and the generated Kotlin/JS package (`sticky-notes-domain`), enforcing at least 80% line coverage. Reports are generated under `frontend/coverage/index.html` and `frontend/coverage/lcov.info`.
- **Domain boundary verification (`:commons:verifyDomainBoundaries`)**: verifies that `commons/src/commonMain` contains no JVM-specific imports (`java.*`, `javax.*`, `jdk.*`) and no infrastructure imports (`org.springframework.*`, `org.mongodb.*`), and that `commons/build.gradle.kts` declares no Spring or MongoDB dependencies.
