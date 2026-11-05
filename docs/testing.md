<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Testing

`commons/src/commonTest` contains the domain scenarios shared by the JVM and ChromeHeadless targets. The backend has application-service, persistence, HTTP, and MongoDB integration suites; the frontend has Vitest component tests and Playwright journeys on the delivered stack.

Run the complete suite with a running Docker engine:

```sh
./gradlew fullTest
```

## Test design

Keep domain rule combinations in the shared suite, orchestration in `BoardServiceTest`, transport in the HTTP suites, and stored state in the MongoDB suites. Test representative boundaries once, at the layer where a regression would appear: `1`, `500`, and `501` units, omitted versus null versus `false`, and stale revisions. Do not repeat the same permutation across every endpoint and layer.

Install JDK 21 and Google Chrome before running the browser tests. Point `CHROME_BIN` to the executable if Karma cannot find it:

```sh
# macOS
export CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# Linux
export CHROME_BIN="$(command -v google-chrome)"

./gradlew :commons:jvmTest :commons:browserDomainTest
```

Both reports under `commons/build/test-results/` must show the shared scenarios executed without failures or skips.

## Domain Invariant Coverage Matrix

The shared suite covers every rule in [domain rules](domain.md): `NoteTest` and `BoardTest` cover content, identity, creation, deletion, and capacity; `MovementTest` covers transitions, WIP, and ordering; `BlockerAndChecklistTest` covers blockers and checklists; `InvariantBoundaryTest` covers boundaries and rejection without mutation; `SnapshotTest` and `ContractSerializationTest` cover snapshots and serialization. The same scenarios run on the JVM and in ChromeHeadless, and `frontend/src/domain.test.js` covers the JavaScript facade. Test names state the rule they protect, so the suites are the coverage matrix.

## Backend startup and liveness

```sh
./gradlew :backend:test --tests stickynotes.MainTest
```

`MainTest.livenessIsUpWithoutDatabase` starts the application with the `test` profile and an unreachable MongoDB URI. It sends a real HTTP request to `/actuator/health/liveness`, checks `200` with `status: UP`, and verifies that credential fixtures do not appear in the startup output. It does not verify persistence or readiness.

## Backend application service unit tests

```sh
./gradlew :backend:test --tests stickynotes.application.BoardServiceTest
```

`BoardServiceTest` calls every `BoardService` mutation against an in-memory fake repository. It covers generated UUIDs, partial updates, one conditional save per command, revision increments, stale revisions, a competing write, revision overflow, corrupt persisted state, and conflict metadata. Domain rejections are checked without a save. These tests do not exercise MongoDB or HTTP.

## Checklist HTTP tests (R3)

```sh
./gradlew :backend:test --tests stickynotes.api.ChecklistHttpTest
```

`ChecklistHttpTest` starts the Spring HTTP server with a mocked `BoardService`. It covers rejected JSON types, numeric strings, decimal revisions, explicit nulls, unknown fields, malformed bodies, missing revisions, a PATCH without mutable fields, and whitespace-only item IDs. Accepted requests verify the values passed to the service, including explicit `false` and omitted fields. Simulated infrastructure failures verify neutral `500` and `503` responses and logs without request content or exception details.

## Command JSON validation (R3)

```sh
./gradlew :backend:test --tests stickynotes.api.CommandJsonHttpTest
```

`CommandJsonHttpTest` uses the real HTTP server and a mocked service. It rejects unknown fields, wrong scalar types, numeric enum ordinals, fractional indices, and invalid revisions before a service call, and verifies that omitted and null optional note fields behave as documented.

## HTTP command body-size limit (R3)

```sh
./gradlew :backend:test --tests stickynotes.api.CommandBodyLimitHttpTest
```

The suite checks that POST, PUT, PATCH, and DELETE bodies above 65,536 bytes return `413 PAYLOAD_TOO_LARGE` with no service call, including chunked requests without `Content-Length`. A body of exactly 65,536 bytes succeeds.

## Backend persistence unit tests

```sh
./gradlew :backend:test --tests 'stickynotes.infrastructure.*'
```

`BoardDocumentMapperTest` verifies snapshot round trips and rejects missing fields, unknown colors, unsupported schemas, duplicate IDs, invalid `DONE` states, non-canonical order, and WIP limits below occupancy. `MongoBoardRepositoryTest` simulates `MongoTemplate` and checks the replacement query, revision increment, disabled upsert, unacknowledged writes, overflow, and invalid state. `BoardBootstrapTest` checks the insert-only upsert, duplicate-key tolerance, and startup behavior on database failures.

These tests verify the adapter decisions and arguments. They do not exercise BSON conversion or a real MongoDB server; the integration suite covers those.

## Backend MongoDB integration tests (R2)

```sh
./gradlew :backend:integrationTest --rerun-tasks
```

The task starts the isolated container from `compose.test.yaml` on `127.0.0.1:27018` and finalizes with cleanup. Each test creates a unique `sticky_notes_it_…` database and drops only that database.

`MongoBoardRepositoryIntegrationTest` verifies bootstrap creation without overwrites, snapshot survival across an application restart, conditional saves with a stale revision, revision overflow, and two connections competing for the last WIP slot.

## Concurrent HTTP commands and MongoDB (R3)

```sh
./gradlew :backend:integrationTest \
  --tests stickynotes.integration.ConcurrentBoardTest \
  --rerun-tasks --no-build-cache --no-daemon
```

`ConcurrentBoardTest` starts two independent Spring contexts in one JVM, each with its own HTTP port and MongoDB client, against the same database. Repository spies share a `CyclicBarrier` at the start of `save()`, so both services validate before either write reaches MongoDB. The three scenarios cover the last WIP slot, two edits of the same note, and a WIP limit change competing with a movement. Each expects one success, one `409 REVISION_CONFLICT`, and no lost update.

### Revision-filter mutation check

`ConcurrentBoardTest.lastWipSlotAllowsExactlyOneMove` fails if the `revision` predicate is removed from the replacement query: both commands would save and the expected `409` would become a second `200`. Restore the predicate after the experiment.

## Checklist HTTP and MongoDB integration tests (R3)

```sh
./gradlew :backend:integrationTest --tests stickynotes.integration.ChecklistHttpIntegrationTest
```

`ChecklistHttpIntegrationTest` runs the real HTTP server, service, shared domain, and MongoDB. It covers every documented endpoint and status, snapshot fields, revision increments, the twentieth item, restart persistence, guarded completion and reopening, an empty checklist, rejected labels, missing items, stale revisions, oversized chunked bodies, unknown JSON fields, and an empty board. Each rejected command is compared with the raw stored document and a fresh GET before and after the request.

## MongoDB outage and recovery (R3)

This manual check uses JDK 21, Docker, and ports `27019` and `8081`:

1. Start a container from the image pinned in `compose.test.yaml` on `127.0.0.1:27019`, then start the backend with `MONGODB_URI=mongodb://127.0.0.1:27019/sticky_notes_recovery PORT=8081`.
2. Create a note at revision 1 and save the GET snapshot and the raw `boards` document.
3. Stop MongoDB. Liveness stays `200 UP`; readiness, `GET /api/board`, and a valid WIP update return `503`; malformed JSON still returns `400`.
4. Start MongoDB and wait for readiness `200 UP`. The saved GET snapshot and BSON document are unchanged, and a new WIP update returns `200` at revision 2.
5. Remove the container and its volumes.

## Compose delivery stack (R4)

These checks start real containers and are not part of `./gradlew check`.

```sh
./gradlew buildImages
./scripts/start-local.sh
docker compose ps
```

Every service must be `healthy`. Only `web` publishes a port, on `127.0.0.1:8080`. `GET /api/board` must return the snapshot through the proxy, and an unknown `/api/*` path must return JSON rather than the page. A note created before `./scripts/stop-local.sh` must still exist after `./scripts/start-local.sh`. The development override is checked with `./scripts/start-local.sh --dev`: the API is published on `8080`, MongoDB on `27017`, and `web` publishes nothing.

### Backup and restore

```sh
./gradlew buildImages
./gradlew backupRestoreVerify
```

The task backs up a board with a note, a checklist item, and a blocker, checks the manifest, restores into a new volume, starts a second stack, and compares the snapshots. It also checks that a restore into an existing volume stops without confirmation and that teardown leaves no container, volume, or network behind. Procedures are in [Operations](operations.md#backup).

## Other test scopes

- `frontend/src/App.test.js` mounts the interface with mocked HTTP and checks loading, creation, deletion confirmation, and movement.
- `useBoard.test.js` checks draft preservation, concurrent-command prevention, WIP rejection, and board refresh on a revision conflict. `NoteEditor.test.js`, `NoteCard.test.js`, `BoardView.test.js`, `BlockEditor.test.js`, `WipLimitEditor.test.js`, `ChecklistEditor.test.js`, and `ConflictNotice.test.js` cover the components.
- `DomainInteropTest` verifies Java access to the JVM facade.
- `BackendImagePolicyTest` verifies the runtime image contract: a digest-pinned base image, one copied artifact, an unprivileged user, an exec-form entrypoint, and a declared stop signal.

## Code coverage and architectural boundary checks

- **Commons**: JaCoCo enforces 80% line coverage on the JVM target; the browser target is verified by `:commons:browserDomainTest`.
- **Backend**: JaCoCo enforces 80% line coverage on unit and HTTP tests, excluding `stickynotes.Main`.
- **Frontend**: Vitest with `@vitest/coverage-v8` enforces 80% line coverage on maintained sources.
- **Domain boundaries**: `:commons:verifyDomainBoundaries` rejects JVM and infrastructure imports in `commonMain` and Spring or MongoDB dependencies in `commons/build.gradle.kts`.

## Browser E2E tests on the production container stack (R4)

```sh
./gradlew :frontend:frontendE2e
```

`frontend/e2e/kanban.spec.js` runs Playwright with headless Chromium against the production web image, the API image, and MongoDB. It covers CRUD persistence across reload, WIP enforcement, blocking, checklist guards, and multi-window conflict recovery. `./gradlew fullVerify` includes it.
