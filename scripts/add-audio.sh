#!/usr/bin/env bash
# add-audio.sh — Convert an audio file to Opus and upload to athar-media R2.
#
# Usage:
#   scripts/add-audio.sh <input-file> <output-key>
#
# Example:
#   scripts/add-audio.sh /path/to/file.m4a audio/manzumat-al-ilbiri.opus
#
# The output key becomes the R2 object key. The public URL will be:
#   https://r2.arthurarchive.com/<output-key>
#
# Opus encoding settings:
#   - 48 kHz mono (speech — default for recitation/lecture audio)
#   - 48 kbps (Opus at 48k mono ≈ better than MP3 128k stereo for speech)
#   - VBR (variable bitrate, default for libopus)
set -euo pipefail

INPUT="${1:?Usage: $0 <input-file> <output-key>}"
KEY="${2:?Usage: $0 <input-file> <output-key>}"

TMP="$(mktemp --suffix=.opus)"
trap 'rm -f "$TMP"' EXIT

echo "⟳ Converting to Opus 48kbps mono …"
ffmpeg -y -i "$INPUT" \
  -vn \
  -c:a libopus \
  -b:a 48k \
  -ac 1 \
  -ar 48000 \
  -application audio \
  "$TMP" 2>&1 | grep -E "^(ffmpeg|Input|Output|Stream|Error|size=)" || true

SIZE="$(stat -c '%s' "$TMP")"
DURATION="$(ffprobe -v quiet -show_entries format=duration -of csv=p=0 "$TMP" 2>/dev/null)"
DURATION_FMT="$(printf '%d:%02d' $((${DURATION%.*} / 60)) $((${DURATION%.*} % 60)))"

echo "✓ Encoded: ${SIZE} bytes, duration ${DURATION_FMT}"
echo "⟳ Uploading to R2: athar-media1/${KEY} …"

./node_modules/.bin/wrangler r2 object put "athar-media1/${KEY}" \
  --file "$TMP" \
  --content-type "audio/ogg; codecs=opus" \
  --remote

echo ""
echo "✓ Done."
echo ""
echo "  URL:          https://r2.arthurarchive.com/${KEY}"
echo "  size_bytes:   ${SIZE}"
echo "  duration:     ${DURATION_FMT}"
