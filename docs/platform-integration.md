# Android Platform Integration

## Component Shape

```text
Compose activity
      |
      v
BoreasVpnService -- VpnService.Builder --> Android TUN descriptor
      |                                           |
      | protect(unconnected egress fd)            | detached ownership transfer
      v                                           v
Android framework <-------------------------- native boreas-core host
```

`BoreasVpnService` is a thin effect interpreter. It owns permission, VPN
interface construction, foreground-service requirements, network callbacks,
and resource disposal. The native host owns the already-created device and the
core runtime. The UI only sends typed commands and renders immutable state.

## Service State

The Kotlin model must use a sealed state hierarchy equivalent to:

```text
Stopped
AwaitingConsent
Starting
Running(session, status)
Stopping(session)
Failed(operation, recoverable)
```

Only `Starting` can establish a TUN and only `Running` can request a controlled
configuration change. Repeated Start and Stop commands coalesce at the service
owner; they do not spawn independent coroutines. Cancellation remains inside
the service scope and is never caught as an ordinary failure.

## Descriptor Handoff

The descriptor is an affine resource, not an `Int` value that two runtimes may
retain.

1. `BoreasVpnService` validates `PlatformConfig` and establishes the TUN with
   `VpnService.Builder`.
2. Kotlin calls `ParcelFileDescriptor.detachFd()` exactly once.
3. The native `start` bridge receives that descriptor and constructs one Rust
   `OwnedFd`; it then creates `AndroidTun::from_owned_fd(OwnedFd, Mtu)`.
4. Kotlin immediately forgets the raw descriptor. It must not call `close`,
   wrap it again, or issue packet I/O.
5. Native shutdown drops the `OwnedFd` exactly once, after all core work using
   the device has stopped.

If native startup fails after accepting the descriptor, native code closes it
before returning the typed error. If `detachFd()` fails, Kotlin closes the
still-owned `ParcelFileDescriptor` and no native call occurs. These two paths
are mandatory ownership tests.

## Egress Bypass

An upstream socket directed through the new VPN is a loop. Before native code
connects an egress socket, it must synchronously ask the service to execute
`VpnService.protect(fd)` on the unconnected descriptor. `false`, an exception,
or a dead service is a `BypassDenied` error; it never falls back to connecting.

The callback crosses only a file descriptor and a typed success/failure result.
It carries neither packet buffers nor policy. The Rust implementation of the
core `TunnelBypass` seam must use this callback for Android, not the desktop
`DirectSockets` implementation.

## Android Responsibilities

Kotlin must implement these effect-boundary concerns:

- VPN consent and service declaration.
- Foreground-service notification lifecycle and Android version requirements.
- TUN address, route, DNS, MTU and allowed/disallowed-app configuration.
- Network capability changes, restart policy, and user-visible recoverable
  errors.
- User-store CA installation and removal UX only.
- Service-to-UI state delivery with a bounded latest-state stream.

It must not implement DNS filtering, packet parsing, TCP state, HTTP logic,
egress selection, or trust-policy exceptions.

## Device Test Matrix

The first device gate covers, at minimum:

| Scenario | Required observation |
|---|---|
| First consent | no native start before consent succeeds |
| Start then immediate Stop | one descriptor close and no leaked child work |
| Native startup failure | descriptor closes exactly once and UI reaches `Failed` |
| Protected DNS/egress socket | traffic bypasses the VPN and no loop occurs |
| Wi-Fi to cellular change | typed transition with no duplicate service session |
| Process recreation | service and UI recover from persisted configuration without retaining a stale descriptor |
| Core packet fixture | byte-for-byte result matches the same fixture in `boreas-core` |