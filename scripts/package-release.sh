#!/bin/sh
# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

set -eu

version=${1:?usage: package-release.sh <version>}
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
bundle="$root/build/release-bundle"
archive="$root/sticky-notes-deliverables.zip"

cd "$root"
./gradlew packageReleaseZip -PreleaseVersion="$version" --no-daemon

# Verify that the generated manifest matches the requested release version and Git HEAD
grep -q "\"version\": \"$version\"" "$bundle/manifest.json"
revision=$(sed -n 's/.*"revision": "\([0-9a-f]*\)".*/\1/p' "$bundle/manifest.json")
if [ "$revision" != "$(git rev-parse HEAD)" ]; then
    printf 'Bundle revision %s does not match HEAD %s\n' "$revision" "$(git rev-parse HEAD)" >&2
    exit 1
fi

# Verify SHA-256 checksums of all delivered files
if command -v sha256sum >/dev/null 2>&1; then
    (cd "$bundle" && sha256sum --check --quiet SHA256SUMS)
else
    (cd "$bundle" && shasum -a 256 -c SHA256SUMS)
fi

test -s "$archive"
printf 'Packaged %s from %s\n' "$archive" "$revision"
