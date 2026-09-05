#!/usr/bin/env bash
#
# Asserts the device suite ran something.
#
# A test APK is minified like any other, and R8 has no reason to keep a class
# nothing references: the runner finds tests by reflection. When it took them
# all, the task reported success having run nothing, and the cell was green and
# empty for four pushes. A count is the only thing that tells those apart.
#
# Reads the UTP result files under the given directory. Assumes bash 4+ and GNU
# grep. Exits nonzero when no file reports a test.
set -euo pipefail

readonly RESULTS="${1:?usage: ${0##*/} <androidTest-results dir>}"

[[ -d "$RESULTS" ]] || {
  printf 'no results under %s: the suite wrote nothing\n' "$RESULTS" >&2
  exit 1
}

# One grep over the whole set. `tests="0"` is the empty run; anything else is a
# count, and the suite is the union of every device that reported.
if grep -rqE '<testsuites[^>]* tests="[1-9]' "$RESULTS" --include='TEST-*.xml'; then
  printf 'the device suite reported tests\n'
  exit 0
fi

printf 'every result file under %s reports zero tests\n' "$RESULTS" >&2
grep -rhoE '<testsuites[^>]*>' "$RESULTS" --include='TEST-*.xml' >&2 || true
exit 1
