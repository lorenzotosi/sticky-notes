<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Dependencies and licenses

Sticky Notes runs on Java 21 and Spring Boot on the backend and on Vue in the browser. The runtime dependencies and the licenses of the delivered artifacts are listed below.

## Scope

The review covers what a recipient runs: the backend runtime classpath, the frontend production tree and browser bundle, the three container images, and the release bundle. Build and test dependencies are listed separately because they do not enter the delivered application.

## Runtime inventory

### Backend

The backend runs on Java 21 and Spring Boot 4.1.1. The resolved runtime classpath contains 67 artifacts:

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

The delivery stack publishes only `web` on `127.0.0.1:8080`. The test stack in `compose.test.yaml` uses the same MongoDB digest.

## License inventory

The project is MIT licensed. `LICENSE` carries the copyright of the two authors and is copied into the release bundle. `./gradlew verifyLicense` completes successfully for the checked-in sources and configuration.

The 67 Java runtime artifacts use these licenses:

| License | Artifacts |
| --- | --- |
| Apache License 2.0 | 60 |
| Eclipse Public License 2.0 and GNU LGPL 2.1 (Logback) | 2 |
| MIT (SLF4J) | 2 |
| Eclipse Public License 2.0 or GPL 2.0 with the Classpath Exception (Jakarta Annotation) | 1 |
| CC0 and BSD 2-Clause (HdrHistogram) | 1 |
| Apache License 2.0 (JetBrains annotations) | 1 |

All are permissive or weak-copyleft licenses. No GPL-only component is distributed in the runtime.

The production frontend tree contains 20 MIT packages, the MIT-licensed `sticky-notes-domain` package, `entities` under BSD 2-Clause, `source-map-js` under BSD 3-Clause, and `picocolors` under ISC. All are permissive.

The base images carry their own licenses: Nginx under BSD 2-Clause, Eclipse Temurin under GPL 2.0 with the Classpath Exception, and the MongoDB Community image under the Server Side Public License. The repository references the MongoDB image by digest; it does not redistribute the image.

## Third-party notices

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

The license texts remain in the upstream artifacts and base images. MIT and BSD attribution lines are reproduced here or inside the distributed artifacts. Anyone who republishes an artifact outside the release bundle must carry the corresponding license text with it.
