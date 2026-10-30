#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Automated end-to-end smoke test of the containerized Compose stack.
#
# Runs against an isolated Compose project name and temporary volume, so it
# never touches or deletes the local demonstration board volume. On failure,
# container status and logs are dumped before tearing down the isolated stack.
#
# Usage:
#
#   scripts/smoke-test.sh [--port PORT] [--wait-timeout SECONDS]

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(dirname -- "$script_dir")

project_name="${SMOKE_PROJECT_NAME:-sticky-notes-smoke}"
web_port="${SMOKE_WEB_PORT:-18080}"
wait_timeout=180
base_url="http://127.0.0.1:${web_port}"
created_note_id=""
last_revision=""
failed=1
work_dir=""

usage() {
    echo "Usage: $0 [--port PORT] [--wait-timeout SECONDS]" >&2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --port)
            [ $# -ge 2 ] || { echo "Missing value for --port." >&2; usage; exit 2; }
            web_port=$2
            base_url="http://127.0.0.1:${web_port}"
            shift 2
            ;;
        --wait-timeout)
            [ $# -ge 2 ] || { echo "Missing value for --wait-timeout." >&2; usage; exit 2; }
            wait_timeout=$2
            shift 2
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage
            exit 2
            ;;
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

if [ ! -d "$root_dir/build/release-bundle" ]; then
    echo "Release bundle not found in $root_dir/build/release-bundle. Run './gradlew releaseBundle' first." >&2
    exit 1
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/sticky-notes-smoke.XXXXXX")

compose_cmd() {
    WEB_PORT="$web_port" \
    API_IMAGE="$api_image" \
    WEB_IMAGE="$web_image" \
    MONGO_USER="stickynotes_smoke" \
    MONGO_PASSWORD="stickynotes_smoke_password" \
    MONGO_DATABASE="stickynotes_smoke" \
    docker compose --project-directory "$work_dir" -p "$project_name" -f "$work_dir/compose.yaml" "$@"
}

cleanup() {
    exit_code=$?
    set +e
    if [ -n "$work_dir" ] && [ -f "$work_dir/compose.yaml" ]; then
        if [ "$failed" -ne 0 ] || [ "$exit_code" -ne 0 ]; then
            echo "--- Smoke test failed: collecting container status and logs ---" >&2
            compose_cmd ps >&2
            compose_cmd logs --no-color >&2
        elif [ -n "$created_note_id" ] && [ -n "$last_revision" ]; then
            curl -fsS -X DELETE "${base_url}/api/board/notes/${created_note_id}?expectedRevision=${last_revision}" >/dev/null 2>&1 || true
        fi
        compose_cmd down -v --remove-orphans >/dev/null 2>&1 || true
    fi
    if [ -n "$work_dir" ] && [ -d "$work_dir" ]; then
        rm -rf "$work_dir"
    fi
    exit "$exit_code"
}

trap cleanup EXIT INT TERM

cp -R "$root_dir/build/release-bundle/." "$work_dir/"

# Verify bundle integrity, the stamped identity, and the runtime .env
if command -v sha256sum >/dev/null 2>&1; then
    (cd "$work_dir" && sha256sum --check --quiet SHA256SUMS)
else
    (cd "$work_dir" && shasum -a 256 -c SHA256SUMS)
fi
if grep -q "^STICKY_NOTES_VERSION=" "$work_dir/.env"; then
    echo "The editable .env must not carry the build identity." >&2
    exit 1
fi
bundle_version=$(sed -n 's/.*"version": "\([^"]*\)".*/\1/p' "$work_dir/manifest.json" | head -n 1)
bundle_revision=$(sed -n 's/.*"revision": "\([^"]*\)".*/\1/p' "$work_dir/manifest.json" | head -n 1)
grep -q "VERSION: \"$bundle_version\"" "$work_dir/compose.yaml"
grep -q "REVISION: \"$bundle_revision\"" "$work_dir/compose.yaml"
grep -q "^API_IMAGE=sticky-notes-backend:" "$work_dir/.env"
grep -q "^WEB_IMAGE=sticky-notes-web:" "$work_dir/.env"

# Clean up any leftover isolated test stack before starting
compose_cmd down -v --remove-orphans >/dev/null 2>&1 || true

echo "Starting isolated Compose stack '$project_name' on 127.0.0.1:${web_port}..."
compose_cmd up --detach --wait --wait-timeout "$wait_timeout"

# 1. Verify container metadata, non-root users, and port isolation
api_uid=$(compose_cmd exec -T api id -u | tr -d '\r\n')
web_uid=$(compose_cmd exec -T web id -u | tr -d '\r\n')
if [ "$api_uid" = "0" ] || [ "$web_uid" = "0" ]; then
    echo "Expected non-root runtime users, got api=$api_uid, web=$web_uid." >&2
    exit 1
fi

for image in "$api_image" "$web_image"; do
    image_version=$(docker image inspect "$image" --format '{{index .Config.Labels "org.opencontainers.image.version"}}')
    image_revision=$(docker image inspect "$image" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')
    if [ -z "$image_version" ] || [ -z "$image_revision" ]; then
        echo "Missing OCI version/revision labels on $image." >&2
        exit 1
    fi
done

api_port=$(compose_cmd port api 8080 2>/dev/null || true)
case "$api_port" in
    *[0-9]:[0-9]*)
        echo "Service 'api' must not publish host ports in delivery configuration, but reported: $api_port" >&2
        exit 1
        ;;
esac

mongo_port=$(compose_cmd port mongo 27017 2>/dev/null || true)
case "$mongo_port" in
    *[0-9]:[0-9]*)
        echo "Service 'mongo' must not publish host ports in delivery configuration, but reported: $mongo_port" >&2
        exit 1
        ;;
esac

# 2. Verify SPA serving and API error routing (must not return HTML for unknown API routes)
spa_html=$(curl -fsS "${base_url}/")
case "$spa_html" in
    *"<!DOCTYPE html>"* | *"<!doctype html>"*) ;;
    *) echo "Unexpected root HTML response from web proxy." >&2; exit 1 ;;
esac

unknown_meta=$(curl -s -o /dev/null -w '%{http_code} %{content_type}' "${base_url}/api/unknown")
case "$unknown_meta" in
    *"text/html"*)
        echo "Expected JSON error on unknown API route, but received HTML (SPA fallback leak): $unknown_meta" >&2
        exit 1
        ;;
    [45][0-9][0-9]*"application/json"* | [45][0-9][0-9]*"application/problem+json"*)
        ;;
    *)
        echo "Expected JSON error on unknown API route, got: $unknown_meta" >&2
        exit 1
        ;;
esac

# 3. Read initial board and create a note
board_json=$(curl -fsS "${base_url}/api/board")
rev0=$(printf '%s' "$board_json" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')
if [ -z "$rev0" ]; then
    echo "Could not parse initial board revision from: $board_json" >&2
    exit 1
fi

create_resp=$(curl -fsS -X POST "${base_url}/api/board/notes" \
    -H 'Content-Type: application/json' \
    -d "{\"content\":\"Smoke test initial note\",\"color\":\"BLUE\",\"expectedRevision\":${rev0}}")

created_note_id=$(printf '%s' "$create_resp" | tr -d '\r\n' | sed -n 's/.*"createdNoteId":"\([^"]*\)".*/\1/p')
rev1=$(printf '%s' "$create_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')

if [ -z "$created_note_id" ] || [ -z "$rev1" ]; then
    echo "Failed to parse createdNoteId or revision from create response: $create_resp" >&2
    exit 1
fi
last_revision="$rev1"

# 4. Read back and update the note
board_after_create=$(curl -fsS "${base_url}/api/board")
case "$board_after_create" in
    *"$created_note_id"*) ;;
    *) echo "Created note ID not found in board snapshot: $board_after_create" >&2; exit 1 ;;
esac
case "$board_after_create" in
    *"Smoke test initial note"*) ;;
    *) echo "Created note content not found in board snapshot: $board_after_create" >&2; exit 1 ;;
esac

update_resp=$(curl -fsS -X PATCH "${base_url}/api/board/notes/${created_note_id}" \
    -H 'Content-Type: application/json' \
    -d "{\"content\":\"Smoke test updated note\",\"color\":\"GREEN\",\"expectedRevision\":${rev1}}")

rev2=$(printf '%s' "$update_resp" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')
if [ -z "$rev2" ]; then
    echo "Failed to parse revision after note update: $update_resp" >&2
    exit 1
fi
last_revision="$rev2"

# 5. Stop stack without -v, verify volume exists, restart and confirm persistence
echo "Stopping stack without removing volumes..."
compose_cmd down

volume_name="${project_name}_mongo-data"
if ! docker volume inspect "$volume_name" >/dev/null 2>&1; then
    echo "Named volume '$volume_name' was not preserved after 'down'." >&2
    exit 1
fi

echo "Restarting stack and verifying data persistence..."
compose_cmd up --detach --wait --wait-timeout "$wait_timeout"

board_after_restart=$(curl -fsS "${base_url}/api/board")
case "$board_after_restart" in
    *"$created_note_id"*) ;;
    *) echo "Note ID did not persist across restart: $board_after_restart" >&2; exit 1 ;;
esac
case "$board_after_restart" in
    *"Smoke test updated note"*) ;;
    *) echo "Updated note content did not persist across restart: $board_after_restart" >&2; exit 1 ;;
esac
case "$board_after_restart" in
    *"GREEN"*) ;;
    *) echo "Updated note color did not persist across restart: $board_after_restart" >&2; exit 1 ;;
esac

# 6. Delete only the created note and confirm removal
delete_resp=$(curl -fsS -X DELETE "${base_url}/api/board/notes/${created_note_id}?expectedRevision=${rev2}")
case "$delete_resp" in
    *"$created_note_id"*)
        echo "Note $created_note_id is still present after deletion: $delete_resp" >&2
        exit 1
        ;;
esac
created_note_id=""

failed=0
echo "Container stack smoke test passed successfully."
