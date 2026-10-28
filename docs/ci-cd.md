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

The `deliverables` artifact contains `backend/build/libs/backend.jar`, the Commons libraries from `commons/build/libs/`, the frontend production bundle from `frontend/dist/`, and the documentation bundle from `build/docs/`. The Spring Boot plugin makes `:backend:build` produce the executable JAR, so it is already part of `fullBuild`, and `documentation` generates the bundle from the revision the job checked out. The release workflow packages the downloaded files into `sticky-notes-deliverables.zip`; see [deployment](deployment.md#executable-backend) for the extracted JAR location and startup command.

After a successful full build on `main`, semantic-release derives the next version from squash-merge titles and creates the GitHub release from those deliverables. Release executions are serialized to prevent concurrent version calculations. It uses the workflow `GITHUB_TOKEN`; no repository secret is required. The same push publishes the Markdown documentation and generated Dokka API reference through GitHub Pages.

## Release policy

Releases remain automatic after successful full builds on `main`. They identify distributable revisions, including incremental changes that do not yet provide a complete UI/API/database interaction.

- `0.x.y` identifies initial development. Public interfaces and behavior may change incompatibly; the number is not a percentage of completion.
- `1.0.0` establishes the first stable public API and supported product scope. Later versions communicate compatibility through SemVer; stable software continues to evolve.
- A suffix such as `-beta.1` identifies a preview of a specific intended version. A `0.x.y` release is not automatically a prerelease and does not receive the GitHub Pre-release flag merely because its major version is zero. The current workflow publishes normal versions from `main`; preview channels require an explicit configuration change.

Before merging a change that would produce the first stable release, both maintainers must review the declared product scope and public API, evidence for the required interactions across UI/API/database, reproducible installation, and documented operational limits and upgrade requirements. Record that decision and the supporting evidence in the pull request. The automated version calculation does not perform this readiness review: during `0.x`, a major-triggering commit can already produce `1.0.0`.

Integration gates [R0-R5](index.md#integration-gates) describe verification boundaries, independently of versions. A release can contribute to a gate without completing it. Neither a version number nor completion of a gate automatically declares the product stable.

### Release notes

Automatic notes retain feature, fix, performance, and breaking-change entries, plus comparison and commit links. They also explain version maturity and link to the policy, installation instructions, and gate evidence at the released tag, so readers see documentation for the downloaded revision.

Gate references are optional context, not release names or required fields. Do not infer gate completion from commit titles or generate a checklist of all gates in every release.

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

The documentation workflow runs `./gradlew documentation` on the revision GitHub Actions checked out, so the published site carries the documentation of that same revision. Gradle copies the versioned files from `docs/` into `build/docs/` and generates the Kotlin domain API reference with Dokka under `build/docs/api/`. GitHub's Jekyll action then converts the Markdown files into the `_site` artifact published by GitHub Pages; the generated HTML under `api/` is published unchanged. The job runs after the quality job on `main`, so a revision is published only once the full build of that revision has passed.

Pages publishes the documentation bundle and nothing else. It is not a hosting target for the application: the running application is the container stack described in [deployment](deployment.md#delivery-stack), which Pages never starts. The published bundle is `docs/`, so the repository entry points `README.md` and `CONTRIBUTING.md` stay outside the site, because their links are written relative to the repository and would not resolve from the site root.

`docs/index.md` is the manually maintained navigation page. A document copied into the site but omitted from that index remains publishable by URL, but it is not visible in the main list.
