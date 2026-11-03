#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Verifies a clean Docker-only installation from sticky-notes-deliverables.zip
# in an empty temporary directory outside the repository checkout.
#
# Usage:
#   scripts/clean-install-verify.sh [--port PORT] [--wait-timeout SECONDS]

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(dirname -- "$script_dir")
archive="$root_dir/sticky-notes-deliverables.zip"

ts=$(date +%s)
project_name="sticky-notes-clean-${ts}"
web_port="${CLEAN_INSTALL_PORT:-18096}"
wait_timeout=180
base_url="http://127.0.0.1:${web_port}"
work_dir=""
failed=1

while [ $# -gt 0 ]; do
    case "$1" in
        --port)
            [ $# -ge 2 ] || { echo "Missing value for --port." >&2; exit 2; }
            web_port=$2
            base_url="http://127.0.0.1:${web_port}"
            shift 2
            ;;
        --wait-timeout)
            [ $# -ge 2 ] || { echo "Missing value for --wait-timeout." >&2; exit 2; }
            wait_timeout=$2
            shift 2
            ;;
        -h | --help)
            echo "Usage: $0 [--port PORT] [--wait-timeout SECONDS]" >&2
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 2
            ;;
    esac
done

for cmd in docker curl unzip; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Required command '$cmd' is not available." >&2
        exit 1
    fi
done

if [ ! -f "$archive" ]; then
    echo "Release archive not found at $archive. Run './gradlew packageReleaseZip' first." >&2
    exit 1
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/sticky-notes-clean-install.XXXXXX")

compose_cmd() {
    (
        cd "$work_dir" && \
        WEB_PORT="$web_port" \
        docker compose -p "$project_name" "$@"
    )
}

cleanup() {
    exit_code=$?
    set +e
    if [ -n "$work_dir" ] && [ -f "$work_dir/compose.yaml" ]; then
        if [ "$failed" -ne 0 ] || [ "$exit_code" -ne 0 ]; then
            echo "--- Clean installation verification failed: collecting logs ---" >&2
            compose_cmd ps >&2 2>&1 || true
            compose_cmd logs --no-color >&2 2>&1 || true
        fi
        compose_cmd down -v --remove-orphans >/dev/null 2>&1 || true
    fi
    if [ -n "$work_dir" ] && [ -d "$work_dir" ]; then
        rm -rf "$work_dir"
    fi
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

# 1. Extract bundle in empty directory and verify checksums
unzip -q "$archive" -d "$work_dir"

if command -v sha256sum >/dev/null 2>&1; then
    (cd "$work_dir" && sha256sum --check --quiet SHA256SUMS)
else
    (cd "$work_dir" && shasum -a 256 -c SHA256SUMS >/dev/null)
fi

# 2. Build and start stack using only Docker Compose and delivered binaries
compose_cmd up --build --detach --wait --wait-timeout "$wait_timeout"

# 3. Verify image labels match manifest.json
manifest_version=$(sed -n 's/.*"version": "\([^"]*\)".*/\1/p' "$work_dir/manifest.json" | head -n 1 | tr -d '\r')
manifest_image_version=$(sed -n 's/.*"imageVersion": "\([^"]*\)".*/\1/p' "$work_dir/manifest.json" | head -n 1 | tr -d '\r')
api_label_version=$(docker image inspect "sticky-notes-backend:${manifest_image_version}" --format '{{index .Config.Labels "org.opencontainers.image.version"}}' | tr -d '\r')
if [ "$api_label_version" != "$manifest_version" ]; then
    echo "Expected image version label '$manifest_version', got '$api_label_version'." >&2
    exit 1
fi

# 4. Exercise Kanban workflows: create, WIP limit, blocker, checklist, completion
board_json=$(curl -fsS "${base_url}/api/board")
rev=$(printf '%s' "$board_json" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

wip_resp=$(curl -fsS -X PUT "${base_url}/api/board/wip-limit" \
    -H 'Content-Type: application/json' \
    -d "{\"limit\":1,\"expectedRevision\":${rev}}")
rev=$(printf '%s' "$wip_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

create_a=$(curl -fsS -X POST "${base_url}/api/board/notes" \
    -H 'Content-Type: application/json' \
    -d "{\"content\":\"Clean install note A\",\"color\":\"YELLOW\",\"expectedRevision\":${rev}}")
note_a=$(printf '%s' "$create_a" | tr -d '\r\n' | sed -n 's/.*"createdNoteId":"\([^"]*\)".*/\1/p')
rev=$(printf '%s' "$create_a" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

create_b=$(curl -fsS -X POST "${base_url}/api/board/notes" \
    -H 'Content-Type: application/json' \
    -d "{\"content\":\"Clean install note B\",\"color\":\"BLUE\",\"expectedRevision\":${rev}}")
note_b=$(printf '%s' "$create_b" | tr -d '\r\n' | sed -n 's/.*"createdNoteId":"\([^"]*\)".*/\1/p')
rev=$(printf '%s' "$create_b" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

# Block note A, verify move is rejected (409), then unblock and move to DOING
block_resp=$(curl -fsS -X PUT "${base_url}/api/board/notes/${note_a}/block" \
    -H 'Content-Type: application/json' \
    -d "{\"reason\":\"Waiting on dependency\",\"expectedRevision\":${rev}}")
rev=$(printf '%s' "$block_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

blocked_status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${base_url}/api/board/notes/${note_a}/move" \
    -H 'Content-Type: application/json' \
    -d "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":${rev}}")
if [ "$blocked_status" != "409" ]; then
    echo "Expected 409 when moving blocked note, got $blocked_status." >&2
    exit 1
fi

unblock_resp=$(curl -fsS -X DELETE "${base_url}/api/board/notes/${note_a}/block?expectedRevision=${rev}")
rev=$(printf '%s' "$unblock_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

move_a=$(curl -fsS -X POST "${base_url}/api/board/notes/${note_a}/move" \
    -H 'Content-Type: application/json' \
    -d "{\"targetStatus\":\"DOING\",\"destinationIndex\":0,\"expectedRevision\":${rev}}")
rev=$(printf '%s' "$move_a" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

# Verify WIP=1 rejects moving note B into DOING (409)
wip_status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${base_url}/api/board/notes/${note_b}/move" \
    -H 'Content-Type: application/json' \
    -d "{\"targetStatus\":\"DOING\",\"destinationIndex\":1,\"expectedRevision\":${rev}}")
if [ "$wip_status" != "409" ]; then
    echo "Expected 409 when exceeding WIP limit, got $wip_status." >&2
    exit 1
fi

# Add checklist item to note A, verify completion rejected until item is completed
item_resp=$(curl -fsS -X POST "${base_url}/api/board/notes/${note_a}/checklist" \
    -H 'Content-Type: application/json' \
    -d "{\"label\":\"Verify clean install\",\"expectedRevision\":${rev}}")
item_id=$(printf '%s' "$item_resp" | tr -d '\r\n' | sed -n 's/.*"createdItemId":"\([^"]*\)".*/\1/p')
rev=$(printf '%s' "$item_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

incomplete_status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${base_url}/api/board/notes/${note_a}/move" \
    -H 'Content-Type: application/json' \
    -d "{\"targetStatus\":\"DONE\",\"destinationIndex\":0,\"expectedRevision\":${rev}}")
if [ "$incomplete_status" != "409" ]; then
    echo "Expected 409 when completing note with incomplete checklist, got $incomplete_status." >&2
    exit 1
fi

toggle_resp=$(curl -fsS -X PATCH "${base_url}/api/board/notes/${note_a}/checklist/${item_id}" \
    -H 'Content-Type: application/json' \
    -d "{\"completed\":true,\"expectedRevision\":${rev}}")
rev=$(printf '%s' "$toggle_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

done_resp=$(curl -fsS -X POST "${base_url}/api/board/notes/${note_a}/move" \
    -H 'Content-Type: application/json' \
    -d "{\"targetStatus\":\"DONE\",\"destinationIndex\":0,\"expectedRevision\":${rev}}")
rev=$(printf '%s' "$done_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

# 5. Restart stack without removing volume and verify persistence
compose_cmd down
compose_cmd up --detach --wait --wait-timeout "$wait_timeout"

after_restart=$(curl -fsS "${base_url}/api/board")
case "$after_restart" in
    *"$note_a"* | *"Clean install note A"*) ;;
    *) echo "Board state did not persist across restart in clean installation." >&2; exit 1 ;;
esac

failed=0
echo "Clean Docker-only installation verification passed."
