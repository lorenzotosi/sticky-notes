<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Sticky Notes

A Kanban web application for a single shared board of sticky notes. Notes are created, edited, moved between columns, blocked, and annotated with checklists!

## Run the released bundle with Docker Compose

Every GitHub release carries `sticky-notes-deliverables.zip`. Download it from the [releases](https://github.com/lorenzotosi/sticky-notes/releases) page and extract it: the ZIP carries the executable backend JAR, the production frontend bundle, the Dockerfiles, and `compose.yaml`, so the machine needs neither a JDK, nor Node.js, nor a source checkout. The only requirements are Docker Engine with the Compose plugin (Compose v2.19 or later) and network access on the first build, which pulls the base images pinned by digest.

From the extracted directory:

```sh
docker compose up --build --detach --wait
```

Compose builds the images from the delivered binaries, starts MongoDB, then the API, then the web server, and returns when every service reports healthy. Open `http://127.0.0.1:8080`; only that port is published, so the API and the database stay inside the Docker network.

```sh
docker compose down
```

The board lives in the named volume `sticky-notes_mongo-data` and survives a stop and a restart; `docker compose down --volumes` deletes the volume and the board with it. `.env` carries the ports and the demonstration database credentials of the bundle, and the bundle `README.md` documents its configuration, its version records (`manifest.json`), and the `SHA256SUMS` integrity check.

## Run the released bundle without Docker

The bundle also runs as plain host processes when you provide a Java 21 runtime and a MongoDB the backend can reach. From the extracted release directory:

```sh
java -jar backend/build/libs/backend.jar \
  --spring.profiles.active=local \
  --spring.web.resources.static-locations=file:frontend/dist/
```

The `local` profile binds the process to `127.0.0.1` on port `8080` (`PORT` overrides it), and the same process serves the frontend bundle at `http://127.0.0.1:8080` and the REST API under `/api`, so no reverse proxy is needed. `http://127.0.0.1:8080/actuator/health/readiness` reports both the application and the database it needs. Stop the process with `Ctrl+C`.

The backend reads `MONGODB_URI` and defaults to `mongodb://127.0.0.1:27017/sticky_notes` (while the Compose stack above configures `stickynotes`), so the variable is optional: set it only when your MongoDB listens on another host or port, uses another database name (such as `stickynotes`), or requires credentials, as in `MONGODB_URI=mongodb://user:password@127.0.0.1:27017/sticky_notes?authSource=admin java -jar backend/build/libs/backend.jar …`. The MongoDB of the Compose stack above is not reachable from the host because only the web port is published, so the host run needs a database of its own.

This host run is a manual alternative for a machine without containers: the delivered shape of the product is the Compose stack above, and the bundle ships no web-server configuration for the host run.

## Run the source tree with Gradle

From a clone, one Gradle command builds the images from the current source and starts the same stack:

```sh
git clone https://github.com/lorenzotosi/sticky-notes.git
cd sticky-notes
./gradlew startLocal
```

The requirements are a running Docker engine and a JDK 17 or later: the build provisions the JDK 21 of the [configured toolchain](docs/development.md#toolchain-requirements) when the machine does not provide it. `startLocal` creates `.env` from `.env.example` when none exists, and returns when every service reports healthy; the interface is available at `http://127.0.0.1:8080`. Stop the stack with:

```sh
./scripts/stop-local.sh
```

For development outside the containers — `./gradlew fullBuild`, `./gradlew :backend:bootRun`, and the Vite development server with the development override of the stack — see the [local development guide](docs/development.md).

## Documentation

- [Deployment](docs/deployment.md#delivery-stack): the services, the configuration, the runtime image, and the release delivery.
- [Local development](docs/development.md): toolchain, build entry points, backend runtime, and the containerized stack.
- [Release process](docs/release-process.md): the bundle contents, the single version it carries, and its records; the [release policy](docs/ci-cd.md#release-policy) covers version rules and release notes.
