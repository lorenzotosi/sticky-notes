<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# CI/CD

Every pull request runs `./gradlew fullBuild verifyLicense`. Pull request titles follow Conventional Commits and are checked before merge. Branch protection must require both workflows and squash merges.

Semantic release will run from `main`: the squash-merge title determines the next version and creates the GitHub release. Add the `NPM_TOKEN` repository secret before enabling package publication.
