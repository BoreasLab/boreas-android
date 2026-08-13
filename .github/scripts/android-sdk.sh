#!/usr/bin/env bash
#
# Install the Android SDK packages this build compiles against, using the
# sdkmanager already present on the runner image.
#
# Assumes bash 4+ and the ubuntu-latest runner's preinstalled Android SDK.
#
# No third-party action for this. sdkmanager already ships on the image, it
# already verifies what it downloads against Google's repository manifest, and
# a wrapper action would add an owner to trust for the sake of three lines.
set -euo pipefail

# The parse boundary: the caller names the packages, and nothing below re-checks
# them. Passing none is a caller error rather than a silent no-op.
if (($# == 0)); then
  printf 'usage: %s <sdk-package>...\n' "$0" >&2
  exit 2
fi

readonly SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?ANDROID_HOME or ANDROID_SDK_ROOT must be set}}"

# The command-line tools live under a version directory that has been named
# both "latest" and a version number across image revisions, so find it rather
# than assuming either.
find_sdkmanager() {
  local candidate
  for candidate in "$SDK_ROOT"/cmdline-tools/*/bin/sdkmanager; do
    [[ -x "$candidate" ]] && {
      printf '%s\n' "$candidate"
      return 0
    }
  done
  return 1
}

main() {
  local sdkmanager
  sdkmanager="$(find_sdkmanager)" || {
    printf 'no sdkmanager under %s/cmdline-tools\n' "$SDK_ROOT" >&2
    return 1
  }

  # Licenses are normally pre-accepted on the runner image. Accepting again is
  # idempotent, and its failure is not the failure worth reporting: the install
  # below will say so itself, with the package that could not be had.
  "$sdkmanager" --licenses >/dev/null 2>&1 <<<"$(yes y | head -n 50)" || true

  "$sdkmanager" --install "$@"
  printf 'installed: %s\n' "$*"
}

main "$@"
