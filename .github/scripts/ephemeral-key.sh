#!/usr/bin/env bash
#
# Makes a signing key that lives as long as this job.
#
# Android installs nothing unsigned, so running the release build on a device
# needs a key, and the release key is a secret this job must not hold: a job with
# no secret runs on a pull request from a fork, and this one should.
#
# Throwaway by construction. The password is random and masked, the key is
# written outside the workspace so no upload glob can sweep it up, and nothing
# signed with it may be published: such an artefact would install and would not
# be ours.
#
# Exports the four variables app/build.gradle.kts reads, rather than printing
# them as step outputs, because one of them is a password and outputs are not
# masked in a re-run log.
#
# Requires bash 4+, keytool from the JDK, openssl, and a writable RUNNER_TEMP.
set -euo pipefail

(($# == 0)) || {
  printf 'usage: %s\n' "${0##*/}" >&2
  exit 2
}

: "${GITHUB_ENV:?this script exports the signing environment}"
: "${RUNNER_TEMP:?the key must be written outside the workspace}"

readonly KEYSTORE="$RUNNER_TEMP/boreas-ephemeral.p12"
readonly ALIAS='ephemeral'
password="$(openssl rand -hex 24)"
readonly password

# Before the value can reach a log through keytool's diagnostics.
printf '::add-mask::%s\n' "$password"

keytool -genkeypair -noprompt \
  -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$password" \
  -alias "$ALIAS" -keypass "$password" \
  -keyalg RSA -keysize 2048 -validity 30 \
  -dname 'CN=boreas ephemeral, OU=ci, O=boreas' >/dev/null

{
  printf 'BOREAS_KEYSTORE=%s\n' "$KEYSTORE"
  printf 'BOREAS_KEYSTORE_PASSWORD=%s\n' "$password"
  printf 'BOREAS_KEY_ALIAS=%s\n' "$ALIAS"
  printf 'BOREAS_KEY_PASSWORD=%s\n' "$password"
} >> "$GITHUB_ENV"

printf 'ephemeral key at %s, valid 30 days, never published\n' "$KEYSTORE"
