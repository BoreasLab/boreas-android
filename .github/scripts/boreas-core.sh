#!/usr/bin/env bash
#
# Fetch the pinned Boreas core release into the build's cache and prove where it
# came from, before anything unpacks it.
#
# Assumes bash 4+, git, curl, GNU coreutils, and the GitHub CLI with a token in
# the environment (the runner's own is enough: the attestations are public).
#
# Two checks, answering different questions. The digest in
# gradle/boreas-core.properties answers "did this arrive intact", and it is the
# weaker of the two -- a checksum only proves the file matches a list that came
# from the same place the file did. Build provenance answers "was this built by
# that workflow, from that commit", which is the claim that matters for an
# artifact fetched on every build. api/artifacts.md asks for the provenance check
# to run in CI rather than once by hand, and that is what this script is for.
#
# The download lands in the same cache the Gradle task reads, keyed by tag, so
# the build finds it already present and verified rather than fetching a second
# copy that nothing checked.
set -euo pipefail

# The repository these checks belong to.
#
# Asked of git, from the script's own directory, rather than assumed to be the
# working directory. Anchoring on the caller's cwd means the answer changes with
# where the script is invoked from, and the wrong answer is still a valid path.
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
cd "$REPO"

readonly PIN="gradle/boreas-core.properties"
readonly SOURCE_REPOSITORY="BoreasLab/boreas-core"
# Must match FetchBoreasCore.cacheDirectory in app/build.gradle.kts. Both derive
# from Gradle's user home so that the script fills the cache the build reads.
readonly CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/boreas-core"

(($# == 0)) || { printf 'usage: %s\n' "${0##*/}" >&2; exit 2; }

# One key out of the pin file. Fails rather than returning empty, because an
# empty tag would build a URL that 404s and read as a network problem.
pinned() {
  local key="${1:?key required}" value
  value="$(sed -n "s/^${key}=//p" "$PIN")"
  [[ -n "$value" ]] || { printf 'no %s in %s\n' "$key" "$PIN" >&2; return 1; }
  printf '%s\n' "$value"
}

main() {
  local tag archive expected actual target url

  tag="$(pinned tag)"
  archive="$(pinned archive)"
  expected="$(pinned sha256)"

  target="$CACHE/$tag/$archive"
  url="https://github.com/$SOURCE_REPOSITORY/releases/download/$tag/$archive"

  if [[ -f "$target" ]] && [[ "$(sha256sum "$target" | cut -d' ' -f1)" == "$expected" ]]; then
    printf 'cached %s\n' "$target"
  else
    mkdir -p -- "$CACHE/$tag"
    # -f so an HTTP error page is a failure rather than a file that hashes wrong
    # for a reason nobody reads. Written beside the target and renamed, so an
    # interrupted transfer cannot be mistaken for a cached download.
    curl --fail --silent --show-error --location \
      --retry 3 --retry-delay 2 --retry-connrefused \
      --output "$target.part" "$url"
    mv -f -- "$target.part" "$target"
    printf 'fetched %s\n' "$url"
  fi

  actual="$(sha256sum "$target" | cut -d' ' -f1)"
  if [[ "$expected" != "$actual" ]]; then
    rm -f -- "$target"
    printf 'digest mismatch for %s\n' "$tag" >&2
    printf '  expected %s\n  actual   %s\n' "$expected" "$actual" >&2
    printf 'the file has been deleted; check %s\n' "$PIN" >&2
    return 1
  fi
  printf 'digest verified %s\n' "$actual"

  command -v gh >/dev/null ||
    { printf 'the GitHub CLI is required to verify build provenance\n' >&2; return 1; }

  # Prints the workflow and the commit the archive was built from, and fails if
  # no attestation covers this exact file.
  gh attestation verify "$target" --repo "$SOURCE_REPOSITORY"
}

main "$@"
