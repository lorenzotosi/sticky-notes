#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Verifies release bundle integrity, detects incomplete/tampered bundles, and
# exercises a data-safe rollback across isolated Docker Compose stacks.
#
# Usage:
#   scripts/verify-release.sh [<bundle-dir-or-zip>] [--wait-timeout SECONDS]

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(dirname -- "$script_dir")
wait_timeout=180
target=""

while [ $# -gt 0 ]; do
    case "$1" in
        --wait-timeout)
            [ $# -ge 2 ] || { echo "Missing value for --wait-timeout." >&2; exit 2; }
            wait_timeout=$2
            shift 2
            ;;
        -h | --help)
            echo "Usage: $0 [<bundle-dir-or-zip>] [--wait-timeout SECONDS]" >&2
            exit 0
            ;;
        *)
            target=$1
            shift
            ;;
    esac
done

check_checksums() {
    dir=$1
    if command -v sha256sum >/dev/null 2>&1; then
        (cd "$dir" && sha256sum --check --quiet SHA256SUMS)
    else
        (cd "$dir" && shasum -a 256 -c SHA256SUMS >/dev/null)
    fi
}

validate_bundle_dir() {
    bdir=$1
    for rel_path in \
        manifest.json SHA256SUMS compose.yaml .env .dockerignore README.md LICENSE \
        backend/Dockerfile backend/build/libs/backend.jar \
        frontend/Dockerfile frontend/nginx.conf frontend/dist/index.html \
        commons/build/libs/commons-jvm.jar commons/build/npm/sticky-notes-domain/package.json \
        build/docs/index.md build/docs/api/index.html; do
        if [ ! -f "$bdir/$rel_path" ]; then
            echo "Verification failed: missing required deliverable '$rel_path'." >&2
            return 1
        fi
    done

    version=$(sed -n 's/.*"version": "\([^"]*\)".*/\1/p' "$bdir/manifest.json" | head -n 1)
    image_version=$(sed -n 's/.*"imageVersion": "\([^"]*\)".*/\1/p' "$bdir/manifest.json" | head -n 1)
    revision=$(sed -n 's/.*"revision": "\([^"]*\)".*/\1/p' "$bdir/manifest.json" | head -n 1)

    [ -n "$version" ] && [ -n "$image_version" ] && [ -n "$revision" ] || {
        echo "Verification failed: manifest.json is missing identity fields." >&2
        return 1
    }

    grep -q "VERSION: \"$version\"" "$bdir/compose.yaml" || return 1
    grep -q "REVISION: \"$revision\"" "$bdir/compose.yaml" || return 1
    grep -q "sticky-notes-backend:${image_version}" "$bdir/compose.yaml" || return 1
    grep -q "sticky-notes-web:${image_version}" "$bdir/compose.yaml" || return 1

    paths_file=$(mktemp "${TMPDIR:-/tmp}/verify_paths.XXXXXX")
        sed -n 's/.*"path": "\([^"]*\)".*/\1/p' "$bdir/manifest.json" > "$paths_file"

        if [ ! -s "$paths_file" ]; then
            echo "Verification failed: manifest.json lists no files." >&2
            rm -f "$paths_file"
            return 1
        fi

        path_failed=0
        while IFS= read -r p || [ -n "$p" ]; do
            p=$(printf '%s' "$p" | tr -d '\r')
            [ -n "$p" ] || continue
            if [ ! -f "$bdir/$p" ]; then
                echo "Verification failed: file '$p' listed in manifest.json is missing." >&2
                path_failed=1
                break
            fi
        done < "$paths_file"

        rm -f "$paths_file"

        if [ "$path_failed" -ne 0 ]; then
            return 1
        fi

    check_checksums "$bdir"
}

# Mode 1: validate a specific directory or ZIP passed on the command line
if [ -n "$target" ]; then
    tmp_extract=""
    trap 'if [ -n "$tmp_extract" ] && [ -d "$tmp_extract" ]; then rm -rf "$tmp_extract"; fi' EXIT INT TERM
    if [ -f "$target" ]; then
        tmp_extract=$(mktemp -d "${TMPDIR:-/tmp}/sticky-notes-verify.XXXXXX")
        unzip -q "$target" -d "$tmp_extract"
        validate_bundle_dir "$tmp_extract"
    elif [ -d "$target" ]; then
        validate_bundle_dir "$target"
    else
        echo "Error: '$target' is neither a file nor a directory." >&2
        exit 1
    fi
    echo "Release bundle verified successfully: $target"
    exit 0
fi

# Mode 2: full automated verification (bundle check, negative tampering check, and rollback test)
archive="$root_dir/sticky-notes-deliverables.zip"
if [ ! -f "$archive" ]; then
    echo "Release archive not found at $archive. Run './gradlew packageReleaseZip' first." >&2
    exit 1
fi

ts=$(date +%s)
project_name="sticky-notes-rollback-${ts}"
web_port=18095
base_url="http://127.0.0.1:${web_port}"
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/sticky-notes-rollback.XXXXXX")
failed=1

compose_cmd() {
    dir=$1
    shift
    WEB_PORT="$web_port" \
    API_IMAGE="${API_IMAGE:-sticky-notes-backend:local}" \
    WEB_IMAGE="${WEB_IMAGE:-sticky-notes-web:local}" \
    MONGO_USER="stickynotes_rb" \
    MONGO_PASSWORD="stickynotes_rb_password" \
    MONGO_DATABASE="stickynotes_rb" \
    docker compose --project-directory "$dir" -p "$project_name" -f "$dir/compose.yaml" "$@"
}

cleanup() {
    exit_code=$?
    set +e
    if [ -d "$work_dir/v1" ] && [ -f "$work_dir/v1/compose.yaml" ]; then
        if [ "$failed" -ne 0 ] || [ "$exit_code" -ne 0 ]; then
            echo "--- Release/rollback verification failed: collecting logs ---" >&2
            compose_cmd "$work_dir/v1" ps >&2 2>&1 || true
            compose_cmd "$work_dir/v1" logs --no-color >&2 2>&1 || true
        fi
        compose_cmd "$work_dir/v1" down -v --remove-orphans >/dev/null 2>&1 || true
    fi
    rm -rf "$work_dir"
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

mkdir -p "$work_dir/v1" "$work_dir/v2" "$work_dir/tampered"
unzip -q "$archive" -d "$work_dir/v1"
validate_bundle_dir "$work_dir/v1"

# Negative check: missing file and tampered manifest must be rejected
cp -R "$work_dir/v1/." "$work_dir/tampered/"
rm -f "$work_dir/tampered/backend/build/libs/backend.jar"
if validate_bundle_dir "$work_dir/tampered" >/dev/null 2>&1; then
    echo "Negative check failed: incomplete bundle without backend.jar was accepted." >&2
    exit 1
fi

cp -R "$work_dir/v1/." "$work_dir/v2/"
# Simulate a newer candidate bundle deployment directory sharing the same schemaVersion=1
sed 's/"version": "[^"]*"/"version": "99.0.0-candidate"/' "$work_dir/v1/manifest.json" > "$work_dir/v2/manifest.json"
if validate_bundle_dir "$work_dir/v2" >/dev/null 2>&1; then
    echo "Negative check failed: manifest modification without matching SHA256SUMS was accepted." >&2
    exit 1
fi

# Rollback check: start v1, create data, switch to v2, then roll back to v1 preserving the volume
compose_cmd "$work_dir/v1" up --detach --wait --wait-timeout "$wait_timeout"

board_json=$(curl -fsS "${base_url}/api/board")
rev0=$(printf '%s' "$board_json" | tr -d '\r\n' | sed -n 's/.*"revision":\([0-9][0-9]*\).*/\1/p')
create_resp=$(curl -fsS -X POST "${base_url}/api/board/notes" \
    -H 'Content-Type: application/json' \
    -d "{\"content\":\"Rollback survival note\",\"color\":\"YELLOW\",\"expectedRevision\":${rev0}}")
note_id=$(printf '%s' "$create_resp" | tr -d '\r\n' | sed -n 's/.*"createdNoteId":"\([^"]*\)".*/\1/p')
snapshot_before=$(curl -fsS "${base_url}/api/board")

# Stop current deployment without removing the volume and roll back to verified v1 bundle
compose_cmd "$work_dir/v1" down
docker volume inspect "${project_name}_mongo-data" >/dev/null 2>&1
compose_cmd "$work_dir/v1" up --detach --wait --wait-timeout "$wait_timeout"

snapshot_after=$(curl -fsS "${base_url}/api/board")
if [ "$snapshot_before" != "$snapshot_after" ]; then
    echo "Rollback failed: board snapshot after rollback does not match pre-rollback state." >&2
    exit 1
fi
case "$snapshot_after" in
    *"$note_id"*) ;;
    *) echo "Rollback failed: created note missing after rollback." >&2; exit 1 ;;
esac

failed=0
echo "Release verification passed: bundle complete, tampering rejected, data-safe rollback preserved board state."
