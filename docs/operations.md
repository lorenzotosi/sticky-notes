<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Operations

Operational procedures for the Sticky Notes delivery stack: backup, restore,
and data compatibility. The board lives in the named MongoDB volume
`mongo-data`, which survives a `docker compose down` without `--volumes` and a
stack restart. The volume preserves data but is not a substitute for a backup:
use the procedures below to back up and restore the board.

## Data model

The database contains a single `boards` collection with a single document
`_id=main`, schema `schemaVersion=1`:

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | string | Document identifier, always `main` |
| `schemaVersion` | integer | Schema version, currently `1` |
| `revision` | integer | Optimistic concurrency counter, incremented on each command |
| `wipLimit` | integer | Maximum notes allowed in `DOING` |
| `notes` | array | Notes with `id`, `content`, `color`, `status`, `blockedReason`, `checklist` |

The board aggregate is the consistency boundary: invariants are enforced on the
JVM/Kotlin domain and revalidated when a snapshot is rehydrated from MongoDB.
Corrupted or unsupported persisted state produces a diagnostic error and is
never silently repaired.

## Operating from the Release Bundle

When deploying from `sticky-notes-deliverables.zip` instead of a source checkout:

1. Extract the archive into an empty deployment directory:
   ```sh
   unzip sticky-notes-deliverables.zip -d sticky-notes-deploy
   cd sticky-notes-deploy
   ```
2. Verify payload integrity against the signed checksum record (`SHA256SUMS` covers all files including `manifest.json`, while `.env` remains editable):
   ```sh
   sha256sum --check SHA256SUMS
   ```
3. Inspect or customize `.env` (ports and database credentials), then start the stack:
   ```sh
   docker compose up --build --detach --wait
   ```

The version, revision, and image tags of the bundle are stamped into
`compose.yaml` and covered by `SHA256SUMS`, so `.env` carries runtime variables
only and cannot change the identity or version of the delivered images.

## Backup

`scripts/backup.sh` dumps the board database with `mongodump` inside a
temporary container. Both scripts read the MongoDB image reference from
`compose.yaml`, so the tooling cannot drift away from the pinned server, and
the dump is written to a timestamped directory under `outputs/` (outside Git)
together with a manifest that records `schemaVersion`, the database name, and
the image digest.

```sh
./scripts/backup.sh
```

By default the output goes to `outputs/backup-<timestamp>/`. Use `--output` to
choose a different directory and `--project` to target a non-default Compose
project name. The backup is read-only: it never modifies the database or the
volume.

## Restore

`scripts/restore.sh` restores a backup with `mongorestore` inside a temporary
container. A backup path is required:

```sh
./scripts/restore.sh outputs/backup-20260101-120000/
```

By default the restore creates a new test project and volume
(`sticky-notes-restore-test-<timestamp>_mongo-data`) and never touches the
local demonstration board volume. Use `--project` and `--volume` to name the
target instead; if that volume already exists, the script asks for confirmation
before overwriting it, and `--force` skips the prompt. This separates the
destructive overwrite path from the default test-target behavior. A volume you
name yourself is left in place after the restore for you to delete.

The archive carries the namespace it was dumped from, so the restored database
name is the one recorded in `manifest.txt` and not a choice the script can make.
`scripts/restore.sh` reads that manifest and refuses a backup whose database name
differs from the `MONGO_DATABASE` of the stack that will read the volume, rather
than restoring successfully into a database the application never queries. The
manifest's schema version is reported during the restore; the compatibility rule
is described [below](#data-compatibility).

Restoring into an existing volume requires the credentials that created it.
MongoDB creates the root user only on an empty data directory, so a volume
that already holds data under different credentials cannot be restored in
place: remove the volume first, or restore into a new one.

After a restore into a project's default volume (<project>_mongo-data), 
start a Compose stack with that project name so the API serves the recovered board:

```sh
docker compose -p <project> up --detach --wait
```

## Stopping never deletes data

`scripts/stop-local.sh` runs `docker compose down` without `--volumes`: the
containers and the network are removed, the board data stays in `mongo-data`.
Removing the volume is a deliberate deletion of the data and is never part of a
normal stop. The backup procedure above covers what the volume does not.

## Data compatibility

- `schemaVersion=1` is the current and only supported persisted schema.
- Data from earlier in-memory prototypes is not migrated into the database;
  there is no automatic DB migration for pre-existing prototype data.
- Future schema upgrades are introduced as tested migrations, never as silent
  schema changes.
- Rolling back to an earlier image version is valid only when the image schema
  is compatible with the persisted `schemaVersion`.

## Related procedures

- Stack lifecycle: `scripts/start-local.sh`, `scripts/stop-local.sh`
- Delivery stack verification: `scripts/smoke-test.sh` (`./gradlew containerSmoke`)
- Deployment notes: `docs/deployment.md`
