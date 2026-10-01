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

## Development and delivery

- [Local development](development.md)
- [Testing](testing.md)
- [Deployment](deployment.md)

## Specifications

- [Requirements](requirements.md)
- [UI and interaction design](ux.md)
- [API contracts](contracts.md)
- [OpenAPI definition](openapi.yaml)

## Implementation

- [Domain rules](domain.md)
- [Glossary](glossary.md)
- [Generated domain API reference](api/index.html)
- [Shared-domain acceptance evidence](evidence/R1.md)

## Architecture decisions

- [Architecture](architecture.md)
- [ADR 001: Board aggregate](adr/001-board-aggregate.md)
- [ADR 002: Local scope](adr/002-local-scope.md)
- [ADR 003: JVM and JavaScript interoperability](adr/003-interop.md)

## Process

- [CI/CD](ci-cd.md)
- [Repository governance](repository-governance.md)
