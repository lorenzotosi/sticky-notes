<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# CI/CD

Delivery means build artifacts, GitHub Releases, and GitHub Pages; it does not deploy a running application.

## Workflow execution policy

| Event | Gradle command | Purpose |
| --- | --- | --- |
| Push to a development branch | `./gradlew check --no-daemon` | Gives immediate feedback before the pull request is merged. It verifies formatting, SPDX licenses, Detekt rules, JVM and JavaScript tests, frontend linting, and frontend tests. |
| Pull request targeting `main` | `./gradlew check --no-daemon` | Repeats the verification on the proposed revision and also validates the commits. The pull request title is checked separately against Conventional Commits. |
| Merge or other push to `main` | `./gradlew fullBuild --no-daemon` | Runs the complete `check` task and then builds every publishable deliverable: Commons libraries, backend executable JAR, frontend production bundle, and documentation artifact. |
| Manual CI/CD execution | `./gradlew fullBuild --no-daemon` | Allows maintainers to verify and rebuild every deliverable on demand. |

The development-branch check is the fast quality gate: its purpose is to discover broken code as soon as it is pushed. A merge into `main` appears to GitHub Actions as a push to `main`; this activates the stronger `fullBuild` gate. Because `fullBuild` already depends on `check`, it includes every branch-level control before creating the final artifacts.

Test reports are retained after quality runs. Deliverables are uploaded only by successful full builds, so ordinary branch pushes do not create release artifacts.

The backend integration task starts `mongo-test` through the Gradle `testMongoUp` dependency, waits for its health check, and finalizes with `testMongoDown`. The quality job sets `TEST_MONGODB_URI` to the runner's local port `27018`; the suite is included in both `check` and `fullBuild`. Backend integration reports are uploaded with the other test reports, including after failures. An unconditional Docker Compose cleanup step remains as a safeguard if Gradle is interrupted before its finalizer runs.

The `deliverables` artifact contains `backend/build/libs/backend.jar`, the Commons libraries from `commons/build/libs/`, and the frontend production bundle from `frontend/dist/`. The Spring Boot plugin makes `:backend:build` produce the executable JAR, so it is already part of `fullBuild`. The release workflow packages the downloaded files into `sticky-notes-deliverables.zip`; see [deployment](deployment.md#executable-backend) for the extracted JAR location and startup command.

After a successful full build on `main`, semantic-release derives the next version from squash-merge titles and creates the GitHub release from those deliverables. Release executions are serialized to prevent concurrent version calculations. It uses the workflow `GITHUB_TOKEN`; no repository secret is required. The same push publishes the Markdown documentation and generated Dokka API reference through GitHub Pages.

## Version rules

Pull request titles use `type(scope): description`, where the scope is optional. With squash merging, the pull request title becomes the commit message analyzed on `main`.

| Pull request title | Version change |
| --- | --- |
| `fix: correct note validation` | Patch (`0.0.0` to `0.0.1`) |
| `perf: reduce API response time` | Patch |
| `feat: add note colors` | Minor (`0.0.0` to `0.1.0`) |
| `feat!: change the notes API` | Major (`0.0.0` to `1.0.0`) |
| `build:`, `chore:`, `ci:`, `docs:`, `refactor:`, `style:`, `test:` | No release |

A breaking change can also be declared with a `BREAKING CHANGE:` footer. The `!` form is preferred for squash-merged pull requests because the release impact is visible in the title.

Third-party actions are pinned to immutable commit SHAs, permissions are granted per workflow and every job has a bounded execution time.

## GitHub Pages publication

The documentation workflow runs `./gradlew documentation`. Gradle copies the versioned files from `docs/` into `build/docs/` and generates the Kotlin domain API reference with Dokka under `build/docs/api/`. GitHub's Jekyll action then converts the Markdown files into the `_site` artifact published by GitHub Pages.

`docs/index.md` is the manually maintained navigation page. A document copied into the site but omitted from that index remains publishable by URL, but it is not visible in the main list.
