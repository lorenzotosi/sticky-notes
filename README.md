<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Sticky Notes

Kanban web application for persistent sticky notes. Kotlin Multiplatform shares the domain between a Java/Spring Boot backend and a Vue/JavaScript frontend.

## Versions

Versions `0.x.y` are initial development releases; interfaces and behavior may change incompatibly. Version `1.0.0` establishes the first stable public API and supported product scope. A prerelease suffix such as `-beta.1` identifies a preview of a specific version, not development in general.

See the [release policy](docs/ci-cd.md#release-policy) for stability criteria, release notes, and the relationship with integration gates R0-R5.

## Start here

Use JDK 21 and the [configured toolchain](docs/development.md#toolchain-requirements). Start the [development MongoDB instance](docs/development.md#full-stack-end-to-end-execution) before using the persistent board, then run these commands from the repository root:

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
./gradlew :frontend:frontendInstall
cd frontend
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

## Backend runtime image

Build the non-root container image that carries the same JAR:

```bash
./gradlew :backend:dockerImage
```

The image runs as an unprivileged account, records the version and the commit in its labels, and needs a reachable MongoDB through the `MONGODB_URI` environment variable. See [deployment](docs/deployment.md#backend-runtime-image) for the image metadata and the runtime configuration.

## Local stack with Docker Compose

The whole application also runs as three containers. The runtime of the stack needs neither a JDK nor Node.js on the machine; building the images needs the toolchain of the [development guide](docs/development.md#toolchain-requirements):

```bash
./gradlew buildImages
./scripts/start-local.sh
```

The interface is available at `http://127.0.0.1:8080`. Only that port is published: the API and MongoDB stay inside the Docker network. The board data is kept in a named volume and survives a stop.

```bash
./scripts/stop-local.sh
```

See [deployment](docs/deployment.md#delivery-stack) for the services, the configuration, and the development override.

## Repository layout

- `commons/`: Kotlin Multiplatform domain, targeting JVM and JavaScript.
- `backend/`: Java/Spring Boot application with Actuator health endpoints.
- `frontend/`: Vue application.
- `docs/`: architecture, process, CI/CD and deployment notes.
- `compose.yaml`, `compose.dev.yaml`: delivery stack and development override.
- `scripts/`: local stack lifecycle scripts.

The Vue frontend reads and changes the board through the backend REST API. The generated Kotlin/JS domain package supplies client prevalidation; the backend validates commands and persists the board in MongoDB. Reloading the page fetches the stored board.

See [shared-domain acceptance evidence](docs/evidence/R1.md) for the JVM/browser scenarios, Java/Vue consumers, generated artifacts, and reproduction commands.
