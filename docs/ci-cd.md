<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# CI/CD

Every pull request runs commit validation and `./gradlew fullBuild verifyLicense`. Pull request titles follow Conventional Commits and are checked before merge.

After a successful push to `main`, semantic-release derives the next version from squash-merge titles and creates the GitHub release. It uses the workflow `GITHUB_TOKEN`; no repository secret is required. The same push publishes the Markdown documentation through GitHub Pages.
