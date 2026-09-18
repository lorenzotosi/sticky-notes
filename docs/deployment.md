<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# Deployment

The current continuous-delivery targets are GitHub Releases for version metadata and GitHub Pages for project documentation. Application deliverables are retained as CI artifacts but are not deployed automatically.

The planned application deployment target is Docker Compose: one container for the frontend and one for the Java API. Database persistence is deliberately deferred until note persistence is introduced. Future registry or hosting credentials will use GitHub Actions secrets, never repository files.
