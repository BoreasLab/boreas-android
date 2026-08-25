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

## Change Process

1. Read the owning Android document and the linked core document.
2. State the lifecycle invariant and the cheapest device or unit check that
   could falsify it.
3. Keep framework effects in Kotlin adapters and pure decisions in the core.
4. Add tests with each state transition or ownership boundary.
5. Run the narrowest relevant Gradle checks after the project exists, then run
   the documented full Android gate before merging.

For the present documentation-only repository, run `git diff --check` after
edits. Do not add a Gradle project merely to make an empty check pass.