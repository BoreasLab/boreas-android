#!/usr/bin/env bash
#
# Materialises the release signing key, when the repository has one.
#
# Signing is optional and its absence is a state rather than a failure: this
# repository can be forked and built by somebody who has no key and wants an
# artefact to inspect. What must never happen is handing a tester an APK that
# looks installable and is not, so the answer is an output the caller names its
# assets from.
#
# Reads KEYSTORE_BASE64 from the environment, writes the key outside the
# workspace so no later step can upload it, and prints two outputs:
#
#   signed=true|false
#   keystore=<path>   (empty when unsigned)
#
# Requires bash 4+, base64, and a writable RUNNER_TEMP. Exits nonzero if the
# secret is present and unusable, which is the case that must not be silent.

set -euo pipefail

(($# == 0)) || {
  printf 'usage: %s\n' "${0##*/}" >&2
  exit 2
}

: "${GITHUB_OUTPUT:?this script writes GitHub step outputs}"

emit() { printf '%s\n' "$1" >> "$GITHUB_OUTPUT"; }

if [[ -z "${KEYSTORE_BASE64:-}" ]]; then
  printf 'no signing key in the environment; artefacts will be unsigned\n' >&2
  emit 'signed=false'
  emit 'keystore='
  exit 0
fi

# Outside the workspace on purpose. A key inside it can be swept up by a later
# upload-artifact glob, and the artefact is public.
keystore="${RUNNER_TEMP:-/tmp}/boreas-release.jks"

# A secret that is present and malformed is a broken release, not an unsigned
# one. Decoding into place and checking the result separates "no key" from
# "wrong key" before Gradle reports it as a signing failure fifteen minutes on.
if ! printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$keystore" 2>/dev/null; then
  printf 'BOREAS_KEYSTORE_BASE64 is not valid base64\n' >&2
  rm -f "$keystore"
  exit 1
fi

if [[ ! -s "$keystore" ]]; then
  printf 'BOREAS_KEYSTORE_BASE64 decoded to an empty file\n' >&2
  rm -f "$keystore"
  exit 1
fi

chmod 600 "$keystore"
printf 'signing key unsealed at %s\n' "$keystore" >&2
emit 'signed=true'
emit "keystore=$keystore"
