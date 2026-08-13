#!/usr/bin/env bash
#
# Audit the workflows for the failure modes actionlint does not model:
# over-broad permissions, unpinned actions, persisted credentials, and
# untrusted context interpolated into a shell.
#
# Assumes bash 4+, git, and a Python 3 with pip (the ubuntu-latest runner image).
set -euo pipefail

# The repository these checks belong to.
#
# Asked of git, from the script's own directory, rather than assumed to be the
# working directory. Anchoring on the caller's cwd means the answer changes with
# where the script is invoked from, and the wrong answer is still a valid path:
# run from a subdirectory it silently checks nothing, run from another checkout
# it silently checks that one. git answers the question actually being asked.
repo_root() {
  local here
  command -v git >/dev/null ||
    { printf 'git is required to locate the repository\n' >&2; return 1; }
  here="$(cd -- "$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)"
  git -C "$here" rev-parse --show-toplevel 2>/dev/null ||
    { printf 'not inside a git repository: %s\n' "$here" >&2; return 1; }
}

REPO="$(repo_root)"
readonly REPO
# Every path below is written relative to the repository root, and this is what
# makes that true regardless of where the caller stood.
cd "$REPO"

readonly VERSION="${ZIZMOR_VERSION:?ZIZMOR_VERSION must be set}"

# Isolated so the audit tool cannot disturb whatever Python the rest of the
# job might use, and torn down whichever way the script exits.
venv="$(mktemp -d)"
readonly venv
cleanup() { rm -rf "$venv"; }
trap cleanup EXIT

main() {
  python3 -m venv "$venv"
  "$venv/bin/pip" install --quiet --disable-pip-version-check "zizmor==${VERSION}"

  # --persona=regular reports what is actionable rather than every
  # theoretical finding; --min-severity=low still fails the job on anything
  # it does report, because a finding nobody has to act on should not be
  # reported at all.
  "$venv/bin/zizmor" \
    --persona=regular \
    --min-severity=low \
    --format=plain \
    .github/workflows
}

main "$@"
