#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Starts the Sticky Notes stack with Docker Compose.
#
# The stack runs the images produced by `./gradlew buildImages`. The script
# creates `.env` from `.env.example` only when no `.env` exists, so an
# existing configuration is never overwritten, and it waits until every
# service reports healthy.
#
# Usage:
#
#   scripts/start-local.sh [--dev] [--wait-timeout SECONDS]
#
#   --dev           also applies the development override, which publishes the
#                   API and MongoDB on the loopback interface
#   --wait-timeout  seconds to wait for the healthy state, default 300

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(dirname -- "$script_dir")
wait_timeout=300
dev=0

usage() {
    echo "Usage: $0 [--dev] [--wait-timeout SECONDS]" >&2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --dev)
            dev=1
            shift
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

if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose is not available. Install Docker Desktop or the Compose plugin." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "The Docker engine is not running. Start Docker Desktop or the local Docker Engine." >&2
    exit 1
fi

if [ ! -f "$root_dir/.env" ]; then
    cp "$root_dir/.env.example" "$root_dir/.env"
    echo "Created .env from .env.example with demonstration values."
fi

# The image references are read from the configuration, so the missing image is
# reported before Compose tries to pull it from a registry.
api_image=$(sed -n 's/^API_IMAGE=//p' "$root_dir/.env" | tail -1)
web_image=$(sed -n 's/^WEB_IMAGE=//p' "$root_dir/.env" | tail -1)
for image in "${api_image:-sticky-notes-backend:local}" "${web_image:-sticky-notes-web:local}"; do
    if ! docker image inspect "$image" >/dev/null 2>&1; then
        echo "The image $image is not available. Run './gradlew buildImages' first." >&2
        exit 1
    fi
done

# The Compose files are kept as positional parameters, so a repository path
# with spaces does not split the arguments.
set -- -f "$root_dir/compose.yaml"
if [ "$dev" -eq 1 ]; then
    set -- "$@" -f "$root_dir/compose.dev.yaml"
fi

docker compose --project-directory "$root_dir" "$@" \
    up --detach --wait --wait-timeout "$wait_timeout"

if [ "$dev" -eq 1 ]; then
    echo "The API and the database are running."
    echo "Start the interface with 'npm run dev' in frontend/, it proxies /api to the API container."
else
    # The published address is read from the running container, so the message
    # stays correct when the host port differs from the default.
    address=$(docker compose --project-directory "$root_dir" "$@" port web 8080 2>/dev/null || echo "127.0.0.1:8080")
    echo "Sticky Notes is running at http://$address"
    echo "Stop it with scripts/stop-local.sh"
fi
