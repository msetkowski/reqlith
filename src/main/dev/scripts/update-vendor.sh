#!/usr/bin/env bash
# Downloads the front-end libraries the UI needs and copies them into
# src/main/resources/static/vendor, so the app never depends on a CDN.
# Bump a version below and re-run to upgrade.
set -euo pipefail

HTMX_VERSION=2.0.11
MERMAID_VERSION=11.17.2
PLEX_SANS_VERSION=5.3.0
PLEX_MONO_VERSION=5.3.0

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"  # repository root
VENDOR="$ROOT/src/main/resources/static/vendor"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fetch() { # <tarball url> <target dir>
  mkdir -p "$2"
  curl -fsSL "$1" | tar -xz -C "$2"
}

fetch "https://registry.npmjs.org/htmx.org/-/htmx.org-${HTMX_VERSION}.tgz" "$WORK/htmx"
fetch "https://registry.npmjs.org/mermaid/-/mermaid-${MERMAID_VERSION}.tgz" "$WORK/mermaid"
fetch "https://registry.npmjs.org/@fontsource/ibm-plex-sans/-/ibm-plex-sans-${PLEX_SANS_VERSION}.tgz" "$WORK/sans"
fetch "https://registry.npmjs.org/@fontsource/ibm-plex-mono/-/ibm-plex-mono-${PLEX_MONO_VERSION}.tgz" "$WORK/mono"

mkdir -p "$VENDOR/fonts"
cp "$WORK/htmx/package/dist/htmx.min.js" "$VENDOR/htmx.min.js"
cp "$WORK/mermaid/package/dist/mermaid.min.js" "$VENDOR/mermaid.min.js"
for w in 400 500 600; do
  cp "$WORK/sans/package/files/ibm-plex-sans-latin-ext-${w}-normal.woff2" "$VENDOR/fonts/"
  cp "$WORK/sans/package/files/ibm-plex-sans-latin-${w}-normal.woff2" "$VENDOR/fonts/"
done
cp "$WORK/sans/package/files/ibm-plex-sans-latin-400-italic.woff2" "$VENDOR/fonts/"
for w in 400 500; do
  cp "$WORK/mono/package/files/ibm-plex-mono-latin-${w}-normal.woff2" "$VENDOR/fonts/"
done

# Licenses travel with the redistributed files (the minified files carry no license header).
mkdir -p "$VENDOR/licenses"
cp "$WORK/htmx/package/LICENSE" "$VENDOR/licenses/htmx.LICENSE"
cp "$WORK/mermaid/package/LICENSE" "$VENDOR/licenses/mermaid.LICENSE"
cp "$WORK/sans/package/LICENSE" "$VENDOR/licenses/ibm-plex-sans.LICENSE"
cp "$WORK/mono/package/LICENSE" "$VENDOR/licenses/ibm-plex-mono.LICENSE"

cat > "$VENDOR/VERSIONS" <<EOF
htmx.org ${HTMX_VERSION} (0BSD)
mermaid ${MERMAID_VERSION} (MIT)
@fontsource/ibm-plex-sans ${PLEX_SANS_VERSION} (OFL-1.1)
@fontsource/ibm-plex-mono ${PLEX_MONO_VERSION} (OFL-1.1)
EOF
echo "Vendored assets updated in $VENDOR"
