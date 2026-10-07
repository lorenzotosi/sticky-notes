<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Local Development Guide

## Toolchain Requirements

- **JDK 21** for Java and Kotlin multiplatform modules.
- **Gradle 9.7.1** managed via the official `./gradlew` wrapper.
- **Node.js 24.21.0**, pinned in `.nvmrc`, compatible with semantic-release and Vite.
- **Docker with Compose**, running Linux containers, for the backend MongoDB integration tests.

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
- **Backend (Java/Spring Boot):** `./gradlew :backend:check`
- **Frontend (Vue / Vite):** `./gradlew :frontend:frontendCheck` (or `cd frontend && npm run lint && npm test`)

### Custom Gradle Commands

| Command | Description |
| --- | --- |
| `./gradlew updateDependencyLocks` | Regenerates the dependency lockfiles for every Gradle subproject. Use it after changing a dependency or version in `gradle/libs.versions.toml`; the task enables Gradle's `--write-locks` mode automatically. It does not update `frontend/package-lock.json`. |
| `./gradlew documentation` | Generates the Dokka API reference for `commons` and copies it with the Markdown documentation into `build/docs/`. |
| `./gradlew verifyLicense` | Checks that source and configuration files contain the required SPDX license header. |
| `./gradlew testMongoUp` | Pulls the pinned MongoDB image when missing, creates or starts the test container, and waits up to 120 seconds for it to be healthy. Requires a running Docker engine and free local port `27018`. |
| `./gradlew testMongoDown` | Stops and removes the Docker Compose test environment. Its temporary MongoDB data is discarded. |
| `./gradlew fullTest` | Runs the Commons checks and all backend and frontend test suites without producing every final deliverable. |
| `./gradlew check` | Runs repository-wide verification: formatting, Detekt, license validation, JVM and JavaScript tests, frontend linting, and frontend tests. |
| `./gradlew fullBuild` | Runs `check`, then builds the Commons libraries, backend executable JAR, frontend production bundle, and documentation artifact. |
| `./gradlew :commons:browserDomainTest` | Runs the shared Commons domain tests in ChromeHeadless through the Kotlin/JS target. |
| `./gradlew :frontend:frontendInstall` | Installs the exact npm dependencies recorded in `frontend/package-lock.json` by running `npm ci`. |
| `./gradlew :frontend:frontendLint` | Installs the frontend dependencies when necessary and runs the ESLint checks. |
| `./gradlew :frontend:frontendTest` | Installs the frontend dependencies when necessary and runs the Vitest unit tests. |
| `./gradlew :frontend:frontendCheck` | Runs both `frontendLint` and `frontendTest`. |
| `./gradlew :frontend:frontendBuild` | Runs the frontend checks and creates the production Vite bundle in `frontend/dist/`. |

The `:backend:integrationTest` task starts MongoDB through `testMongoUp` and always finalizes with `testMongoDown`; `:backend:check`, `fullTest`, `check`, and `fullBuild` inherit that lifecycle. Docker must be installed and running, but the test container does not need to be started manually. IDE runs delegated to Gradle inherit the same lifecycle. When using the IDE's direct JUnit runner, run `./gradlew testMongoUp` before the test and `./gradlew testMongoDown` afterward. See [MongoDB integration tests](testing.md#backend-mongodb-integration-tests-r2). On Windows PowerShell, use `.\gradlew.bat testMongoUp` and `.\gradlew.bat testMongoDown`.

### Gradle Configuration Cache

Gradle configuration cache is supported across the task graph. You can execute:

```sh
./gradlew fullBuild --configuration-cache
```

Subsequent runs will reuse the cached task graph (`Configuration cache entry reused`).

## Backend runtime

The backend uses Spring Boot with an embedded Tomcat server. Run it from the repository root with the `local` profile:

```sh
./gradlew :backend:bootRun --args='--spring.profiles.active=local'
```

The configuration is in `backend/src/main/resources/application.yml`. The `local` profile binds the server to `127.0.0.1`. The `test` profile uses an automatically assigned HTTP port and a separate MongoDB database name.

| Environment variable | Purpose | Default |
| --- | --- | --- |
| `PORT` | HTTP port outside the `test` profile. | `8080` |
| `MONGODB_URI` | MongoDB connection URI outside the `test` profile. | `mongodb://127.0.0.1:27017/sticky_notes` |
| `TEST_MONGODB_URI` | MongoDB connection URI in the `test` profile. | `mongodb://127.0.0.1:27017/sticky_notes_test` |

Supply connection credentials through the environment; do not commit them to configuration files. Spring Data configures the MongoDB client used by `MongoBoardRepository` to load validated board snapshots and perform conditional saves. See [architecture](architecture.md) for the persistence boundary and [persistence unit tests](testing.md#backend-persistence-unit-tests) for verification commands.

At startup, `BoardBootstrap` attempts to create an empty `main` board in `boards`, with schema version `1`, revision `0`, and WIP limit `3`. Insert-only fields preserve an existing board across restarts. If MongoDB is unreachable, startup continues with a warning and liveness remains available; restart the backend after MongoDB recovers to retry initialization. There is no background bootstrap retry.

Check liveness after startup, using the configured port:

```sh
curl -i http://127.0.0.1:8080/actuator/health/liveness
```

The expected response is HTTP `200` with `{"status":"UP"}`, including when MongoDB is unavailable. `GET /actuator/health` aggregates health checks, including MongoDB connectivity, and may return `503` when the database is unavailable. `GET /actuator/health/readiness` currently reports application readiness without a MongoDB connectivity check.

Build and run the executable JAR with JDK 21:

```sh
./gradlew :backend:bootJar
java -jar backend/build/libs/backend.jar --spring.profiles.active=local
```

Stop an existing backend process with `Ctrl+C` before starting another on the same port. `fullBuild` also produces this JAR through `:backend:build`. See [testing](testing.md#backend-startup-and-liveness) for the automated startup check and [deployment](deployment.md) for release packaging.

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
