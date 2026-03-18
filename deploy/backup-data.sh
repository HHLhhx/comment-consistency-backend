#!/usr/bin/env bash
set -euo pipefail

# Backup docker volumes directory for migration or disaster recovery.
VOLUME_DIR="${1:-/data/comment-consistency-backend}"
BACKUP_DIR="${2:-/data/backups}"
TS="$(date +%Y%m%d-%H%M%S)"
ARCHIVE="${BACKUP_DIR}/comment-consistency-backend-${TS}.tar.gz"

mkdir -p "${BACKUP_DIR}"

tar -czf "${ARCHIVE}" -C "${VOLUME_DIR}" .
echo "Backup created: ${ARCHIVE}"
