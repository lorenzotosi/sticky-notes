<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Sticky Notes release bundle

This bundle runs the Sticky Notes Kanban board as three containers. Docker
Compose builds the API and web images from the binaries the bundle carries, so
the machine needs neither a JDK, nor Node.js, nor a source checkout.

## Requirements

- Docker Engine with the Compose plugin, Docker Compose v2.19 or later.
- Network access on the first build: the images start from base images pinned
  by digest, which Docker pulls once.

## Start

From this directory:

```sh
docker compose up --build --detach --wait
```

Compose builds the images, starts MongoDB, then the API, then the web server,
and returns when every service reports healthy. Open `http://127.0.0.1:8080`.
Only that port is published; the API and the database stay inside the Docker
network.

## Stop

```sh
docker compose down
```

The board lives in the named volume `sticky-notes_mongo-data` and survives a
stop and a restart of the machine. `docker compose down --volumes` deletes the
volume and the board with it, so it is never part of a normal stop.

## Configuration

`.env` carries the build identity and the image references of this bundle. The
Compose file supplies a default for every variable, so the stack also starts
without that file. To change a port or the demonstration database credentials,
edit `.env`; the values are documented in `.env.example`.

The credentials are demonstration values for a Docker network that is not
reachable from other machines. Changing them after the first start requires a
new volume, because MongoDB creates the database user only on an empty one.

## What the bundle contains

| Path | Content |
| --- | --- |
| `backend/build/libs/backend.jar` | Executable Spring Boot application with its runtime dependencies embedded. |
| `backend/Dockerfile` | Runtime image of the API, built from the JAR above. |
| `frontend/dist/`, `frontend/Dockerfile`, `frontend/nginx.conf` | Production web bundle, its unprivileged Nginx image, and the reverse proxy that forwards `/api`. |
| `commons/build/libs/commons-jvm.jar`, `commons/build/npm/sticky-notes-domain/` | The shared domain as a JVM library and a Kotlin/JS package, for consumers. The stack does not need them: the executable JAR embeds the domain. |
| `compose.yaml` | The delivery stack: services, network, volume, health checks. |
| `.env`, `.env.example` | Build identity and image references of this bundle, and the documented variables. |
| `build/docs/` | Documentation of this revision, including the generated domain API reference. |
| `manifest.json` | Version, commit, source, runtime requirements, base images, and the checksum of every file. |
| `SHA256SUMS` | SHA-256 checksums of every file except `.env`, which is editable. |

The bundle contains no shell script: every operation is a Docker Compose
command.

## Verify the bundle

```sh
sha256sum --check --quiet SHA256SUMS   # Linux
shasum -a 256 -c SHA256SUMS            # macOS
```

The delivered images carry the identity recorded in `.env`:

```sh
. ./.env
docker image inspect "$API_IMAGE" --format '{{index .Config.Labels "org.opencontainers.image.version"}}'
```

The value matches the `version` field of `manifest.json` and
`STICKY_NOTES_VERSION` in `.env`. A bundle attached to a release carries the
version that release publishes; one built outside a release names the commit it
was produced from. Either way the release notes link the documentation of the
release tag.

## Documentation

`build/docs/index.md` links the user, deployment, operations, and API
documentation of this revision.
