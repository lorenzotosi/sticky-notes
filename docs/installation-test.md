<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Clean installation verification

This guide documents how to install and verify Sticky Notes from `sticky-notes-deliverables.zip` in an empty directory without a source checkout, a JDK, Gradle, or Node.js.

## Prerequisites

- Docker Engine with the Compose plugin (Docker Compose v2.19 or later).
- Network access on the first run to pull the base images pinned by `@sha256` digest (`eclipse-temurin:21-jre`, `nginxinc/nginx-unprivileged`, and `mongo`).

## Manual clean-room installation procedure

1. Download `sticky-notes-deliverables.zip` from the GitHub Release into an empty directory outside the repository and extract it:

       mkdir sticky-notes-clean && cd sticky-notes-clean
       unzip /path/to/sticky-notes-deliverables.zip

2. Verify the integrity of all delivered files against `SHA256SUMS`:

       sha256sum --check --quiet SHA256SUMS   # Linux / Git Bash
       shasum -a 256 -c SHA256SUMS            # macOS

3. Inspect `.env` if you need to change `WEB_PORT` (default `8080`) or the demonstration MongoDB credentials, then build and start the stack using Docker Compose only:

       docker compose up --build --detach --wait

4. Open `[http://127.0.0.1:8080](http://127.0.0.1:8080)` and verify that only the `web` service publishes a host port while `api` and `mongo` stay isolated on the internal Docker network:

       docker compose ps

5. Stop the stack preserving the `sticky-notes_mongo-data` volume and restart it to confirm persistence:

       docker compose down
       docker compose up --detach --wait

## Automated verification from Gradle

Maintainers and CI execute the clean-room installation check in an isolated temporary directory and Compose project via Gradle:

    ./gradlew cleanInstallVerify

The task packages `sticky-notes-deliverables.zip`, extracts it into a temporary directory outside the repository, checks `SHA256SUMS`, starts the stack with `docker compose up --build --detach --wait`, exercises note creation, WIP limit enforcement, blockers, checklist completion, and restart persistence, and tears down all test containers and volumes on exit.
