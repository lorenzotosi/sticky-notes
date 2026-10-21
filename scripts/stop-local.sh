#!/usr/bin/env sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

# Stops the Sticky Notes stack with Docker Compose.
#
# The containers and the network are removed, the named volume is kept: the
# board survives the stop and the next start. Removing the volume is a
# deliberate deletion of the data and is never part of a normal stop.
#
# Usage:
#
#   scripts/stop-local.sh [--dev]
#
#   --dev   also applies the development override used to start the stack

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
root_dir=$(dirname -- "$script_dir")
dev=0

usage() {
    echo "Usage: $0 [--dev]" >&2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --dev)
            dev=1
            shift
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

# The Compose files are kept as positional parameters, so a repository path
# with spaces does not split the arguments.
set -- -f "$root_dir/compose.yaml"
if [ "$dev" -eq 1 ]; then
    set -- "$@" -f "$root_dir/compose.dev.yaml"
fi

docker compose --project-directory "$root_dir" "$@" down

echo "Sticky Notes stopped. The board data is still in the named MongoDB volume."
