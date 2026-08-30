# Shared Core Contract

## Status

**The contract is `boreas-core/api/`.** It is self-contained and normative: six
functions, two vtables, one configuration struct, and the semantics that page
set describes. `ffi/include/boreas.h` is the same content in a form a compiler
reads, and it ships in every release archive beside the binaries.

This document holds only what belongs to *this* repository: where each
obligation is discharged in the Kotlin tree, and which of the contract's traps
are closed by construction here rather than by review. It states no fact about
the core that the contract does not.

If something needed to make progress is not in `api/`, that is a defect in
`api/` and it is reported there. Reading `boreas-core/src/` or `ffi/src/` to
work it out is not the fallback.

## Boundary

```text
Compose UI -> BoreasVpnService -> NativeEngineHost -> libboreas.so
                    |                    |
                    +-- Android VPN API  +-- raw IP, over two vtables
```

| Concern | This repository owns | The core owns |
|---|---|---|
| User interaction | consent, settings, Compose state, notification actions | nothing |
| VPN device | `VpnService`, TUN creation, addresses, routes, OS lifecycle | reads and writes it through `BoreasDevice` |
| Packet processing | one descriptor, handed over once | parsing, reassembly, MTU, ICMP, TCP, UDP, policy, egress |
| Socket bypass | `VpnService.protect(fd)` through `BoreasBypass` | asks, and fails the dial when refused |
| Observability | folds the event stream into state a screen can show | the event stream, which is the whole diagnostic surface |
| Persistence | the certificate authority's material, and nothing else | opens no file, reads no environment variable |

## Where each obligation lives

| Obligation | Discharged in |
|---|---|
| One `libboreas.so` per shipped ABI at `jniLibs/<abi>/`, `libc++_shared.so` beside it | `app/build.gradle.kts`, `FetchBoreasCore` |
| The ABI comparison at startup, before anything else | `core/BoreasLibrary.kt`, `BoreasCore.load` |
| `Builder.setMtu(n)` and `BoreasConfig.mtu` the same `n` | `PlatformConfig.mtu`, read by `BoreasVpnService.establish` and `CoreConfig` |
| `establish()` null-checked | `Establishment.Refused` |
| Every callback object held in a long-lived field | `core/TunDevice.kt`, `core/VpnBypass.kt` |
| `recv` returns `0` on timeout rather than blocking | `TunDevice.recv`, `poll(2)` with a bounded interval |
| Never `close(fd)` to unblock a read | `TunDevice.close` sets a flag; nothing closes to signal |
| `send` errors on a short write | `TunDevice.send` |
| Events read on a thread of their own | `NativeTunnel.reader` |
| shutdown, join, free, then close the descriptor | `NativeTunnel.shutdown`, `TunDevice.awaitRelease` |
| CA material kept, certificate offered to the installer | `data/KeystoreAuthorityStore.kt`, `data/CertificateExport.kt` |

## What is unrepresentable rather than checked

The core refuses several configurations that would run and filter nothing.
Those are the dangerous ones, because such a tunnel reports itself healthy. The
domain types make most of them unwritable, so nothing in this repository has to
check for them:

| The core refuses | Here it is |
|---|---|
| filtering with no resolver | `Filtering.Names` carries the resolver |
| interception with an empty host list | `Interception` requires a non-empty list |
| document rewriting without interception | a field of `Interception` |
| exactly one of certificate / keys | one `CaMaterial` holding both |
| an intercepted host that is not a hostname | `Hostname.parse` |
| a resolver that is not `host:port` numeric | `Endpoint.parse` |
| an MTU below 1280 | `Mtu.parse` |

## The two silent mistakes

Both produce a tunnel that starts, reports itself healthy, and does the wrong
thing. Neither raises an error.

**Different MTUs on the two sides.** One field, `PlatformConfig.mtu`, is read by
both the `VpnService.Builder` call and the config marshaller. They cannot be
given different answers. The symptom if they ever were is a sustained
`paths_reported`, which the activity screen surfaces without the reader needing
to know what it means.

**An unprotected socket.** `BoreasBypass.protect` is never allowed to return
success without having protected something: a refusal from
`VpnService.protect` is returned as a refusal, and the core fails the dial
rather than using the socket.

## Binding route

JNA, not a JNI shim. Kotlin cannot produce a C function pointer, so the two
vtables need trampolines that something else builds, and the alternative needs
the NDK.

One consequence is recorded in `core/VpnBypass.kt` and reported upstream:
`boreas_android_bypass` cannot be called from JNA, because it takes a `jobject`
and JNA's argument marshaller has no case that passes one. The bypass vtable is
therefore filled in here, by the same mechanism the contract prescribes for the
device vtable.
