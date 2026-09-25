<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# CI/CD

## Workflow execution policy

| Event | Gradle command | Purpose |
| --- | --- | --- |
| Push to a development branch | `./gradlew check --no-daemon` | Gives immediate feedback before the pull request is merged. It verifies formatting, SPDX licenses, Detekt rules, JVM and JavaScript tests, frontend linting, and frontend tests. |
| Pull request targeting `main` | `./gradlew check --no-daemon` | Repeats the verification on the proposed revision and also validates the commits. The pull request title is checked separately against Conventional Commits. |
| Merge or other push to `main` | `./gradlew fullBuild --no-daemon` | Runs the complete `check` task and then builds every publishable deliverable: Commons libraries, backend distributions, frontend production bundle, and documentation artifact. |
| Manual CI/CD execution | `./gradlew fullBuild --no-daemon` | Allows maintainers to verify and rebuild every deliverable on demand. |

The development-branch check is the fast quality gate: its purpose is to discover broken code as soon as it is pushed. A merge into `main` appears to GitHub Actions as a push to `main`; this activates the stronger `fullBuild` gate. Because `fullBuild` already depends on `check`, it includes every branch-level control before creating the final artifacts.

Test reports are retained after quality runs. Deliverables are uploaded only by successful full builds, so ordinary branch pushes do not create release artifacts.

After a successful full build on `main`, semantic-release derives the next version from squash-merge titles and creates the GitHub release from those deliverables. Release executions are serialized to prevent concurrent version calculations. It uses the workflow `GITHUB_TOKEN`; no repository secret is required. The same push publishes the Markdown documentation through GitHub Pages.

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
