# Shared Core Contract

## Status

This is the implementation contract between this repository and `boreas-core`.
It is not yet an exported ABI and does not authorize adding placeholder FFI
symbols. The first native bridge must implement this contract in lockstep with a
versioned core interface and tests on both sides.

## Boundary

```text
Compose UI -> Kotlin service/control shell -> native bridge -> boreas-core
                                      |                         |
                                      +-- Android VPN API        +-- raw IP
```

The boundary transports an ordered sequence of raw IPv4 or IPv6 packets. The
platform creates and owns Android resources; the core consumes the device and
owns packet and network semantics.

| Concern | Android shell owns | Rust core owns |
|---|---|---|
| User interaction | consent, settings, Compose state, notification actions | no UI state |
| VPN device | `VpnService`, TUN creation, addresses, routes, OS lifecycle | reads and writes the admitted device |
| Packet processing | descriptor transfer only | parsing, reassembly, MTU, ECN, ICMP, TCP, UDP, policy and egress |
| Upstream bypass | call `VpnService.protect(fd)` before connect | request the bypass and fail closed when it is denied |
| Observability | present typed status and counters | produce typed status, counters, errors and logs at effect boundaries |

Neither side may introduce an Android-only parser, filter, route decision, or
packet queue. Packet work remains in the core's $O(p)$ per-packet path for a
packet of $p$ bytes; the Android shell must not add an avoidable copy,
classification pass, or unbounded queue.

## Logical Interface v1

These are semantic operations. Names, serialization, and exported function
signatures are deliberately deferred to the core FFI crate.

| Operation | Input | Success result | Failure rule |
|---|---|---|---|
| `start` | validated engine configuration, platform configuration, one transferred device | running session identity and initial status | leaves no live TUN or core task |
| `stop` | session identity and a typed reason | terminal stopped status | idempotent after the first accepted stop |
| `configuration_changed` | validated replacement or explicit restart requirement | applied status or restart-required status | no partial silent application |
| `network_changed` | typed Android network capability event | replan or typed degradation status | preserves cancellation and existing flow invariants |
| `status_snapshot` | none | immutable status and bounded counters | never returns packet payloads |
| `protect_socket` | an unconnected socket descriptor | success or typed denial | native code must not connect on denial |

`EngineConfig` owns policy and egress choices. `PlatformConfig` owns Android
addressing, routes, DNS servers, MTU, foreground-notification data, and
per-app/always-on choices. Each configuration is parsed once at its untrusted
entry and becomes an immutable trusted value before the service starts.

## Resource and Cancellation Law

Startup is linear:

```text
validate -> obtain consent -> establish TUN -> transfer ownership -> start core
```

Shutdown is the reverse:

```text
cancel control work -> stop core -> await native completion -> release Android resources
```

At most one start or stop transition may own the session at a time. A cancelled
start closes every resource it acquired. The native core must finish or be
joined before Android replaces the device, so no task can retain a descriptor
from an obsolete VPN session.

## Binding Choice

Use UniFFI for Kotlin value and control models only when the matching core API
is ready. A descriptor transfer is not an ordinary value: it requires one
explicit native call that documents the ownership move. Generated bindings do
not change that law.

The Android bridge must expose no packet callback to Kotlin. It may expose
typed status/events and a synchronous, cancellation-aware `protect_socket`
callback because Android alone can discharge that OS obligation.