# Android Platform Integration

## Component Shape

```text
Compose activity
      |
      v
BoreasVpnService -- VpnService.Builder --> ParcelFileDescriptor
      |                                           |
      | protect(unconnected egress fd)            | detachFd(): handed over,
      v                                           v  owned by the core
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

The descriptor is an affine resource with one owner at a time. This app holds
it from `establish()` to `boreas_tunnel_start_fd`, then the core holds it until
`free` closes it — on a refused start too. `detachFd()` is what makes the
handover structural: after it the `ParcelFileDescriptor` is inert, so there is
no second owner to close it twice.

1. `BoreasVpnService.establish` validates nothing and receives everything already
   validated: it turns one `PlatformConfig` into one `ParcelFileDescriptor`, or
   into `Establishment.Refused` when `establish()` answers null, which is a
   documented path rather than a theoretical one.
2. `NativeTunnel.start` detaches the number and passes it with the MTU. No
   device callbacks exist: the core reads and writes the descriptor on its own
   reactor, one syscall per packet, and nothing crosses JNA on the packet path.
3. Nothing here closes the descriptor, to signal anything or otherwise.
4. `boreas_tunnel_free` closes it, after the core's reactor has stopped reading.

A start the core refuses still closes the descriptor and still runs the bypass
`release`, so the refusal path leaves nothing for this app to clean up.

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
| Every JNA callback object held in a long-lived field | Met. `VpnBypass`, held by the tunnel; the device has no callbacks. |
| `recv` returns `0` on timeout, never relies on `close(fd)` | Not this app's: `boreas_tunnel_start_fd` owns the read. |
| `send` errors on a short write | Not this app's, for the same reason. |
| Bypass built with `boreas_android_bypass` | **Not met, and cannot be.** See above. |
| Events read on a thread of their own | Met. One dedicated daemon thread, one collector. |
| Teardown is shutdown, join, free, then the fd | Met in code. Not yet observed. |
| CA material in the Keystore, certificate offered to the installer | Met. The keys are sealed under a Keystore key; the certificate goes to Downloads because the one-tap intent no longer installs CA certificates. |

## Device Test Matrix

Split by venue. An emulator answers every claim about this app's own boundaries:
the shared object, consent, the descriptor. It answers none about a second
transport or a real path MTU, having neither.

`app/src/androidTest` holds the emulator half and `.github/workflows/ci.yml` runs
it on every push, on API 29 debug and on API 36 release. The devices are declared
in `app/build.gradle.kts` under `testOptions`, so one Gradle task reproduces a CI
result and no third-party action owns the emulator.

| Scenario | Venue | Answered by |
|---|---|---|
| The library loads at all | emulator | `CoreLinkTest`. The only way: an Android `.so` links bionic and will not open on a host JVM at any price. |
| Startup ABI check | emulator | `CoreLinkTest`, the matching side. Refusing a mismatch needs a build with a wrong `abiVersion`, which no cell produces yet. |
| First consent | emulator | `ConsentTest`, both answers, and that a withheld one establishes nothing |
| Start then immediate Stop | emulator | `TunnelLifecycleTest`, two cycles, asserting no `/dev/tun` descriptor outlives a stop. In `androidTestRelease`; see below. |
| Real traffic | emulator | Not yet. Needs an upstream on the runner, reached from the guest at `10.0.2.2`. |
| A blocked name | emulator | Not yet. Same upstream, asserting nothing arrived. |
| Reload | emulator | Not yet. |
| Protected DNS and egress sockets | emulator | Not yet. |
| Process recreation | emulator | Not yet. |
| MTU agreement | hardware | Every emulator path is 1500, so `paths_reported` falling to zero would prove nothing. |
| Wi-Fi to cellular change | hardware | One virtual NIC. Disabling it is an outage, not a handover. |

Two more, from docs/verified-inputs.md, are hardware's for the same reason: an
OEM's Settings accepting a `.crt` written to Downloads, and lockdown's
interaction with the per-app exclusion list.

## A Debug Build Does Not Reach Running

Found by the device lane on 2026-09-05, and unexplained. `TunnelLifecycleTest`
asserts where the session stops today rather than where it should, so the run
stays honest and the harness stays alive; it fails the day the finding is fixed,
which is the day to restore the real assertion.

A session reaches `Starting` and stops there. The interface is up by then:
logcat carries `Vpn: Established by org.joefang.boreas.android on tun0`, and the
address the draft names is on `tun0`. Nothing further is logged, no thread of
ours is blocked, and no exception arrives, so the start coroutine is suspended
rather than stuck in a call. The state then reaches `Stopped` by way of the
cancellation path, which means the service was destroyed while starting.

A descriptor outlives the stop on API 29 and not on API 36, on the same build.
A stop that follows a start which never finished is not the teardown the
contract describes, so this is read as part of the finding rather than as a leak
of its own, and `TunnelLifecycleTest` does not assert it. The assertion returns
with the rest when the session reaches Running.

Both API levels fail to reach Running alike, so the level is not that variable. The one branch a
release build does not execute is `BoreasVpnService.selectEngine`, where
`SIMULATION_AVAILABLE` short-circuits before the simulation setting is read; a
release build never performs that DataStore read. That is the suspect, and it is
untested, because the release build cannot be instrumented here either.

## Instrumenting the Release Build Was Abandoned

Recorded so it is not re-derived. The intent was to run the same suite against
the shipped, shrunk artefact, signed by a key made in the job so that the job
would hold no secret and would run on a fork's pull request. The signing half
worked: the key was generated, the release APK built and installed, and the
emulator booted.

The suite never ran. `am instrument` starts, discovers no test, and exits
reporting success, with nothing in the Gradle log at `--info` to say why. Ruled
out along the way, each by a run of its own:

- R8 deleting the test classes. They are present: `unzip -p` of the release test
  APK counts them in the dex.
- R8 renaming the app out from under them. `-dontobfuscate` belongs in
  `proguardFiles` and not `testProguardFiles`, which R8 reads only for the test
  APK. Moving it changed nothing.
- R8 shrinking the test APK, and R8 discarding the annotations the runner looks
  for. `-dontshrink` and `-keepattributes` changed nothing.

Each of those workarounds also removes some of what the cell was for: a run
against an unobfuscated, unshrunk test APK proves less about the shipped
artefact than it appears to. That, and the empty runs, is why the cells are
gone. `device-ran.sh` stays: it is what turned four green and empty runs into a
failure.
