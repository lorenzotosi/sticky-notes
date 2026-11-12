<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Clean installation verification

Install and verify Sticky Notes from `sticky-notes-deliverables.zip` in an empty directory, without a source checkout, a JDK, Gradle, or Node.js.

## Prerequisites

- Docker Engine with the Compose plugin (Docker Compose v2.19 or later).
- Network access on the first run to pull the base images pinned by `@sha256` digest (`eclipse-temurin:21-jre`, `nginxinc/nginx-unprivileged`, and `mongo`).

## Manual clean-room installation procedure

1. Download `sticky-notes-deliverables.zip` from the GitHub Release into an empty directory outside the repository and extract it:

   ```sh
   mkdir sticky-notes-clean && cd sticky-notes-clean
   unzip /path/to/sticky-notes-deliverables.zip
   ```

2. Check the integrity of all delivered files against `SHA256SUMS`:

   ```sh
   sha256sum --check --quiet SHA256SUMS   # Linux / Git Bash
   shasum -a 256 -c SHA256SUMS            # macOS
   ```

3. Inspect `.env` if you need to change `WEB_PORT` (default `8080`) or the demonstration MongoDB credentials. Then build and start the stack with Docker Compose only:

   ```sh
   docker compose up --build --detach --wait
   ```

4. Open the app at `http://127.0.0.1:8080`. Check that only the `web` service publishes a host port. `api` and `mongo` stay isolated on the internal Docker network:

   ```sh
   docker compose ps
   ```

5. Stop the stack preserving the `sticky-notes_mongo-data` volume and restart it to confirm persistence:

   ```sh
   docker compose down
   docker compose up --detach --wait
   ```

## Automated verification from Gradle

Maintainers and CI run the clean-room check in an isolated temporary directory and Compose project:

```sh
./gradlew cleanInstallVerify
```

The task:

1. Packages `sticky-notes-deliverables.zip`.
2. Extracts it into a temporary directory outside the repository.
3. Checks `SHA256SUMS`.
4. Starts the stack with `docker compose up --build --detach --wait`.
5. Exercises note creation, WIP limit enforcement, blockers, checklist completion, and restart persistence.
6. Tears down all test containers and volumes on exit.
