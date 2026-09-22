<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# Sticky Notes

Minimal web application for creating sticky notes. The project is an exam case study for Software Process Engineering: Kotlin Multiplatform shares the domain between a Java/JVM backend and a Vue/JavaScript frontend.

## Start here

```bash
./gradlew fullBuild
./gradlew :backend:run
```

The frontend is started separately during development:

```bash
cd frontend
npm ci
npm run dev
```

## Repository layout

- `commons/`: Kotlin Multiplatform domain, targeting JVM and JavaScript.
- `backend/`: minimal Java HTTP API.
- `frontend/`: Vue application.
- `docs/`: architecture, process, CI/CD and deployment notes.

## Conventions

Use Conventional Commit titles for pull requests, for example `feat: create note` or `fix: reject blank content`. GitHub validates both titles and commits. Squash merging preserves the title for semantic-release, which creates GitHub releases after merges to `main`.

All comment-capable files carry SPDX headers. JSON package metadata declares the MIT license through its `license` field.
