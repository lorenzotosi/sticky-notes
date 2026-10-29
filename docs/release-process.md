<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Release process

A release carries the artifacts a recipient can run, the version they share, and the records that let a reader prove the published bytes are the tested bytes. A GitHub release that carries release notes only is not a delivery.

## One version for every deliverable

Semantic Release computes the next version from the squash-merge titles on `main`, after the quality workflow succeeds; see the [release policy](ci-cd.md#release-policy) for the version rules. The root build owns the value and exposes it to every module:

| Where it lands | Name |
| --- | --- |
| Executable JAR | `Implementation-Version` and `Implementation-Revision` in the manifest |
| Kotlin/JS package | `version` in the generated `package.json` of `sticky-notes-domain` |
| Container images | `org.opencontainers.image.version` and `…revision` labels, plus the versioned tag |
| Stack configuration | `STICKY_NOTES_VERSION`, `STICKY_NOTES_REVISION`, and the image references of the bundle `.env` |
| Release records | `version`, `imageVersion`, and `revision` in `manifest.json` |

A release build passes the version through the `releaseVersion` property:

```sh
./gradlew releaseBundle -PreleaseVersion=1.2.3
```

Without that property the build identifies the commit it was produced from, as `0.0.0-dev+<commit>`. Docker tags do not accept `+`, so tags and image references carry the same value with `-` instead. Nothing in the repository hardcodes a version, and no deliverable references `latest`: a version is either the one supplied through that property or the commit the artifact was built from.

## The bundle

`./gradlew releaseBundle` assembles `build/release-bundle/`, the directory the quality workflow uploads and the release workflow packages as `sticky-notes-deliverables.zip`. The bundle keeps the repository paths, because the Dockerfiles and `compose.yaml` address the artifacts through them: an extracted bundle is a Docker build context.

| Path in the bundle | Content |
| --- | --- |
| `backend/build/libs/backend.jar` | Executable Spring Boot application with its runtime dependencies embedded; the only executable artifact. |
| `backend/Dockerfile` | Runtime image of the API, built from that JAR. |
| `frontend/dist/`, `frontend/Dockerfile`, `frontend/nginx.conf` | Production web bundle, its unprivileged Nginx image, and the same-origin reverse proxy for `/api`. |
| `commons/build/libs/commons-jvm.jar` | JVM library of the shared domain, for consumers; it needs the Kotlin standard library and is not executable on its own. |
| `commons/build/npm/sticky-notes-domain/` | Installable Kotlin/JS package of the shared domain, for consumers. |
| `compose.yaml`, `.dockerignore`, `.env.example` | Delivery stack and its documented configuration. |
| `.env` | Build identity and image references of this bundle, generated from the same version. |
| `build/docs/` | Markdown documentation, generated Dokka API reference, and the OpenAPI document of the same revision. |
| `README.md`, `LICENSE` | Startup instructions for the bundle and the project license. |
| `manifest.json`, `SHA256SUMS` | Provenance and integrity records. |

The bundle holds no shell script, no source checkout, and no credential. Every operation is a Docker Compose command documented in the bundle README: `docker compose up --build --detach --wait` builds the images from the delivered binaries and starts the stack, and `docker compose down` stops it without removing the board volume.

## Records

`manifest.json` records the version, the Docker-safe image version, the full commit, the source repository, the runtime requirements, the base images pinned by digest, and the size and SHA-256 of every file in the bundle. `SHA256SUMS` records the same digests in the format of the standard tools, so the check needs no project script:

```sh
sha256sum --check --quiet SHA256SUMS   # Linux
shasum -a 256 -c SHA256SUMS            # macOS
```

`.env` is editable configuration, so it stays out of both records; editing a port or a credential therefore keeps the check green. The version recorded in `manifest.json` is the one the images carry, and the versioned image references in `.env` repeat it.

A bundle built outside a release identifies the commit it was produced from. The tag of the release that carries it identifies the published version: the bundle is attached to the release of the revision the quality workflow built, and the release notes link the documentation of that tag.

## Limits

- The stack builds its images on the target machine from base images pinned by digest; the first build needs network access to pull them.
- The delivery covers one shared board without accounts, as described in [local scope](adr/002-local-scope.md).
- Backup and restore are repository tooling, not part of the bundle; see [backup](operations.md#backup) and [restore](operations.md#restore).
