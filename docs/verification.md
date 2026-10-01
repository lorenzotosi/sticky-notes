<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Verification and traceability matrix

This document defines the project's acceptance cases C01–C55. It connects expected behavior and process checks to [functional requirements](requirements.md), supporting specifications, verification levels, and recorded evidence. A case ID identifies a scenario, not a single test method or a completion status.

## How to use the cases

- Functional cases link existing REQ identifiers; technical and process cases link the relevant local document instead of inventing functional requirements.
- `common` means shared Kotlin tests on both JVM and a real headless browser. Java/JS consumer tests check the adapters. Vue component tests, HTTP tests, Mongo integration tests, and production-browser E2E tests exercise different boundaries.
- A case can require several levels. A passing common test does not prove the API, database, or full UI behavior in the same row.
- Record evidence with the case ID, exact test or repeatable procedure, exercised layer, source revision, environment, result, and report/run/artifact reference. Link that evidence from this document; do not replace it with a bare “passed” label.
- Reference the same IDs in tests, architecture discussions, and the project report. Keep IDs stable; add new cases explicitly rather than renumbering existing cases.

These are acceptance criteria for the complete project. HTTP endpoints, Mongo persistence, Docker/E2E behavior, and administrative handover criteria remain requirements to verify at their respective integration boundaries. Their inclusion does not assert that those features or checks currently exist.

## Recorded evidence

| Cases | Exercised scope | Evidence |
| --- | --- | --- |
| C01–C20 | Shared JVM/ChromeHeadless domain scenarios and the specific Java, JS, and Vue consumer checks listed in R1 | [Shared-domain acceptance evidence](evidence/R1.md#acceptance-cases), including reproduction commands and observed results |

R1 does not prove the future API, Mongo mapper, or complete Kanban UI checks attached to C01–C20. Evidence for C21–C55 must be linked when those checks are actually executed; none is asserted by this transfer.

## Shared domain

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c01"></a>C01 | [REQ-02](requirements.md#req-02-content-length-and-normalization) | A note with no content or only whitespace | Create or edit the note | INVALID_CONTENT; no mutation; content field error | common JVM + browser, API, UI | T017,T019,T035,T036 |
| <a id="c02"></a>C02 | [REQ-02](requirements.md#req-02-content-length-and-normalization) | Content of 1, 500, and 501 UTF-16 code units | Normalize and validate | 1 and 500 are valid; 501 is invalid. Apply the limit after trimming and CRLF-to-LF normalization | common JVM + browser | T017,T024 |
| <a id="c03"></a>C03 | [REQ-02](requirements.md#req-02-content-length-and-normalization) | A non-BMP emoji, CRLF, and leading, trailing, and internal spaces | Validate on JVM and JS | Identical normalized text and length. The emoji counts as 2 units; internal spaces are preserved | common, JS facade | T017,T020,T024 |
| <a id="c04"></a>C04 | [domain](domain.md); [adr/001-board-aggregate](adr/001-board-aggregate.md) | Two entities with the same ID but different content; two corresponding snapshots | Compare equality and mutate the original list | Entities are equal by ID; snapshots differ structurally; changing the original list does not alter the entities | common JVM + browser | T017 |
| <a id="c05"></a>C05 | [REQ-01](requirements.md#req-01-note-creation-and-defaults); [REQ-04](requirements.md#req-04-note-editing-and-color-assignment); [contracts](contracts.md) | An empty board at revision R | Create, edit, and delete a note | Editing preserves the ID; new notes are appended to TODO with default color YELLOW; deletion removes only the specified ID | common, API, UI | T019,T035,T036 |
| <a id="c06"></a>C06 | [REQ-04](requirements.md#req-04-note-editing-and-color-assignment); [contracts](contracts.md) | A duplicate or nonexistent ID, or an unknown color enum value | Send commands | Duplicate IDs and unknown enum values are rejected; a missing entity returns HTTP 404 through the API; the snapshot remains unchanged | common, API | T019,T035,T045 |
| <a id="c07"></a>C07 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement) | WIP limit 1, note A in DOING, and note B in TODO | Move B to DOING | WIP_LIMIT_REACHED; B remains in TODO. Browser and Java return the same result | common, Java consumer, JS, API, UI | T021,T025,T037,T041 |
| <a id="c08"></a>C08 | [REQ-05](requirements.md#req-05-allowed-state-transitions); [REQ-07](requirements.md#req-07-in-column-reordering) | One note in each of the three states | Test all 9 source/target pairs | Only TODO → DOING, DOING → TODO, DOING → DONE, DONE → DOING, and the three same-state reorderings are valid, subject to other constraints | common, API | T021,T037 |
| <a id="c09"></a>C09 | [REQ-07](requirements.md#req-07-in-column-reordering) | DOING list [A, B, C] with full WIP capacity | Reorder A to index 2 after removing it | [B, C, A]; no WIP rejection, duplicates, or lost notes | common, API, UI | T021,T037,T041 |
| <a id="c10"></a>C10 | [REQ-08](requirements.md#req-08-configurable-wip-limit) | Two DOING notes with a current WIP limit of 3 | Set the limit to 1, 2, 0, 21, and a non-integer value | 1 is rejected; 2 is valid; other out-of-range or incorrectly typed payloads are rejected | common, API, UI | T021,T037,T042 |
| <a id="c11"></a>C11 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement); [REQ-09](requirements.md#req-09-note-blocking-and-reasons) | A blocked DOING note | Attempt to move to DONE or TODO, then reorder within the same column | State changes return NOTE_BLOCKED; reordering is allowed; the note still counts toward WIP | common, API, UI | T023,T037,T042 |
| <a id="c12"></a>C12 | [REQ-09](requirements.md#req-09-note-blocking-and-reasons) | An unblocked TODO or DOING note | Block with an empty reason or a reason of 1, 200, or 201 units | Empty and 201-unit reasons are invalid; 1 and 200 are valid; unblocking removes the reason | common, API, UI | T023,T037,T042 |
| <a id="c13"></a>C13 | [REQ-04](requirements.md#req-04-note-editing-and-color-assignment); [REQ-09](requirements.md#req-09-note-blocking-and-reasons) | A DONE note | Attempt to block it and edit its text/color | Blocking is rejected; text/color edits are allowed. The UI reflects these rules | common, API, UI | T023,T037,T042 |
| <a id="c14"></a>C14 | [REQ-11](requirements.md#req-11-guarded-transition-to-done) | A DOING note with an incomplete checklist item | Attempt to move to DONE | CHECKLIST_INCOMPLETE; the snapshot remains unchanged | common, Java, JS, API, UI | T023,T025,T038,T043 |
| <a id="c15"></a>C15 | [REQ-11](requirements.md#req-11-guarded-transition-to-done) | An unblocked DOING note with no checklist items or all items complete | Attempt to move to DONE | Both checklist configurations succeed; the note enters DONE | common, API, UI | T023,T038,T043 |
| <a id="c16"></a>C16 | [REQ-12](requirements.md#req-12-immutability-of-done-checklists--reopening) | A DONE note | Add, rename, toggle, or delete a checklist item | Every checklist mutation returns NOTE_DONE_READ_ONLY; data remains unchanged | common, API, UI | T023,T038,T043 |
| <a id="c17"></a>C17 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement); [REQ-12](requirements.md#req-12-immutability-of-done-checklists--reopening) | A DONE note and a DOING column at full capacity | Reopen the note, then free a slot and try again | First WIP_LIMIT_REACHED, then DOING. The checklist remains consistent | common, API, UI | T023,T038,T043 |
| <a id="c18"></a>C18 | [REQ-03](requirements.md#req-03-board-and-note-capacity-limits) | A board with 200 notes or a note with 20 checklist items | Create one more, then delete and recreate | BOARD_FULL or CHECKLIST_FULL; after deletion, creation succeeds within the limit | common, API | T019,T023,T038 |
| <a id="c19"></a>C19 | [REQ-07](requirements.md#req-07-in-column-reordering) | A destination list of size 2 after removing the moving note | Use indices -1, 0, 2, 3, and a fractional value | 0 and 2 are valid; -1, 3, and fractional values are rejected without clamping | common, API, UI | T021,T037,T041 |
| <a id="c20"></a>C20 | [contracts](contracts.md); [adr/003-interop](adr/003-interop.md) | A JSON snapshot with duplicates, an incomplete DONE note, a blocked DONE note, or inconsistent WIP | Rehydrate the Board | Reject with a diagnostic error; no silent reset or save | common, Mongo mapper | T027,T030,T032 |

## Persistence and concurrency

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c21"></a>C21 | [contracts](contracts.md); [architecture](architecture.md) | Database revision R and a client sending R-1 | Attempt an edit | HTTP 409 REVISION_CONFLICT with currentRevision; no save. A revision at the maximum Int value cannot overflow: reject with a diagnostic error before saving | unit, API, Mongo | T031,T032,T035 |
| <a id="c22"></a>C22 | [architecture](architecture.md); [contracts](contracts.md) | WIP limit 1, two TODO notes, and two requests using the same revision | Run concurrent moves into DOING using a barrier and separate connections | One success and one HTTP 409; one DOING note; revision increases by 1; no lost update | Mongo integration, HTTP | T032,T044 |
| <a id="c23"></a>C23 | [contracts](contracts.md) | A stale revision and/or a missing ID or invalid payload | Send commands with multiple possible errors | Follow the validation precedence described below; return a stable error code and the corresponding HTTP status | API | T031,T045 |
| <a id="c24"></a>C24 | [architecture](architecture.md) | A persisted board with ordering, colors, a blocker, and a checklist | Restart the backend and run bootstrap | The snapshot remains unchanged; bootstrap does not overwrite the existing board | Mongo integration | T032 |

## User interface and accessibility

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c25"></a>C25 | [ux](ux.md) | A form with entered text and a failed request | Simulate HTTP 400, HTTP 503, and a timeout | Preserve the draft, show a useful message, exit the saving state, and do not report false success | Vue unit, UI | T033,T036,T046 |
| <a id="c26"></a>C26 | [ux](ux.md); [contracts](contracts.md) | Two windows reading revision R | Save in the first window, then save different text in the second | The second receives HTTP 409, reloads, and preserves the draft; resubmission requires an explicit action | Vue, 2 browser contexts | T033,T046,T053 |
| <a id="c27"></a>C27 | [ux](ux.md) | The server saves but the response is lost due to a timeout | Attempt client recovery | Read the current state before resubmitting; do not automatically create a duplicate | Vue unit, E2E | T033,T046 |
| <a id="c28"></a>C28 | [ux](ux.md); [contracts](contracts.md) | A note/item deleted while open in another editor | Submit an edit from the stale editor | Return a consistent conflict or HTTP 404; keep the text available to copy; do not silently recreate the entity | Vue, E2E | T033,T046 |
| <a id="c29"></a>C29 | [REQ-07](requirements.md#req-07-in-column-reordering); [ux](ux.md) | A snapshot with all three states, defined ordering, and literal HTML text | Render the board | Preserve ordering; display HTML as text without v-html or script execution | Vue component | T034 |
| <a id="c30"></a>C30 | [ux](ux.md) | A keyboard-only user at 200% zoom | Create, edit, recolor, move, complete, and delete notes | All actions are reachable; focus is visible and restored; announcements and labels are present | Manual UI, selected E2E checks | T034,T036,T041,T048,T068 |
| <a id="c31"></a>C31 | [ux](ux.md) | A 360 px viewport and maximum-length content | View and use the editor and board | All controls are reachable; text wraps sensibly; colors have labels | Manual UI | T034,T048,T068 |

## HTTP validation and availability

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c32"></a>C32 | [contracts](contracts.md) | API requests with malformed JSON, extra fields, and incorrect types | Send direct requests | HTTP 400 with an error code and message without a stack trace; no mutation | HTTP integration | T039,T045 |
| <a id="c33"></a>C33 | [contracts](contracts.md) | A JSON command body larger than 64 KiB | Send the body and inspect the database | HTTP 413 and no mutation; verify the limit through the real request path | HTTP integration | T039,T045 |
| <a id="c34"></a>C34 | [contracts](contracts.md); [deployment](deployment.md) | MongoDB stopped and then restarted | Read/write and query health endpoints | Operations return HTTP 503; liveness stays healthy and readiness becomes unhealthy; recovery loses no data | HTTP integration, Docker | T039,T045,T052 |

## Containers, end-to-end behavior, and recovery

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c35"></a>C35 | [deployment](deployment.md) | Candidate images with version V and commit H | Start the application and inspect metadata | Both run as non-root, carry V/H labels, pass health checks, and connect successfully to the API | Docker | T049,T050,T052 |
| <a id="c36"></a>C36 | [deployment](deployment.md) | A freshly started delivery stack | Use only 127.0.0.1:8080 and the /api proxy | UI/API work; database/API have no published host ports; API errors are not replaced with HTML | Compose, HTTP | T050,T051,T052 |
| <a id="c37"></a>C37 | [deployment](deployment.md) | A note persisted on a named volume | Stop and start the stack without -v | Preserve the note, ordering, and checklist; normal shutdown does not remove the volume | Compose | T051,T052 |
| <a id="c38"></a>C38 | [REQ-01](requirements.md#req-01-note-creation-and-defaults); [REQ-04](requirements.md#req-04-note-editing-and-color-assignment); [contracts](contracts.md) | The real stack and a browser running the production frontend | Exercise CRUD, color changes, and reload | Values are persisted and IDs remain consistent; no fake data | Playwright E2E | T053 |
| <a id="c39"></a>C39 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement) | A stack with WIP limit 1 and two notes | Move both notes to DOING through the UI | The first succeeds; the second is rejected with a message explaining the WIP limit | Playwright E2E | T053 |
| <a id="c40"></a>C40 | [REQ-09](requirements.md#req-09-note-blocking-and-reasons) | A blocked note with a reason | Attempt a move, then unblock it | The move is rejected before unblocking and succeeds afterward; the reason is readable | Playwright E2E | T053 |
| <a id="c41"></a>C41 | [REQ-06](requirements.md#req-06-work-in-progress-enforcement); [REQ-11](requirements.md#req-11-guarded-transition-to-done); [REQ-12](requirements.md#req-12-immutability-of-done-checklists--reopening) | A DOING note with an incomplete checklist item | Complete → toggle the item → complete → reopen | Reject, then succeed; the DONE checklist is read-only; reopening respects capacity | Playwright E2E | T053 |
| <a id="c42"></a>C42 | [contracts](contracts.md); [ux](ux.md) | Two separate browser contexts | Edit concurrently using the same revision | No lost data; preserve the draft; require explicit resubmission | Playwright E2E | T053 |
| <a id="c43"></a>C43 | [deployment](deployment.md) | A test database with a complex snapshot | Back up and restore to a new volume | An equivalent snapshot is restored; schema/version are documented; the original database remains unchanged | Script integration | T054 |

## Documentation, CI, releases, installation, and audit

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c44"></a>C44 | [ci-cd](ci-cd.md); [index](index.md) | A documentation bundle and Pages site using the repository base path | Open the index, guides, and API reference | All relative links resolve; API documentation is actually generated | Link check + manual verification | T055,T068 |
| <a id="c45"></a>C45 | [ci-cd](ci-cd.md); [repository-governance](repository-governance.md) | A PR with a deliberate Vue/domain/E2E error | Run CI and attempt to merge | The required check fails; merge and release are blocked; reports are available | GitHub Actions | T014,T015,T056 |
| <a id="c46"></a>C46 | [ci-cd](ci-cd.md) | A candidate release bundle | Compare versions and recalculate checksums | Consistent version/SHA; all assets are present; no secrets | Release verification | T057,T059 |
| <a id="c47"></a>C47 | [deployment](deployment.md) | A computer/runner on a declared supported platform | Pull images from the registry by digest | The pull succeeds; the architecture is supported; labels are consistent | Registry + Docker | T058,T062 |
| <a id="c48"></a>C48 | [ci-cd](ci-cd.md) | Sample feat, fix, docs, and chore(release) commits | Simulate semantic-release version calculation and execution | Correct version increments; docs produces no release; chore(release) produces a patch; publish only after quality checks | Release dry-run + CI | T016,T059,T060 |
| <a id="c49"></a>C49 | [deployment](deployment.md) | A partial manifest and an older compatible release | Validate and test rollback on a test stack | Detect missing content; tags remain immutable; rollback preserves data | Release + Docker | T061 |
| <a id="c50"></a>C50 | [deployment](deployment.md) | An empty directory with only Docker Compose available | Follow the bundle quickstart | The application starts without Node, Gradle, or local repository caches | Independent installation | T062,T068 |
| <a id="c51"></a>C51 | [development](development.md) | Delivery lockfiles and images | Scan for vulnerabilities, license issues, and exposure | Document actual findings; resolve or mitigate applicable issues; make no false certification claims | Technical audit | T063 |

## Course traceability and handover

| Case | Requirement / local reference | Given | When | Expected result | Verification level | Delivery tasks |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="c52"></a>C52 | [index](index.md) | Every exam requirement stated in the slides | Follow the traceability links | The decision, code, test, run, and artifact are present and accessible | Documentation review | T065 |
| <a id="c53"></a>C53 | [development](development.md) | An author who did not write the demo script | Follow the demo and recovery procedure without assistance | Demonstrate all main cases; reset only the demo environment | Peer walkthrough | T066,T068 |
| <a id="c54"></a>C54 | [repository-governance](repository-governance.md) | A candidate commit/tag and PR history | Clone and compare the declared contributions | No essential untracked files; contributions and reviews are authentic | Git audit | T069 |
| <a id="c55"></a>C55 | [repository-governance](repository-governance.md) | The final bundle and index, with submission procedures confirmed | Submit and verify receipt and access | Submission is received; instructors can access it; both authors are identified | Human checklist | T070,T072 |

## Error precedence for C23

The imported case originally referred to operational decision S09. Its required sequence is stated here so the case is self-contained.

For backend commands: structural decoding/validation → board load → expected revision → entity presence → domain validation → atomic conditional save (CAS). Within domain validation: existing entity → valid payload → DONE mutation restrictions → blocker → transition → checklist → WIP/capacity → destination index. Tests combining several invalid conditions must identify the expected first error.

HTTP expectations are defined in [API contracts](contracts.md): malformed requests use 400, missing entities 404, revision/domain conflicts 409, database unavailability 503, and unexpected or invalid-persisted-state diagnostics 500. The JS facade returns domain/decoding codes without HTTP statuses; a non-decodable JSON request returns `INVALID_REQUEST`. Client prevalidation cannot guarantee server success.

## Maintaining the matrix

Update case definitions when an accepted requirement changes, and update the affected specifications and tests in the same change. Add links to new evidence documents or CI artifacts after verification. Preserve the distinction between a designed behavior, an implemented test, and an observed successful run. The Sheet remains the scheduling tool; this versioned matrix is the repository reference for case meanings.
