<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Sticky Notes

Minimal web application for creating sticky notes. Kotlin Multiplatform shares the domain between a Java/JVM backend and a Vue/JavaScript frontend.

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

The Vue form creates in-memory notes through the generated Kotlin/JS domain package. The backend is not needed for this interaction; reloading the page discards the notes.

See [shared-domain acceptance evidence](docs/evidence/R1.md) for the JVM/browser scenarios, Java/Vue consumers, generated artifacts, and reproduction commands.
