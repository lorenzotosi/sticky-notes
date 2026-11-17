<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Contributing & Development Workflow

## 1. Branching & Lifecycle
- **Protected Main**: Direct pushes to `main` are disabled. All changes must land via Pull Requests.
- **Short-Lived Branches**: Branch names must follow the convention:
  `tNNN-short-description`.
- **One Task per PR**: Each technical task corresponds to exactly one pull request.

## 2. Commit Convention and Semantic Release

All commits and pull request titles must follow the **Conventional Commits** specification:

- `feat(...)`: A new user-facing feature (minor release).
- `fix(...)`: A bug fix (patch release).
- `perf(...)`: A performance improvement (patch release).
- `feat(...)!` or a `BREAKING CHANGE:` footer: An incompatible change (major release).
- `docs(...)`, `test(...)`, `build(...)`, `ci(...)`, `chore(...)`: A documentation, test, tooling, or maintenance change (no release).

## 3. Merging Protocol
- Use **Squash and Merge** exclusively.
- Use the verified pull request title as the first line of the squash commit message.
- Add a `BREAKING CHANGE:` footer to the extended description to describe the migration. The version calculation reads this footer from the commit.
- Delete the remote branch immediately after the merge.
