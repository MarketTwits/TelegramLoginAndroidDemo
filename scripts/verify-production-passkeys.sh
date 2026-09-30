#!/usr/bin/env bash
set -Eeuo pipefail

# Production Digital Asset Links (DAL) and Passkey Readiness Verification
# Validates assetlinks.json and backend readiness without leaking secrets.

readonly base_url="${1:-${PRODUCTION_BASE_URL:-}}"
readonly app_token="${2:-${APP_TOKEN:-}}"
readonly expected_package="${3:-${EXPECTED_PACKAGE_NAME:-com.markettwits.devx.tgsignin}}"
readonly expected_fingerprints="${4:-${EXPECTED_CERT_SHA256:-}}"
readonly expected_revision="${5:-${EXPECTED_REVISION:-}}"
readonly expected_api_version="${6:-${EXPECTED_API_VERSION:-8}}"

if [[ -z "$base_url" ]]; then
  echo "Usage: $0 <base-url> [app-token] [expected-package] [expected-cert-sha256] [expected-revision] [expected-api-version]" >&2
  exit 2
fi

clean_base_url="${base_url%/}"
if [[ "$clean_base_url" != https://* && "$clean_base_url" != http://127.0.0.1:* && "$clean_base_url" != http://localhost:* ]]; then
  echo "ERROR: Base URL must start with https:// for production verification" >&2
  exit 2
fi

echo "=================================================="
echo "Passkey Production Verification"
echo "Target Base URL: $clean_base_url"
echo "Expected Package: $expected_package"
if [[ -n "$expected_revision" ]]; then
  echo "Expected Revision: $expected_revision"
fi
echo "=================================================="

readonly tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

readonly assetlinks_file="$tmp_dir/assetlinks.json"
readonly assetlinks_url="$clean_base_url/.well-known/assetlinks.json"

echo "--> Checking $assetlinks_url..."
http_info="$(curl --silent --show-error \
  --max-time 15 \
  --max-filesize 131072 \
  --output "$assetlinks_file" \
  --write-out "%{http_code}:%{num_redirects}:%{content_type}" \
  "$assetlinks_url" 2>/dev/null || true)"

if [[ -z "$http_info" ]]; then
  echo "FAIL: Could not connect to $assetlinks_url" >&2
  exit 3
fi

IFS=':' read -r http_code num_redirects content_type <<< "$http_info"

if [[ "$http_code" != "200" ]]; then
  echo "FAIL: Expected HTTP 200 for assetlinks.json, got HTTP $http_code" >&2
  exit 3
fi

if [[ "$num_redirects" != "0" ]]; then
  echo "FAIL: Digital Asset Links must not follow redirects (got $num_redirects redirects)" >&2
  exit 3
fi

if [[ "$content_type" != *"application/json"* ]]; then
  echo "FAIL: Expected Content-Type application/json, got $content_type" >&2
  exit 3
fi
echo "  [OK] HTTP 200 without redirects, Content-Type: $content_type"

echo "--> Validating assetlinks.json content..."
node -e '
const fs = require("fs");
const file = process.argv[1];
const expectedPkg = process.argv[2];
const expectedFpStr = process.argv[3] || "";
const expectedFps = expectedFpStr ? expectedFpStr.split(",").map(s => s.trim().toUpperCase()).filter(Boolean) : [];

let data;
try {
  data = JSON.parse(fs.readFileSync(file, "utf8"));
} catch (e) {
  console.error("FAIL: assetlinks.json is not valid JSON:", e.message);
  process.exit(1);
}

if (!Array.isArray(data)) {
  console.error("FAIL: assetlinks.json root must be a JSON array");
  process.exit(1);
}

const credEntry = data.find(entry =>
  Array.isArray(entry.relation) &&
  entry.relation.includes("delegate_permission/common.get_login_creds")
);

if (!credEntry) {
  console.error("FAIL: No statement found with relation delegate_permission/common.get_login_creds");
  process.exit(1);
}

if (credEntry.target?.namespace !== "android_app") {
  console.error("FAIL: Target namespace must be \"android_app\", found:", credEntry.target?.namespace);
  process.exit(1);
}

if (credEntry.target?.package_name !== expectedPkg) {
  console.error(`FAIL: Target package_name mismatch: expected "${expectedPkg}", found "${credEntry.target?.package_name}"`);
  process.exit(1);
}

const foundFps = (credEntry.target?.sha256_cert_fingerprints || []).map(f => f.toUpperCase());
if (foundFps.length === 0) {
  console.error("FAIL: No sha256_cert_fingerprints found in statement");
  process.exit(1);
}

if (expectedFps.length > 0) {
  for (const exp of expectedFps) {
    if (!foundFps.includes(exp)) {
      console.error(`FAIL: Expected fingerprint "${exp}" not found in assetlinks.json`);
      process.exit(1);
    }
  }
  for (const found of foundFps) {
    if (!expectedFps.includes(found)) {
      console.error(`FAIL: Unexpected fingerprint "${found}" found in assetlinks.json`);
      process.exit(1);
    }
  }
}

console.log("  [OK] Statement found with delegate_permission/common.get_login_creds");
console.log(`  [OK] Package name: ${expectedPkg}`);
console.log(`  [OK] Fingerprints verified: ${foundFps.join(", ")}`);

// Print computed Android origins
for (const fp of foundFps) {
  const hex = fp.replaceAll(":", "");
  const digest = Buffer.from(hex, "hex").toString("base64url");
  console.log(`  [OK] Android Origin derived: android:apk-key-hash:${digest}`);
}
' "$assetlinks_file" "$expected_package" "$expected_fingerprints"

if [[ -n "$app_token" ]]; then
  echo "--> Checking readiness endpoint (/api/health/ready)..."
  readonly readiness_file="$tmp_dir/readiness.json"
  readonly readiness_url="$clean_base_url/api/health/ready"

  ready_status="$(curl --silent --show-error \
    --max-time 10 \
    --header "X-App-Token: $app_token" \
    --output "$readiness_file" \
    --write-out "%{http_code}" \
    "$readiness_url" 2>/dev/null || true)"

  if [[ "$ready_status" != "200" ]]; then
    echo "FAIL: Readiness endpoint returned HTTP $ready_status (expected 200)" >&2
    exit 4
  fi

  node -e '
  const fs = require("fs");
  const file = process.argv[1];
  const expectedRev = process.argv[2] || "";
  const expectedApiVer = parseInt(process.argv[3], 10);

  let data;
  try {
    data = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (e) {
    console.error("FAIL: Readiness response is not valid JSON:", e.message);
    process.exit(1);
  }

  if (data.status !== "ready") {
    console.error(`FAIL: Readiness status is "${data.status}" (expected "ready")`);
    process.exit(1);
  }
  if (data.database !== "connected") {
    console.error(`FAIL: Database is "${data.database}" (expected "connected")`);
    process.exit(1);
  }
  if (data.passkeys !== "configured") {
    console.error(`FAIL: Passkeys are "${data.passkeys}" (expected "configured")`);
    process.exit(1);
  }
  if (data.appToken && data.appToken !== "configured") {
    console.error(`FAIL: App token is "${data.appToken}" (expected "configured")`);
    process.exit(1);
  }
  if (data.apiVersion !== expectedApiVer) {
    console.error(`FAIL: API version is ${data.apiVersion} (expected ${expectedApiVer})`);
    process.exit(1);
  }
  if (expectedRev && data.revision !== expectedRev) {
    console.error(`FAIL: Deployed revision is "${data.revision}" (expected "${expectedRev}")`);
    process.exit(1);
  }

  console.log("  [OK] Backend readiness status: ready");
  console.log("  [OK] Database: connected");
  console.log("  [OK] Passkeys: configured");
  if (data.appToken) {
    console.log("  [OK] App Token: configured");
  }
  console.log(`  [OK] API Version: ${data.apiVersion}`);
  if (data.revision) {
    console.log(`  [OK] Revision: ${data.revision}`);
  }
  ' "$readiness_file" "$expected_revision" "$expected_api_version"
else
  echo "--> NOTE: APP_TOKEN not provided; skipped authenticated readiness check."
fi

echo "=================================================="
echo "SUCCESS: Passkey production verification passed!"
echo "=================================================="
