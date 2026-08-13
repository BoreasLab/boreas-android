# Boreas Android Agent Guide

## Start Here

Read [docs/README.md](docs/README.md). Read the document that owns the boundary
you will change before introducing code or dependencies.

## Non-Negotiable Invariants

- Kotlin and Jetpack Compose are the native UI and Android framework shell.
  They do not parse, filter, route, or forward IP packets.
- `BoreasVpnService` is the only owner of Android VPN consent, interface
  creation, foreground-service compliance, routes, and lifecycle callbacks.
- The Rust engine receives an ordered raw-IP device and owns all L3 through L7
  semantics. Do not create an Android-specific datapath or duplicate policy.
- A `ParcelFileDescriptor` has exactly one owner at every instant. Once Kotlin
  calls `detachFd()`, native Rust owns and closes the returned descriptor exactly
  once. Kotlin must neither use nor close it afterwards.
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

- The handoff contract in [docs/core-contract.md](docs/core-contract.md) is a
  logical interface, not an implemented ABI. Do not invent exported symbols in
  the app layer; add them with the matching core change and tests.
- UniFFI is a supported Kotlin binding route for value/control types. It does
  not erase file-descriptor ownership. The descriptor transfer needs one
  explicit, reviewed native boundary as specified in
  [docs/platform-integration.md](docs/platform-integration.md).
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