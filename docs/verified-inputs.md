# Verified Inputs

Checked on 2026-08-12. These sources justify the platform choices; they do not
replace device testing for Boreas-specific behavior.

| Input | Evidence | Implementation consequence |
|---|---|---|
| Compose is Android's recommended modern native UI toolkit | [Android Developers](https://developer.android.com/develop/ui/compose): "Jetpack Compose is Android's recommended modern toolkit for building native UI." | Use Kotlin and Compose for the control surface. |
| Android VPN services inherit from `VpnService` | [Android Developers](https://developer.android.com/develop/connectivity/vpn): "create an Android service inheriting from VpnService." | `BoreasVpnService` owns the platform VPN boundary. |
| Android can protect a socket from the VPN | [`VpnService.protect`](https://developer.android.com/reference/android/net/VpnService#protect(int)): "Protect a socket from VPN connections." | Every native upstream socket needs the service callback before connect. |
| Either descriptor route works, and the core closes neither | api/android.md#who-owns-the-file-descriptor, quoting both methods: `getFd()` leaves ownership with the `ParcelFileDescriptor`, `detachFd()` moves it to the caller's native code | `getFd()`, and the `ParcelFileDescriptor` is closed once after the device vtable's `release` has run. This makes a double close unreachable rather than forbidden. An earlier entry here read this the other way round, as Rust taking ownership through `detachFd()`, which is not what the contract says and would have left the descriptor unclosed. |
| Closing a descriptor is not how a blocked read is unblocked | [`close(2)`, CAVEATS](https://man7.org/linux/man-pages/man2/close.2.html): "It is probably unwise to close file descriptors while they may be in use by system calls in other threads in the same process." | `TunDevice.recv` polls with a bounded timeout and answers `0`, and `close` sets a flag the next pass reads. Nothing closes anything to signal. |

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
| The seven ways to qualify for `systemExempted` are independent, and holding `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` is only one of them | Same page: demo mode, Device Owner, Profile Owner, `ROLE_EMERGENCY`, device admin, exact-alarm permission holders, VPN apps, listed as alternatives | Lint's `ForegroundServicePermission` reads the type as requiring the exact-alarm permission, because the other six criteria cannot be seen in a manifest. Suppressed on the `<service>` element, not in lint configuration. Requesting an alarm permission to satisfy a static check would ask for capability the app has no use for, and `USE_EXACT_ALARM` is restricted on Play to alarm and calendar apps. |
| Eligibility is evaluated when `startForeground` is called, and a `SecurityException` is what a failure looks like | Lint's own explanation for `ForegroundServicePermission`: "when the foreground service is started with a foregroundServiceType that has missing permissions, a SecurityException will be thrown" | The service posts a plain notification in `AwaitingConsent` and promotes only from `Starting` onward. Before consent the app is not the configured VPN, so it satisfies no criterion; promoting there was the one call that could have thrown. |
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` arrives at API 34 | `api-versions.xml`: `since="34"` for the permission constant, matching `ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` in `android.jar` | The `<uses-permission>` carries `android:minSdkVersion="34"`, so it is not requested on devices where it does not exist. |
| `POST_NOTIFICATIONS` is a runtime permission from API 33 | `api-versions.xml`: `since="33"` | The app requests it when the reader first starts the tunnel. Without the grant the foreground notification is suppressed, so the tunnel would run with nothing on screen saying so. |
| `<uses-permission>` takes `android:maxSdkVersion` and no minimum | [Android Developers, `<uses-permission>`](https://developer.android.com/guide/topics/manifest/uses-permission-element) lists `android:name` and `android:maxSdkVersion` only | Both permissions above are declared unconditionally. A platform that does not know a permission name ignores it, which is the behavior a minimum would have been reaching for. |
| `android:allowBackup` no longer governs device-to-device transfer from Android 12 | lint `DataExtractionRules`, and [Android Developers, back up user data](https://developer.android.com/identity/data/autobackup) | `res/xml/data_extraction_rules.xml` excludes every domain from both cloud backup and transfer. A domain that is not named is included, so the sections list them rather than being left empty. |

### Still unverified for always-on

- Behavior when always-on is enabled while the packet engine is absent: the
  system will start the service, and the service will reach `Failed`. The
  resulting user-visible outcome has not been observed on a device.
- Interaction between lockdown and the per-app exclusion list on real devices.

## The Core Contract, Checked Against the Shipped Artifact

Checked on 2026-08-30 against the pinned release
`v0.0.1-dev.2026-08-30.18-00-50.g98e3f4b`, by reading the binaries rather than
the page that describes them.

| Input | Evidence | Implementation consequence |
|---|---|---|
| The 64-bit libraries are 16 KB aligned | `readelf -lW` reports `0x4000` for every LOAD segment in the `arm64-v8a` and `x86_64` objects | Nothing to do. The requirement is 64-bit only, so `x86`'s `0x1000` is not a finding. The linker flags api/android.md names are for a host that builds the core; this repository does not. |
| The requirement itself | [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes): "all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play. Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates." | Recorded as met by the artifact, to be re-checked whenever the pin moves. |
| `JNI_OnLoad` is exported | present in the dynamic symbol table of every ABI | `boreas_android_bypass` would work if it could be reached; see the finding below about JNA. |
| The archive carries three ABIs | `tar tzf` lists `arm64-v8a`, `x86_64`, `x86` and no `armeabi-v7a` | Was four until this pin. `boreasAbis` names the three, and both the unpack and `abiFilters` derive from it, so a fourth could not reach the APK either way. |
| `libc++_shared.so` ships beside `libboreas.so` | `readelf -dW` reports it `NEEDED` on all three ABIs, and `tar tzf` lists it in all three directories. It and the platform libraries are the whole `NEEDED` set | Fixed upstream in this pin. `FetchBoreasCore` copies both names and fails on either missing, so a regression stops the build instead of reaching a device. |
| The shipped `boreas.h` matches the one in the checkout | `diff` reports no difference | The pinned `abiVersion` is checked against the shipped header on every fetch. |

### `boreas_android_bypass` is not reachable from JNA

api/android.md offers two binding routes and says both are supported, then says
of the bypass: "You do not implement this on Android." Those two statements do
not hold together for the JNA route.

`boreas_android_bypass(void *env, void *service, BoreasBypass *out)` needs a
`JNIEnv *` and a `jobject`. JNA supplies the first through
`com.sun.jna.JNIEnv.CURRENT`, and has no facility for the second: its argument
conversion handles primitives, `Pointer`, `Structure`, `Buffer`, primitive
arrays, `String`, `WString`, `Callback`, `NativeMapped`, `PointerType`,
`IntegerType`, and `JNIEnv`, and nothing else. Checked against JNA's own
`native/dispatch.c`, in `get_conversion_flag` and the argument loop in
`dispatch`, at the version this app pins.

So the JNA route as described requires a `Java_...` shim in C, which requires
the NDK, which is the cost the JNA route exists to avoid. Reported as a defect
in that page. The bypass vtable is filled in here instead, by the same JNA
callback mechanism the same page prescribes for the device vtable, and for the
same reason it is sound there: JNA attaches the calling native thread to the
JVM before invoking a callback, which is the obligation `boreas_android_bypass`
exists to discharge.

## Installing a CA Certificate

Checked on 2026-08-25. api/android.md records the one-tap flow as unverified
and asks for it to be checked. It does not work, and the platform says so.

| Input | Evidence | Implementation consequence |
|---|---|---|
| `KeyChain.createInstallIntent()` cannot install a CA certificate from API 30 | [AOSP `KeyChain.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/keystore/java/android/security/KeyChain.java), javadoc on `createInstallIntent()`: "Starting from `android.os.Build.VERSION_CODES#R`, the intent returned by this method cannot be used for installing CA certificates. Since CA certificates can only be installed via Settings, the app should provide the user with a file containing the CA certificate. One way to do this would be to use the `android.provider.MediaStore` API to write the certificate to the `MediaStore.Downloads` collection." | The certificate screen writes the DER to Downloads and sends the user to Settings, in that order, with the second step disabled until the first has produced a file. This is the documented route, not a fallback. |
| `minSdk` 29 is the one level where the intent still works | Same javadoc: the restriction starts at R, which is API 30 | No second flow for it. One path that works everywhere beats two where one cannot be tested and both must be maintained. |
| Whether the root is installed is not observable | Android exposes no read of the user trust store | The screen records what the user says rather than claiming to know, and says why. |

### Still unverified for the certificate

- Whether a given OEM's Settings accepts a `.crt` written to Downloads through
  `MediaStore`. The MIME type is the documented one; the file picker's
  behaviour has not been seen on hardware.

## IPv6 Routing

Checked on 2026-08-25 as a decision, not as a fact: this one needs a
dual-stack device.

The interface adds a default route for both families while configuring an IPv4
address only. Leaving `::/0` out is the fail-open choice, and for a filtering
VPN it is the worse one: on a dual-stack network every IPv6 flow would leave
beside the tunnel, unfiltered, while the interface reported itself up. Routing
it in is fail-closed. api/ names no IPv6 limitation, and the MTU floor of 1280
is described as "the IPv6 minimum", so the core is expected to carry it.

What has not been observed: whether Android accepts the route without a
matching address, and what IPv6 traffic actually does once inside. Watch for
IPv6 connectivity failing entirely, which would mean the route is accepted and
the packets are not carried.

## Explicitly Unverified Until Device Work

- Vendor and Android-version behavior of user-store CA trust and WebView.
- Whether a vendor image accepts `systemExempted` for this app. The type is
  declared per Google's documented eligibility; whether a given OEM's
  foreground-service policy agrees has not been observed on hardware. The first
  device run should watch specifically for a `SecurityException` out of
  `startForeground` on the `Starting` transition, which is where the VPN-app
  criterion is first relied on.
- Background-restriction behavior for the selected target SDK.
- Socket bypass behavior for the actual native runtime and egress transports.
  In particular that a JNA callback is reached from a core worker thread at all,
  which is the assumption the bypass rests on.
- That `recv` returning zero on a poll timeout keeps the core's read loop
  healthy rather than spinning it.
- Teardown leaking neither the descriptor nor either callback context, which is
  observable as `release` running before the descriptor is closed.
- Device battery, wakeup, memory, throughput and network-transition behavior.

Record each result with device, Android version, app build, test procedure, and
observed outcome. Do not turn a passing emulator test into a product claim.