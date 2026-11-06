<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# CI/CD

Delivery means build artifacts, GitHub Releases, and GitHub Pages. The pipeline does not deploy a running application.

## Workflow execution policy

| Event | Jobs | Gradle command | Purpose |
| --- | --- | --- | --- |
| Push to a development branch | `linux-verify`, `verify` | `./gradlew check --no-daemon` | Formatting, licenses, Detekt, domain boundaries, tests, coverage, and MongoDB integration. |
| Pull request targeting `main` | `linux-verify`, `macos-smoke`, `verify` | `./gradlew fullVerify --no-daemon` | Complete build and delivery checks: container smoke, backup and restore, clean installation, release bundle, and browser E2E. |
| Push to `main` | `linux-verify`, `macos-smoke`, `verify`, `release`, `docs` | `./gradlew fullVerify --no-daemon` | Repeats the full verification, then publishes the release and the documentation. |
| Manual run from `main` | `linux-verify`, `macos-smoke`, `verify`, `docs` | `./gradlew fullVerify --no-daemon` | Runs the full verification and rebuilds every deliverable. |
| Manual run from another branch | `linux-verify`, `macos-smoke`, `verify` | `./gradlew fullVerify --no-daemon` | Runs the full verification without publishing. |

`linux-verify` runs on `ubuntu-latest` and `macos-smoke` on `macos-latest`. The macOS job runs the portability suite that does not need Docker. The `verify` job aggregates both and fails unless each reports the result its policy expects. The workflow uploads reports even when a job fails, with a 14-day retention.

The MongoDB integration task starts and stops `mongo-test` through `testMongoUp` and `testMongoDown`. An unconditional `docker compose -f compose.test.yaml down -v --remove-orphans` step runs as a safeguard.

`fullBuild` stages the release bundle through `releaseBundle`. `fullVerify` packages it with `packageReleaseZip`, checks it with `releaseVerify`, and checks a clean Docker-only installation with `cleanInstallVerify`. A staging, record, or installation regression therefore fails in the pull request. The release job repeats the same build at the published version through `scripts/package-release.sh`, checks `SHA256SUMS`, and packages `sticky-notes-deliverables.zip`. The workflow uploads nothing before the quality job succeeds. See [release process](release-process.md) and [deployment](deployment.md#executable-backend).

After `quality` succeeds on `main`, semantic-release calculates the next version from the squash-merge commit messages. It runs the `prepare` hook, which rebuilds the bundle at that version. Then it creates the GitHub release with the ZIP as an asset. The workflow uses `GITHUB_TOKEN` and needs no repository secret.

## Release policy

Releases are automatic after a successful full build on `main`. A release identifies a distributable revision.

- `0.x.y` is initial development: public interfaces and behavior can change incompatibly.
- `1.0.0` establishes the first stable public API and product scope.
- A suffix such as `-beta.1` identifies a preview. A `0.x.y` release is not automatically a prerelease.

Before the first stable release, both maintainers review the declared product scope, the public API, and the evidence for the required interactions, installation, and operational limits. The automated version calculation does not do this review.

### Release notes

Automatic notes keep feature, fix, performance, and breaking-change entries, with comparison and commit links. They link the release policy, the installation instructions, and the documentation at the released tag.

## Version rules

A pull request title uses `type(scope): description` or `type(scope)!: description`. After a squash merge, the title becomes the commit message analyzed on `main`.

| Pull request title | Version change |
| --- | --- |
| `fix: correct note validation` | Patch |
| `perf: reduce API response time` | Patch |
| `feat: add note colors` | Minor |
| `feat!: change the notes API` | Major |
| `feat: ...` with a `BREAKING CHANGE:` footer | Major |
| `build:`, `chore:`, `ci:`, `docs:`, `refactor:`, `style:`, `test:` | No release |

The footer must be part of the squash commit message. The version calculation reads the commit, not the pull request description. Third-party actions are pinned to commit SHAs. Workflows declare their permissions, and each job has a time limit.

## GitHub Pages publication

The documentation workflow runs `./gradlew documentation` on the checked-out revision. Gradle copies the files from `docs/` into `build/docs/` and generates the Dokka API reference under `build/docs/api/`. The Jekyll action publishes the Markdown and the generated HTML. The job runs only after the quality job succeeds on `main`.

Pages publishes the documentation bundle and nothing else. The running application is the container stack described in [deployment](deployment.md#delivery-stack). `README.md` and `CONTRIBUTING.md` stay outside the site because their links are repository-relative.
