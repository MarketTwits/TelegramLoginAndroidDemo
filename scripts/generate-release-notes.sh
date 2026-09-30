#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <release-tag> <output-file>" >&2
  exit 2
fi

release_tag="$1"
output_file="$2"
repository="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
server_url="${GITHUB_SERVER_URL:-https://github.com}"
release_ref="${GITHUB_SHA:-HEAD}"
release_tag_pattern='^[0-9]+\.[0-9]+\.[0-9]+\.[1-9][0-9]*$'

if [[ ! "$release_tag" =~ $release_tag_pattern ]]; then
  echo "Release tag must match X.Y.Z.versionCode: $release_tag" >&2
  exit 1
fi

previous_tag="$({
  git tag --merged "$release_ref" --sort=-version:refname \
    | grep -E "$release_tag_pattern" \
    | grep -Fxv "$release_tag" \
    || true
} | head -n 1)"

if [[ -n "$previous_tag" ]]; then
  commit_range="$previous_tag..$release_ref"
else
  commit_range="$release_ref"
fi

: > "$output_file"

if [[ -n "${APK_SHA256:-}" || -n "${SIGNING_CERT_SHA256:-}" ]]; then
  version_part="${release_tag%.*}"
  code_part="${release_tag##*.}"
  cat >> "$output_file" <<EOF
### Release Verification
- **Version**: \`v$version_part\` (Build \`$code_part\`)
- **Target Commit**: \`$release_ref\`
EOF
  if [[ -n "${APK_SHA256:-}" ]]; then
    printf -- '- **APK SHA-256**: `%s`\n' "$APK_SHA256" >> "$output_file"
  fi
  if [[ -n "${SIGNING_CERT_SHA256:-}" ]]; then
    printf -- '- **Signing Certificate SHA-256**: `%s`\n' "$SIGNING_CERT_SHA256" >> "$output_file"
  fi
  printf -- '- **Minimum Backend API Version**: `%s`\n\n' "${MIN_BACKEND_API_VERSION:-8}" >> "$output_file"
  printf '### What'\''s Changed\n\n' >> "$output_file"
fi

while IFS=$'\t' read -r commit subject; do
  [[ -n "$commit" && -n "$subject" ]] || continue
  printf -- '- [%s](%s/%s/commit/%s)\n' \
    "$subject" "$server_url" "$repository" "$commit" >> "$output_file"
done < <(git log --no-merges --format=$'%H\t%s' "$commit_range")

if [[ ! -s "$output_file" ]]; then
  printf 'No user-visible changes in this release.\n\n' >> "$output_file"
else
  printf '\n' >> "$output_file"
fi

if [[ -n "$previous_tag" ]]; then
  printf '**Full Changelog**: %s/%s/compare/%s...%s\n' \
    "$server_url" "$repository" "$previous_tag" "$release_tag" >> "$output_file"
else
  printf '**Release commit**: %s/%s/commit/%s\n' \
    "$server_url" "$repository" "$release_ref" >> "$output_file"
fi
