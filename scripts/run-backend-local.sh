#!/usr/bin/env sh
set -eu
umask 077

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
env_file="$project_dir/.env"
backend_dir="$project_dir/backend"
default_port=8080
minimum_node_major=22
minimum_node_minor=13
server_pid=""

if [ -f "$env_file" ]; then
  set -a
  . "$env_file"
  set +a
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Error: Node.js is not installed. Node.js 22.13 or newer is required." >&2
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "Error: npm is not installed." >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "Error: curl is required for the backend readiness check." >&2
  exit 1
fi

if ! node -e "const [major, minor] = process.versions.node.split('.').map(Number); process.exit(major > $minimum_node_major || (major === $minimum_node_major && minor >= $minimum_node_minor) ? 0 : 1)"; then
  echo "Error: Node.js 22.13 or newer is required. Installed: $(node --version)." >&2
  exit 1
fi

port=${PORT:-$default_port}
case "$port" in
  ''|*[!0-9]*)
    echo "Error: PORT must be a number between 1 and 65535." >&2
    exit 1
    ;;
esac
if [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
  echo "Error: PORT must be a number between 1 and 65535." >&2
  exit 1
fi

for obsolete_variable in BACKEND_PORT SQLITE_VOLUME_NAME SQLITE_DATABASE_PATH LOCAL_SQLITE_DATABASE_PATH; do
  eval "obsolete_value=\${$obsolete_variable:-}"
  if [ -n "$obsolete_value" ]; then
    echo "Warning: $obsolete_variable is no longer used by this script." >&2
  fi
done

SQLITE_DATABASE_PATH="$backend_dir/data/telegram-signin.sqlite"
export SQLITE_DATABASE_PATH
health_url="http://127.0.0.1:$port/api/health/ready"

read_backend_health() {
  if [ -n "${APP_TOKEN:-}" ]; then
    curl --fail --silent --show-error --max-time 2 \
      --header "X-App-Token: $APP_TOKEN" "$health_url" 2>/dev/null || true
  else
    curl --fail --silent --show-error --max-time 2 "$health_url" 2>/dev/null || true
  fi
}

configure_android_reverse() {
  if ! command -v adb >/dev/null 2>&1; then
    echo "Android port forwarding skipped: adb is not available."
    return
  fi

  if [ -n "${ANDROID_SERIAL:-}" ]; then
    android_serial=$ANDROID_SERIAL
  else
    android_devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    android_device_count=$(printf '%s\n' "$android_devices" | awk 'NF { count++ } END { print count + 0 }')
    if [ "$android_device_count" -eq 0 ]; then
      echo "Android port forwarding skipped: no running ADB device was found."
      return
    fi
    if [ "$android_device_count" -gt 1 ]; then
      echo "Android port forwarding skipped: set ANDROID_SERIAL when multiple devices are connected." >&2
      return
    fi
    android_serial=$android_devices
  fi

  if adb -s "$android_serial" reverse "tcp:$port" "tcp:$port" >/dev/null; then
    echo "Android port forwarding configured for $android_serial: 127.0.0.1:$port"
  else
    echo "Warning: could not configure Android port forwarding for $android_serial." >&2
  fi
}

configure_android_reverse

existing_health=$(read_backend_health)
if [ -n "$existing_health" ]; then
  echo "Backend is already running: $health_url"
  echo "$existing_health"
  exit 0
fi

mkdir -p "$backend_dir/data"
cd "$backend_dir"

if [ ! -d node_modules ] || [ package-lock.json -nt node_modules/.package-lock.json ]; then
  npm ci
fi

forward_signal() {
  if [ -n "$server_pid" ]; then
    kill -TERM "$server_pid" 2>/dev/null || true
  fi
}
trap forward_signal INT TERM HUP

npm start &
server_pid=$!

attempt=1
max_attempts=30
while [ "$attempt" -le "$max_attempts" ]; do
  if ! kill -0 "$server_pid" 2>/dev/null; then
    wait "$server_pid"
    exit $?
  fi

  readiness=$(read_backend_health)
  if [ -n "$readiness" ]; then
    echo "Backend is ready: $health_url"
    echo "$readiness"
    wait "$server_pid"
    exit $?
  fi

  attempt=$((attempt + 1))
  sleep 0.25
done

echo "Error: backend did not become ready at $health_url." >&2
kill -TERM "$server_pid" 2>/dev/null || true
wait "$server_pid" 2>/dev/null || true
exit 1
