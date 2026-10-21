<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Deployment

## Current delivery

The current continuous-delivery targets are GitHub Releases and GitHub Pages. Each release includes the compiled application deliverables produced by the verified build, while the project documentation is published through GitHub Pages. The application itself is not deployed to a runtime environment automatically.

## Executable backend

The backend deliverable is `backend/build/libs/backend.jar`, produced by `:backend:bootJar` and included in `fullBuild`. It contains the application, embedded server, and runtime dependencies. Running it requires Java 21; Gradle and the source checkout are not needed.

The quality workflow uploads this JAR with the Commons libraries and frontend bundle in the `deliverables` artifact. The release workflow downloads that artifact into `release-assets/` and packages it as `sticky-notes-deliverables.zip` for the GitHub release.

After extracting the release ZIP, run this command from the directory containing `release-assets/`:

```sh
java -jar release-assets/backend/build/libs/backend.jar --spring.profiles.active=local
```

See [backend runtime configuration](development.md#backend-runtime) for the profiles, environment variables, and liveness check. Stop any backend already using the configured port before starting this process.

## Backend runtime image

The backend is also delivered as a container image. The image carries the executable JAR on a JRE 21 base image pinned to a digest, and nothing else: no Gradle runtime, no sources, no build cache. The application runs as the unprivileged account `sticky-notes` with UID 10001, and it writes only to the temporary directory inherited from the base image and to its own `/app` directory.

Build the image from the repository root with a running Docker engine:

```sh
./gradlew :backend:dockerImage
```

The task produces the JAR first and assembles the image from it, so the image carries exactly the artifact the build produced. The image is tagged `sticky-notes-backend:<version>`, with `+` replaced by `-` because a Docker tag does not accept that character. The version itself comes from the `releaseVersion` property, which a release pipeline can pass to Gradle:

```sh
./gradlew :backend:dockerImage -PreleaseVersion=1.2.3
```

Without that property the build identifies the commit it was produced from, as `0.0.0-dev+<commit>`. That value is kept unchanged in the JAR manifest and in the image label, while the tag carries the same value as `0.0.0-dev-<commit>`. The version and the full commit are recorded twice: in the JAR manifest and in the image labels. The tag is also written to `backend/build/docker/image-tag.txt`, so a script can address the image without repeating the naming rule.

```sh
unzip -p backend/build/libs/backend.jar META-INF/MANIFEST.MF
docker inspect sticky-notes-backend:<version> --format '{{json .Config.Labels}}'
```

The labels are `org.opencontainers.image.version`, `org.opencontainers.image.revision`, `org.opencontainers.image.source`, and `org.opencontainers.image.licenses`.

The same image also receives the stable alias `sticky-notes-backend:local`. The versioned tag identifies the artifact for a release, while the alias is the reference the local Compose stack uses, because the configuration of the stack cannot follow the commit of the versioned tag.

Run the image against a reachable MongoDB:

```sh
docker run --rm -p 8080:8080 \
  -e MONGODB_URI=mongodb://host.docker.internal:27017/sticky_notes \
  sticky-notes-backend:<version>
```

The container exposes port 8080 and reads the same environment variables as the executable JAR. Its health check calls the readiness endpoint, which reports the application and the database it needs. `docker stop` sends `SIGTERM`, which the JVM receives directly and answers with a graceful shutdown.

| Item | Value |
| --- | --- |
| Base image | `eclipse-temurin:21-jre` pinned to a digest |
| Runtime user | `sticky-notes`, UID 10001 |
| Copied artifact | `backend/build/libs/backend.jar` |
| Entry point | `java -jar /app/backend.jar` |
| Stop signal | `SIGTERM` |
| Health check | `GET /actuator/health/readiness` |
| Configuration | `PORT` and `MONGODB_URI` environment variables |

## Delivery stack

`compose.yaml` describes the delivery runtime: three services on one internal network.

| Service | Image | Role | Published host port |
| --- | --- | --- | --- |
| `web` | `sticky-notes-web:local` | serves the interface and proxies `/api` | `127.0.0.1:8080` |
| `api` | `sticky-notes-backend:local` | applies the domain rules and stores the board | none |
| `mongo` | `mongo` pinned to a digest | stores the `main` board document | none |

Only the web service publishes a port, and only on the loopback interface. The API and MongoDB are reachable from the other containers of the stack and from nowhere else, so the browser always talks to a single origin. The board lives in the named volume `mongo-data`, which survives a stop, a `down` without volumes, and a restart of the machine.

The services start in dependency order. MongoDB reports healthy through an authenticated ping, the API reports healthy through its readiness endpoint, which includes the database, and the web container starts only after the API is healthy. `docker compose up --wait` therefore returns when the board can be used, without any fixed sleep.

The stack references images by name and never builds them, so it runs on a machine without a source checkout, a JDK, or Node.js.

### Configuration

The environment file `.env` holds the local configuration: the image references, the host ports, and the database credentials. `.env.example` documents the variables with demonstration values, and `scripts/start-local.sh` copies it to `.env` only when `.env` does not exist, so an existing configuration is never overwritten. The file stays out of Git.

| Variable | Purpose | Default |
| --- | --- | --- |
| `API_IMAGE` | image of the API service | `sticky-notes-backend:local` |
| `WEB_IMAGE` | image of the web service | `sticky-notes-web:local` |
| `WEB_PORT` | host port of the web interface | `8080` |
| `API_PORT` | host port of the API, development override | `8080` |
| `MONGO_PORT` | host port of the database, development override | `27017` |
| `MONGO_USER` | database user | `stickynotes_user` |
| `MONGO_PASSWORD` | password of the database user | `stickynotes_local_password` |
| `MONGO_DATABASE` | database of the shared board | `stickynotes` |

The credentials are demonstration values for a local Docker network. MongoDB creates the database user on the first start of an empty volume, so changing the credentials afterwards requires removing the volume, which deletes the board. A password with characters that need URL encoding has to be encoded in `MONGODB_URI`, because the stack embeds it in the connection URI.

### Starting and stopping the stack

Build the local images once, then start the stack:

```sh
./gradlew buildImages
./scripts/start-local.sh
```

`start-local.sh` checks that Docker Compose is available, creates `.env` when it is missing, and starts the stack with `up --detach --wait`. The interface is then available at `http://127.0.0.1:8080`.

```sh
./scripts/stop-local.sh
```

`stop-local.sh` runs `docker compose down` without `--volumes`: the containers and the network are removed, the board data stays in the named volume. Removing the volume deletes the board and is never part of a normal stop; the backup procedure covers what the volume does not.

### Development override

`compose.dev.yaml` adds the host ports needed during development: the API on `127.0.0.1:8080`, which the Vite development proxy targets, and MongoDB on `127.0.0.1:27017`, for local tooling. The published port of the web container is reset, because that port belongs to the API container in this mode and the interface comes from the Vite development server.

```sh
./scripts/start-local.sh --dev
./scripts/stop-local.sh --dev
```

The scripts are POSIX shell scripts and need Docker Compose only. They run on macOS and Linux with Docker Desktop or with the Compose plugin of a local Docker Engine.

### Published images

The stack uses published images instead of the local ones by setting `API_IMAGE` and `WEB_IMAGE` in `.env` to their published references, including a digest. Publishing the images belongs to the release process; registry credentials belong to GitHub Actions secrets, never to repository files.
