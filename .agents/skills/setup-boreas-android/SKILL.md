---
name: setup-boreas-android
description: >-
  Provisions the complete Boreas Android toolchain in a userspace temporary
  directory on Linux x86_64 or aarch64, installing a JDK, the Android SDK, an
  isolated Gradle cache and HOME, and, on hosts that cannot execute Google's
  x86_64-only aapt2, an emulated resource toolchain, so that unit tests, debug
  and release APK assembly, and lint all run on either architecture with no
  global installation and no sudo. Use when an agent needs to build, test, lint,
  or reproduce a CI failure for this repository on a machine that lacks Java,
  Gradle, Kotlin, or the Android SDK. Do not use for CI runner provisioning, for
  Android device or emulator setup, or for shipping a release build.
license: MIT
compatibility: >-
  Linux x86_64 or aarch64, network access, and bash 4+, git, curl, tar, unzip,
  sha256sum. Roughly 6 GB of free space under the toolchain root. Both
  architectures reach every gate; aarch64 runs resource tasks under emulation
  and is therefore slower.
---

# Setup Boreas Android

One script provisions everything. Run it, then build. Every command after setup
is identical on both architectures.

## Registry

| Name | Path |
| --- | --- |
| `setup` | [scripts/setup.sh](scripts/setup.sh) |
| `build-inputs` | [docs/build-inputs.md](../../../docs/build-inputs.md) |
| `ci-workflow` | [.github/workflows/ci.yml](../../../.github/workflows/ci.yml) |
| `version-catalog` | [gradle/libs.versions.toml](../../../gradle/libs.versions.toml) |

## Procedure

Run `setup` from anywhere. It asks git where the repository is, starting from
its own location, so neither the working directory nor the script's depth in the
tree affects the answer.

<setup_command>

```bash
bash .agents/skills/setup-boreas-android/scripts/setup.sh
```

</setup_command>

Expected: a final block naming the host subdirectory, the resource toolchain
(`native` or `emulated`), and the gate commands. Exit status 0.

Re-running is safe and is the correct repair action: every step checks its own
postcondition and skips completed work. Use `--reinstall` only to discard the
root and start over.

Then source the generated activation script and run the gates. These four
commands are the same on every supported host.

<gate_commands>

```bash
source /tmp/boreas-android-dev/activate.sh
./gradlew --no-daemon :domain:test :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
./gradlew --no-daemon :app:lintDebug :domain:lint
./.github/scripts/design-gate.sh
```

</gate_commands>

Expected: `BUILD SUCCESSFUL` from each Gradle invocation, `all properties hold`
from the design gate, and two APKs under `app/build/outputs/apk`.

Set `BOREAS_DEV_ROOT` before running `setup` to relocate the toolchain. The
activation script is written inside whatever root was used.

## Invariant

Every mutable toolchain path begins at the toolchain root, default
`/tmp/boreas-android-dev`. Repository source stays in its checkout. The ignored
`local.properties` and `$GRADLE_USER_HOME/gradle.properties` are generated. No
command uses sudo, writes beneath `/usr`, or reads the caller's HOME, Gradle
cache, JDK, Android SDK, or Kotlin installation.

Falsifier: run a gate through `env -i` carrying only the generated paths. A pass
proves the toolchain does not depend on user-global configuration.

<isolation_check>

```bash
env -i \
  HOME=/tmp/boreas-android-dev/home \
  PATH=/tmp/boreas-android-dev/toolchain/host/lib/jvm/bin:/usr/bin:/bin \
  JAVA_HOME=/tmp/boreas-android-dev/toolchain/host/lib/jvm \
  ANDROID_HOME=/tmp/boreas-android-dev/android-sdk \
  ANDROID_SDK_ROOT=/tmp/boreas-android-dev/android-sdk \
  GRADLE_USER_HOME=/tmp/boreas-android-dev/gradle-home \
  TMPDIR=/tmp/boreas-android-dev/tmp \
  ./gradlew --no-daemon :domain:test
```

</isolation_check>

## What The Script Owns

Read `setup` for the authoritative version pins and layout. It is the single
source for both; nothing is restated here.

Three suppliers, each the only sensible one for what it provides:

| Supplier | Provides | Why |
| --- | --- | --- |
| conda-forge, through micromamba | JVM; on non-x86_64 hosts also the emulator and the x86_64 system libraries | Needed for emulation regardless, so owning the JVM too removes a mechanism rather than adding one |
| Google | Android SDK, and aapt2 | Sole publisher of both |
| This repository | Gradle, through the committed wrapper, and Kotlin, through the Gradle plugin | Already pinned here; never install either separately |

Two versions are derived rather than pinned, so they cannot drift: SDK package
names come from `ci-workflow`, and the aapt2 version comes from the AGP version
in `version-catalog`. Bumping AGP therefore needs no edit to `setup`.

## The One Architecture Fact

Google publishes aapt2 for linux as an x86_64 binary only. Every Android
resource task runs it, so on any other host every task downstream of resource
compilation is unreachable: no APK, no lint, no unit test that needs `R`.

`setup` names that fact once and answers it two ways, both first class:

| Host | Resource toolchain | aapt2 invocation |
| --- | --- | --- |
| linux-64 | `native` | executed directly |
| linux-aarch64 | `emulated` | executed under qemu x86_64 user-mode emulation against an x86_64 sysroot |

Both write one executable to the same path and register it with Gradle through
`android.aapt2FromMavenOverride` in the isolated `gradle.properties`. Nothing
downstream branches on architecture, including the commands above: there is no
flag to pass and nothing to remember differently per machine.

## Gotchas

- `local.properties` overrides the SDK environment variables. `setup` rewrites
  it every run; a stale one from another toolchain root is the first thing that
  will mislead a build.
- `micromamba create` replaces a prefix rather than adding to it. Installing two
  packages into one prefix with two calls silently discards the first. `setup`
  builds each prefix in a single call for this reason.
- A conda prefix is not a `JAVA_HOME`. conda-forge's `openjdk` puts the JDK at
  `<prefix>/lib/jvm` and leaves symlinks in `<prefix>/bin`, so pointing
  `JAVA_HOME` at the prefix makes `sdkmanager` report an invalid directory.
- The aapt2 in SDK build-tools is not AGP's aapt2. Its build number differs, so
  substituting it invites a mismatch against what AGP expects. `setup` fetches
  the artifact AGP itself resolves.
- The aapt2 wrapper must write nothing to stdout. AGP speaks the aapt2 daemon
  protocol over the process's stdin and stdout; one stray line corrupts it.
- The `android` CLI that replaces `sdkmanager` is an x86-64 binary and will not
  execute on aarch64. Keep using the Java-based `sdkmanager`; its deprecation
  warning is expected.
- Emulated resource tasks are slower than native ones. A full
  `assembleDebug assembleRelease` takes minutes rather than seconds. Correctness
  is unaffected.
- `--no-daemon` still forks a single-use Gradle daemon. Its files stay under the
  isolated Gradle home.
- The default root is under `/tmp` and is ephemeral by design. A reboot removes
  the environment; re-run `setup`.
- Never add Kotlin to `PATH`. The Gradle plugin pinned in `version-catalog` is
  the compiler, and a second one on `PATH` will not be used but will confuse
  diagnosis.
- Every script in this repository locates the repository through git rather than
  from the working directory or a relative climb. Copy that `repo_root` function
  when adding one; a path built from `..` encodes where the file sits today and
  fails silently rather than loudly when it moves.
