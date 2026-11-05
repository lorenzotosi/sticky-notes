<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Release process

A release carries the artifacts a recipient can run, the version they share, and the records that prove the published bytes come from the tested commit.

## One version for every deliverable

Semantic Release calculates the next version from the squash-merge commit messages on `main`, after the quality workflow succeeds. See the [release policy](ci-cd.md#release-policy) for the version rules. The release workflow then runs the same build through the `releaseVersion` property, so every deliverable carries the published version:

```sh
./gradlew releaseBundle -PreleaseVersion=1.2.3
```

The version lands in the JAR manifest (`Implementation-Version`, `Implementation-Revision`), the generated `sticky-notes-domain` package, the container image labels and tags, the bundle `compose.yaml`, and `manifest.json`. Without the property the build identifies the commit as `0.0.0-dev+<commit>`; Docker tags use `-` instead of `+`. Nothing in the repository hardcodes a version, and no deliverable references `latest`.

## Publishing

Semantic Release computes the version, then its `prepare` hook runs `scripts/package-release.sh <version>`. The script rebuilds the bundle at that version, checks that the records describe the files it wrote, and packages `sticky-notes-deliverables.zip`. The GitHub plugin uploads the ZIP as a release asset. A build failure aborts the release before the tag exists.

The rebuild starts from the commit the quality workflow verified and skips the test suite, which already ran on that commit. The release only changes the identity the artifacts declare. The script also refuses a bundle whose recorded revision differs from the checked-out commit.

## The bundle

`./gradlew releaseBundle` assembles `build/release-bundle/`. The bundle keeps the repository paths, because the Dockerfiles and `compose.yaml` address the artifacts through them; an extracted bundle is a Docker build context.

| Path in the bundle | Content |
| --- | --- |
| `backend/build/libs/backend.jar` | Executable Spring Boot application with its runtime dependencies. |
| `backend/Dockerfile` | Runtime image of the API. |
| `frontend/dist/`, `frontend/Dockerfile`, `frontend/nginx.conf` | Production web bundle, Nginx image, and `/api` proxy. |
| `commons/build/libs/commons-jvm.jar` | JVM library of the shared domain. |
| `commons/build/npm/sticky-notes-domain/` | Kotlin/JS package of the shared domain. |
| `compose.yaml`, `.dockerignore` | Delivery stack with the stamped image tags. |
| `.env` | Runtime variables with demonstration defaults. |
| `build/docs/` | Markdown documentation, Dokka API reference, and OpenAPI document. |
| `README.md`, `LICENSE` | Startup instructions and project license. |
| `manifest.json`, `SHA256SUMS` | Provenance and integrity records. |

The bundle holds no shell script, no source checkout, and no credential. Every operation is a Docker Compose command documented in the bundle README.

## Records

`manifest.json` records the version, the Docker-safe image version, the full commit, the source repository, the runtime requirements, the base images pinned by digest, and the size and SHA-256 of every file. `SHA256SUMS` records the same digests in the standard format:

```sh
sha256sum --check --quiet SHA256SUMS   # Linux
shasum -a 256 -c SHA256SUMS            # macOS
```

`.env` stays out of both records, so editing a port or a credential keeps the check green.

## Limits

- The stack builds its images on the target machine from digest-pinned base images; the first build needs network access.
- The delivery covers one shared board without accounts, as described in [local scope](adr/002-local-scope.md).
- Backup and restore are repository tooling, not part of the bundle; see [backup](operations.md#backup) and [restore](operations.md#restore).
