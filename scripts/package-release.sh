#!/bin/sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani
#
# Builds the release bundle at the version the release publishes, proves its
# records describe the delivered bytes, and packages it as the release asset.
# The semantic-release `prepare` hook runs this from the repository root, so the
# ZIP exists before the GitHub plugin uploads it.

set -eu

version=${1:?usage: package-release.sh <version>}
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
bundle="$root/build/release-bundle"
archive="$root/sticky-notes-deliverables.zip"

cd "$root"
./gradlew releaseBundle -PreleaseVersion="$version" --no-daemon

# The bundle must claim the version this release publishes and the commit this
# job checked out. A silent fallback to the development identity would publish
# an artifact that disagrees with its own release.
grep -q "\"version\": \"$version\"" "$bundle/manifest.json"
revision=$(sed -n 's/.*"revision": "\([0-9a-f]*\)".*/\1/p' "$bundle/manifest.json")
if [ "$revision" != "$(git rev-parse HEAD)" ]; then
    printf 'Bundle revision %s does not match HEAD %s\n' "$revision" "$(git rev-parse HEAD)" >&2
    exit 1
fi

# The record covers every delivered file except the editable `.env`.
if command -v sha256sum >/dev/null 2>&1; then
    (cd "$bundle" && sha256sum --check --quiet SHA256SUMS)
else
    (cd "$bundle" && shasum -a 256 -c SHA256SUMS)
fi

rm -f "$archive"
(cd "$bundle" && zip -qrX "$archive" .)
printf 'Packaged %s from %s\n' "$archive" "$revision"
