<!-- SPDX-License-Identifier: MIT -->
<!-- SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani -->

# Release recovery and rollback

Runbook for recovering from an incomplete release publication and for rolling a local deployment back to an earlier release bundle without losing board data.

## Verifying release deliverables

Maintainers and CI check the bundle with Gradle:

```sh
./gradlew releaseVerify
```

It builds the release deliverables and checks that all required artifacts and records are present. It checks SHA-256 digests against `manifest.json`, and exercises rejection of tampered packages.

To check an existing ZIP archive or extracted bundle without rebuilding, run the script directly:

```sh
./scripts/verify-release.sh sticky-notes-deliverables.zip
```

## Recovering from a partial release publication

In `.github/workflows/release.yml`, `semantic-release` runs `scripts/package-release.sh <version>` during the `prepare` step, before creating the Git tag. If the build or checksum check fails, the release stops before any tag is pushed.

A partial publication can occur when the tag `vX.Y.Z` exists on `main` and the upload of the ZIP fails with a transient network or API error:

1. Do not delete or move the tag. Tags on `main` are immutable.
2. Inspect the tag commit and check it out in a clean working tree:

   ```sh
   git fetch --tags
   git checkout "vX.Y.Z"
   ```

3. Rebuild the deterministic bundle from that exact commit passing the tag version:

   ```sh
   ./scripts/package-release.sh "X.Y.Z"
   ./scripts/verify-release.sh sticky-notes-deliverables.zip
   ```

4. Confirm that `revision` in `build/release-bundle/manifest.json` equals `git rev-parse vX.Y.Z`. Then upload `sticky-notes-deliverables.zip` to the existing `vX.Y.Z` GitHub Release.
5. If a repository defect caused the failure, not a transient upload error, leave `vX.Y.Z` marked as incomplete in its release notes. Merge a fix on `main` to publish the next version (`vX.Y.(Z+1)`).

## Local data-safe rollback

A local deployment runs from an extracted release bundle and stores the board in the named volume `sticky-notes_mongo-data` (`schemaVersion=1`). Rolling back to an earlier bundle replaces the `api` and `web` containers while keeping the MongoDB volume intact:

1. Check `build/docs/operations.md` in the target older bundle to confirm it uses a compatible persisted schema (`schemaVersion=1`).
2. Back up the current board before changing versions:

   ```sh
   ./scripts/backup.sh --output outputs/pre-rollback-backup
   ```

3. From the current deployment directory, stop the running containers without `--volumes`:

   ```sh
   docker compose down
   ```

4. Check that the named volume `sticky-notes_mongo-data` is still present:

   ```sh
   docker volume inspect sticky-notes_mongo-data
   ```

5. Switch to the extracted directory of the previous verified release bundle. Check it with `sha256sum --check SHA256SUMS`, copy any custom port or credential settings into its `.env`, and start the previous version:

   ```sh
   docker compose up --build --detach --wait
   ```

6. Check `http://127.0.0.1:8080/api/board`. The existing notes, ordering, checklist items, blockers, and board revision must be preserved.
