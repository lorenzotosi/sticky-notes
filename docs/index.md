<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Sticky Notes documentation

## Integration gates

The project is organized around these functional gates. They define integration boundaries, not SemVer versions or completion status.

| Gate | Outcome | Acceptance boundary |
| --- | --- | --- |
| R0 | Reliable foundation | Reproducible build, automated checks, CI, repository workflow, and release controls. |
| R1 | Shared domain | The same domain scenarios on JVM and browser, real Java/Vue consumers, and generated API documentation. |
| R2 | Persistent CRUD | Note creation, reading, editing, and deletion through UI/API/database; data survives reload and restart. |
| R3 | Complete Kanban | WIP limits, blockers, checklists, ordering, concurrency, and accessible interactions. |
| R4 | Reproducible delivery | Containers, end-to-end checks, versioned images, backup/restore, rollback, and clean installation. |
| R5 | Handover | Traceable decisions and evidence, demonstration, final artifacts, and verified access for recipients. |

## Quickstart

The delivered application runs as three containers, and its runtime needs neither a JDK nor Node.js on the machine. One command builds the images from the current source and starts the stack:

```sh
./gradlew startLocal
```

The interface is available at `http://127.0.0.1:8080`, and `./scripts/stop-local.sh` stops the stack while keeping the board in the named volume. See [delivery stack](deployment.md#delivery-stack) for the services and the configuration.

For development, build the deliverables, run the backend with the `local` profile, and start the Vite development server. `fullBuild` includes the MongoDB integration tests, so it needs a running Docker engine:

```sh
./gradlew fullBuild
./gradlew :backend:bootRun --args='--spring.profiles.active=local'
./gradlew :frontend:frontendInstall
cd frontend
npm run dev
```

See [full-stack execution](development.md#full-stack-end-to-end-execution) for the development database and the `/api` proxy. The [generated domain API reference](api/index.html) listed below is produced by `./gradlew documentation` into `build/docs/api/`; it is a build artifact and is not a tracked file, so the link resolves in the published site and in that bundle, not in a repository checkout.

## Development and delivery

- [Local development](development.md)
- [Testing](testing.md)
- [Deployment](deployment.md)
- [Release process](release-process.md)
- [Operations](operations.md)

## Specifications

- [Requirements](requirements.md)
- [UI and interaction design](ux.md)
- [API contracts](contracts.md)
- [OpenAPI definition](openapi.yaml)

## Implementation

- [Domain rules](domain.md)
- [Glossary](glossary.md)
- [Concurrent commands and the board revision](concurrency.md)
- [Generated domain API reference](api/index.html)
- [Accessibility verification](accessibility.md)
- [Shared-domain acceptance evidence](evidence/R1.md)
- [Persistent CRUD acceptance evidence](evidence/R2.md)
- [R3 acceptance evidence](evidence/R3.md)
- [R4 acceptance evidence](evidence/R4.md)

## Architecture decisions

- [Architecture](architecture.md)
- [ADR 001: Board aggregate](adr/001-board-aggregate.md)
- [ADR 002: Local scope](adr/002-local-scope.md)
- [ADR 003: JVM and JavaScript interoperability](adr/003-interop.md)

## Process

- [CI/CD](ci-cd.md)
- [Repository governance](repository-governance.md)
