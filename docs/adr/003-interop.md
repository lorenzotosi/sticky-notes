<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# ADR 003: Typed JVM and JSON JavaScript boundaries

## Context

The shared Kotlin domain is consumed by a Java backend and a JavaScript frontend. Persisted or transported snapshots must not bypass aggregate invariants during rehydration.

## Decision

Java uses `JvmBoardFacade` with typed `BoardSnapshot`, `BoardCommand`, and `CommandResponse` values. JavaScript uses `evaluateBoardCommand` with JSON because JSON is the stable browser transport boundary. Both paths execute `BoardEngine`, and every snapshot becomes a `Board` only through the validated `BoardSnapshot.toDomain()` mapper.

Invalid persisted snapshots are rejected. The application must not replace them with an empty board or persist a repaired value silently. JSON is a transport representation, not a second domain model.

## Consequences

- JVM callers retain compile-time types without duplicating domain rules in Java.
- Browser callers use a narrow JSON API without exporting the whole Kotlin object graph.
- Persistence mappers must call the shared rehydration path before accepting stored state.
