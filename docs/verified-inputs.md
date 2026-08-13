# Verified Inputs

Checked on 2026-08-12. These sources justify the platform choices; they do not
replace device testing for Boreas-specific behavior.

| Input | Evidence | Implementation consequence |
|---|---|---|
| Compose is Android's recommended modern native UI toolkit | [Android Developers](https://developer.android.com/develop/ui/compose): "Jetpack Compose is Android's recommended modern toolkit for building native UI." | Use Kotlin and Compose for the control surface. |
| Android VPN services inherit from `VpnService` | [Android Developers](https://developer.android.com/develop/connectivity/vpn): "create an Android service inheriting from VpnService." | `BoreasVpnService` owns the platform VPN boundary. |
| Android can protect a socket from the VPN | [`VpnService.protect`](https://developer.android.com/reference/android/net/VpnService#protect(int)): "Protect a socket from VPN connections." | Every native upstream socket needs the service callback before connect. |
| `detachFd()` transfers close responsibility to native code | [`ParcelFileDescriptor.detachFd`](https://developer.android.com/reference/android/os/ParcelFileDescriptor#detachFd()): "You are now responsible for closing the fd in native." | Use a one-shot Kotlin-to-Rust ownership move. |
| UniFFI includes Kotlin support | [Mozilla UniFFI](https://github.com/mozilla/uniffi-rs/blob/main/README.md): "UniFFI comes with support for Kotlin..." | It may generate Kotlin control/value bindings; it does not define descriptor ownership. |

## Explicitly Unverified Until Device Work

- Vendor and Android-version behavior of user-store CA trust and WebView.
- Exact foreground-service and background-restriction behavior for the selected
  target SDK.
- Socket bypass behavior for the actual native runtime and egress transports.
- Device battery, wakeup, memory, throughput and network-transition behavior.

Record each result with device, Android version, app build, test procedure, and
observed outcome. Do not turn a passing emulator test into a product claim.