#!/usr/bin/env bash
set -Eeuo pipefail

readonly revision="${1:-}"
readonly encoded_app_directory="${2:-}"
readonly encoded_stack_directory="${3:-}"
readonly encoded_health_url="${4:-}"
readonly encoded_app_token="${5:-}"

if [[ ! "$revision" =~ ^[0-9a-f]{40}$ ]]; then
  echo "A full Git commit SHA is required" >&2
  exit 2
fi

if [[ -z "$encoded_app_directory" || -z "$encoded_stack_directory" || -z "$encoded_health_url" || -z "$encoded_app_token" ]]; then
  echo "Encoded deployment configuration is required" >&2
  exit 2
fi

readonly app_directory="$(printf '%s' "$encoded_app_directory" | base64 --decode)"
readonly stack_directory="$(printf '%s' "$encoded_stack_directory" | base64 --decode)"
readonly health_url="$(printf '%s' "$encoded_health_url" | base64 --decode)"
readonly app_token="$(printf '%s' "$encoded_app_token" | base64 --decode)"
export APP_TOKEN="$app_token"

cd "$app_directory"
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Deployment checkout contains local changes; refusing to overwrite them" >&2
  exit 3
fi

readonly previous_revision="$(git rev-parse HEAD)"
git fetch --quiet --prune origin main
git cat-file -e "$revision^{commit}"
git merge-base --is-ancestor "$revision" origin/main
git checkout --quiet --detach --force "$revision"

cd "$stack_directory"
test -f compose.yml
test -f .env
readonly compose_override="$(mktemp "$stack_directory/.deploy-override.XXXXXX.yml")"
chmod 600 "$compose_override"
trap 'rm -f "$compose_override"' EXIT
cat > "$compose_override" <<'YAML'
services:
  backend:
    environment:
      APP_TOKEN: ${APP_TOKEN:?APP_TOKEN is required}
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://127.0.0.1:8080/api/health/ready',{headers:{'X-App-Token':process.env.APP_TOKEN}}).then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"]
YAML

compose() {
  docker compose -f compose.yml -f "$compose_override" "$@"
}

compose config --quiet
compose build --pull --build-arg "BUILD_REVISION=$revision" backend
compose up --detach --no-deps backend

for attempt in $(seq 1 30); do
  response="$(curl --fail --silent --show-error -H "X-App-Token: $app_token" "$health_url" 2>/dev/null || true)"
  if grep -Eq '"status"[[:space:]]*:[[:space:]]*"ready"' <<< "$response" &&
    grep -Eq '"apiVersion"[[:space:]]*:[[:space:]]*8' <<< "$response" &&
    grep -Fq "\"revision\":\"$revision\"" <<< "$response"; then
    echo "Deployed $revision"
    printf '%s\n' "$response"
    exit 0
  fi
  sleep 2
done

compose ps >&2
compose logs --tail 100 backend >&2
echo "Deployment health check failed for $revision" >&2

if [[ "$previous_revision" != "$revision" ]]; then
  echo "Rolling back to $previous_revision" >&2
  cd "$app_directory"
  git checkout --quiet --detach --force "$previous_revision"
  cd "$stack_directory"
  compose build --build-arg "BUILD_REVISION=$previous_revision" backend
  compose up --detach --no-deps backend
fi
exit 4
