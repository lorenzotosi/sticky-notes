<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Sticky Notes

Minimal web application for creating sticky notes. Kotlin Multiplatform shares the domain between a Java/Spring Boot backend and a Vue/JavaScript frontend.

## Start here

Run these commands from the repository root using JDK 21:

```bash
./gradlew fullBuild
./gradlew :backend:bootRun --args='--spring.profiles.active=local'
```

The `local` profile binds the backend to `127.0.0.1`. The default port is `8080`; the `PORT` environment variable overrides it.

Check the running application at:

```text
http://127.0.0.1:8080/actuator/health/liveness
```

The liveness endpoint returns HTTP `200` with `{"status":"UP"}` when the application is running, even when MongoDB is unavailable.

The frontend is started separately during development:

```bash
cd frontend
npm ci
npm run dev
```

See the [local development guide](docs/development.md) for toolchain requirements and verification commands.

## Executable backend

Build and run the executable Spring Boot JAR from the repository root:

```bash
./gradlew :backend:bootJar
java -jar backend/build/libs/backend.jar --spring.profiles.active=local
```

Stop an existing backend process before starting another instance on the same port. Press `Ctrl+C` in its terminal to stop it.

## Repository layout

- `commons/`: Kotlin Multiplatform domain, targeting JVM and JavaScript.
- `backend/`: Java/Spring Boot application with Actuator health endpoints.
- `frontend/`: Vue application.
- `docs/`: architecture, process, CI/CD and deployment notes.

The Vue form creates in-memory notes through the generated Kotlin/JS domain package. The backend is not needed for this interaction; reloading the page discards the notes.

See [shared-domain acceptance evidence](docs/evidence/R1.md) for the JVM/browser scenarios, Java/Vue consumers, generated artifacts, and reproduction commands.
