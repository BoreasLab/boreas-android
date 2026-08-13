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

## Always-On VPN

Checked on 2026-08-13 against the installed SDK platform (`android-37.0`)
rather than prose alone: constant values come from `android.jar`, and every
API level below is the `since` attribute recorded in the platform's own
`data/api-versions.xml`.

| Input | Evidence | Implementation consequence |
|---|---|---|
| `VpnService.isAlwaysOn()` and `isLockdownEnabled()` exist from API 29 | `api-versions.xml`: `since="29"` for both | `minSdk` is 29, so always-on state is readable with no version branch and no unknown case to model. |
| The meta-data key is `android.net.VpnService.SUPPORTS_ALWAYS_ON` | `android.jar` constant, `SERVICE_META_DATA_SUPPORTS_ALWAYS_ON`, present from API 27 | Declared explicitly in the manifest. |
| The field "defaults to true if absent. It will only have effect on O_MR1 or higher" | [AOSP `VpnService.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/net/VpnService.java) javadoc | The declaration records existing behavior rather than changing it. |
| Always-on mode means "the system ensures that the service is always running by restarting it when necessary" | AOSP `VpnService.java` javadoc for `isAlwaysOn()` | `onStartCommand` returns `START_STICKY` under always-on and `START_NOT_STICKY` otherwise, and `Dismiss` does not `stopSelf()` while always-on is on. |
| Lockdown means the system "ensures that the service is always running and that the apps aren't allowed to bypass the VPN" | AOSP `VpnService.java` javadoc for `isLockdownEnabled()` | The Apps screen states that an excluded app gets no network at all while the tunnel is down. |
| "The Android system starts a VPN in the background by calling `startService()`" | [Android Developers, VPN guide](https://developer.android.com/develop/connectivity/vpn) | The service receives an Intent carrying none of our actions, and a sticky restart redelivers `null`. Both are parsed as a start request, but only while `isAlwaysOn` is true. |
| Only a device or profile owner can set always-on programmatically | `DevicePolicyManager.setAlwaysOnVpnPackage`, `api-versions.xml` `since="24"` | The app reports state and deep-links to `Settings.ACTION_VPN_SETTINGS` (`since="24"`); it never offers a control it cannot honor. |
| From API 30 an app sees only packages it declares an interest in | [Android Developers, package visibility](https://developer.android.com/training/package-visibility) | A narrow `<queries>` element for launcher activities, not `QUERY_ALL_PACKAGES`. |

## Foreground Service Type

Checked on 2026-08-13. This was previously listed as unverified, and the cost of
leaving it so was a build failure: `:app:lintDebug` reports `ForegroundServiceType`
as an error, not a warning, because at `targetSdk` 34 and above the call throws
rather than degrades.

| Input | Evidence | Implementation consequence |
|---|---|---|
| A declared type is mandatory from API 34 | [Android Developers, foreground service types are required](https://developer.android.com/about/versions/14/changes/fgs-types-required): starting a foreground service with no declared type raises `MissingForegroundServiceTypeException` | `android:foregroundServiceType` is declared on the service element. |
| VPN apps, "configured using Settings > Network & Internet > VPN", are eligible for `systemExempted` | [Android Developers, foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) | `systemExempted`, not `specialUse`. `specialUse` is the residual bucket for cases the named types miss, and it carries a Play Console review of a free-text justification; this case is named. |
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` arrives at API 34 | `api-versions.xml`: `since="34"` for the permission constant, matching `ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` in `android.jar` | The `<uses-permission>` carries `android:minSdkVersion="34"`, so it is not requested on devices where it does not exist. |
| `POST_NOTIFICATIONS` is a runtime permission from API 33 | `api-versions.xml`: `since="33"` | The app requests it when the reader first starts the tunnel. Without the grant the foreground notification is suppressed, so the tunnel would run with nothing on screen saying so. |

### Still unverified for always-on

- Behavior when always-on is enabled while the packet engine is absent: the
  system will start the service, and the service will reach `Failed`. The
  resulting user-visible outcome has not been observed on a device.
- Interaction between lockdown and the per-app exclusion list on real devices.

## Explicitly Unverified Until Device Work

- Vendor and Android-version behavior of user-store CA trust and WebView.
- Whether a vendor image accepts `systemExempted` for this app. The type is
  declared per Google's documented eligibility; whether a given OEM's
  foreground-service policy agrees has not been observed on hardware.
- Background-restriction behavior for the selected target SDK.
- Socket bypass behavior for the actual native runtime and egress transports.
- Device battery, wakeup, memory, throughput and network-transition behavior.

Record each result with device, Android version, app build, test procedure, and
observed outcome. Do not turn a passing emulator test into a product claim.