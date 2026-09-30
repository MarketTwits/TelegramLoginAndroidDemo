#!/usr/bin/env bash
set -Eeuo pipefail

# Automated SQLite Online Backup Script
# Performs safe online snapshot using VACUUM INTO, integrity checks, compression,
# optional encryption, and generation retention without locking production writes.

readonly db_path="${1:-${SQLITE_DATABASE_PATH:-backend/data/telegram-signin.sqlite}}"
readonly backup_dir="${2:-${BACKUP_STORAGE_DIR:-backend/data/backups}}"
readonly encryption_key="${BACKUP_ENCRYPTION_KEY:-}"

if [[ ! -f "$db_path" ]]; then
  echo "ERROR: SQLite database file not found at: $db_path" >&2
  exit 2
fi

mkdir -p "$backup_dir"
readonly lock_file="$backup_dir/.backup.lock"

# Concurrency lock
if [[ -f "$lock_file" ]]; then
  existing_pid="$(cat "$lock_file" 2>/dev/null || true)"
  if [[ -n "$existing_pid" ]] && kill -0 "$existing_pid" 2>/dev/null; then
    echo "ERROR: Backup process already running (PID $existing_pid)" >&2
    exit 3
  fi
fi

echo "$$" > "$lock_file"
trap 'rm -f "$lock_file"' EXIT

readonly started_at="$(date +%s)"
readonly timestamp="$(date -u +"%Y%m%dT%H%M%SZ")"
readonly work_dir="$(mktemp -d "$backup_dir/.tmp-backup.XXXXXX")"
trap 'rm -rf "$work_dir"; rm -f "$lock_file"' EXIT

readonly snapshot_sqlite="$work_dir/snapshot-$timestamp.sqlite"

echo "=================================================="
echo "Starting SQLite Online Backup"
echo "Source: $db_path"
echo "Destination Directory: $backup_dir"
echo "Timestamp: $timestamp"
echo "=================================================="

echo "--> Executing online VACUUM INTO snapshot..."
node -e '
import { DatabaseSync } from "node:sqlite";
const sourcePath = process.argv[1];
const targetPath = process.argv[2];

const db = new DatabaseSync(sourcePath);
try {
  db.exec(`PRAGMA busy_timeout = 10000;`);
  const safeTarget = targetPath.replaceAll("\x27", "\x27\x27");
  db.exec(`VACUUM INTO \x27${safeTarget}\x27;`);
} finally {
  db.close();
}
' "$db_path" "$snapshot_sqlite"

echo "--> Verifying snapshot integrity..."
node -e '
import { DatabaseSync } from "node:sqlite";
const targetPath = process.argv[1];

const db = new DatabaseSync(targetPath);
try {
  const result = db.prepare("PRAGMA integrity_check;").all();
  if (!result || result.length === 0 || result[0].integrity_check !== "ok") {
    console.error("Integrity check failed:", JSON.stringify(result));
    process.exit(1);
  }
  const version = db.prepare("PRAGMA user_version;").get();
  console.log(`  [OK] PRAGMA integrity_check: ok (schema version: ${version?.user_version})`);
} finally {
  db.close();
}
' "$snapshot_sqlite"

echo "--> Compressing snapshot..."
gzip -9 "$snapshot_sqlite"
readonly compressed_file="$snapshot_sqlite.gz"

final_artifact="$backup_dir/backup-$timestamp.sqlite.gz"

if [[ -n "$encryption_key" ]]; then
  echo "--> Encrypting backup with AES-256-CBC..."
  final_artifact="$backup_dir/backup-$timestamp.sqlite.gz.enc"
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 100000 \
    -in "$compressed_file" \
    -out "$final_artifact" \
    -k "$encryption_key"
  chmod 600 "$final_artifact"
else
  mv "$compressed_file" "$final_artifact"
  chmod 600 "$final_artifact"
  echo "--> NOTE: BACKUP_ENCRYPTION_KEY not set. Archive saved compressed without encryption."
fi

# Calculate SHA-256 checksum
sha256sum "$final_artifact" | awk '{print $1}' > "$final_artifact.sha256"

readonly artifact_size="$(wc -c < "$final_artifact" | tr -d ' ')"
readonly artifact_sha256="$(cat "$final_artifact.sha256")"
readonly artifact_basename="$(basename "$final_artifact")"
readonly duration="$(( $(date +%s) - started_at ))"

is_encrypted="false"
if [[ -n "$encryption_key" ]]; then
  is_encrypted="true"
fi

# Record backup metadata
cat > "$backup_dir/latest.json" <<EOF
{
  "timestamp": "$timestamp",
  "file": "$artifact_basename",
  "sizeBytes": $artifact_size,
  "durationSeconds": $duration,
  "encrypted": $is_encrypted,
  "sha256": "$artifact_sha256"
}
EOF

echo "--> Applying retention policy..."
# Retention: Clean up files older than 30 days
find "$backup_dir" -maxdepth 1 -type f -name "backup-*" -mtime +30 -exec rm -f {} +

echo "=================================================="
echo "Backup Completed Successfully!"
echo "Artifact: $final_artifact [$artifact_size bytes, duration: ${duration}s]"
echo "Checksum: $artifact_sha256"
echo "=================================================="
