#!/usr/bin/env bash
#
# Collects the release outputs under dist/, named for the version they carry.
#
# AGP writes them under names that do not change between builds, so an asset
# uploaded as `app-release.aab` says nothing about which build it is. Every
# asset here carries the versionName, and an unsigned one says so in its name:
# a tester who downloads `-unsigned.apk` learns that before `adb install` does.
#
# Reads VERSION_NAME and SIGNED from the environment. Requires bash 4+ and a
# completed `:app:bundleRelease :app:assembleRelease`.

set -euo pipefail

(($# == 0)) || {
  printf 'usage: %s\n' "${0##*/}" >&2
  exit 2
}

: "${VERSION_NAME:?the resolved versionName}"
readonly SIGNED="${SIGNED:-false}"

repo_root() {
  local here
  here="$(cd -- "$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)"
  git -C "$here" rev-parse --show-toplevel
}

cd "$(repo_root)"

suffix=''
[[ "$SIGNED" == 'true' ]] || suffix='-unsigned'

mkdir -p dist

# Exactly one of each, found rather than assumed: AGP's output path has moved
# between major versions, and a glob that silently matches nothing would publish
# a release with no assets, which is the one state a consumer cannot recover from.
collect() {
  local kind="$1" pattern="$2" found
  mapfile -t found < <(find app/build/outputs -type f -name "$pattern" | sort)
  if ((${#found[@]} != 1)); then
    printf 'expected exactly one %s matching %s, found %d\n' \
      "$kind" "$pattern" "${#found[@]}" >&2
    printf '  %s\n' "${found[@]}" >&2
    return 1
  fi
  cp -- "${found[0]}" "dist/boreas-${VERSION_NAME}${suffix}.${kind}"
  printf 'staged %s as boreas-%s%s.%s\n' "${found[0]}" "$VERSION_NAME" "$suffix" "$kind"
}

collect aab '*-release.aab'
collect apk '*-release*.apk'

ls -l dist
