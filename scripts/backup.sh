#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Creates a backup of the Sticky Notes board database.
#
# Runs mongodump inside a temporary container using the same pinned MongoDB
# image as the delivery stack, authenticates with the credentials from the
# environment, and writes the dump to a timestamped directory under outputs/.
# A manifest file records schemaVersion and the image digest used.
#
# The backup is read-only: it never modifies the database or the volume.
#
# Usage:
#   scripts/backup.sh [--output DIR] [--project NAME]
#
#   --output DIR   directory for the backup (default: outputs/backup-<timestamp>)
#   --project NAME Compose project name (default: sticky-notes)
#
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(dirname -- "$script_dir")

mongo_image="mongo@sha256:f071cbf97a6e52fb7f07ce9ca5d862ad79876bf0eba9ba97758337d740449638"
project_name="sticky-notes"
output_dir=""

usage() {
    echo "Usage: $0 [--output DIR] [--project NAME]" >&2
    echo
    echo "  --output DIR   directory for the backup (default: outputs/backup-<timestamp>)"
    echo "  --project NAME Compose project name (default: sticky-notes)"
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --output)
            [ $# -ge 2 ] || { echo "Missing value for --output." >&2; usage; }
            output_dir=$2; shift 2 ;;
        --project)
            [ $# -ge 2 ] || { echo "Missing value for --project." >&2; usage; }
            project_name=$2; shift 2 ;;
        -h | --help) usage ;;
        *) echo "Unknown option: $1" >&2; usage ;;
    esac
done

if [ -z "$output_dir" ]; then
    output_dir="$root_dir/outputs/backup-$(date +%Y%m%d-%H%M%S)"
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

network="${project_name}_internal-net"

if ! docker network inspect "$network" >/dev/null 2>&1; then
    echo "Docker network '$network' not found. Start the stack first (scripts/start-local.sh)." >&2
    exit 1
fi

mkdir -p "$output_dir"

dump_dir="$output_dir/dump"
manifest="$output_dir/manifest.txt"

echo "Backing up database '$mongo_database' to $output_dir..."

docker run --rm --network "$network" \
    -v "$dump_dir:/backup:rw" \
    "$mongo_image" \
    mongodump --uri="mongodb://$mongo_user:$mongo_password@mongo:27017/$mongo_database?authSource=admin" --out=/backup

digest=$(docker image inspect "$mongo_image" --format '{{index .RepoDigests 0}}')

{
    echo "backup created: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "project: $project_name"
    echo "database: $mongo_database"
    echo "schemaVersion: 1"
    echo "image: $mongo_image"
    echo "digest: $digest"
    echo "output: $output_dir"
} > "$manifest"

echo "Backup complete: $output_dir"
echo "Manifest: $manifest"
