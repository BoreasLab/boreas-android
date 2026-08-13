# Android Implementation Plan

This plan starts after the documentation handoff. Each phase has one outcome
and a gate; do not merge a later phase on the assumption that an earlier
platform boundary will work on a device.

## A0: Pin Build and Product Inputs

Choose and record the Android Gradle Plugin, Kotlin, Compose compiler, minimum
SDK, target SDK, ABI list, application id, signing model, notification policy,
and foreground-service declarations. Add no native dependency before its
license, maintenance, release and transitive graph are reviewed.

**Gate:** a reproducible debug build on a supported emulator or device, with
the selected values recorded in this repository.

## A1: Kotlin Control Shell

Create the Compose settings/status surface and a `BoreasVpnService` skeleton
with the sealed lifecycle model. Implement consent, serialized start/stop, and
foreground-notification ownership, but no packet processing.

**Gate:** unit tests cover every lifecycle variant, command coalescing,
cancellation, and all failure-to-UI transitions.

## A2: Native Boundary

Add the matching `boreas-core` FFI crate and the Android native library. Define
versioned config/status values and the one-shot descriptor-transfer entry point.
Use UniFFI only for Kotlin control values if it fits the final core interface;
the descriptor handoff remains explicit and tested.

**Gate:** integration tests prove one detach, one native `OwnedFd`, and one
close for successful start, native-start failure, and cancellation.

## A3: TUN and Bypass Glue

Build `VpnService.Builder` configuration from trusted `PlatformConfig`, pass the
descriptor to `AndroidTun`, and implement the protected-socket callback for the
core `TunnelBypass` seam. Route and DNS behavior belongs here only as Android
device setup, never as filtering policy.

**Gate:** real-device loopback ping and DNS fixture match `boreas-core`'s
simulator trace; an upstream socket is demonstrably outside the VPN.

## A4: Lifecycle Hardening

Handle network changes, always-on/lockdown interaction where supported,
notification actions, process death, configuration updates, CA UX, and
structured shutdown. Bound every command/event queue and report drops or
coalescing explicitly.

**Gate:** repeated start/stop and network-change soak has no descriptor leak,
no duplicate foreground service, and no packet loss beyond the core's declared
policy.

## A5: Release Evidence

Run the Android test matrix across the agreed API and vendor set. Measure
battery, wakeups, memory, startup, reconnection, and throughput under actual
traffic; do not extrapolate from the host simulator. Record unresolved device
variation in the core verification ledger.

**Gate:** acceptance evidence is attached to the release candidate and all
platform-specific failures have a typed, user-visible outcome.

## Definition of Done

The Android shell is complete only when it has one service-owned session, no
platform policy fork, deterministic descriptor ownership, protected upstream
sockets, byte-equivalent core fixture results, and device evidence for the
acceptance matrix.