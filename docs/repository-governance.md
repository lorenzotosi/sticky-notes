<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Repository Governance

These settings live on GitHub and must be checked in the repository settings; they cannot be proven by the local files alone.

## Protected `main` branch

- Direct pushes are disabled; changes enter through pull requests.
- Squash and merge is the accepted merge strategy.
- `Quality / verify` and `Conventional pull request title / title` are required checks.
- Pull request branches must be up to date with `main` before merging.

Authors may self-merge when all required automated checks pass. Peer review remains recommended for architecture and major feature changes.

Workflow implementation details are documented in [CI/CD](ci-cd.md). Human contribution rules remain in the repository root `CONTRIBUTING.md`.
