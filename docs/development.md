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

### Repository-Wide Verification
- **Run all tests (JVM, JS browser, Vue):** `./gradlew fullTest`
- **Run all linters, static analysis, and license checks:** `./gradlew check`
- **Full end-to-end deliverable build:** `./gradlew fullBuild`

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
./gradlew :commons:dependencies :backend:dependencies --write-locks
```

Re-running the same command without `--write-locks` must not produce any Git diff. Frontend dependencies are pinned via `frontend/package-lock.json`, while Kotlin/JS browser dependencies are managed via `kotlin-js-store/yarn.lock`.

Internal classpaths for Detekt, Gradle build plugins, and Kotlin Multiplatform compiler tooling are intentionally excluded from project application locks.

To inspect the resolved stdlib in the application runtime:

```sh
./gradlew :commons:dependencyInsight \
  --configuration jvmRuntimeClasspath \
  --dependency org.jetbrains.kotlin:kotlin-stdlib
```
