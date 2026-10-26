#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Verifies the backup/restore roundtrip of the delivery stack.
#
# Creates a board with a note, a checklist item, and a blocker on an isolated
# stack, backs the database up with scripts/backup.sh, restores it into a new
# volume with scripts/restore.sh, starts a second stack against that volume,
# and compares the snapshots. Neither stack touches the local demonstration
# board volume.
#
# Usage:
#   scripts/backup-restore-verify.sh [--wait-timeout SECONDS]
#
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(dirname -- "$script_dir")

wait_timeout=180
failed=1
snapshot_a=""
snapshot_b=""
backup_dir=""

usage() {
    echo "Usage: $0 [--wait-timeout SECONDS]" >&2
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --wait-timeout)
            [ $# -ge 2 ] || { echo "Missing value for --wait-timeout." >&2; usage; }
            wait_timeout=$2; shift 2 ;;
        -h | --help) usage ;;
        *) echo "Unknown option: $1" >&2; usage ;;
    esac
done

for cmd in docker curl; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Required command '$cmd' is not available." >&2
        exit 1
    fi
done

if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose is not available." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "The Docker engine is not running." >&2
    exit 1
fi

api_image="${API_IMAGE:-sticky-notes-backend:local}"
web_image="${WEB_IMAGE:-sticky-notes-web:local}"

for image in "$api_image" "$web_image"; do
    if ! docker image inspect "$image" >/dev/null 2>&1; then
        echo "The image $image is not available. Run './gradlew buildImages' first." >&2
        exit 1
    fi
done

# Two isolated Compose projects so the volumes and networks never collide and
# neither stack touches the local sticky-notes_mongo-data volume. Timestamp
# suffixes avoid conflicts with leftover volumes from previous runs.
ts=$(date +%s)
project_a="sticky-notes-backup-verify-a-${ts}"
project_b="sticky-notes-backup-verify-b-${ts}"
web_port_a=18090
web_port_b=18091
base_url_a="http://127.0.0.1:${web_port_a}"
base_url_b="http://127.0.0.1:${web_port_b}"

mongo_user="${MONGO_USER:-stickynotes_user}"
mongo_password="${MONGO_PASSWORD:-stickynotes_local_password}"
mongo_database="${MONGO_DATABASE:-stickynotes}"

compose_cmd() {
    project=$1
    web_port=$2
    shift 2
    WEB_PORT="$web_port" \
    API_IMAGE="$api_image" \
    WEB_IMAGE="$web_image" \
    MONGO_USER="$mongo_user" \
    MONGO_PASSWORD="$mongo_password" \
    MONGO_DATABASE="$mongo_database" \
    docker compose --project-directory "$root_dir" -p "$project" -f "$root_dir/compose.yaml" "$@"
}

cleanup() {
    exit_code=$?
    set +e
    if [ "$failed" -ne 0 ] || [ "$exit_code" -ne 0 ]; then
        echo "--- Backup/restore verification failed: collecting stack state ---" >&2
        compose_cmd "$project_a" "$web_port_a" ps >&2 2>&1 || true
        compose_cmd "$project_a" "$web_port_a" logs --no-color >&2 2>&1 || true
        compose_cmd "$project_b" "$web_port_b" ps >&2 2>&1 || true
        compose_cmd "$project_b" "$web_port_b" logs --no-color >&2 2>&1 || true
        echo "--- The backup directory is kept for inspection: $backup_dir ---" >&2
    else
        if [ -n "$backup_dir" ] && [ -d "$backup_dir" ]; then
            rm -rf "$backup_dir"
        fi
    fi
    compose_cmd "$project_a" "$web_port_a" down -v --remove-orphans >/dev/null 2>&1 || true
    compose_cmd "$project_b" "$web_port_b" down -v --remove-orphans >/dev/null 2>&1 || true
    docker volume rm "${project_b}_mongo-data" >/dev/null 2>&1 || true
    exit "$exit_code"
}

trap cleanup EXIT INT TERM

echo "Starting stack A ($project_a) on 127.0.0.1:${web_port_a}..."
compose_cmd "$project_a" "$web_port_a" up --detach --wait --wait-timeout "$wait_timeout"

# --- Create board data on stack A: note + checklist item + blocker ---

board_json=$(curl -fsS "${base_url_a}/api/board")
rev0=$(printf '%s' "$board_json" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')
if [ -z "$rev0" ]; then
    echo "Could not parse initial board revision from stack A: $board_json" >&2
    exit 1
fi

create_resp=$(curl -fsS -X POST "${base_url_a}/api/board/notes" \
    -H 'Content-Type: application/json' \
    -d "{\"content\":\"Backup verification note\",\"color\":\"BLUE\",\"expectedRevision\":${rev0}}")

note_id=$(printf '%s' "$create_resp" | tr -d '\r\n' | sed -n 's/.*"createdNoteId":"\([^"]*\)".*/\1/p')
rev1=$(printf '%s' "$create_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')
if [ -z "$note_id" ] || [ -z "$rev1" ]; then
    echo "Failed to create note on stack A: $create_resp" >&2
    exit 1
fi

checklist_resp=$(curl -fsS -X POST "${base_url_a}/api/board/notes/${note_id}/checklist" \
    -H 'Content-Type: application/json' \
    -d "{\"label\":\"Verify backup roundtrip\",\"expectedRevision\":${rev1}}")

rev2=$(printf '%s' "$checklist_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')
if [ -z "$rev2" ]; then
    echo "Failed to add checklist item on stack A: $checklist_resp" >&2
    exit 1
fi

block_resp=$(curl -fsS -X PUT "${base_url_a}/api/board/notes/${note_id}/block" \
    -H 'Content-Type: application/json' \
    -d "{\"reason\":\"Pending restore verification\",\"expectedRevision\":${rev2}}")

rev3=$(printf '%s' "$block_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')
if [ -z "$rev3" ]; then
    echo "Failed to block note on stack A: $block_resp" >&2
    exit 1
fi

snapshot_a=$(curl -fsS "${base_url_a}/api/board")
case "$snapshot_a" in
    *"$note_id"*) ;;
    *) echo "Created note missing from snapshot A: $snapshot_a" >&2; exit 1 ;;
esac
case "$snapshot_a" in
    *"Backup verification note"*) ;;
    *) echo "Note content missing from snapshot A: $snapshot_a" >&2; exit 1 ;;
esac
case "$snapshot_a" in
    *"Verify backup roundtrip"*) ;;
    *) echo "Checklist item missing from snapshot A: $snapshot_a" >&2; exit 1 ;;
esac
case "$snapshot_a" in
    *"Pending restore verification"*) ;;
    *) echo "Blocker reason missing from snapshot A: $snapshot_a" >&2; exit 1 ;;
esac

echo "Stack A board created: note=$note_id revision=$rev3"

# --- Back up stack A ---

backup_dir="$root_dir/outputs/backup-verify-$(date +%Y%m%d-%H%M%S)"
"$script_dir/backup.sh" --output "$backup_dir" --project "$project_a"

# The backup is read-only. The source board is re-read to prove the dump
# left stack A's data unchanged before the stack is stopped.
snapshot_a_after_backup=$(curl -fsS "${base_url_a}/api/board")
if [ "$snapshot_a" != "$snapshot_a_after_backup" ]; then
    echo "The source board changed after the backup; the dump was not read-only." >&2
    echo "--- Snapshot A before the backup ---" >&2
    printf '%s\n' "$snapshot_a" >&2
    echo "--- Snapshot A after the backup ---" >&2
    printf '%s\n' "$snapshot_a_after_backup" >&2
    exit 1
fi

# --- Stop stack A, preserving its volume ---

echo "Stopping stack A (volume preserved)..."
compose_cmd "$project_a" "$web_port_a" down

# --- Restore into a new volume for stack B ---

volume_b="${project_b}_mongo-data"
"$script_dir/restore.sh" "$backup_dir" --project "$project_b" --volume "$volume_b" --force

# --- Start stack B against the restored volume ---

echo "Starting stack B ($project_b) on 127.0.0.1:${web_port_b}..."
compose_cmd "$project_b" "$web_port_b" up --detach --wait --wait-timeout "$wait_timeout"

snapshot_b=$(curl -fsS "${base_url_b}/api/board")

# --- Compare snapshots ---

if [ "$snapshot_a" != "$snapshot_b" ]; then
    echo "Snapshots differ after backup/restore roundtrip." >&2
    echo "--- Snapshot A ---" >&2
    printf '%s\n' "$snapshot_a" >&2
    echo "--- Snapshot B ---" >&2
    printf '%s\n' "$snapshot_b" >&2
    exit 1
fi

case "$snapshot_b" in
    *"$note_id"*) ;;
    *) echo "Note ID missing after restore: $snapshot_b" >&2; exit 1 ;;
esac
case "$snapshot_b" in
    *"Backup verification note"*) ;;
    *) echo "Note content missing after restore: $snapshot_b" >&2; exit 1 ;;
esac
case "$snapshot_b" in
    *"Verify backup roundtrip"*) ;;
    *) echo "Checklist item missing after restore: $snapshot_b" >&2; exit 1 ;;
esac
case "$snapshot_b" in
    *"Pending restore verification"*) ;;
    *) echo "Blocker reason missing after restore: $snapshot_b" >&2; exit 1 ;;
esac

failed=0
echo "Backup/restore roundtrip verification passed: snapshot A equals snapshot B."
