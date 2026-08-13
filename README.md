# Boreas Android

Boreas Android is the native Android control surface and VPN lifecycle owner
for the shared Rust engine in `boreas-core`.

The product boundary is raw IP packets. Kotlin owns Android APIs, consent, and
the `VpnService` lifecycle. Rust owns packet semantics, routing and filtering
policy, transport state, and egress. Compose configures and observes the
service; it never handles packets.

## What is here

Phase A1 of [the implementation plan](docs/implementation-plan.md): the Compose
control surface and a `BoreasVpnService` skeleton with the sealed lifecycle
model, consent, serialized start and stop, and foreground-notification
ownership. No packet processing.

The engine is not linked. `EngineHost` is an app-layer seam, not an FFI, and it
declares no exported symbol; the real bridge arrives in A2 with the matching
core change and tests on both sides. Because there is no native owner for a
descriptor, nothing here calls `establish()` or `detachFd()`.

That makes "the packet engine is not in this build" the honest resting state of
a release build, and the surface says so with a typed reason rather than
failing quietly. Debug builds carry a simulated session, off by default, which
runs the lifecycle and labels every number it generates.

| Module | Contains |
|---|---|
| `:domain` | Model, engine seam, lifecycle state machine. No Android type; the module boundary enforces it. |
| `:app` | Compose, the design system, `BoreasVpnService`, and the other Android adapters. |

## Building

Requires the Android SDK with platform 37 and JDK 17 or later.

```
./gradlew :domain:test          # the A1 lifecycle gate
./gradlew :app:assembleDebug
```

## Documentation

Start with [the documentation index](docs/README.md), then read
[AGENTS.md](AGENTS.md) before changing the repository.
