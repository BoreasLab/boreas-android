# Build and Product Inputs

The A0 record from [the implementation plan](implementation-plan.md). Change a
value here with the reason, not silently in a build file.

## Pinned

| Input | Value | Why this value |
|---|---|---|
| Android Gradle Plugin | 9.3.1 | Latest stable. AGP 9 carries Kotlin support itself, so the separate `kotlin-android` plugin is not applied and would fail if it were. |
| Kotlin | 2.4.10 | Latest stable. Used for the `kotlin.plugin.compose` and `kotlin.jvm` plugins; the `:app` module compiles with AGP's built-in Kotlin. |
| Gradle | 9.7.0 | Required by AGP 9.3. Wrapper is committed. |
| Compose BOM | 2026.08.00 | Resolves Compose UI 1.12.0 and Material 3 1.4.0. |
| `compileSdk` | 37 | Compose 1.12 requires it. Compiling against newer APIs is separate from opting in to new runtime behavior. |
| `targetSdk` | 36 | Held one behind deliberately. [Verified inputs](verified-inputs.md) records foreground-service behavior for the target SDK as unverified until device work; raise this with a device result attached, not before. |
| `minSdk` | 29 | Derived from a requirement rather than taste: `VpnService.isAlwaysOn()` and `isLockdownEnabled()` arrive at 29, and below it always-on state cannot be read at all. Raising the floor deletes a whole "cannot know" variant from the model instead of guarding it. |
| JVM target | 17 | Both modules. |
| Application id | `dev.boreaslab.boreas` | Provisional. Nothing depends on it yet, so it is cheap to change before the first signed build. |

## Open

These are A0 items with no decision yet. Each blocks a later phase rather than
this one.

| Input | Blocked on |
|---|---|
| ABI list | A2. There is no native library to build for, so there is nothing to choose between. |
| Signing model | A5. No release candidate exists. |
| Foreground-service type | A4 and a device observation. The manifest declares the service with `BIND_VPN_SERVICE` and the `android.net.VpnService` intent filter and no `foregroundServiceType`. Confirm on a device at the chosen target SDK before adding one. |

## Modules

| Module | Plugin | Holds |
|---|---|---|
| `:domain` | `kotlin.jvm` | The model, the engine seam, the lifecycle state machine. No Android type, enforced by the module boundary rather than by review. Its tests run on a plain JVM. |
| `:app` | `com.android.application` | Compose, the design system, `BoreasVpnService`, and every other Android adapter. |

The split exists because AGENTS.md requires framework effects to stay in Kotlin
adapters and decisions to stay pure. A module boundary makes that a compile error
instead of a convention someone has to notice in review.

## Dependencies

Each is declared in `gradle/libs.versions.toml` and used by name.

| Dependency | Used for |
|---|---|
| `androidx.activity:activity-compose` | The Compose entry point and the consent Activity result. |
| `androidx.lifecycle:lifecycle-runtime-compose` | Lifecycle-aware state collection. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | The single state holder. |
| `androidx.navigation:navigation-compose` | Four peer destinations plus six detail routes, with saved state per destination. |
| `androidx.datastore:datastore-preferences` | Preferences as a flow, which is what makes a persisted setting observable without a change listener written by hand. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `:domain` only. |

All dependency versions were checked against their Maven metadata on 2026-08-13 and every one is at its latest stable release. Prereleases available at that date (AGP 9.4.0-alpha, Kotlin 2.4.20-RC, navigation 2.10.0-rc01, datastore 1.3.0-alpha) were not adopted.

No icon library. The icon set is authored in `design/Icons.kt` so one family at one
stroke weight is a property of the code rather than a rule someone has to keep.

## Typefaces

The supplied design document names Copernicus and StyreneB, which are licensed to
Anthropic and cannot ship here. The substitutes are the ones the document itself
names, all under the SIL Open Font License, with license texts in
`app/src/main/assets/licenses`:

| Role | Face | File |
|---|---|---|
| Display serif | EB Garamond | `res/font/boreas_serif.ttf` |
| Text sans | Inter, optical size pinned to 15 | `res/font/boreas_sans.ttf` |
| Counters and identifiers | JetBrains Mono | `res/font/boreas_mono.ttf` |

All three are variable fonts subset to Latin, keeping the weight axis, at 240KB
for the set. This is an approximation of the brand's voice, not the brand's own
typefaces.

## Continuous integration

`.github/workflows/ci.yml` runs two independent jobs on every push to `main` and
every pull request.

| Job | Asserts |
|---|---|
| `gate` | `actionlint` over the workflows including their embedded shell, `shellcheck` over `ci/*.sh`, and `ci/design-gate.sh`. No toolchain, so a punctuation slip or a stray hex literal reports back in under a minute. |
| `build` | Gradle wrapper checksum validation, `:domain:test`, `:app:lintDebug`, then `assembleDebug` and `assembleRelease`, uploading the APKs and the reports. |

Privilege is minimal by construction: the workflow default is `permissions: {}`,
each job requests only `contents: read`, `persist-credentials` is off, every
third-party action is pinned to a full commit SHA, and the Gradle cache is
read-only outside `main` so a pull request cannot poison what later builds
restore. Verified with `actionlint` and `zizmor --persona=pedantic`, both clean.

`ci/design-gate.sh` asserts ten properties that no test inside the program can
see: punctuation, one source of truth per scale, one icon family, no catch-all
over a sealed hierarchy, copy living in `strings.xml`, and no unreferenced
string. The accessibility floor is deliberately not here: it is a law over the
palette and runs as `ContrastLawTest` in `:domain`.

## Build verification status

`:domain:test` (32 tests) and `:app:compileDebugKotlin` both pass locally. Resource packaging and
APK assembly have not been run here: the build host is `aarch64` and Google
publishes `aapt2` for `linux` as an `x86_64` binary only, so `processDebugResources`
cannot start its daemon. Nothing about the project is arm-specific; run
`./gradlew :app:assembleDebug` on an `x86_64` host or a machine with an
`aarch64` Android SDK to produce an APK.
