#!/usr/bin/env bash
set -euo pipefail

# Restore docker volumes directory from backup archive.
ARCHIVE_PATH="${1:-}"
TARGET_DIR="${2:-/data/comment-consistency-backend}"

if [ -z "${ARCHIVE_PATH}" ]; then
  echo "Usage: $0 <backup_archive.tar.gz> [target_dir]"
  exit 1
fi

mkdir -p "${TARGET_DIR}"
tar -xzf "${ARCHIVE_PATH}" -C "${TARGET_DIR}"
echo "Restore completed to: ${TARGET_DIR}"
