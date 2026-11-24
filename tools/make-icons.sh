#!/usr/bin/env bash
# Generate semua ukuran ikon launcher dari kiplay.png di root repo.
set -euo pipefail

SRC="${1:-kiplay.png}"
[ -f "$SRC" ] || { echo "!! $SRC tidak ditemukan di root repo"; exit 1; }
IM="$(command -v magick || command -v convert)"
[ -n "$IM" ] || { echo "!! ImageMagick tidak terpasang"; exit 1; }

RES="app/src/main/res"
rm -f "$RES/mipmap-anydpi/ic_launcher.xml"

# --- ikon legacy (48dp dasar) + versi bulat ---
gen() {
  local dir="$1" px="$2" r=$(( $2 / 2 ))
  mkdir -p "$RES/$dir"
  "$IM" "$SRC" -resize "${px}x${px}^" -gravity center -extent "${px}x${px}" \
        -strip "$RES/$dir/ic_launcher.png"
  "$IM" "$RES/$dir/ic_launcher.png" -size "${px}x${px}" xc:none -fill white \
        -draw "circle ${r},${r} ${r},1" -compose CopyOpacity -composite \
        -strip "$RES/$dir/ic_launcher_round.png"
}
gen mipmap-mdpi 48
gen mipmap-hdpi 72
gen mipmap-xhdpi 96
gen mipmap-xxhdpi 144
gen mipmap-xxxhdpi 192

# --- adaptive icon (canvas 108dp, artwork 62% di tengah) ---
layer() {
  local dir="$1" canvas="$2" art="$3" blur="$4"
  mkdir -p "$RES/$dir"
  "$IM" "$SRC" -resize "${canvas}x${canvas}^" -gravity center -extent "${canvas}x${canvas}" \
        -blur "0x${blur}" -modulate 88 -strip "$RES/$dir/ic_launcher_background.png"
  "$IM" -size "${canvas}x${canvas}" xc:none \
        \( "$SRC" -resize "${art}x${art}^" -gravity center -extent "${art}x${art}" -strip \) \
        -gravity center -composite -strip "$RES/$dir/ic_launcher_foreground.png"
}
layer mipmap-mdpi 108 67 8
layer mipmap-hdpi 162 100 12
layer mipmap-xhdpi 216 134 16
layer mipmap-xxhdpi 324 201 24
layer mipmap-xxxhdpi 432 268 32

echo "OK: ikon launcher dibuat dari $SRC"
