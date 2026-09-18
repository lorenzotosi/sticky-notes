<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# CI/CD

Every pull request runs commit validation and `./gradlew fullBuild verifyLicense`. Pull request titles follow Conventional Commits and are checked before merge. Successful quality runs retain the backend distributions, shared libraries, frontend bundle and test reports as workflow artifacts. Branch pushes are verified through their pull request, avoiding a duplicate run for the same commit.

After a successful push to `main`, semantic-release derives the next version from squash-merge titles and creates the GitHub release. The release includes a ZIP containing the compiled backend distributions, shared library and frontend bundle produced by the quality job. Release executions are serialized to prevent concurrent version calculations. It uses the workflow `GITHUB_TOKEN`; no repository secret is required. The same push publishes the Markdown documentation through GitHub Pages.

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
