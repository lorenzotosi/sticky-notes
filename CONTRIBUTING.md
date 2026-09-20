<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# Contributing & Development Workflow

This document outlines the collaboration process, branching strategy, and code review rules for the project.

## 1. Roles and Responsibilities
- **Person A**: Domain modeling, JVM interop, backend services, and persistence.
- **Person B**: Build automation, JavaScript interop, frontend UI, and containerization.
- Work is distributed according to primary ownership, but collaboration remains agile.
- Peer review is recommended for major feature and architecture changes, while minor fixes or documentation updates can be fast-tracked to avoid bottlenecks.
- Both authors share responsibility for the overall system consistency and build stability.

## 2. Branching & Lifecycle
- **Protected Main**: Direct pushes to `main` are disabled. All changes must land via Pull Requests.
- **Short-Lived Branches**: Branch names must follow the convention:
  `codex/tNNN-short-description` (e.g., `codex/t002-define-two-person-pull-request-workflow`).
- **One Task per PR**: Each technical task corresponds to exactly one pull request.
- **Shared Files Manipulation**: Before modifying shared project configuration (such as root build scripts, lockfiles, or CI workflows), authors coordinate to prevent conflicts.

## 3. Commit Convention & Semantic Release
All commits and Pull Request titles must adhere strictly to the **Conventional Commits** specification:
- `feat(...)`: A new user-facing feature (triggers minor release).
- `fix(...)`: A bug fix (triggers patch release).
- `docs(...)`: Documentation only changes (no release).
- `test(...)`: Adding or refactoring tests (no release).
- `build(...)`: Changes that affect build tooling, Gradle scripts, or dependencies (no release).
- `ci(...)`: Changes to CI/CD workflows and scripts (no release).
- `chore(release)`: Delivery of versioned operational artifacts (triggers patch release).

## 4. Task States & Definition of Done
Tasks progress through the following states:
1. **To Do**: Ready to be picked up once prerequisites are merged.
2. **In Progress**: Active work on the dedicated branch.
3. **In Review**: Pull Request submitted with passing CI checks.
4. **Done**: Pull Request reviewed, approved, squash-merged into `main`, with `main` CI checks passing and verification evidence linked.
5. **Blocked**: Work halted due to an external dependency or critical blocker.

## 5. Merging Protocol
- Use **Squash and Merge** exclusively.
- The squash commit message must match the verified PR title.
- Delete the remote branch immediately after merging.
