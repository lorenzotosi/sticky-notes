<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Local Development Guide

## Toolchain Requirements

- **JDK 21** for Java and Kotlin multiplatform modules. Gradle provisions it when the machine does not provide it, through the Foojay Disco catalogue declared in `settings.gradle.kts`, so a JDK 17 or later is enough to start Gradle.
- **Gradle 9.7.1** managed via the official `./gradlew` wrapper.
- **Node.js 24.21.0**, pinned in `.nvmrc`, compatible with semantic-release and Vite.
- **Docker with Compose**, running Linux containers, for the backend MongoDB integration tests and the delivery stack.

The repository uses the official Gradle wrapper. Binary distribution integrity is verified via `distributionSha256Sum` declared in `gradle/wrapper/gradle-wrapper.properties`.

## Shell Bootstrap

Set JDK 21 in the active shell without altering machine-wide global variables:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
java -version
./gradlew --version
```

The export is a convenience for the development machine, not a requirement of the build: the modules compile with the JDK 21 toolchain, and Gradle downloads it when no local installation provides it. The daemon itself runs on any JDK 17 or later.

On Windows, point `JAVA_HOME` to your JDK 21 installation path in your active PowerShell session before running `.\gradlew.bat --version`.

For Node.js, use a version manager that supports `.nvmrc` (such as nvm or fnm), then verify:

```sh
nvm use
node --version
npm --version
```

The toolchain verification must display Java 21 under Gradle's `Daemon JVM` section when JDK 21 is the active one, and Node.js `v24.21.0`. The toolchains of the build are listed by `./gradlew javaToolchains`, which also reports the JDK downloaded by the resolver.

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
| `./gradlew :backend:dockerImage` | Builds the non-root backend runtime image from the verified JAR. Requires a running Docker engine. The image tag follows the `releaseVersion` property; without it the build identifies its commit. See [deployment](deployment.md#backend-runtime-image). |
| `./gradlew buildImages` | Builds the backend and web container images used by the local Compose stack. Requires a running Docker engine. See [deployment](deployment.md#delivery-stack). |
| `./gradlew containerSmoke` | Builds the container images and runs the end-to-end delivery stack smoke and lifecycle test in an isolated Compose project without touching local user volumes. Requires a running Docker engine. See [deployment](deployment.md#automated-smoke-and-lifecycle-verification). |
| `./gradlew backupRestoreVerify` | Builds the container images and verifies the backup and restore roundtrip in two isolated Compose projects without touching local user volumes. Requires a running Docker engine. See [testing](testing.md#backup-and-restore). |
| `./gradlew :frontend:frontendE2e` | Runs the Playwright browser journeys against the production container stack through the isolated `compose.test.yaml` stack, and removes that stack afterwards. Requires a running Docker engine. See [testing](testing.md#browser-e2e-tests-on-the-production-container-stack-r4). |
| `./gradlew fullVerify` | Runs `fullBuild`, `containerSmoke`, `backupRestoreVerify`, and `:frontend:frontendE2e`, combining code verification, deliverable packaging, MongoDB integration tests, container stack lifecycle checks, data recovery, and the browser journeys. Requires a running Docker engine. |
| `./gradlew :commons:browserDomainTest` | Runs the shared Commons domain tests in ChromeHeadless through the Kotlin/JS target. |
| `./gradlew :frontend:frontendInstall` | Installs the exact npm dependencies recorded in `frontend/package-lock.json` by running `npm ci`. |
| `./gradlew :frontend:frontendLint` | Installs the frontend dependencies when necessary and runs the ESLint checks. |
| `./gradlew :frontend:frontendTest` | Installs the frontend dependencies when necessary and runs the Vitest unit tests. |
| `./gradlew :frontend:frontendCheck` | Runs both `frontendLint` and `frontendTest`. |
| `./gradlew :frontend:frontendBuild` | Runs the frontend checks and creates the production Vite bundle in `frontend/dist/`. |

The `:backend:integrationTest` task starts MongoDB through `testMongoUp` and always finalizes with `testMongoDown`; `:backend:check`, `fullTest`, `check`, and `fullBuild` inherit that lifecycle. Docker must be installed and running, but the test container does not need to be started manually. IDE runs delegated to Gradle inherit the same lifecycle. When using the IDE's direct JUnit runner, run `./gradlew testMongoUp` before the test and `./gradlew testMongoDown` afterward. See [MongoDB integration tests](testing.md#backend-mongodb-integration-tests-r2). On Windows PowerShell, use `.\gradlew.bat testMongoUp` and `.\gradlew.bat testMongoDown`.

### Gradle Configuration Cache

Gradle can reuse the configured task graph with:

```sh
./gradlew fullBuild --configuration-cache
```

Subsequent compatible runs report `Configuration cache entry reused`. If Spotless reports a stale JVM-local cache, rerun with `--no-configuration-cache`.

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

`MongoConfiguration` applies five-second server-selection, connection, and operation timeouts after the URI settings, including when `MONGODB_URI` or `TEST_MONGODB_URI` is supplied. The operation timeout applies to each MongoDB operation, not the entire HTTP request.

At startup, `BoardBootstrap` attempts to create an empty `main` board in `boards`, with schema version `1`, revision `0`, and WIP limit `3`. Insert-only fields preserve an existing board across restarts. If MongoDB is unreachable, startup continues with a warning and liveness remains available; restart the backend after MongoDB recovers to retry initialization. There is no background bootstrap retry.

Check liveness after startup, using the configured port:

```sh
curl -i http://127.0.0.1:8080/actuator/health/liveness
```

The expected response is HTTP `200` with `{"status":"UP"}`, including when MongoDB is unavailable. `GET /actuator/health` aggregates health checks, including MongoDB connectivity. `GET /actuator/health/readiness` combines application readiness with MongoDB connectivity and returns `503` when the database is unavailable. HTTP management exposure is limited to `health` and `info`; health details are hidden and default info contributors are disabled.

Build and run the executable JAR with JDK 21:

```sh
./gradlew :backend:bootJar
java -jar backend/build/libs/backend.jar --spring.profiles.active=local
```

Stop an existing backend process with `Ctrl+C` before starting another on the same port. `fullBuild` also produces this JAR through `:backend:build`. See [testing](testing.md#backend-startup-and-liveness) for the automated startup check and [deployment](deployment.md) for release packaging and for the runtime image that carries the same JAR.


## Frontend development and local stack

The frontend is a Vue 3 SPA built with Vite. It interacts with the backend REST API via the Vite development proxy.

### Running the Frontend Locally

From the repository root, start the Vite development server:

```sh
./gradlew :frontend:frontendInstall
cd frontend
npm run dev
```

The application is available at http://localhost:5173.
In frontend/vite.config.js, requests matching /api are automatically proxied to the Spring Boot server running at http://localhost:8080:

```js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

### Full-Stack End-to-End Execution
To run and test the complete persistent vertical slice locally:

1. Start a development MongoDB instance on the API's default port. For the first run:
```sh
docker run -d --name sticky-notes-mongo -p 127.0.0.1:27017:27017 \
  -v sticky-notes-mongo-data:/data/db \
  mongo@sha256:f071cbf97a6e52fb7f07ce9ca5d862ad79876bf0eba9ba97758337d740449638
```
For later runs, use `docker start sticky-notes-mongo`. The named volume preserves development data. The integration-test container started by `testMongoUp` uses port 27018 and temporary memory-backed storage; it is not this development database.
2. Start Backend API:
```sh
./gradlew :backend:bootJar
java -jar backend/build/libs/backend.jar --spring.profiles.active=local
```
3. Start Frontend Dev Server:
```sh
./gradlew :frontend:frontendInstall
cd frontend
npm run dev
```

Open http://localhost:5173 in a browser. Board state, note creations, edits, and deletions persist across page reloads and backend restarts directly into MongoDB.

Stop the development database with `docker stop sticky-notes-mongo`; keep its named volume for the next session.


## Containerized local stack

The same application runs as three containers with Docker Compose. The runtime of the stack needs neither a JDK nor Node.js on the machine; building the images needs the toolchain of this guide. Build the images once and start the stack:

```sh
./gradlew buildImages
./scripts/start-local.sh
```

The interface is then available at `http://127.0.0.1:8080`. The stack publishes only that port: the API and MongoDB stay inside the Docker network. `./scripts/stop-local.sh` stops the stack and keeps the data in the named volume.

The development override runs the API and MongoDB on the loopback interface and leaves the interface to the Vite development server:

```sh
./scripts/start-local.sh --dev
```

The override publishes the API on port `8080` and MongoDB on port `27017`, the same ports of the workflow described above, so the two workflows are alternatives: only one of them runs at a time.

The details of both modes are described in [deployment](deployment.md#delivery-stack).


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
