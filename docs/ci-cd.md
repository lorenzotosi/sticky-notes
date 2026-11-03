<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# CI/CD

Delivery means build artifacts, GitHub Releases, and GitHub Pages; it does not deploy a running application.

## Workflow execution policy

| Event                         | Jobs executed                                              | Gradle command (`linux-verify`)    | Purpose                                                                                                                                                                 |
|-------------------------------|------------------------------------------------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Push to a development branch  | `linux-verify`, `verify`                                 | `./gradlew check --no-daemon`      | Gives feedback on formatting, SPDX licenses, Detekt, domain boundaries, JVM/JS tests, coverage, and MongoDB integration.                                                |
| Pull request targeting `main` | `linux-verify`, `macos-smoke`, `verify`                    | `./gradlew fullVerify --no-daemon` | Validates commit messages and PR title, builds all deliverables and container images, and runs container smoke, backup/restore, and Playwright E2E checks before merge. |
| Merge or other push to `main` | `linux-verify`, `macos-smoke`, `verify`, `release`, `docs` | `./gradlew fullVerify --no-daemon` | Repeats the complete verification gate, checks that the release bundle assembles, and gates GitHub Releases and GitHub Pages on an explicit `success` result. |
| Manual CI/CD execution        | `linux-verify`, `macos-smoke`, `verify`, `docs` from `main` | `./gradlew fullVerify --no-daemon` | Allows maintainers to run the complete verification gate and rebuild every deliverable on demand; Pages publishes only for a run dispatched from `main`.                  |


The quality workflow runs its verification jobs in parallel and aggregates them through a final required gate:

- `linux-verify` (`ubuntu-latest`) runs `./gradlew check --no-daemon` on ordinary branch pushes and `./gradlew fullVerify --no-daemon` on pull requests, pushes to `main`, and manual runs. `fullVerify` combines `fullBuild`, `containerSmoke`, `backupRestoreVerify`, `releaseVerify`, and `:frontend:frontendE2e` against isolated Docker Compose stacks.
- `macos-smoke` (`macos-latest`) runs the non-Docker portability suite (`spotlessCheck`, `verifyLicense`, `:commons:check` on JVM and ChromeHeadless, `:backend:test`, `:backend:jacocoTestCoverageVerification`, and `:frontend:frontendCheck`) on the operating system used by one of the authors (l'egregissimo Tosi). It runs on pull requests, pushes to `main`, and manual runs, and is skipped on development-branch pushes, where the pull request that merges the branch already runs it.
- `verify` runs with `if: always()` after `linux-verify` and `macos-smoke` and fails unless each job reports the result its execution policy expects: `success` from both, or `skipped` from `macos-smoke` on a development-branch push. Any other result, including a cancelled or unexpectedly skipped job, fails the gate and blocks a release.

Pull request runs use a concurrency group that cancels superseded runs on the same pull request, while `main` runs never cancel an in-progress verification or release.

Diagnostic reports (`test-reports-linux` and `test-reports-macos`), including JUnit XML/HTML reports, JaCoCo and Vitest coverage, and Playwright E2E HTML reports, JUnit results, traces, and failure screenshots, are uploaded even when a job fails (`if: ${{ !cancelled() }}`) with a 14-day retention period. Local `.env` files and database dumps (`outputs/`) are never uploaded.

The backend integration task starts `mongo-test` through the Gradle `testMongoUp` dependency, waits for its health check, and finalizes with `testMongoDown`. The Linux job sets `TEST_MONGODB_URI` to the runner's local port `27018`; the suite is included in `check`, `fullBuild`, and `fullVerify`. An unconditional `docker compose -f compose.test.yaml down -v --remove-orphans` cleanup step runs with `if: always()` as a safeguard if Gradle is interrupted before its finalizers complete.

The full build stages the release bundle through `releaseBundle`: the executable JAR, the frontend production bundle, the Compose stack with its stamped build identity, the shared-domain libraries, the documentation bundle from `documentation`, the bundle README and license, and the `.env`, `manifest.json`, and `SHA256SUMS` records generated from the same version. Nothing is uploaded: `fullVerify` then packages that bundle with `packageReleaseZip` and verifies it with `releaseVerify`, so a staging or record regression fails in the pull request instead of inside a release. The Spring Boot plugin makes `:backend:build` produce the executable JAR with the Commons domain library already packaged inside it, and `documentation` generates the documentation from the revision the job checked out. The release job reruns the same build at the published version through `scripts/package-release.sh`, verifies `SHA256SUMS`, and packages `sticky-notes-deliverables.zip`, whose root is the Compose project directory; see [release process](release-process.md) for the contents, the identity, and the records, and [deployment](deployment.md#executable-backend) for the extracted startup commands.

After `quality` succeeds on `main` (`needs.quality.result == 'success'`), semantic-release derives the next version from squash-merge titles, runs the `prepare` hook that rebuilds and packages the bundle at that version, and creates the GitHub release with the ZIP as an asset. Release executions are serialized to prevent concurrent version calculations. It uses the workflow `GITHUB_TOKEN`; no repository secret is required. The same push publishes the Markdown documentation and generated Dokka API reference through GitHub Pages.

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

Pull request titles use `type(scope): description` or `type(scope)!: description`, where the scope is optional. With squash merging, the pull request title becomes the commit message analyzed on `main`.

| Pull request title | Version change |
| --- | --- |
| `fix: correct note validation` | Patch (`0.0.0` to `0.0.1`) |
| `perf: reduce API response time` | Patch |
| `feat: add note colors` | Minor (`0.0.0` to `0.1.0`) |
| `feat!: change the notes API` | Major (`0.0.0` to `1.0.0`) |
| `feat: change the notes API` + `BREAKING CHANGE:` footer | Major |
| `build:`, `chore:`, `ci:`, `docs:`, `refactor:`, `style:`, `test:` | No release |

A breaking change is declared either with `!` before the colon in the pull request title or with a `BREAKING CHANGE:` footer in the squash commit message. With squash merging the title becomes the commit's first line, so `feat(api)!: ...` is analyzed directly; the footer is written in the extended description when confirming the merge. Both forms produce the major release and a `BREAKING CHANGES` entry in the notes; without the footer that entry repeats the title, so the footer is the place for the migration note. The release calculation reads the footer from the commit, not from the pull request description, so it must be part of the squash commit message.

Third-party actions are pinned to immutable commit SHAs, the commit-message linter runs at pinned package versions, permissions are granted per workflow and every job has a bounded execution time.

## GitHub Pages publication

The documentation workflow runs `./gradlew documentation` on the revision GitHub Actions checked out, so the published site carries the documentation of that same revision. Gradle copies the versioned files from `docs/` into `build/docs/` and generates the Kotlin domain API reference with Dokka under `build/docs/api/`. GitHub's Jekyll action then converts the Markdown files into the `_site` artifact published by GitHub Pages; the generated HTML under `api/` is published unchanged. The job runs after the quality job on `main`, so a revision is published only once the full build of that revision has passed.

Pages publishes the documentation bundle and nothing else. It is not a hosting target for the application: the running application is the container stack described in [deployment](deployment.md#delivery-stack), which Pages never starts. The published bundle is `docs/`, so the repository entry points `README.md` and `CONTRIBUTING.md` stay outside the site, because their links are written relative to the repository and would not resolve from the site root.

`docs/index.md` is the manually maintained navigation page. A document copied into the site but omitted from that index remains publishable by URL, but it is not visible in the main list.
