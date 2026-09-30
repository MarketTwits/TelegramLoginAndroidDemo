#!/usr/bin/env bash
set -Eeuo pipefail

# Production Smoke Check: Safe read-only verification
# Does not mutate state or print secrets.

readonly target_url="${1:-${PRODUCTION_HEALTH_URL:-http://127.0.0.1:8080}}"
readonly base_url="${target_url%/}"
readonly app_token="${2:-${APP_TOKEN:-}}"

echo "==> Running production smoke check on: $base_url"

# 1. Health Live Check
echo "--> Checking /api/health/live..."
live_response="$(curl --fail --silent --show-error --max-time 10 "$base_url/api/health/live")"
if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"' <<< "$live_response"; then
  echo "FAIL: /api/health/live response unexpected: $live_response" >&2
  exit 1
fi
echo "  [PASS] Live endpoint responded OK"

# 2. Health Ready Check (Protected)
if [[ -n "$app_token" ]]; then
  echo "--> Checking protected /api/health/ready..."
  ready_response="$(curl --fail --silent --show-error --max-time 10 \
    -H "X-App-Token: $app_token" \
    "$base_url/api/health/ready")"
  if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"ready"' <<< "$ready_response"; then
    echo "FAIL: /api/health/ready is not ready: $ready_response" >&2
    exit 2
  fi
  if ! grep -Eq '"database"[[:space:]]*:[[:space:]]*"connected"' <<< "$ready_response"; then
    echo "FAIL: /api/health/ready database is not connected: $ready_response" >&2
    exit 2
  fi
  echo "  [PASS] Ready endpoint responded healthy (database connected)"
else
  echo "  [SKIP] /api/health/ready skipped (no APP_TOKEN provided)"
fi

# 3. Digital Asset Links Check
echo "--> Checking /.well-known/assetlinks.json..."
assetlinks_status="$(curl --silent --show-error --output /dev/null --write-out "%{http_code}" --max-time 10 "$base_url/.well-known/assetlinks.json")"
if [[ "$assetlinks_status" == "200" ]]; then
  assetlinks_content="$(curl --silent --show-error --max-time 10 "$base_url/.well-known/assetlinks.json")"
  if grep -Fq "com.markettwits.devx.tgsignin" <<< "$assetlinks_content" && \
     grep -Fq "delegate_permission/common.get_login_creds" <<< "$assetlinks_content"; then
    echo "  [PASS] Digital Asset Links correctly configured for package com.markettwits.devx.tgsignin"
  else
    echo "WARN: Digital Asset Links returned 200 but did not match expected package content" >&2
  fi
elif [[ "$assetlinks_status" == "404" ]]; then
  echo "  [NOTE] Digital Asset Links returned 404 (passkeys not configured on this host)"
else
  echo "FAIL: Digital Asset Links returned unexpected status: $assetlinks_status" >&2
  exit 3
fi

# 4. Profile Emoji Sets Check
echo "--> Checking /api/profile-emoji-sets..."
emoji_response="$(curl --fail --silent --show-error --max-time 10 "$base_url/api/profile-emoji-sets")"
if ! grep -Eq '"sets"[[:space:]]*:' <<< "$emoji_response"; then
  echo "FAIL: /api/profile-emoji-sets response unexpected" >&2
  exit 4
fi
echo "  [PASS] Profile emoji sets catalog accessible"

echo "==> All smoke checks passed successfully!"
