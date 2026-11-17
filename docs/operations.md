<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Operations

Operational procedures for the Sticky Notes delivery stack: data model, volume lifetime, and data compatibility. The board lives in the named MongoDB volume `mongo-data`, which survives a `docker compose down` without `--volumes` and a stack restart.

## Data model

The database contains one `boards` collection with one document `_id=main`:

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | string | Document identifier, always `main` |
| `schemaVersion` | integer | Schema version, currently `1` |
| `revision` | integer | Optimistic concurrency counter |
| `wipLimit` | integer | Maximum notes allowed in `DOING` |
| `notes` | array | Notes with `id`, `content`, `color`, `status`, `blockedReason`, `checklist` |

The Kotlin domain enforces the invariants. It revalidates them when a snapshot is rehydrated. Corrupted or unsupported persisted state produces a diagnostic error. The application never repairs it silently.

## Stopping never deletes data

`scripts/stop-local.sh` runs `docker compose down` without `--volumes`. It removes the containers and the network. The board stays in `mongo-data`. Removing the volume is a deliberate deletion and is never part of a normal stop.

## Data compatibility

- `schemaVersion=1` is the current and only supported persisted schema.
- Data from earlier prototypes is not migrated automatically.
- Future schema changes are introduced as tested migrations, never silently.
- Rolling back to an earlier image is valid only when the image is compatible with the persisted `schemaVersion`.

## Related procedures

- Stack lifecycle: `scripts/start-local.sh`, `scripts/stop-local.sh`
- Stack verification: `scripts/smoke-test.sh` (`./gradlew containerSmoke`)
- Deployment: [deployment](deployment.md)
