#!/usr/bin/env bash
# Generate sprites/icon_wizard.icns from sprites/icon_wizard_1024.png using
# the macOS-only sips/iconutil tools. Run scripts/generate_icon.py first to
# (re)produce the 1024x1024 master PNG this reads from.
set -euo pipefail
cd "$(dirname "$0")/.."

SRC="sprites/icon_wizard_1024.png"
OUT="sprites/icon_wizard.icns"

if [[ ! -f "$SRC" ]]; then
  echo "Missing $SRC — run: python3 scripts/generate_icon.py" >&2
  exit 1
fi

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
ICONSET="$WORKDIR/icon.iconset"
mkdir -p "$ICONSET"

for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$SRC" --out "$ICONSET/icon_${size}x${size}.png" >/dev/null
  double=$((size * 2))
  sips -z "$double" "$double" "$SRC" --out "$ICONSET/icon_${size}x${size}@2x.png" >/dev/null
done

iconutil -c icns "$ICONSET" -o "$OUT"
echo "Generated $OUT"
