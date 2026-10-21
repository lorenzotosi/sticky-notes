#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

if [ ! -f .env ]; then
  cp .env.example .env
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Error: docker command not found in PATH." >&2
  exit 1
fi

docker compose -f compose.yaml up --build -d --wait

echo "Sticky Notes is available at http://127.0.0.1:8080"
