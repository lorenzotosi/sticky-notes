<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Local Development Guide

## Toolchain Requirements

- **JDK 21** for Java and Kotlin multiplatform modules.
- **Gradle 9.7.1** managed via the official `./gradlew` wrapper.
- **Node.js 24.21.0**, pinned in `.nvmrc`, compatible with semantic-release and Vite.

The repository uses the official Gradle wrapper. Binary distribution integrity is verified via `distributionSha256Sum` declared in `gradle/wrapper/gradle-wrapper.properties`.

## Shell Bootstrap

Set JDK 21 in the active shell without altering machine-wide global variables:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
java -version
./gradlew --version
```

On Windows, point `JAVA_HOME` to your JDK 21 installation path in your active PowerShell session before running `.\gradlew.bat --version`.

For Node.js, use a version manager that supports `.nvmrc` (such as nvm or fnm), then verify:

```sh
nvm use
node --version
npm --version
```

The toolchain verification must display Java 21 under Gradle's `Daemon JVM` section and Node.js `v24.21.0`.

## Build and Verification Entry Points

The project unifies all module checks into a non-circular Gradle task graph.

### Quick Module Commands

- **Commons (KMP):** `./gradlew :commons:check`
- **Backend (Spring Boot / Java):** `./gradlew :backend:check`
- **Frontend (Vue / Vite):** `./gradlew :frontend:frontendCheck` (or `cd frontend && npm run lint && npm test`)

### Custom Gradle Commands

| Command | Description |
| --- | --- |
| `./gradlew updateDependencyLocks` | Regenerates the dependency lockfiles for every Gradle subproject. Use it after changing a dependency or version in `gradle/libs.versions.toml`; the task enables Gradle's `--write-locks` mode automatically. It does not update `frontend/package-lock.json`. |
| `./gradlew documentation` | Copies the Markdown documentation from `docs/` into `build/docs/` so it can be handled as a build artifact. |
| `./gradlew verifyLicense` | Checks that source and configuration files contain the required SPDX license header. |
| `./gradlew fullTest` | Runs the Commons checks and all backend and frontend test suites without producing every final deliverable. |
| `./gradlew check` | Runs repository-wide verification: formatting, Detekt, license validation, JVM and JavaScript tests, frontend linting, and frontend tests. |
| `./gradlew fullBuild` | Runs `check`, then builds the Commons libraries, backend distributions, frontend production bundle, and documentation artifact. |
| `./gradlew :commons:browserDomainTest` | Runs the shared Commons domain tests in ChromeHeadless through the Kotlin/JS target. |
| `./gradlew :frontend:frontendInstall` | Installs the exact npm dependencies recorded in `frontend/package-lock.json` by running `npm ci`. |
| `./gradlew :frontend:frontendLint` | Installs the frontend dependencies when necessary and runs the ESLint checks. |
| `./gradlew :frontend:frontendTest` | Installs the frontend dependencies when necessary and runs the Vitest unit tests. |
| `./gradlew :frontend:frontendCheck` | Runs both `frontendLint` and `frontendTest`. |
| `./gradlew :frontend:frontendBuild` | Runs the frontend checks and creates the production Vite bundle in `frontend/dist/`. |

### Gradle Configuration Cache

Gradle configuration cache is supported across the task graph. You can execute:

```sh
./gradlew fullBuild --configuration-cache
```

Subsequent runs will reuse the cached task graph (`Configuration cache entry reused`).

*Note: MongoDB and end-to-end integration test execution will be wired into `fullVerify` during later database implementation tasks, rather than skipped.*

## Dependency Management and Locking

Shared dependencies and versions are declared in `gradle/libs.versions.toml`. Gradle lockfiles are generated for compile, runtime, and test classpaths:

```sh
./gradlew updateDependencyLocks
```

The custom task enables Gradle's lock-writing mode automatically, so `--write-locks` is not needed. Re-running it without dependency changes must not change the lockfile contents. Frontend dependencies are pinned via `frontend/package-lock.json`, while Kotlin/JS browser dependencies are managed via `kotlin-js-store/yarn.lock`.

Internal classpaths for Detekt, Gradle build plugins, and Kotlin Multiplatform compiler tooling are intentionally excluded from project application locks.

To inspect the resolved stdlib in the application runtime:

```sh
./gradlew :commons:dependencyInsight \
  --configuration jvmRuntimeClasspath \
  --dependency org.jetbrains.kotlin:kotlin-stdlib
```
