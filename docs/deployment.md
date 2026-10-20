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

## Runtime deployment

The application deployment target is Docker Compose: one container for the frontend and one for the Java API, with MongoDB reachable only inside the delivery network. The backend image described above is the runtime artifact of the Java API container. The frontend image, the Compose stack, and the published images are not part of this repository yet; future registry or hosting credentials will use GitHub Actions secrets, never repository files.
