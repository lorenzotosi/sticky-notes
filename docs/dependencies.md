<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Dependency, license, and exposure review

This document records the runtime dependencies of Sticky Notes, the licenses that apply to the delivered artifacts, and the result of the vulnerability review of the delivery stack.

## Scope and method

The review covers what a recipient runs:

- The backend runtime classpath resolved by Gradle, including transitive artifacts.
- The frontend production dependency tree and the built browser bundle.
- The three container images of the delivery stack, including their operating-system packages.
- The repository and the release bundle for tracked secrets and license attribution.

The review uses three tools:

| Scope | Tool | Version |
| --- | --- | --- |
| npm dependencies | `npm audit` | npm 12.2.1, Node.js 24.21.0 |
| Java runtime artifacts | OSV batch query | `https://api.osv.dev/v1/querybatch`, Maven ecosystem |
| Container images | Trivy | 0.76.1, pinned by digest |

Build and test dependencies are reported separately from the runtime dependencies. A finding in a tool that does not enter the delivered artifact is not a finding in the delivery, but it is still recorded because the release and test pipelines execute that tool.

## Runtime inventory

### Backend

The backend runs on Java 21 and Spring Boot 4.1.1. The resolved runtime classpath contains 67 artifacts. Dependency locking records the resolved versions, and explicit constraints override versions requested by the Spring Boot BOM where a newer patch was required:

| Component | Version | Note |
| --- | --- | --- |
| Spring Boot | 4.1.1 | Platform BOM and starters |
| Spring Framework | 7.0.9 | Transitive from Spring Boot |
| Apache Tomcat (embedded) | 11.0.26 | Constraint; the BOM requests 11.0.24 |
| Logback | 1.6.5 | Constraint; the BOM requests 1.5.38 |
| SLF4J | 2.0.19 | Logging facade |
| MongoDB driver | 5.11.1 | Synchronous driver and BSON |
| Jackson | 3.1.7 | JSON binding |
| Kotlin standard library | 2.3.21 | Runtime of the shared domain |

The runtime image uses `eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a`, based on Ubuntu 26.04.1.

### Frontend

| Component | Version | Role |
| --- | --- | --- |
| Vue | 3.5.39 | Browser runtime |
| Vite | 7.3.6 | Build and development server |
| Vitest | 3.2.4 | Unit tests |
| Playwright | 1.52.0 | Browser end-to-end tests |
| `sticky-notes-domain` | project build | Shared Kotlin/JS domain package |

Only Vue and the shared domain package enter the production browser bundle. Vite, Vitest, Playwright, ESLint, and the coverage tooling are build or test dependencies.

### Images

| Service | Base image | Operating system |
| --- | --- | --- |
| `web` | `nginxinc/nginx-unprivileged:1.31.7-alpine3.24-slim@sha256:a8cb0f33d51fa107c96fd2bd3a71aaf564ec131e9d2bdf8700944f0ed8b170d7` | Alpine 3.24.2 |
| `api` | `eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a` | Ubuntu 26.04.1 |
| `mongo` | `mongo@sha256:e0ce8c35124d4a9f9785532d1f268f39e9728ffa1cb38f46fa482436424c4bd3` | Ubuntu 24.04, MongoDB 8.2.12 |

The delivery stack publishes only `web` on `127.0.0.1:8080`. The API and MongoDB services have no host port. The backend runs as UID 10001 and the web server as UID 101. The test stack in `compose.test.yaml` uses the same MongoDB digest.

## Vulnerability results

### npm

| Scope | Total | Critical | High | Moderate |
| --- | ---: | ---: | ---: | ---: |
| Root release tooling | 41 | 1 | 2 | 38 |
| Frontend, all dependencies | 27 | 3 | 10 | 14 |
| Frontend, production dependencies | 5 | 0 | 4 | 1 |

The root advisories belong to `semantic-release` and its transitive dependencies. They execute in the release pipeline and do not enter the delivered stack.

The frontend advisories with a critical severity belong to `vitest`, `@vitest/coverage-v8`, and `tinypool`. They execute only in tests. The production-tree advisories are:

| Severity | Package | Applicability |
| --- | --- | --- |
| High | `vue` through `@vue/server-renderer` | The advisory affects server-side rendering; this application is a client-side SPA and the built bundle does not reference the server renderer. |
| High | `nanoid` | Transitive build dependency; not referenced by the built browser bundle. |
| High | `source-map-js` | Source-map tooling; not part of the runtime path. |
| Moderate | `postcss` | Build tooling; not part of the runtime path. |

The built SPA bundle does not contain `@vue/server-renderer`, `nanoid`, `source-map-js`, `postcss`, `js-yaml`, `tinypool`, or `vitest`.

The non-major updates that remove the development advisories are available: `vitest` and `@vitest/coverage-v8` 3.2.7, `@playwright/test` 1.64.1, and patch releases of Vue and Vite. They belong in a dedicated dependency-update pull request, not in a mixed quality change. `npm audit fix --force` is not used because it applies major updates without review.

### Java

The OSV batch query covered all 67 runtime artifacts. It returned no advisory for the resolved versions. As a control, the same query returns eight advisories for `org.apache.logging.log4j:log4j-core:2.14.1`, so an empty result reflects the queried versions rather than a failed query.

This result depends on the locked versions. A dependency update that changes a resolved version requires a new query.

### Images

The images were rebuilt from this revision before the final scan. The result includes operating-system packages and the Java artifacts inside the backend image. The image table reports the CRITICAL and HIGH filter used by the scan; it is not a statement that the images have no lower-severity findings.

| Image | Findings | Unique identifiers | Critical | High | Fix available |
| --- | ---: | ---: | ---: | ---: | ---: |
| `web` | 0 | 0 | 0 | 0 | — |
| `api` | 0 | 0 | 0 | 0 | — |
| `mongo` | 336 | 47 | 1 | 335 | 336 |

The earlier base images are the reason the web and API images now scan clean. The previous web base used Alpine 3.21.3, which Trivy marks as end-of-life, and reported 25 findings, including two critical OpenSSL findings that apply to 32-bit systems only. The previous API base reported five high findings in packages outside the application process. The previous MongoDB image was 8.2.6 and reported 370 findings. The current pins are the remediated ones listed in [Images](#images).

The remaining MongoDB findings are not in the database server process. They are in the bundled Go utilities (`mongodump`, `mongorestore`, `mongoexport`, `mongoimport`, `mongostat`, `mongotop`, `bsondump`, `mongofiles`), in `gosu`, and in three Ubuntu libraries. The critical finding is a Go `crypto/tls` issue in `gosu`; `gosu` only changes the process user at container start and does not perform TLS. The delivery stack does not enable TLS and does not publish the MongoDB port. The backup and restore procedures operate on local dump files. The official 8.2.12 image has not rebuilt those Go tools with a fixed toolchain, so the finding is recorded as a known limitation of the upstream image rather than a defect in the application.

After the base-image update, `./gradlew containerSmoke` verified the stack lifecycle, non-root users, host-port isolation, SPA routing, CRUD persistence, and volume teardown. `./gradlew backupRestoreVerify` verified a MongoDB 8.2.12 backup and restore roundtrip, and `./gradlew :frontend:frontendE2e` verified the browser journeys against the Nginx 1.31.7 image. All three completed successfully.

## Exposure checks

| Check | Result |
| --- | --- |
| Tracked secrets | None. Only `.env.example` is tracked; `.env` and `.env.*` are ignored. |
| Demonstration credentials | The values in `.env.example` are labelled as local demonstration values and are not production secrets. |
| Published ports | Only the web interface is published, on `127.0.0.1:8080`. The API and MongoDB have no host port. |
| Container users | The backend runs as UID 10001; the web image runs as UID 101. |
| Raw HTML rendering | The frontend has no `v-html` binding. |
| Base image pins | Every image is referenced by an immutable `@sha256` digest. |
| Security headers | The Nginx configuration does not add security headers. The delivery exposes a local interface, so this is not a delivery blocker; a public deployment would require them. |

## License inventory

The project is MIT licensed. `LICENSE` carries the copyright of the two authors and is copied into the release bundle. `./gradlew verifyLicense` completes successfully for the checked-in sources and configuration.

The 67 Java runtime artifacts use these licenses:

| License | Artifacts |
| --- | ---: |
| Apache License 2.0 | 60 |
| Eclipse Public License 2.0 and GNU LGPL 2.1 (Logback) | 2 |
| MIT (SLF4J) | 2 |
| Eclipse Public License 2.0 or GPL 2.0 with the Classpath Exception (Jakarta Annotation) | 1 |
| CC0 and BSD 2-Clause (HdrHistogram) | 1 |
| Apache License 2.0 (JetBrains annotations) | 1 |

All are permissive or weak-copyleft licenses. No GPL-only component is distributed in the runtime.

The production frontend tree contains 20 MIT packages, the MIT-licensed `sticky-notes-domain` package, `entities` under BSD 2-Clause, `source-map-js` under BSD 3-Clause, and `picocolors` under ISC. All are permissive.

The base images carry their own licenses: Nginx under BSD 2-Clause, Eclipse Temurin under GPL 2.0 with the Classpath Exception, and the MongoDB Community image under the Server Side Public License. The repository references the MongoDB image by digest; it does not redistribute the image. A recipient pulls it from the registry.

### Third-party notices

The release bundle carries this document, the project `LICENSE`, and the generated documentation. The following attributions apply to the redistributed components:

- Vue.js, Copyright (c) 2018-present Yuxi (Evan) You, MIT License.
- Spring Boot, Spring Framework, Spring Data, and Spring Security, copyright the Spring project authors, Apache License 2.0.
- Apache Tomcat, copyright the Apache Software Foundation, Apache License 2.0.
- Logback, copyright QOS.ch, Eclipse Public License 2.0 or GNU LGPL 2.1.
- SLF4J, copyright QOS.ch, MIT License.
- Jackson, copyright the Jackson project authors, Apache License 2.0.
- MongoDB Java driver, copyright MongoDB, Inc., Apache License 2.0.
- HdrHistogram, copyright the HdrHistogram project authors, CC0 and BSD 2-Clause.
- JetBrains annotations, copyright JetBrains, Apache License 2.0.
- Jakarta Annotation, copyright the Eclipse Foundation, Eclipse Public License 2.0 or GPL 2.0 with the Classpath Exception.
- Nginx, copyright Nginx, Inc., BSD 2-Clause.
- Eclipse Temurin, copyright the Eclipse Adoptium project, GPL 2.0 with the Classpath Exception.
- MongoDB Community Server, copyright MongoDB, Inc., Server Side Public License.

The license texts remain in the upstream artifacts and in the base images. MIT and BSD attribution lines are reproduced here or inside the distributed artifacts. Anyone who republishes an artifact outside the release bundle must carry the corresponding license text with it.

## Reproduction

Run the dependency audits from the repository root:

```sh
npm audit
(cd frontend && npm audit)
(cd frontend && npm audit --omit=dev)
```

Resolve the Java runtime coordinates and query OSV with the resulting list. The query is a JSON object with one entry per coordinate:

```json
{"queries":[{"package":{"ecosystem":"Maven","name":"group:artifact"},"version":"1.2.3"}]}
```

```sh
curl -sS -X POST https://api.osv.dev/v1/querybatch \
  -H 'Content-Type: application/json' \
  --data @queries.json
```

Build the images, then scan them with the pinned scanner:

```sh
./gradlew buildImages

docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD/scans:/scans" \
  aquasec/trivy:0.76.1@sha256:790fd66d1697395d8693d1acaa1ea702406a20f109b6fc7bac8db623eca6c06c \
  image --scanners vuln --severity CRITICAL,HIGH \
  --format json --output /scans/web.json \
  sticky-notes-web:local
```

Repeat the command for `sticky-notes-backend:local` and for the MongoDB digest in `compose.yaml`. Review the result together with the applicability notes above; a raw count without the runtime path is not a verdict.

The delivery behaviour after the base-image update is covered by:

```sh
./gradlew containerSmoke
./gradlew backupRestoreVerify
./gradlew :frontend:frontendE2e
```
