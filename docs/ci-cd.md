<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# CI/CD

Delivery means build artifacts, GitHub Releases, and GitHub Pages. It does not deploy a running application.

## Workflow execution policy

| Event | Jobs executed | Gradle command (`linux-verify`) | Purpose |
| --- | --- | --- | --- |
| Push to a development branch | `linux-verify`, `verify` | `./gradlew check --no-daemon` | Checks formatting, SPDX licenses, Detekt, domain boundaries, JVM/JS tests, coverage, and MongoDB integration. |
| Pull request targeting `main` | `linux-verify`, `macos-smoke`, `verify` | `./gradlew fullVerify --no-daemon` | Validates commit messages and the pull request title. Builds all deliverables and container images. Runs container smoke, backup and restore, release bundle verification, and Playwright E2E checks. |
| Merge or other push to `main` | `linux-verify`, `macos-smoke`, `verify`, `release`, `docs` | `./gradlew fullVerify --no-daemon` | Repeats the complete gate. Packages and verifies the release bundle. Starts GitHub Releases and GitHub Pages only when the quality result is `success`. |
| Manual execution from `main` | `linux-verify`, `macos-smoke`, `verify`, `docs` | `./gradlew fullVerify --no-daemon` | Runs the complete gate and rebuilds every deliverable. Pages publishes the documentation. |
| Manual execution from another branch | `linux-verify`, `macos-smoke`, `verify` | `./gradlew fullVerify --no-daemon` | Runs the complete gate and rebuilds every deliverable. Pages does not publish. |

The quality workflow runs its verification jobs in parallel. A final gate aggregates the results:

- `linux-verify` runs on `ubuntu-latest`. It runs `./gradlew check --no-daemon` after a development-branch push. It runs `./gradlew fullVerify --no-daemon` after a pull request, a push to `main`, or a manual start. `fullVerify` combines `fullBuild`, `containerSmoke`, `backupRestoreVerify`, `releaseVerify`, and `:frontend:frontendE2e`. These tasks use isolated Docker Compose stacks.
- `macos-smoke` runs on `macos-latest`. It runs the portability suite that does not need Docker: `spotlessCheck`, `verifyLicense`, `:commons:check` on JVM and ChromeHeadless, `:backend:test`, `:backend:jacocoTestCoverageVerification`, and `:frontend:frontendCheck`. The job runs after a pull request, a push to `main`, or a manual start. It is skipped after a development-branch push.
- `verify` runs with `if: always()` after `linux-verify` and `macos-smoke`. It fails unless each job reports the result that its policy expects. `linux-verify` must report `success`. `macos-smoke` must report `success`, or `skipped` after a development-branch push. Any other result fails the gate and blocks a release. This rule also covers a cancelled or unexpected skipped job.

A pull request run cancels a superseded run of the same pull request. A run on `main` does not cancel an in-progress verification or release.

The workflow uploads diagnostic reports even when a job fails: `test-reports-linux` and `test-reports-macos`. The reports contain JUnit XML/HTML, JaCoCo and Vitest coverage, and Playwright E2E HTML, JUnit results, traces, and failure screenshots. The retention period is 14 days. The workflow never uploads local `.env` files or the database dumps in `outputs/`.

The backend integration task starts `mongo-test` through the Gradle `testMongoUp` dependency. It waits for the health check and finalizes with `testMongoDown`. The Linux job sets `TEST_MONGODB_URI` to port `27018` on the runner. The suite is part of `check`, `fullBuild`, and `fullVerify`. An unconditional `docker compose -f compose.test.yaml down -v --remove-orphans` step runs with `if: always()`. This step is a safeguard when Gradle stops before its finalizers complete.

The full build stages the release bundle through `releaseBundle`. The bundle contains the executable JAR, the frontend production bundle, the Compose stack with its stamped build identity, and the shared-domain libraries. It also contains the documentation bundle from `documentation`, the bundle README and license, and the `.env`, `manifest.json`, and `SHA256SUMS` records. The build generates all records from the same version. Nothing is uploaded. `fullVerify` packages the bundle with `packageReleaseZip` and verifies it with `releaseVerify`. A staging or record regression therefore fails in the pull request, not inside a release. The Spring Boot plugin makes `:backend:build` produce the executable JAR with the Commons domain library already embedded. `documentation` generates the documentation from the revision that the job checked out. The release job repeats the same build at the published version through `scripts/package-release.sh`, verifies `SHA256SUMS`, and packages `sticky-notes-deliverables.zip`. The root of the ZIP is the Compose project directory. See [release process](release-process.md) for the contents, the identity, and the records. See [deployment](deployment.md#executable-backend) for the startup commands of the extracted bundle.

After `quality` succeeds on `main` (`needs.quality.result == 'success'`), semantic-release calculates the next version from the squash-merge commit messages. It runs the `prepare` hook, which rebuilds and packages the bundle at that version. It then creates the GitHub release with the ZIP as an asset. The release workflow serializes its executions, so two version calculations cannot run at the same time. The workflow uses `GITHUB_TOKEN` and needs no repository secret. The same push publishes the Markdown documentation and the generated Dokka API reference through GitHub Pages.

## Release policy

Releases remain automatic after a successful full build on `main`. A release identifies a distributable revision. It does not prove that the product supports every UI/API/database interaction.

- `0.x.y` identifies initial development. Public interfaces and behavior can change incompatibly. The number is not a percentage of completion.
- `1.0.0` establishes the first stable public API and supported product scope. Later versions communicate compatibility through SemVer. Stable software continues to evolve.
- A suffix such as `-beta.1` identifies a preview of a specific intended version. A `0.x.y` release is not automatically a prerelease. It does not receive the GitHub Pre-release flag only because its major version is zero. The current workflow publishes normal versions from `main`. A preview channel needs an explicit configuration change.

Before a merge that would produce the first stable release, both maintainers must review the declared product scope and the public API. They must also review the evidence for the required UI/API/database interactions, reproducible installation, and the documented operational limits and upgrade requirements. Record that decision and the supporting evidence in the pull request. The automated version calculation does not perform this readiness review. During `0.x`, a breaking-change commit can already produce `1.0.0`.

Integration gates [R0-R5](index.md#integration-gates) describe verification boundaries, independently of versions. A release can contribute to a gate without completing it. A version number or a completed gate does not declare the product stable.

### Release notes

Automatic notes keep feature, fix, performance, and breaking-change entries. They also contain comparison and commit links. The notes explain version maturity and link to the policy, the installation instructions, and the gate evidence at the released tag. A reader therefore sees the documentation of the downloaded revision.

Gate references are optional context. They are not release names and not required fields. Do not infer gate completion from commit titles. Do not generate a checklist of all gates in every release.

## Version rules

A pull request title uses `type(scope): description` or `type(scope)!: description`. The scope is optional. After a squash merge, the pull request title becomes the commit message that the workflow analyzes on `main`.

| Pull request title | Version change |
| --- | --- |
| `fix: correct note validation` | Patch (`0.0.0` to `0.0.1`) |
| `perf: reduce API response time` | Patch |
| `feat: add note colors` | Minor (`0.0.0` to `0.1.0`) |
| `feat!: change the notes API` | Major (`0.0.0` to `1.0.0`) |
| `feat: change the notes API` + `BREAKING CHANGE:` footer | Major |
| `build:`, `chore:`, `ci:`, `docs:`, `refactor:`, `style:`, `test:` | No release |

Mark the breaking change with one of two markers. Put `!` before the colon in the pull request title, or add a `BREAKING CHANGE:` footer to the squash commit message. After a squash merge, the pull request title becomes the first line of the commit. The workflow therefore analyzes `feat(api)!: ...` directly. The maintainer writes the footer in the extended description when the merge is confirmed. Both forms produce a major release and a `BREAKING CHANGES` entry in the notes. Without the footer, the entry repeats the title. The footer is the correct place for the migration note. The version calculation reads the footer from the commit, not from the pull request description. The footer must therefore be part of the squash commit message.

The project pins third-party actions to immutable commit SHAs. The commit-message linter runs at pinned package versions. Each workflow declares its permissions, and each job has a time limit.

## GitHub Pages publication

The documentation workflow runs `./gradlew documentation` on the revision that GitHub Actions checked out. The published site therefore carries the documentation of that same revision. Gradle copies the versioned files from `docs/` into `build/docs/` and generates the Kotlin domain API reference with Dokka under `build/docs/api/`. The GitHub Jekyll action converts the Markdown files into the `_site` artifact that GitHub Pages publishes. The generated HTML under `api/` is published unchanged. The job runs after the quality job on `main`. A revision is therefore published only after the full build of that revision passes. A manual run started from another branch skips this job.

Pages publishes the documentation bundle and nothing else. It is not a hosting target for the application. The running application is the container stack described in [deployment](deployment.md#delivery-stack). Pages never starts that stack. The published bundle is `docs/`. The repository entry points `README.md` and `CONTRIBUTING.md` stay outside the site, because their links use repository-relative paths and would not resolve from the site root.

`docs/index.md` is the manually maintained navigation page. The build copies every document into the site. A document that the index omits is still available by URL, but it is not visible in the main list.
