<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Release process

A release carries the artifacts a recipient can run, the version they share, and the records that let a reader prove the published bytes come from the tested commit and match the version the release publishes. A GitHub release that carries release notes only is not a delivery.

## One version for every deliverable

Semantic Release computes the next version from the squash-merge titles on `main`, after the quality workflow succeeds; see the [release policy](ci-cd.md#release-policy) for the version rules. The release workflow then runs the same build through the `releaseVersion` property, so every deliverable carries the version the release publishes instead of the identity of the build that verified the commit. The root build owns the value and exposes it to every module:

| Where it lands | Name |
| --- | --- |
| Executable JAR | `Implementation-Version` and `Implementation-Revision` in the manifest |
| Kotlin/JS package | `version` in the generated `package.json` of `sticky-notes-domain` |
| Container images | `org.opencontainers.image.version` and `…revision` labels, plus the versioned tag |
| Stack configuration | The `VERSION` and `REVISION` build arguments stamped into the bundle `compose.yaml`, and the image references of the bundle `.env` |
| Release records | `version`, `imageVersion`, and `revision` in `manifest.json` |

A release build passes the version through the `releaseVersion` property:

```sh
./gradlew releaseBundle -PreleaseVersion=1.2.3
```

Without that property the build identifies the commit it was produced from, as `0.0.0-dev+<commit>`. Docker tags do not accept `+`, so tags and image references carry the same value with `-` instead. Nothing in the repository hardcodes a version, and no deliverable references `latest`: a version is either the one supplied through that property or the commit the artifact was built from.

## Publishing

Semantic Release computes the version, then its `prepare` hook runs `scripts/package-release.sh <version>`. The script rebuilds the bundle at that version, checks that the records describe the files it just wrote, and packages `sticky-notes-deliverables.zip`; the GitHub plugin uploads the ZIP as a release asset afterwards. A build failure therefore aborts the release before the tag exists.

The rebuild starts from the commit the quality workflow verified and skips the test suite: the tests already ran on that commit, and the release only changes the identity the artifacts declare. The script also refuses a bundle whose recorded revision differs from the checked-out commit, so a publish cannot silently carry the development identity.

## The bundle

`./gradlew releaseBundle` assembles `build/release-bundle/`; the release workflow rebuilds and packages it as `sticky-notes-deliverables.zip`, and the quality workflow builds it on `main` to fail an assembly regression before a release. The bundle keeps the repository paths, because the Dockerfiles and `compose.yaml` address the artifacts through them: an extracted bundle is a Docker build context.

| Path in the bundle | Content |
| --- | --- |
| `backend/build/libs/backend.jar` | Executable Spring Boot application with its runtime dependencies embedded; the only executable artifact. |
| `backend/Dockerfile` | Runtime image of the API, built from that JAR. |
| `frontend/dist/`, `frontend/Dockerfile`, `frontend/nginx.conf` | Production web bundle, its unprivileged Nginx image, and the same-origin reverse proxy for `/api`. |
| `commons/build/libs/commons-jvm.jar` | JVM library of the shared domain, for consumers; it needs the Kotlin standard library and is not executable on its own. |
| `commons/build/npm/sticky-notes-domain/` | Installable Kotlin/JS package of the shared domain, for consumers. |
| `compose.yaml`, `.dockerignore` | Delivery stack with the stamped build identity. |
| `.env` | Image references and runtime variables of this bundle, generated from the same version. |
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

`.env` is editable configuration, so it stays out of both records; editing a port or a credential therefore keeps the check green. The version recorded in `manifest.json` is the one the images carry: the bundle `compose.yaml` stamps it into the build arguments, which the checksums cover, and the versioned image references in `.env` repeat it.

A bundle built outside a release identifies the commit it was produced from; the published bundle carries the version the release publishes and the revision the release job checked out. Either way the release notes link the documentation of the tag that carries the ZIP.

## Limits

- The stack builds its images on the target machine from base images pinned by digest; the first build needs network access to pull them.
- The delivery covers one shared board without accounts, as described in [local scope](adr/002-local-scope.md).
- Backup and restore are repository tooling, not part of the bundle; see [backup](operations.md#backup) and [restore](operations.md#restore).
