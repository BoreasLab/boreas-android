#!/usr/bin/env bash
#
# Asserts the design properties that are cheaper to check as text than to encode
# in the type system.
#
# The contrast floor is NOT here: it is a law over the palette and lives in
# domain/src/test as ContrastLawTest, where it runs as an ordinary JVM test. What
# remains are properties about the shape of the source itself, which no test
# inside the program can see.
#
# Failures accumulate; the script reports every violation in one run rather than
# stopping at the first, because a caller fixing three of these should not have to
# run it three times.
#
# Every check feeds `report` through process substitution rather than a pipe: a
# pipeline stage runs in a subshell, so a piped `report` would increment its
# failure count in a child and the script would exit 0 while printing FAIL.
#
# Assumes bash 4+, GNU grep, and GNU find. Reads the working tree, writes a report
# to stdout and diagnostics to stderr, and exits nonzero if any property fails.

set -euo pipefail

readonly ROOT="${1:-.}"
cd "$ROOT"

readonly STRINGS="app/src/main/res/values/strings.xml"
readonly DIMENS="app/src/main/java/dev/boreaslab/boreas/design/Dimens.kt"
readonly TYPE="app/src/main/java/dev/boreaslab/boreas/design/Type.kt"
readonly COLOR="app/src/main/java/dev/boreaslab/boreas/design/Color.kt"
readonly ICONS="app/src/main/java/dev/boreaslab/boreas/design/Icons.kt"

failures=0

# report <name> <expectation>; offending lines arrive on stdin, empty means pass.
report() {
  local name="$1" expectation="$2" offenders
  offenders="$(cat)"
  if [[ -z "$offenders" ]]; then
    printf '  pass  %s\n' "$name"
  else
    failures=$((failures + 1))
    printf '  FAIL  %s\n        expected: %s\n' "$name" "$expectation"
    printf '%s\n' "$offenders" | sed 's/^/          /'
  fi
}

# Kotlin sources as one NUL-delimited stream, so a path with a space cannot split.
kotlin_sources() { find app/src domain/src -name '*.kt' -print0; }

# grep across the source set, tolerating "no match" (exit 1) as an empty result.
scan() { kotlin_sources | xargs -0 --no-run-if-empty grep "$@" -- || true; }

printf 'Design gate\n'

# Punctuation. The em-dash is the highest-signal marker of generated copy, and the
# en-dash used as a separator is the same tell wearing a smaller hat.
report 'no em-dash in Kotlin' 'U+2014 never appears' \
  < <(scan -nP '\x{2014}')
report 'no em-dash in strings' 'U+2014 never appears' \
  < <(grep -nP '\x{2014}' "$STRINGS" || true)
report 'no en-dash separator in strings' 'U+2013 never appears' \
  < <(grep -nP '\x{2013}' "$STRINGS" || true)

# One source of truth per scale. A literal outside its token file is a token that
# was never declared, which is how a second radius scale gets born.
report 'colors come from the palette' "no Color(0x…) outside Color.kt" \
  < <(scan -n 'Color(0x' | grep -v "$COLOR" || true)
report 'dimensions come from the scale' 'no bare .dp outside Dimens.kt' \
  < <(scan -nE '[0-9]+\.dp' | grep -vE "$DIMENS|$ICONS|tonalElevation" || true)
report 'type sizes come from the scale' 'no bare .sp outside Type.kt' \
  < <(scan -nE '[0-9]+\.sp' | grep -v "$TYPE" || true)

# One icon family at one weight: every glyph comes through the single factory.
report 'one icon family' 'ImageVector.Builder only in Icons.kt' \
  < <(scan -n 'ImageVector.Builder' | grep -v "$ICONS" || true)

# A catch-all over a sealed hierarchy turns "somebody must handle the new variant"
# into a blank region at runtime.
report 'closed sets have no catch-all' 'no else -> over a sealed hierarchy' \
  < <(scan -nE -A30 'when \((val )?[a-zA-Z_.]+\) \{' |
    grep -E 'VpnLifecycleState|TypedFailure|ContainerState|TunnelParse|AlwaysOn|ServiceRequest|ForegroundIntent|FieldProblem' -A20 |
    grep -E '^[^ ]+[-:][0-9]+[-:][[:space:]]*else ->' || true)

# Observable state is one cell with one writer, and both halves of that are now
# carried by the declaration: a property with an explicit backing field, exposed at
# a read-only type.
#
# What the declaration cannot stop is a reader casting the exposed value back to
# the mutable type it is at runtime. Under the previous shape an asStateFlow()
# wrapper made that cast fail; the check below replaces that guarantee, and does it
# across the whole tree rather than one cell at a time.
report 'read-only state stays read-only' 'no cast to a Mutable*Flow' \
  < <(scan -nE '\bas\??[[:space:]]+Mutable(State|Shared)Flow' || true)

# A private mutable property shadowed by a public read-only one is the shape the
# explicit backing field replaced. Two names for one cell is how a second writer
# gets added without anyone noticing the rule was broken.
report 'one name per state cell' 'no _-prefixed shadow property' \
  < <(scan -nE '\bval _[a-zA-Z]' || true)

# Every user-visible string is a resource, so it is reviewed and translated in one
# place. A quoted sentence inside a composable is copy nobody will find again.
report 'copy lives in strings.xml' 'no literal sentence passed to Text()' \
  < <(scan -nE 'Text\([[:space:]]*"[A-Z]' || true)

# A declared string nothing references is either dead copy or a screen that was
# never finished. Manifest and other resources count as references.
#
# The match is anchored, not a substring. `grep -F "R.string.policy_profile"` is
# satisfied by `R.string.policy_profile_off`, so every name that happens to be a
# prefix of another looked used and two dead strings survived this check until
# lint's UnusedResources found them. `\b` after the name is enough because a
# resource name is word characters throughout: it cannot match inside a longer
# name, since the character that would follow is `_` or a letter.
unreferenced() {
  local kind name
  while IFS=' ' read -r kind name; do
    grep -rqE "R\.$kind\.$name\b" app/src --include='*.kt' && continue
    grep -rqE "@$kind/$name\b" app/src --include='*.xml' && continue
    printf '%s: %s/%s\n' "$STRINGS" "$kind" "$name"
  done < <(grep -oE '<(string|plurals) name="[^"]+"' "$STRINGS" |
    sed -E 's/^<([a-z]+) name="([^"]+)"$/\1 \2/')
}
report 'no unreferenced strings' 'every declared string is used' < <(unreferenced)

printf '\n'
if ((failures > 0)); then
  printf '%d propert%s failed\n' "$failures" "$( ((failures == 1)) && echo y || echo ies)" >&2
  exit 1
fi
printf 'all properties hold\n'
