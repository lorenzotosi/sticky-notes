<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# Deployment

The first deployment target is Docker Compose: one container for the frontend and one for the Java API. Database persistence is deliberately deferred until note persistence is introduced.

The CI pipeline builds the same artifacts used locally; credentials for registries or hosting are GitHub Actions secrets, never repository files.
