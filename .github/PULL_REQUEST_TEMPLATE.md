<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

## Task Reference
- **Task ID**: TXXX
- **Closes Issue**: Fixes # (if applicable)

## Problem & Summary
Describe the gap or requirement addressed, and the resulting behavior.

## Key Changes
- List key modifications, files touched, or architecture decisions.

## Release Impact
- Describe what becomes usable and through which layer (domain, API, or UI); use that result in the squash-merge title.
- Describe material limitations and upgrade requirements in the affected documentation.
- Cite a gate only when relevant; link evidence before claiming it complete.
- For a change that produces `1.0.0`, record both maintainers' readiness review against the [release policy](https://github.com/lorenzotosi/sticky-notes/blob/main/docs/ci-cd.md#release-policy).

## Verification & Proof
- [ ] Automated tests executed and passing locally
- [ ] Static analysis / linter clean (`npm run lint` / `./gradlew check`)
- [ ] Covered scenarios, expected results, and exercised layers are described.
- Provide commands executed, test outputs, or UI screenshots where applicable.

## Review Checklist
- [ ] PR title follows Conventional Commits specification.
- [ ] Changes reviewed and approved by peer author.
- [ ] CI pipeline is green.
