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

## Strictness

| Setting | Where | Why |
|---|---|---|
| `allWarningsAsErrors` | both modules | A warning is a defect the compiler already located. Tolerated once, the list grows until nobody reads it. |
| `explicitApi()` | `:domain` only | Every declaration states its visibility and its return type, so what is public is a decision rather than a default and an edited body cannot silently change a published signature. Not applied to `:app`: it is an application rather than a published surface, and every `@Composable` would have to write out `: Unit` for no reader's benefit. |
| `lint { warningsAsErrors, abortOnError, checkDependencies, checkTestSources }` | `:app` | Same rule for the tool that sees what the compiler cannot. |
| `lint { baseline = null }` | `:app` | A baseline converts today's findings into permanent exemptions. Adding one needs a reason recorded here. |

Report format is not configured: AGP 9 always writes the text, HTML, and XML
reports and has deprecated the switches that used to select them, so CI prints
the text report from disk rather than aiming lint at stdout.

## Language level

Kotlin 2.4.10, at the compiler's default language version. Two features are used
deliberately rather than incidentally:

| Feature | Stable since | Used for |
|---|---|---|
| Explicit backing fields | 2.4.0 | Observable state cells. One cell gets one name, exposed at a read-only type and mutable only inside its owner, which replaces the private-mutable-plus-public-view pair the repository used before. `.github/scripts/design-gate.sh` asserts that no `_`-prefixed shadow returns and that nothing casts the exposed value back to a `Mutable*Flow`, which is the guarantee the old `asStateFlow()` wrapper used to carry. |
| Guard conditions in `when` | 2.2.0 | Cases that were a conditional nested inside one branch, most visibly lockdown beside the other always-on states and the simulated session beside the real one. Exhaustiveness is unaffected: each guarded branch is followed by an unguarded one for the same type. |

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

`.github/workflows/ci.yml` runs three jobs on every push to `main`, every pull
request against it, and on demand. There is no `needs:` edge between them: none
consumes another's output, so one push reports three results instead of hiding
the second and third behind the first.

| Job | Asserts |
|---|---|
| `gate` | `.github/scripts/design-gate.sh`. No toolchain, so a punctuation slip or a stray hex literal answers while the build is still provisioning. |
| `build` | Gradle wrapper checksum, `:domain:test`, `assembleDebug` and `assembleRelease`, then `:app:lintDebug`, uploading the unsigned APKs and the reports. Assembly runs before lint deliberately: lint fails on warnings, so running it first would leave "does the artifact build at all" unanswered for another round trip. |
| `workflows` | `actionlint`, `shellcheck` over the scripts, and `zizmor`. |

### Supply chain

The layout matches `boreas-windows` so the two repositories have one story.

Every action is GitHub's own, at a major tag. `.github/zizmor.yml` records why:
`actions/*` runs on the platform that already holds the token, so a hash pin
buys nothing against an actor who controls the runner and costs a bump per
patch release; `BTreeMap/*` is vendored to track upstream, so freezing it
defeats the point. Everything else still needs a hash, and relaxing that is a
written decision rather than an omission.

No third-party action appears at all. Where an outside tool is needed it is
fetched by exact version and verified against a digest before it runs, which is
a stronger guarantee than pinning an action that can do anything once started:

| Script | Fetches | Verified against |
|---|---|---|
| `actionlint.sh` | actionlint 1.7.12 | SHA256 recorded in the workflow |
| `zizmor.sh` | zizmor 1.29.0 into a throwaway venv | pinned version, isolated from the job's Python |
| `gradle-wrapper.sh` | nothing | the checksum Gradle publishes for the version `gradle-wrapper.properties` declares |
| `android-sdk.sh` | SDK packages | `sdkmanager`, already on the runner image, against Google's repository manifest |

Privilege is minimal by construction: the workflow default is `permissions: {}`,
each job requests only `contents: read`, `persist-credentials` is off, and
`submodules: false` keeps the vendored skills out of CI entirely. Verified with
`actionlint`, `shellcheck`, and `zizmor`, all clean.

`design-gate.sh` asserts twelve properties that no test inside the program can
see: punctuation, one source of truth per scale, one icon family, no catch-all
over a sealed hierarchy, one name per observable state cell, no cast back to a
mutable flow, copy living in `strings.xml`, and no unreferenced string. The
accessibility floor is deliberately not there: it is a law over the palette and
runs as `ContrastLawTest` in `:domain`.

## Build verification status

`:domain:test` (38 tests), `:domain:compileKotlin` under `explicitApi()`, and
`:app:compileDebugKotlin` and `:app:compileReleaseKotlin` under
`allWarningsAsErrors` all pass locally, warning-free.

Three things cannot run on this host and CI is where they first execute:
`:app:lintDebug`, `assembleDebug`, and `assembleRelease`. The build host is
`aarch64` and Google publishes `aapt2` for `linux` as an `x86_64` binary only, so
`processDebugResources` cannot start its daemon and every task downstream of
resource compilation is unreachable here. `actionlint` is the same story: the
release archive is `linux_amd64`.

Nothing about the project is arm-specific. Run `./gradlew :app:assembleDebug` on
an `x86_64` host to produce an APK.
