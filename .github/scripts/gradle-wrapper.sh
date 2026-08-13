#!/usr/bin/env bash
#
# Verify the committed Gradle wrapper jar against the checksum Gradle publishes
# for the version this repository declares.
#
# Assumes bash 4+ and GNU coreutils (the ubuntu-latest runner image).
#
# The wrapper jar is executable code committed to the repository, and the build
# runs it with whatever privileges the job holds. Checking it is the same
# argument as checking a downloaded release: the distribution URL says which
# artifact was asked for, and only the digest says which one is present.
#
# The expected checksum is not recorded here. It is fetched from the same
# metadata that names the version, so bumping the wrapper needs no second edit
# and the two can never disagree.
set -euo pipefail

readonly PROPERTIES="gradle/wrapper/gradle-wrapper.properties"
readonly JAR="gradle/wrapper/gradle-wrapper.jar"

workspace="$(mktemp -d)"
readonly workspace
cleanup() { rm -rf "$workspace"; }
trap cleanup EXIT

# The declared version, read out of the distribution URL the wrapper will use.
declared_version() {
  sed -n 's/^distributionUrl=.*gradle-\([0-9][^-]*\)-bin\.zip$/\1/p' "$PROPERTIES"
}

main() {
  local version expected actual
  version="$(declared_version)"
  : "${version:?could not read a Gradle version from $PROPERTIES}"

  # -f so an HTTP error is a failure rather than an error page hashed as a
  # checksum, and -L because the checksum host redirects.
  curl --fail --silent --show-error --location \
    --retry 3 --retry-delay 2 --retry-connrefused \
    --output "$workspace/expected" \
    "https://services.gradle.org/distributions/gradle-${version}-wrapper.jar.sha256"

  expected="$(tr -d '[:space:]' <"$workspace/expected")"
  actual="$(sha256sum "$JAR" | cut -d' ' -f1)"

  if [[ "$expected" != "$actual" ]]; then
    printf 'gradle wrapper jar does not match the published checksum for %s\n' "$version" >&2
    printf '  expected %s\n  actual   %s\n' "$expected" "$actual" >&2
    return 1
  fi

  printf 'gradle wrapper %s verified (%s)\n' "$version" "$actual"
}

main "$@"
