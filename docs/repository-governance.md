<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Repository Governance

These settings live on GitHub. The local files alone cannot prove them.

## Protected `main` branch

- GitHub disables direct pushes. Changes enter through pull requests.
- Squash and merge is the accepted merge strategy. Branch deletion after merge is automatic.
- `Quality / verify` and `Conventional pull request title / title` are required status checks.
- `Quality / verify` runs with `if: always()` after `Quality / linux-verify` and `Quality / macos-smoke`. `linux-verify` runs the full build, MongoDB integration tests, container smoke tests, backup and restore, release bundle verification, and Playwright E2E tests on Linux. `macos-smoke` runs JVM, ChromeHeadless, and Vitest portability tests on macOS. This job is skipped after a development-branch push. `Quality / verify` succeeds only when each upstream job reports the result that its execution policy expects.
- Pull request branches must be up to date with `main` before merging.

Authors may self-merge when all required automated checks pass. Peer review remains recommended for architecture and major feature changes.

See [CI/CD](ci-cd.md) for the workflow implementation. `CONTRIBUTING.md` in the repository root holds the contribution rules.
