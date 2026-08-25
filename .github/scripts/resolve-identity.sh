#!/usr/bin/env bash
#
# Runs the version algebra and hands its answer to the job that called it.
#
# The whole step is here rather than in the workflow because of what
# GITHUB_OUTPUT is: every line written to it becomes a job output, so anything
# that reaches it is read as `key=value` by the jobs downstream. A line of
# Gradle chatter surviving --quiet would either be a malformed output or, if it
# happened to contain an `=`, an output nobody declared.
#
# So the lines are counted before they are forwarded. Six keys, exactly, the
# ones Identity.lines() emits; a seventh or a fifth means the algebra and this
# script have drifted and the release stops rather than publishing under a name
# half of the pipeline does not agree on.
#
# Requires bash 4+, a JDK, and the Android SDK (configuring :app needs it).
# Writes the identity to stdout and to GITHUB_OUTPUT.

set -euo pipefail

(($# == 0)) || {
  printf 'usage: %s\n' "${0##*/}" >&2
  exit 2
}

: "${GITHUB_OUTPUT:?this script writes GitHub step outputs}"

readonly KEYS=(tag version versionName versionCode prerelease provenance)

repo_root() {
  local here
  here="$(cd -- "$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)"
  git -C "$here" rev-parse --show-toplevel
}

cd "$(repo_root)"

identity="$(./gradlew --no-daemon -q :resolve)"

for key in "${KEYS[@]}"; do
  if ! grep -qE "^$key=" <<<"$identity"; then
    printf 'the resolved identity carries no %s=\n' "$key" >&2
    printf '%s\n' "$identity" >&2
    exit 1
  fi
done

lines="$(grep -c . <<<"$identity")"
if ((lines != ${#KEYS[@]})); then
  printf 'expected %d lines from :resolve, got %d\n' "${#KEYS[@]}" "$lines" >&2
  printf '%s\n' "$identity" >&2
  exit 1
fi

printf '%s\n' "$identity"
printf '%s\n' "$identity" >> "$GITHUB_OUTPUT"
