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

## Planned runtime deployment

The planned application deployment target is Docker Compose: one container for the frontend and one for the Java API. Database persistence is deliberately deferred until note persistence is introduced. Future registry or hosting credentials will use GitHub Actions secrets, never repository files.
