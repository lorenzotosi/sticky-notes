<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# Deployment

The current continuous-delivery targets are GitHub Releases and GitHub Pages. Each release includes the compiled application deliverables produced by the verified build, while the project documentation is published through GitHub Pages. The application itself is not deployed to a runtime environment automatically.

The planned application deployment target is Docker Compose: one container for the frontend and one for the Java API. Database persistence is deliberately deferred until note persistence is introduced. Future registry or hosting credentials will use GitHub Actions secrets, never repository files.
