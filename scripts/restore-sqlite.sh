#!/usr/bin/env bash
set -Eeuo pipefail

# Automated SQLite Restore and Disaster Recovery Verification Script
# Restores an encrypted or compressed backup snapshot, verifies integrity,
# and checks schema version and core tables.

readonly artifact_input="${1:-}"
readonly apply_target="${2:-}"
readonly encryption_key="${BACKUP_ENCRYPTION_KEY:-}"

if [[ -z "$artifact_input" ]]; then
  echo "Usage: $0 <backup-artifact-file> [--apply <destination-db-path>]" >&2
  exit 2
fi

if [[ ! -f "$artifact_input" ]]; then
  echo "ERROR: Backup artifact not found: $artifact_input" >&2
  exit 2
fi

readonly started_at="$(date +%s)"
readonly work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

echo "=================================================="
echo "Starting SQLite Disaster Recovery / Restore Check"
echo "Artifact: $artifact_input"
echo "=================================================="

# Check checksum if companion .sha256 file exists
readonly companion_checksum="$artifact_input.sha256"
if [[ -f "$companion_checksum" ]]; then
  echo "--> Verifying SHA-256 checksum..."
  expected_hash="$(cat "$companion_checksum")"
  actual_hash="$(sha256sum "$artifact_input" | awk '{print $1}')"
  if [[ "$expected_hash" != "$actual_hash" ]]; then
    echo "FAIL: Checksum mismatch! Expected $expected_hash, got $actual_hash" >&2
    exit 3
  fi
  echo "  [OK] SHA-256 checksum verified: $actual_hash"
fi

current_file="$work_dir/stage"
cp "$artifact_input" "$current_file"

# Decrypt if needed
if [[ "$artifact_input" == *.enc ]]; then
  echo "--> Decrypting artifact..."
  if [[ -z "$encryption_key" ]]; then
    echo "ERROR: BACKUP_ENCRYPTION_KEY is required to restore an encrypted artifact" >&2
    exit 4
  fi
  decrypted_file="$work_dir/decrypted.gz"
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
    -in "$current_file" \
    -out "$decrypted_file" \
    -k "$encryption_key"
  current_file="$decrypted_file"
fi

# Decompress if needed
restored_sqlite="$work_dir/restored.sqlite"
if [[ "$artifact_input" == *.gz* ]]; then
  echo "--> Decompressing archive..."
  gzip -d -c "$current_file" > "$restored_sqlite"
else
  cp "$current_file" "$restored_sqlite"
fi

echo "--> Verifying database integrity and schema..."
node -e '
import { DatabaseSync } from "node:sqlite";
const dbPath = process.argv[1];

const db = new DatabaseSync(dbPath);
try {
  const integrity = db.prepare("PRAGMA integrity_check;").all();
  if (!integrity || integrity.length === 0 || integrity[0].integrity_check !== "ok") {
    console.error("FAIL: PRAGMA integrity_check failed:", JSON.stringify(integrity));
    process.exit(1);
  }
  console.log("  [OK] PRAGMA integrity_check: ok");

  const userVersionRow = db.prepare("PRAGMA user_version;").get();
  const userVersion = userVersionRow ? userVersionRow.user_version : 0;
  console.log(`  [OK] Schema user_version: ${userVersion}`);

  // Check required tables
  const expectedTables = ["app_users", "app_sessions", "app_profiles", "passkey_credentials"];
  const tables = db.prepare("SELECT name FROM sqlite_master WHERE type = '\''table'\'';").all().map(r => r.name);

  for (const table of expectedTables) {
    if (!tables.includes(table)) {
      console.error(`FAIL: Missing required table "${table}" in restored database`);
      process.exit(1);
    }
  }
  console.log(`  [OK] All critical tables present: ${expectedTables.join(", ")}`);

  // Run read-only row counts
  for (const table of expectedTables) {
    const countRow = db.prepare(`SELECT COUNT(*) AS cnt FROM "${table}";`).get();
    console.log(`  [OK] Table ${table}: ${countRow.cnt} records`);
  }
} finally {
  db.close();
}
' "$restored_sqlite"

readonly duration="$(( $(date +%s) - started_at ))"
echo "  [OK] Restore verification completed in ${duration}s"

if [[ "$apply_target" == "--apply" && -n "${3:-}" ]]; then
  target_db="$3"
  echo "--> Applying restored database to target: $target_db..."
  mkdir -p "$(dirname "$target_db")"
  if [[ -f "$target_db" ]]; then
    backup_existing="$target_db.pre-restore-$(date +%s).bak"
    echo "  [INFO] Backing up existing database to $backup_existing"
    cp "$target_db" "$backup_existing"
  fi
  cp "$restored_sqlite" "$target_db"
  chmod 600 "$target_db"
  echo "  [OK] Successfully deployed restored database to $target_db"
fi

echo "=================================================="
echo "SUCCESS: SQLite Disaster Recovery Drill Passed!"
echo "Restore verified cleanly without corruption."
echo "=================================================="
