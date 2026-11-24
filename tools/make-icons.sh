#!/usr/bin/env bash
# Generate launcher icon PNGs with the JDK only (no ImageMagick dependency).
set -euo pipefail
SRC="${1:-kiplay.png}"
[ -f "$SRC" ] || { echo "!! $SRC tidak ditemukan"; exit 1; }
OUT="${TMPDIR:-/tmp}/cinema-icon-tool"
mkdir -p "$OUT"
javac -d "$OUT" tools/MakeIcons.java
java -Djava.awt.headless=true -cp "$OUT" MakeIcons "$SRC"
