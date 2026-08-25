# Boreas Android Agent Guide

## Start Here

Read [docs/README.md](docs/README.md). Read the document that owns the boundary
you will change before introducing code or dependencies.

If this host has no userspace Kotlin and Android toolchain, follow
[setup-boreas-android](.agents/skills/setup-boreas-android/SKILL.md). It keeps
the JDK, SDK, Gradle, Kotlin artifacts, caches, and temporary HOME under `/tmp`.

## Non-Negotiable Invariants

- Kotlin and Jetpack Compose are the native UI and Android framework shell.
  They do not parse, filter, route, or forward IP packets.
- `BoreasVpnService` is the only owner of Android VPN consent, interface
  creation, foreground-service compliance, routes, and lifecycle callbacks.
- The Rust engine receives an ordered raw-IP device and owns all L3 through L7
  semantics. Do not create an Android-specific datapath or duplicate policy.
- A `ParcelFileDescriptor` has exactly one owner at every instant, and that
  owner is this app. The core never closes a descriptor it was given: with
  `getFd()` the `ParcelFileDescriptor` keeps ownership and must be closed
  through its own API, and with `detachFd()` the responsibility moves to the
  caller's native code. This app uses `getFd()`, so a double close is not a
  rule to follow but a state it cannot reach. The close happens after the
  device vtable's `release` callback has run, never before: a `recv` already
  inside the callback keeps running after its task is abandoned.
- Every egress socket must be protected by `VpnService.protect(fd)` before it
  connects. A false result is an error, never a fallback that risks a tunnel
  loop.
- Keep service state a closed Kotlin sealed hierarchy. Do not encode lifecycle
  state in nullable-field bags or Boolean flags.
- UI-to-service and Kotlin-to-native control paths are bounded and cancellable.
  Packet bytes never traverse those control paths.
- Android CA installation remains user-store only. Do not request root-only
  system-store, iptables, or APEX modifications.

## Boundary Rules

- The contract is `boreas-core/api/`, and it is sufficient by construction. If
  making progress needs `src/` or `ffi/src/`, stop and report which api/ page
  should have carried it. That is a documentation defect and it is fixed there.
  [docs/core-contract.md](docs/core-contract.md) records how this repository
  maps onto it and holds nothing the contract does not.
- Do not invent exported symbols. The surface is six functions, two vtables,
  and one config struct; nothing else is supported.
- Raw packets flow only through the TUN descriptor and the native engine. The
  UI sees status, counters, and typed errors only.

## Comments

**A comment earns its place only by saying something the code cannot say about
itself.** Code already states what it does. Four things it cannot state, and
they are the only reasons to write one:

1. Why this, and not the obvious alternative a reader would otherwise try.
2. An invariant the types do not enforce.
3. What breaks if you change this: platform behaviour, OEM deviation, lifecycle
   ordering.
4. A reference out: an API level, an AOSP issue, a vendor bug, a path.

Everything else restates the code. Do not write it, and delete it when you find
it: no comment that narrates the line below it, no KDoc that spells out the
signature, no history of what the file used to be. Git holds the history, and a
file that carries its own changelog grows one forever. A comment repeated a few
lines later, once on the `try` and again on the `catch`, is one comment.

On Android the third reason carries most of the value. A note that a vendor
image throws where the documentation says it returns exists nowhere else. Those
are the comments to protect and the ones to write.

**Write plain technical English, not essay prose.** This guide is written in a
register that suits a guide: an aphorism, a contrast, a claim landed on a short
sentence. **Comments do not get that register**, and imitating it here is the
most common way this rule is broken. No "X is the Y of Z", no "the real question
is", no fact trailed by a participial flourish ("..., reflecting the fail-closed
posture"). Name the mechanism and stop. Prefer a colon or a full stop to a dash,
and keep at most one dash in a block.

**Economy, not telegraphese.** Cut "in order to", "it is important to note
that", "has the ability to", stacked hedges, and any sentence announcing the one
after it. Keep articles and whole sentences: a comment is prose a human reads,
and `// Vendor throws. Return Unavailable.` is not an improvement on the
sentence it replaced.

**Never delete, and edit only with the change it describes:**

- Any note recording OEM or vendor deviation from documented behaviour.
- Any note naming an API level, a permission, or a manifest requirement.
- The justification on a `@Suppress`.
- `TODO`, `FIXME`, `HACK`, and anything carrying an issue ID.
- A stated limit that hardware has not yet confirmed. "This needs a dual-stack
  device to confirm" is an open question, not hedging.
- The text inside a KDoc `[Reference]`. It resolves to a declaration, and
  `allWarningsAsErrors` turns a wrong one into a failed build. Deleting the
  whole sentence is fine.

## Change Process

1. Read the owning Android document and the linked core document.
2. State the lifecycle invariant and the cheapest device or unit check that
   could falsify it.
3. Keep framework effects in Kotlin adapters and pure decisions in the core.
4. Add tests with each state transition or ownership boundary.
5. Run the narrowest relevant Gradle check first, then the full gate below
   before merging.

The Gradle project exists; this repository is no longer documentation-only. The
gate is what `.github/workflows/ci.yml` runs, and a comment-only change must
clear it too, because `allWarningsAsErrors` is on for `:domain`, `:app`, and
`build-logic`, and a broken KDoc reference fails the build:

```sh
./gradlew --no-daemon -p build-logic test
./gradlew --no-daemon :domain:test :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
./gradlew --no-daemon :app:lintDebug :domain:lint
git diff --check
```