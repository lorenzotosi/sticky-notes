<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Operations

Operational procedures for the Sticky Notes delivery stack: backup, restore, and data compatibility. The board lives in the named MongoDB volume `mongo-data`, which survives a `docker compose down` without `--volumes` and a stack restart. The volume preserves data but is not a backup.

## Data model

The database contains one `boards` collection with one document `_id=main`:

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | string | Document identifier, always `main` |
| `schemaVersion` | integer | Schema version, currently `1` |
| `revision` | integer | Optimistic concurrency counter |
| `wipLimit` | integer | Maximum notes allowed in `DOING` |
| `notes` | array | Notes with `id`, `content`, `color`, `status`, `blockedReason`, `checklist` |

Invariants are enforced in the Kotlin domain and revalidated when a snapshot is rehydrated. Corrupted or unsupported persisted state produces a diagnostic error and is never silently repaired.

## Backup

`scripts/backup.sh` dumps the board with `mongodump` inside a temporary container. Both scripts read the MongoDB image reference from `compose.yaml`, so the tooling cannot drift from the pinned server.

```sh
./scripts/backup.sh
```

The dump goes to `outputs/backup-<timestamp>/` with a manifest that records `schemaVersion`, the database name, and the image digest. Use `--output` to choose another directory and `--project` to target another Compose project. The backup is read-only.

## Restore

`scripts/restore.sh` restores a backup with `mongorestore` inside a temporary container. A backup path is required:

```sh
./scripts/restore.sh outputs/backup-20260101-120000/
```

By default the restore creates a new test project and volume (`sticky-notes-restore-test-<timestamp>_mongo-data`) and never touches the local board volume. Use `--project` and `--volume` to name the target; if the volume already exists, the script asks for confirmation, and `--force` skips the prompt. A volume you name yourself is left in place after the restore.

The archive carries the namespace it was dumped from, so the restored database name is the one in `manifest.txt`. The script refuses a backup whose database name differs from the `MONGO_DATABASE` of the stack that will read the volume. Restoring into an existing volume requires the credentials that created it, because MongoDB creates the root user only on an empty data directory.

After a restore into a project's default volume, start a stack with that project name:

```sh
docker compose -p <project> up --detach --wait
```

## Stopping never deletes data

`scripts/stop-local.sh` runs `docker compose down` without `--volumes`: containers and network are removed, and the board stays in `mongo-data`. Removing the volume is a deliberate deletion and is never part of a normal stop.

## Data compatibility

- `schemaVersion=1` is the current and only supported persisted schema.
- Data from earlier prototypes is not migrated automatically.
- Future schema changes are introduced as tested migrations, never silently.
- Rolling back to an earlier image is valid only when the image is compatible with the persisted `schemaVersion`.

## Related procedures

- Stack lifecycle: `scripts/start-local.sh`, `scripts/stop-local.sh`
- Stack verification: `scripts/smoke-test.sh` (`./gradlew containerSmoke`)
- Deployment: [deployment](deployment.md)
