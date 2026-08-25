#!/usr/bin/env bash
#
# Writes the release notes body to stdout.
#
# Its whole job is the composition record. An artefact from this repository is
# an app half and a core half pinned together, and a bug report has to map to
# one (app version, core version) pair or it maps to nothing. The same pair is
# on the app's About screen, so a tester reading a phone and a maintainer
# reading a release page are looking at the same two lines.
#
# Reads TAG, VERSION_NAME, and PROVENANCE from the environment; reads the core
# pin from gradle/boreas-core.properties. Requires bash 4+ and git.

set -euo pipefail

(($# == 0)) || {
  printf 'usage: %s\n' "${0##*/}" >&2
  exit 2
}

: "${TAG:?the resolved tag}"
: "${VERSION_NAME:?the resolved versionName}"
: "${PROVENANCE:?the resolved provenance}"

repo_root() {
  local here
  here="$(cd -- "$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)"
  git -C "$here" rev-parse --show-toplevel
}

REPO="$(repo_root)"
readonly REPO
readonly PIN="$REPO/gradle/boreas-core.properties"

# The pinned core, read from the file the build reads. Anchored to the start of
# the line so a commented example cannot be mistaken for the pin.
core_tag="$(sed -n 's/^tag=//p' "$PIN" | head -n1)"
if [[ -z "$core_tag" ]]; then
  printf 'gradle/boreas-core.properties declares no tag\n' >&2
  exit 1
fi

cat <<NOTES
## Composition

\`\`\`
app  $VERSION_NAME  ($PROVENANCE)
core $core_tag
\`\`\`

## Verifying

Every asset carries SLSA build provenance. Before installing anything:

\`\`\`
gh attestation verify <asset> --repo ${GITHUB_REPOSITORY:-BoreasLab/boreas-android}
\`\`\`

\`SHA256SUMS\` covers every asset in this release. A checksum proves the file
matches a list published beside it; the attestation proves which workflow built
it, from which commit. Check the attestation.
NOTES

if [[ "${PRERELEASE:-}" == 'true' ]]; then
  cat <<'NOTES'

## This is a pre-release

Cut automatically from `main`. It has passed the same gate a release passes and
is marked pre-release, so it never becomes "Latest" and `gh release download`
with no tag will not return it.
NOTES
fi

printf '\nBuilt from %s.\n' "$TAG"
