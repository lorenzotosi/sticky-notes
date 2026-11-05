<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Local Development Guide

## Toolchain Requirements

- **JDK 21** for the Java and Kotlin Multiplatform modules. Gradle downloads it through the Foojay Disco catalogue when the machine does not provide it. A JDK 17 or later is enough to start Gradle.
- **Gradle 9.7.1**, managed by the official `./gradlew` wrapper.
- **Node.js 24.21.0**, pinned in `.nvmrc`.
- **Docker with Compose**, running Linux containers, for the MongoDB integration tests and the delivery stack.

The wrapper checks the distribution through `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`.

## Build and Verification Entry Points

### Quick Module Commands

- **Commons (KMP):** `./gradlew :commons:check`
- **Backend (Java/Spring Boot):** `./gradlew :backend:check`
- **Frontend (Vue / Vite):** `./gradlew :frontend:frontendCheck`

### Custom Gradle Commands

| Command | Description |
| --- | --- |
| `./gradlew updateDependencyLocks` | Regenerates the Gradle lockfiles after a dependency change. |
| `./gradlew formatCode` | Formats Kotlin, Java, Gradle, and frontend sources. |
| `./gradlew prepareCommit` | Runs `formatCode` and `verifyLicense` before committing. |
| `./gradlew documentation` | Generates the Dokka API reference. Copies it and the Markdown documentation into `build/docs/`. |
| `./gradlew verifyLicense` | Checks the SPDX license header on sources and configuration files. |
| `./gradlew testMongoUp` | Starts the pinned MongoDB test container on port `27018`. |
| `./gradlew testMongoDown` | Stops and removes the test container. |
| `./gradlew fullTest` | Runs the Commons, backend, and frontend test suites. |
| `./gradlew check` | Runs repository-wide verification: formatting, licenses, Detekt, domain boundaries, tests, coverage, and frontend checks. |
| `./gradlew fullBuild` | Runs `check`, then builds the Commons libraries, the backend JAR, the frontend bundle, and the documentation. |
| `./gradlew buildImages` | Builds the backend and web container images. Requires Docker. |
| `./gradlew startLocal` | Builds the images from the current source and starts the local Compose stack. |
| `./gradlew containerSmoke` | Runs the delivery stack smoke and lifecycle test in an isolated Compose project. |
| `./gradlew backupRestoreVerify` | Checks the backup and restore roundtrip in isolated Compose projects. |
| `./gradlew :frontend:frontendE2e` | Runs the Playwright journeys against the production container stack. |
| `./gradlew fullVerify` | Runs `fullBuild`, `containerSmoke`, `backupRestoreVerify`, `releaseVerify`, and the browser journeys. |
| `./gradlew releaseBundle` | Assembles the release bundle in `build/release-bundle/`. |
| `./gradlew releaseVerify` | Checks the release bundle contents, records, and digests. |

The `:backend:integrationTest` task starts MongoDB through `testMongoUp`. It always ends with `testMongoDown`. `:backend:check`, `fullTest`, `check`, and `fullBuild` inherit the same lifecycle. Docker must be running. You do not start the test container by hand.

### Gradle Configuration Cache

```sh
./gradlew fullBuild --configuration-cache
```

Subsequent compatible runs report `Configuration cache entry reused`. If Spotless reports a stale cache, rerun with `--no-configuration-cache`.

## Backend runtime

The backend uses Spring Boot with embedded Tomcat. Run it from the repository root with the `local` profile:

```sh
./gradlew :backend:bootRun --args='--spring.profiles.active=local'
```

The configuration is in `backend/src/main/resources/application.yml`. The `local` profile binds the server to `127.0.0.1`. The `test` profile uses an assigned HTTP port and a separate database name.

| Environment variable | Purpose | Default |
| --- | --- | --- |
| `PORT` | HTTP port outside the `test` profile. | `8080` |
| `MONGODB_URI` | MongoDB connection URI outside the `test` profile. | `mongodb://127.0.0.1:27017/sticky_notes` |
| `TEST_MONGODB_URI` | MongoDB connection URI in the `test` profile. | `mongodb://127.0.0.1:27017/sticky_notes_test` |

Supply credentials through the environment. Do not commit them.

At startup, `BoardBootstrap` creates an empty `main` board when none exists. Insert-only fields preserve an existing board across restarts. If MongoDB is unreachable, startup continues with a warning. Restart the backend after recovery to retry initialization.

Check liveness after startup:

```sh
curl -i http://127.0.0.1:8080/actuator/health/liveness
```

The expected response is `200 {"status":"UP"}`. `GET /actuator/health/readiness` also checks MongoDB connectivity. It returns `503` when the database is unavailable. Management exposes only `health` and `info`. Health details are not shown.

Build and run the executable JAR with JDK 21:

```sh
./gradlew :backend:bootJar
java -jar backend/build/libs/backend.jar --spring.profiles.active=local
```

## Frontend development and local stack

The frontend is a Vue 3 SPA built with Vite. It calls the backend through the development proxy.

### Running the Frontend Locally

```sh
./gradlew :frontend:frontendInstall
cd frontend
npm run dev
```

The Vite dev server serves the app at `http://localhost:5173`. `frontend/vite.config.js` proxies requests that match `/api` to the Spring Boot server on `http://localhost:8080`.

### Full-Stack End-to-End Execution

1. Start a development MongoDB instance:

   ```sh
   docker run -d --name sticky-notes-mongo -p 127.0.0.1:27017:27017 \
     -v sticky-notes-mongo-data:/data/db \
     mongo@sha256:e0ce8c35124d4a9f9785532d1f268f39e9728ffa1cb38f46fa482436424c4bd3
   ```

   Later runs use `docker start sticky-notes-mongo`. The test container from `testMongoUp` uses port 27018 and temporary storage. It is not this database.

2. Start the backend:

   ```sh
   ./gradlew :backend:bootJar
   java -jar backend/build/libs/backend.jar --spring.profiles.active=local
   ```

3. Start the frontend:

   ```sh
   ./gradlew :frontend:frontendInstall
   cd frontend
   npm run dev
   ```

Open `http://localhost:5173`. New notes, edits, and deletions persist in MongoDB across page reloads and backend restarts. Stop the development database with `docker stop sticky-notes-mongo`.

## Containerized local stack

The application runs as three Docker Compose containers. One command builds the images from the current source and starts the stack:

```sh
./gradlew startLocal
```

`./gradlew buildImages` builds the images without starting them. `./scripts/start-local.sh` starts images that are already built. The app is reachable at `http://127.0.0.1:8080`. Compose publishes only that port. `./scripts/stop-local.sh` stops the stack and keeps the data in the named volume.

The development override publishes the API and MongoDB on loopback. The Vite server serves the app:

```sh
./scripts/start-local.sh --dev
```

It publishes the API on `8080` and MongoDB on `27017`. This is an alternative to the manual workflow above. The details of both modes are in [deployment](deployment.md#delivery-stack).
