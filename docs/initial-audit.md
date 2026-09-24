<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Initial Project Audit and Baseline Gaps

## 1. Baseline Context
- **Starting Commit**: `ecfb281bbf7b18b98b6c53c159bbb31186c434be` 
- **Initial Tag**: `v0.0.1` (present in repository history; must remain immutable)
- **Local Working Tree**: Untracked root `package-lock.json` detected and preserved for subsequent dependency locking in task T008.

---

## 2. Identified Deficiencies and Architecture Gaps (A01 - A14)

### A01 - Unproven Domain Sharing (Commons / KMP)
- **Evidence**: `commons/build.gradle.kts`, `frontend/package.json`, `frontend/src/App.vue`.
- **Finding**: KMP JVM and JS targets are declared, but no `@JsExport` facade exists in `jsMain`, and the frontend does not import or depend on the compiled commons package.
- **Resolution Plan**: Tasks T020, T022, T025, T028.

### A02 - Prototype Backend Stack
- **Evidence**: `backend/src/main/java/stickynotes/Main.java`, `backend/build.gradle.kts`.
- **Finding**: Backend relies on raw JDK `com.sun.net.httpserver.HttpServer` exposing only `/health`. Spring Boot, MongoDB integration, and note API endpoints are absent.
- **Resolution Plan**: Tasks T029–T039.

### A03 - Tautological / Non-Functional Tests
- **Evidence**: `backend/src/test/java/stickynotes/MainTest.java`, `frontend/src/App.test.js`.
- **Finding**: Java test only verifies `assertNotNull(Main.class)`. Vue test asserts `expect(true).toBe(true)`. A passing CI run currently gives false confidence on functional correctness.
- **Resolution Plan**: Tasks T012, T024, T032, T045, T053.

### A04 - Incomplete Domain Model
- **Evidence**: `commons/src/commonMain/kotlin/stickynotes/domain/Note.kt`.
- **Finding**: Contains a basic `Note` data class with an inline `NoteId` and freeform `NotePosition(x, y)`. The `Board` aggregate root, WIP limit logic, state transitions (TODO/DOING/DONE), blockers, and completion checklists are missing.
- **Resolution Plan**: Tasks T003, T005, T017–T027.

### A05 - Fragile Gradle Wrapper and Toolchain
- **Evidence**: `gradlew`, `gradle/wrapper/gradle-wrapper.properties`.
- **Finding**: Wrapper script defaults to host `PATH` Java instead of respecting `JAVA_HOME` / Java 21 toolchain. `gradlew.bat` and distribution SHA-256 verification are missing.
- **Resolution Plan**: Task T007.

### A06 - Transient Frontend State
- **Evidence**: `frontend/src/App.vue`.
- **Finding**: Sticky notes exist only in local reactive state (`ref([])`) with `crypto.randomUUID()`. Page reload loses all user data; no persistence or backend client is connected.
- **Resolution Plan**: Tasks T033–T043.

### A07 - Toolchain Dependency Drift
- **Evidence**: `package.json`, untracked root `package-lock.json`, workflow configs.
- **Finding**: Root dependencies and tooling (`semantic-release`, `commitlint`) are not strictly locked, making CI runs dependent on external registry resolution at build time.
- **Resolution Plan**: Tasks T008, T014.

### A08 - Incomplete Frontend Gradle Task Inputs
- **Evidence**: `frontend/build.gradle.kts`.
- **Finding**: `frontendInstall` does not declare the lockfile as an input, and `frontendBuild` omits `index.html`, `vite.config.js`, and the generated Kotlin package from its inputs, risking false UP-TO-DATE task states.
- **Resolution Plan**: Tasks T010, T022.

### A09 - Ineffective Frontend Linting
- **Evidence**: `frontend/eslint.config.js`.
- **Finding**: ESLint only targets `src/**/*.js` and lacks `eslint-plugin-vue`. Component template files (`.vue`) are not validated during lint checks.
- **Resolution Plan**: Task T012.

### A10 - Misconfigured Static Analysis Scope
- **Evidence**: Root `build.gradle.kts`.
- **Finding**: Detekt is applied across all subprojects, including pure Java and frontend modules, causing NO-SOURCE overhead while Kotlin build scripts are unverified.
- **Resolution Plan**: Task T011.

### A11 - Configuration Cache & Check Graph Separation
- **Evidence**: `gradle.properties`, root `build.gradle.kts`.
- **Finding**: Gradle configuration cache is enabled, but subproject check tasks are not aggregated into an explicit, cycle-free root verification graph.
- **Resolution Plan**: Task T013.

### A12 - Unprotected Release Workflow & Untrusted Dispatch
- **Evidence**: `.github/workflows/release.yml`, `pull-request-title.yml`.
- **Finding**: Release workflow can be triggered via unverified `workflow_dispatch`. GitHub Actions use floating tags instead of pinned full commit SHAs.
- **Resolution Plan**: Tasks T014, T016, T056, T060.

### A13 - Static Documentation Artifacts
- **Evidence**: `build.gradle.kts` (task `documentation`), `docs/*.md`.
- **Finding**: The documentation task simply copies raw Markdown files without compiling API reference documentation (e.g., Dokka).
- **Resolution Plan**: Tasks T026, T055.

### A14 - Missing Containerization and Local Orchestration
- **Evidence**: Repository root, `docs/deployment.md`.
- **Finding**: No Dockerfiles, Compose orchestration (`compose.yaml`), or production web reverse proxy exist for isolated local delivery.
- **Resolution Plan**: Tasks T049–T064.

---

## 3. Local Verification Status (A15)
- **Frontend**: Vitest tautological test, ESLint, and Vite production bundle build pass locally.
- **Gradle Build**: Complete `./gradlew fullBuild` is not validated locally pending Java 21 toolchain alignment and dependency locking.
- **Integrations**: MongoDB, Testcontainers, and Docker workflows are not yet executed.
