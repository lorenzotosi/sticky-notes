<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Process Evidence: Repository Protections and Workflow Rules

## Branch Protection Rules (`main`)

To preserve repository integrity and enforce deterministic quality gates, the `main` branch is protected against direct pushes and non-compliant merges.

### Required Protections

- **Pull Request Required**: Direct pushes to `main` are disabled. All changes must be integrated through pull requests.
- **Merge Strategy**: Enforced `Squash and merge` to maintain a clean, linear, and bisectable Git history.
- **Required Status Checks**:
    - `Quality / verify` (executes `./gradlew check --no-daemon` on branch pushes and pull requests, running formatting, static analysis, license checks, and unit tests; it also validates commits on pull requests).
    - `Conventional pull request title / title` (validates the pull request title against Conventional Commits).
- **Branch Up-to-Date**: PR branches must be up-to-date with `main` before merging.

### Post-Merge Verification

A merge creates a push to `main`, where `Quality / verify` executes `./gradlew fullBuild --no-daemon`. This task repeats every check performed on the branch and additionally builds the Commons libraries, backend distributions, frontend production bundle, and documentation artifact. Release and documentation publication run only after this stronger gate succeeds.

### Peer Review & Self-Approval Policy

- In alignment with team operational agreement for this project, strict peer approvals are configured as non-blocking to prevent development bottlenecks between pair authors.
- Authors are permitted to self-merge provided that **all required automated CI status checks are passing (green)** and title linting succeeds.

## Licensing Verification

- Project license is formally registered under the **MIT License** (`LICENSE`).
- Source files and configuration assets across subprojects are continuously validated via the `verifyLicense` Gradle task against SPDX metadata standards.
