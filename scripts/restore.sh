#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Restores a Sticky Notes board backup into a MongoDB database.
#
# Requires an explicit backup path. By default restores into a fresh test
# project/volume, never into the local demonstration board volume. Prompts for
# confirmation only when the target volume already exists (a restore would
# overwrite the board it contains).
#
# The restore runs mongorestore inside a temporary container using the same
# pinned MongoDB image as the delivery stack, then stops the container. The
# named volume persists, so a Compose stack can be started against it.
#
# Usage:
#   scripts/restore.sh PATH [--project NAME] [--volume NAME] [--force]
#
#   PATH        path to a backup directory produced by scripts/backup.sh
#   --project   Compose project name (default: sticky-notes-restore-test-<timestamp>)
#   --volume    Docker volume name (default: <project>_mongo-data)
#   --force     skip confirmation when overwriting an existing volume
#
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(dirname -- "$script_dir")

mongo_image="mongo@sha256:f071cbf97a6e52fb7f07ce9ca5d862ad79876bf0eba9ba97758337d740449638"
project_name="sticky-notes-restore-test-$(date +%s)"
volume_name=""
backup_path=""
force=0

usage() {
    echo "Usage: $0 PATH [--project NAME] [--volume NAME] [--force]" >&2
    echo
    echo "  PATH        path to a backup directory produced by scripts/backup.sh"
    echo "  --project   Compose project name (default: sticky-notes-restore-test-<timestamp>)"
    echo "  --volume    Docker volume name (default: <project>_mongo-data)"
    echo "  --force     skip confirmation when overwriting an existing volume"
    exit 2
}

if [ $# -lt 1 ]; then
    echo "Missing backup path argument." >&2
    usage
fi

backup_path=$1
shift

while [ $# -gt 0 ]; do
    case "$1" in
        --project)
            [ $# -ge 2 ] || { echo "Missing value for --project." >&2; usage; }
            project_name=$2; shift 2 ;;
        --volume)
            [ $# -ge 2 ] || { echo "Missing value for --volume." >&2; usage; }
            volume_name=$2; shift 2 ;;
        --force) force=1; shift ;;
        -h | --help) usage ;;
        *) echo "Unknown option: $1" >&2; usage ;;
    esac
done

if [ -z "$volume_name" ]; then
    volume_name="${project_name}_mongo-data"
fi

archive="$backup_path/dump.archive"
if [ ! -f "$archive" ]; then
    echo "Backup archive not found: $archive" >&2
    exit 1
fi

# Verify the archive is non-empty.
if [ ! -s "$archive" ]; then
    echo "Backup archive is empty: $archive" >&2
    exit 1
fi

for cmd in docker; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Required command '$cmd' is not available." >&2
        exit 1
    fi
done

# Load environment from .env when present (credentials and database name).
if [ -f "$root_dir/.env" ]; then
    # shellcheck disable=SC1090
    . "$root_dir/.env"
fi

mongo_user="${MONGO_USER:-stickynotes_user}"
mongo_password="${MONGO_PASSWORD:-stickynotes_local_password}"
mongo_database="${MONGO_DATABASE:-stickynotes}"

if docker volume inspect "$volume_name" >/dev/null 2>&1; then
    if [ "$force" -eq 0 ]; then
        printf "Volume '%s' already exists. Restoring will overwrite its data. Continue? [y/N] " "$volume_name" >&2
        set +e
        confirmed=""
        if read -r confirmed; then
            :
        fi
        set -e
        case "$confirmed" in
            y | Y | yes | YES) ;;
            *) echo "Restore cancelled." >&2; exit 1 ;;
        esac
    else
        echo "Overwriting existing volume '$volume_name' (--force)." >&2
    fi
fi

network="${project_name}_restore-net"

echo "Restoring backup '$archive' into volume '$volume_name'..."

docker network create "$network" >/dev/null 2>&1 || true

# Start a temporary MongoDB container with the target volume, restore, then stop it.
container_id=$(docker run --detach --network "$network" \
    -v "$volume_name:/data/db" \
    -e MONGO_INITDB_ROOT_USERNAME="$mongo_user" \
    -e MONGO_INITDB_ROOT_PASSWORD="$mongo_password" \
    -e MONGO_INITDB_DATABASE="$mongo_database" \
    "$mongo_image")

# Give MongoDB time to run its initialization scripts (create root user)
# before it starts accepting authenticated connections.
sleep 15

# Wait until MongoDB is ready, authenticating with the root credentials
# (same pattern as the compose healthcheck).
for i in $(seq 1 60); do
    if docker exec "$container_id" mongosh --quiet \
        --username "$mongo_user" --password "$mongo_password" --authenticationDatabase admin \
        --eval "quit(db.adminCommand({ ping: 1 }).ok ? 0 : 1)" >/dev/null 2>&1; then
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "MongoDB did not become ready in time." >&2
        docker stop "$container_id" >/dev/null 2>&1 || true
        docker rm "$container_id" >/dev/null 2>&1 || true
        exit 1
    fi
    sleep 2
done

# Stream the archive into mongorestore via stdin (--archive reads from stdin
# when no file argument is given).
cat "$archive" | docker exec -i "$container_id" mongorestore \
    --uri="mongodb://$mongo_user:$mongo_password@127.0.0.1:27017/$mongo_database?authSource=admin" \
    --drop \
    --archive

docker stop "$container_id" >/dev/null 2>&1 || true
docker rm "$container_id" >/dev/null 2>&1 || true
docker network rm "$network" >/dev/null 2>&1 || true

echo "Restore complete: volume '$volume_name'"
