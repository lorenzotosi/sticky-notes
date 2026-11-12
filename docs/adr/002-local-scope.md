<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# ADR 002: Local Deployment Boundary and Scope Exclusions

## Context
The application needs consistent domain rules across JVM and browser consumers, reproducible builds and tests, and a local containerized deployment.

A full enterprise suite (user accounts, session authentication, multi-tenant databases, real-time WebSockets, cloud orchestration) risks complexity the scope does not justify.

## Decision
We bound the application scope to a single shared Kanban board without authentication. The target runtime deployment uses Docker Compose locally:
- The board runs in a trusted local network environment, with no user accounts and no authentication.
- The database holds one board, `_id: "main"`.
- No WebSockets. Windows sync through client-initiated REST calls, and optimistic concurrency control (CAS) handles the race conditions between windows.
- No distributed scheduling. Reminders and deadlines are deferred.

## Consequences
### Positive
- Development effort stays on domain rules, JVM/JS multiplatform verification, Spring Boot and MongoDB integration, and Docker CI/CD delivery.
- Domain, frontend component, and MongoDB integration checks need no authentication token and no hosted infrastructure. A complete browser-to-database journey needs separate verification.

### Negative / Trade-offs
- The system is not designed for public multi-user hosting or untrusted environments. That would need an API Gateway or an Identity and Access bounded context in a later iteration.
