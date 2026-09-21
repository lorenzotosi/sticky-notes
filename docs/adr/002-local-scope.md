<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi -->

# ADR 002: Local Deployment Boundary and Scope Exclusions

## Context
The project guidelines require demonstrating robust software process engineering, automated builds, Kotlin Multiplatform sharing, verifiable testing, and containerized deployment.

Attempting to implement a full enterprise software suite (user accounts, session authentication, multi-tenant databases, real-time WebSockets, cloud orchestration) risks introducing excessive incidental complexity.

## Decision
We strictly bound the application scope to a **single shared Kanban board** hosted locally using Docker Compose without authentication:
- **No User Accounts / Authentication**: The board operates in a trusted local network environment.
- **Single Board (`main`)**: The database maintains one canonical board instance.
- **No WebSockets**: Synchronization relies on client-initiated REST calls with optimistic concurrency control (CAS) handling race conditions between windows.
- **No Distributed Scheduling**: Reminders and deadlines are deferred.

## Consequences
### Positive
- **Focus on Core Process Quality**: Development effort remains focused on rigorous domain rules, full JVM/JS multiplatform verification, Spring Boot/MongoDB integration, and Docker CI/CD delivery.
- **Deterministic Testing**: Local end-to-end and integration tests run reliably in CI without managing authentication tokens or external infrastructure.

### Negative / Trade-offs
- The system is not designed for public multi-user hosting or untrusted environments without adding an API Gateway or Identity and Access Bounded Context in future iterations.
