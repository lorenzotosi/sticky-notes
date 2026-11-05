<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Deployment

## Current delivery

Each GitHub release carries `sticky-notes-deliverables.zip` with the backend JAR, the production frontend bundle, the Dockerfiles, `compose.yaml`, and the documentation. GitHub Pages publishes only the documentation. The application runs as the delivery stack described [below](#delivery-stack).

## Executable backend

The backend deliverable is `backend/build/libs/backend.jar`, produced by `:backend:bootJar` and included in `fullBuild`. It contains the application, the embedded server, and the runtime dependencies. Running it requires Java 21.

`./gradlew releaseBundle` assembles the release bundle in `build/release-bundle/` with the JAR, the frontend bundle, the Compose stack, the shared-domain libraries, and the documentation. The release workflow rebuilds it at the published version and packages it as `sticky-notes-deliverables.zip`. The root of the ZIP is the Compose project directory. [Release process](release-process.md) describes the bundle, its version, and its records.

From the extracted directory, with a running Docker engine:

```sh
docker compose up --build --detach --wait
```

Compose builds the API and web images from the delivered binaries, starts them in dependency order behind MongoDB, and returns when every service is healthy. The app is reachable at `http://127.0.0.1:8080`. `docker compose down` stops the stack. The defaults in the Compose file cover every variable, so you can edit the generated `.env` freely.

The bundle also carries `README.md`, `manifest.json` with the version, commit, base images, and file digests, and `SHA256SUMS`:

```sh
sha256sum --check --quiet SHA256SUMS   # Linux
shasum -a 256 -c SHA256SUMS            # macOS
```

The same JAR can serve the app from `frontend/dist/` when no container is available:

```sh
java -jar backend/build/libs/backend.jar --spring.profiles.active=local \
  --spring.web.resources.static-locations=file:frontend/dist/
```

This host run is a manual alternative. In the Compose stack the web container serves the app and forwards `/api` to the API container.

## Backend runtime image

The API image carries the executable JAR on a JRE 21 base image pinned to a digest, and nothing else. It runs as the unprivileged user `sticky-notes` (UID 10001).

```sh
./gradlew :backend:dockerImage
```

The build tags the image `sticky-notes-backend:<version>` and adds the alias `sticky-notes-backend:local`. The Compose stack uses that alias. The version comes from the `releaseVersion` property. Without it the build identifies the commit, as `0.0.0-dev+<commit>`. Docker tags do not accept `+`, so the tag uses `-`.

| Item | Value |
| --- | --- |
| Base image | `eclipse-temurin:21-jre` pinned to a digest |
| Runtime user | `sticky-notes`, UID 10001 |
| Entry point | `java -jar /app/backend.jar` |
| Stop signal | `SIGTERM` |
| Health check | `GET /actuator/health/readiness` |
| Configuration | `PORT` and `MONGODB_URI` |

The health check calls readiness, which reports both the application and the database. `docker stop` sends `SIGTERM`, and the JVM answers with a graceful shutdown.

## Delivery stack

`compose.yaml` describes the delivery runtime: three services on one private bridge network.

| Service | Image | Role | Published host port |
| --- | --- | --- | --- |
| `web` | `sticky-notes-web:local` | serves the app and proxies `/api` | `127.0.0.1:8080` |
| `api` | `sticky-notes-backend:local` | applies the domain rules and stores the board | none |
| `mongo` | `mongo` pinned to a digest | stores the `main` board document | none |

Only the web service publishes a port, and only on the loopback interface. The API and MongoDB are reachable only from the other containers. The browser therefore talks to a single origin. The board lives in the named volume `mongo-data`, which survives a stop, a `down` without volumes, and a machine restart.

The services start in dependency order. MongoDB reports healthy through an authenticated ping, the API through its readiness endpoint, and the web container starts only after the API is healthy. `docker compose up --wait` therefore returns when the board can be used.

The stack builds its images only when they are missing. After `./gradlew buildImages` the pre-built images run unchanged. The release ZIP builds the same images from the binaries it carries.

### Configuration

`.env` holds the local configuration: host ports, database credentials, and optional image overrides. `.env.example` documents the variables with demonstration values. `./scripts/start-local.sh` copies it to `.env` only when `.env` does not exist. The file stays out of Git.

| Variable | Purpose | Default |
| --- | --- | --- |
| `API_IMAGE` | image of the API service, optional override | `sticky-notes-backend:local` |
| `WEB_IMAGE` | image of the web service, optional override | `sticky-notes-web:local` |
| `WEB_PORT` | host port of the app | `8080` |
| `API_PORT` | host port of the API, development override | `8080` |
| `MONGO_PORT` | host port of the database, development override | `27017` |
| `MONGO_USER` | database user | `stickynotes_user` |
| `MONGO_PASSWORD` | password of the database user | `stickynotes_local_password` |
| `MONGO_DATABASE` | database of the shared board | `stickynotes` |

The credentials are demonstration values for a local Docker network. MongoDB creates the user on the first start of an empty volume. Changing the credentials later requires removing the volume, and the board goes with it.

### Starting and stopping the stack

```sh
./gradlew startLocal
./scripts/stop-local.sh
```

`startLocal` runs `buildImages` and then `start-local.sh`. The script creates `.env` when missing and starts the stack with `up --detach --wait`. `stop-local.sh` runs `docker compose down` without `--volumes`. It removes the containers and the network, and the board stays in the named volume. Removing the volume deletes the board and is never part of a normal stop.

### Development override

`compose.dev.yaml` publishes the API on `127.0.0.1:8080` for the Vite proxy and MongoDB on `127.0.0.1:27017` for local tooling. The override resets the web port, because the app comes from the Vite development server.

```sh
./scripts/start-local.sh --dev
./scripts/stop-local.sh --dev
```

### Automated smoke and lifecycle verification

`scripts/smoke-test.sh` runs the stack in an isolated Compose project (`sticky-notes-smoke`, loopback port `18080`). It never touches the local `.env` or the `sticky-notes_mongo-data` volume.

```sh
./gradlew containerSmoke
```

The check covers:

- All containers reach `healthy`.
- Only `web` publishes a port.
- `web` and `api` run unprivileged.
- The SPA and the JSON `/api` error paths are served correctly.
- CRUD works through the proxy, with revision checks.

The check also stops and restarts `api` and `mongo` to confirm that the board survives in the named volume. On failure it prints container statuses and logs, then removes the isolated containers and volume.

### Backup and restore

The named volume survives stops and restarts but is not a backup. Use `scripts/backup.sh` and `scripts/restore.sh`. [Operations](operations.md#backup) has the procedures, data compatibility, and volume-safety rules.

## Troubleshooting

| Symptom | Cause | Check and remedy |
| --- | --- | --- |
| The start script exits before any container starts | Docker is not running, or Compose is missing | Run `docker info` and `docker compose version`. |
| Port `8080` is in use | Another process listens on the app port | Run `lsof -i :8080`. Stop that process, or set `WEB_PORT` (stack) or `PORT` (JAR). |
| The `web` service never becomes healthy | The API is not ready, or MongoDB is unreachable | Run `docker compose ps`. Then send the readiness request inside the API container: `docker compose exec api curl --fail --silent -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/actuator/health/readiness`. It answers `503` while the database is unavailable. |
| A mutation returns `503 DATABASE_UNAVAILABLE`, or no board exists after startup | MongoDB was unreachable when the backend started | `BoardBootstrap` does not retry in the background. Restart the backend once the database is reachable. |
| A mutation returns `409 REVISION_CONFLICT` | The submitted snapshot is older than the stored revision | Reload the snapshot and send the command again. The backend never retries a conflicting command. |
