# Android Platform Integration

## Component Shape

```text
Compose activity
      |
      v
BoreasVpnService -- VpnService.Builder --> ParcelFileDescriptor
      |                                           |
      | protect(unconnected egress fd)            | read and written through
      v                                           v  BoreasDevice, never owned
Android framework <----------------------------- libboreas.so
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

## Descriptor Ownership

The descriptor is an affine resource, and this app owns it from end to end. The
core never closes a descriptor it was given; api/android.md offers `getFd()` and
`detachFd()` and asks only that it be closed exactly once, after `release`.

`getFd()` is the one used, because it makes the rule structural rather than
remembered: the `ParcelFileDescriptor` keeps ownership and closes through its own
API, so there is no raw integer for a second owner to acquire.

1. `BoreasVpnService.establish` validates nothing and receives everything already
   validated: it turns one `PlatformConfig` into one `ParcelFileDescriptor`, or
   into `Establishment.Refused` when `establish()` answers null, which is a
   documented path rather than a theoretical one.
2. `TunDevice` wraps it. `recv` and `send` go through `Os.poll` and `Os.read` /
   `Os.write` on the descriptor the `ParcelFileDescriptor` still owns, with the
   core's own buffer wrapped as a direct `ByteBuffer`, so a packet is not copied
   on the way past.
3. Nothing closes the descriptor to signal anything. `close` sets a flag that the
   next poll pass reads, within one bounded interval.
4. `release` counts a latch down. It can run after `boreas_tunnel_free` has
   returned, if a `recv` was still in flight when the tunnel stopped, so teardown
   waits on the latch rather than on `free`.
5. Only then does `dispose()` close the `ParcelFileDescriptor`, once.

A start the core refuses still runs both `release` callbacks, so the refusal path
closes the descriptor in the same place the success path does.

## Egress Bypass

An upstream socket directed through the new VPN is a loop. Before native code
connects an egress socket, it must synchronously ask the service to execute
`VpnService.protect(fd)` on the unconnected descriptor. `false`, an exception,
or a dead service is a `BypassDenied` error; it never falls back to connecting.

The callback crosses only a file descriptor and a typed success or failure. It
carries neither packet buffers nor policy.

api/android.md says not to write this one, and provides `boreas_android_bypass`.
That entry point takes a `jobject`, which JNA cannot pass, so it is unreachable
from the binding route this app takes; the finding is recorded in
docs/verified-inputs.md and reported upstream. What is written instead is the
same JNA callback mechanism the same page prescribes for the device vtable.

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

## The Contract's Checklist

api/android.md ends with one. What can be checked without hardware has been, and
the rest names what to watch for on the first device.

| Item | Status |
|---|---|
| One `libboreas.so` per shipped ABI at `jniLibs/<abi>/`, `libc++_shared.so` beside it | Met. Verified against the built APK: three ABIs, no `armeabi-v7a`, both libraries in each. |
| Built with NDK r28+, or with the 16 KB linker flags | Met by the artifact. Every LOAD segment on both 64-bit ABIs is `0x4000` aligned. |
| `minSdk >= 23`, `useLegacyPackaging` unset | Met. `minSdk` is 29 and the DSL option is absent, so `.so` files stay uncompressed and page aligned. |
| `Builder.setMtu(n)` and `BoreasConfig.mtu = n`, the same `n` | Met by construction. One field is read by both call sites. |
| `establish()` null-checked | Met. It is a variant, not an exception. |
| Every JNA callback object held in a long-lived field | Met. Fields of `TunDevice` and `VpnBypass`, both held by the tunnel. |
| `recv` returns `0` on timeout, never relies on `close(fd)` | Met. `poll(2)` with a 100 ms bound; `close` sets a flag. |
| `send` errors on a short write | Met. |
| Bypass built with `boreas_android_bypass` | **Not met, and cannot be.** See above. |
| Events read on a thread of their own | Met. One dedicated daemon thread, one collector. |
| Teardown is shutdown, join, free, then the fd | Met in code. Not yet observed. |
| CA material in the Keystore, certificate offered to the installer | Met. The keys are sealed under a Keystore key; the certificate goes to Downloads because the one-tap intent no longer installs CA certificates. |

## Device Test Matrix

Nothing below has been run. There is no device on the machine this was built on.

| Scenario | Required observation |
|---|---|
| The library loads at all | `Native.load` succeeds. It will not until the archive ships `libc++_shared.so`; see docs/verified-inputs.md. |
| Startup ABI check | a mismatched library is refused with a sentence, not a crash |
| First consent | no native start before consent succeeds |
| Real traffic | a page loads through the tunnel |
| A blocked name | `RESOLVED` arrives with `blocked`, and nothing left the device |
| Reload | rules change with no connection dropped |
| Start then immediate Stop | one descriptor close, `release` before it, no leaked context |
| Protected DNS and egress sockets | the resolver answers, which it cannot if `protect` was skipped |
| MTU agreement | `paths_reported` falls to near zero once senders converge |
| Wi-Fi to cellular change | typed transition with no duplicate service session |
| Process recreation | service and UI recover from persisted configuration |
| IPv6 on a dual-stack network | routed into the tunnel rather than leaving beside it |