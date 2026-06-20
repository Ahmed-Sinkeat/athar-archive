#!/usr/bin/env bash
# Mirror a local media directory into the R2 bucket. See docs/media-and-backup.md.
# Requires: rclone with an "r2" remote configured; R2_BUCKET env var.
# Usage: R2_BUCKET=athar-media ./scripts/upload-media.sh ./local-media
set -euo pipefail

SRC="${1:?usage: upload-media.sh <local-dir>}"
BUCKET="${R2_BUCKET:?set R2_BUCKET (e.g. athar-media)}"

if ! command -v rclone >/dev/null 2>&1; then
  echo "rclone not found — install it and configure an 'r2' remote (see docs/media-and-backup.md)." >&2
  exit 1
fi

echo "Syncing ${SRC} → r2:${BUCKET} (Opus audio, covers, pdf/epub) …"
rclone sync "$SRC" "r2:${BUCKET}" \
  --progress \
  --s3-no-check-bucket \
  --exclude ".DS_Store" \
  --header-upload "Cache-Control: public, max-age=31536000, immutable"

echo "Done. Verify a sample URL resolves under https://r2.ahlalathar.com/"
