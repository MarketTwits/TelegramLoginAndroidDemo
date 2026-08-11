#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
env_file="$project_dir/.env"

if [ -f "$env_file" ]; then
  set -a
  . "$env_file"
  set +a
fi

cd "$project_dir"
docker compose up --build -d
docker compose ps
echo "Backend: http://127.0.0.1:8080"
if [ -z "${TELEGRAM_CLIENT_ID:-}" ]; then
  echo "Setup mode: add TELEGRAM_CLIENT_ID to .env and restart to enable authentication."
fi
