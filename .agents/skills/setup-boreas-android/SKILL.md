---
name: setup-boreas-android
description: >-
  Sets up Boreas Android Kotlin and Android development entirely in a
  userspace temporary directory, including a native JDK, Android SDK, Gradle
  cache, isolated HOME, package verification, and architecture-aware checks.
  Use when a Linux agent needs to build or test this repository without global
  Kotlin, Java, Gradle, or Android SDK installation and without sudo. Do not use
  for CI runner provisioning or Android device setup.
license: MIT
compatibility: >-
  Linux x86_64 or aarch64, network access, and bash, curl, jq, tar, unzip,
  sha1sum, and sha256sum. Android app builds require x86_64 because Google's
  Linux aapt2 package is x86-64; aarch64 supports the pure JVM domain gate.
---

# Setup Boreas Android

Install nothing globally. Keep the JDK, Android SDK, Gradle distribution,
dependency cache, temporary HOME, and build experiment under
`/tmp/boreas-android-dev`. Kotlin comes from the Gradle plugins declared by the
repository. Never install a standalone Kotlin compiler.

## Registry

| Name | Path |
| --- | --- |
| `build-inputs` | [docs/build-inputs.md](../../../docs/build-inputs.md) |
| `ci-workflow` | [.github/workflows/ci.yml](../../../.github/workflows/ci.yml) |
| `version-catalog` | [gradle/libs.versions.toml](../../../gradle/libs.versions.toml) |
| `wrapper-properties` | [gradle/wrapper/gradle-wrapper.properties](../../../gradle/wrapper/gradle-wrapper.properties) |

## Invariant

Every mutable toolchain path must begin with `/tmp/boreas-android-dev`.
Repository source remains in its checkout. The ignored `local.properties` may
point into the temporary root. No command uses `sudo`, writes beneath `/usr`, or
uses the caller's HOME, Gradle cache, JDK, Android SDK, or Kotlin installation.

Falsifier: run `:domain:test` through `env -i` with only the temporary paths. A
successful run proves JDK, Gradle, Kotlin plugin, dependencies, and JVM tests do
not depend on user-global configuration.

## 1. Preflight

Run from the repository root. Stop if the temporary root already exists but was
not created for this repository. Do not delete an unknown directory.

<preflight_commands>

```bash
cd "$(git rev-parse --show-toplevel)"
readonly ROOT=/tmp/boreas-android-dev

case "$(uname -m)" in
  x86_64) readonly ADOPTIUM_ARCH=x64 ;;
  aarch64|arm64) readonly ADOPTIUM_ARCH=aarch64 ;;
  *) printf 'unsupported architecture: %s\n' "$(uname -m)" >&2; exit 1 ;;
esac

for command in bash curl jq tar unzip sha1sum sha256sum; do
  command -v "$command" >/dev/null || {
    printf 'missing bootstrap command: %s\n' "$command" >&2
    exit 1
  }
done

test ! -e "$ROOT" || {
  printf '%s already exists; verify and reuse it or choose explicitly how to preserve it\n' "$ROOT" >&2
  exit 1
}

mkdir -p "$ROOT"/{downloads,home,gradle-home,android-sdk/cmdline-tools,jdk,tmp}
```

</preflight_commands>

Expected: no output and directories only beneath the temporary root.

## 2. Install JDK 21

Use Adoptium's structured API to select its latest Temurin 21 package for the
host architecture. Verify its published SHA-256 before extraction. JDK 21 is the
repository's CI runtime; source and bytecode targets remain JVM 17 as owned by
`build-inputs`.

<jdk_commands>

```bash
readonly JDK_METADATA="$ROOT/downloads/adoptium-jdk21.json"
readonly JDK_ARCHIVE="$ROOT/downloads/temurin-jdk21.tar.gz"

curl --disable --fail --show-error --location --retry 3 \
  --output "$JDK_METADATA" \
  "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=$ADOPTIUM_ARCH&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=linux&vendor=eclipse"

read -r JDK_SHA256 JDK_URL < <(
  jq -er 'first(.[] | .binary.package) | [.checksum, .link] | @tsv' "$JDK_METADATA"
)

curl --disable --fail --show-error --location --retry 3 \
  --output "$JDK_ARCHIVE" "$JDK_URL"
printf '%s  %s\n' "$JDK_SHA256" "$JDK_ARCHIVE" | sha256sum --check
tar -xzf "$JDK_ARCHIVE" --strip-components=1 -C "$ROOT/jdk"
"$ROOT/jdk/bin/java" -version
```

</jdk_commands>

Expected: checksum `OK`; Java reports Temurin/OpenJDK 21.

## 3. Install Android Command-Line Tools

Use the repository-verified command-line tools revision. Google publishes SHA-1
for this archive in its SDK repository metadata. Check the digest before
extraction.

<android_tools_commands>

```bash
readonly ANDROID_TOOLS_REVISION=15859902
readonly ANDROID_TOOLS_SHA1=040d3996a65543d22ec4bf73e4c37aa37a8d4af4
readonly ANDROID_TOOLS_ARCHIVE="$ROOT/downloads/android-commandline-tools.zip"

curl --disable --fail --show-error --location --retry 3 \
  --output "$ANDROID_TOOLS_ARCHIVE" \
  "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_TOOLS_REVISION}_latest.zip"
printf '%s  %s\n' "$ANDROID_TOOLS_SHA1" "$ANDROID_TOOLS_ARCHIVE" | sha1sum --check

unzip -q "$ANDROID_TOOLS_ARCHIVE" -d "$ROOT/android-sdk/cmdline-tools"
mv "$ROOT/android-sdk/cmdline-tools/cmdline-tools" \
  "$ROOT/android-sdk/cmdline-tools/latest"
```

</android_tools_commands>

Expected: checksum `OK`; `cmdline-tools/latest/bin/sdkmanager` exists.

## 4. Install Repository SDK Packages

Derive package names from `ci-workflow`, the execution owner for SDK
provisioning. The current pair is platform 37.0 and build-tools 37.0.0. Keep
license files under the temporary SDK.

<sdk_package_commands>

```bash
export HOME="$ROOT/home"
export JAVA_HOME="$ROOT/jdk"
export ANDROID_HOME="$ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ROOT/android-sdk"
export GRADLE_USER_HOME="$ROOT/gradle-home"
export TMPDIR="$ROOT/tmp"
export PATH="$JAVA_HOME/bin:/usr/bin:/bin"

mapfile -t SDK_PACKAGES < <(
  grep -oE "'(platforms|build-tools);[^']+'" .github/workflows/ci.yml | tr -d "'"
)
((${#SDK_PACKAGES[@]} == 2)) || {
  printf 'expected one platform and one build-tools package in CI, found %d\n' \
    "${#SDK_PACKAGES[@]}" >&2
  exit 1
}

readonly SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
"$SDKMANAGER" --licenses >/dev/null 2>&1 <<<"$(yes y | head -n 50)" || true
"$SDKMANAGER" --install "${SDK_PACKAGES[@]}"
```

</sdk_package_commands>

Expected: installation reaches 100 percent. A deprecation warning directing
users to the new Android CLI is expected. On aarch64, continue using the
Java-based `sdkmanager`: the replacement `android` executable and `aapt2` are
x86-64 binaries.

## 5. Bind the Checkout

Create or replace the checkout's ignored `local.properties`. Do not commit it.
Keep it aligned with the Android SDK environment variables. AGP reads each as an
SDK source; an invalid `sdk.dir` warns and falls back to the valid environment
path.

<local_properties_template>

```properties
sdk.dir=/tmp/boreas-android-dev/android-sdk
```

</local_properties_template>

Create `/tmp/boreas-android-dev/activate.sh` with the following content, then
make it executable. The script is optional convenience; `env -i` remains the
proof check.

<activation_script_template>

```bash
#!/usr/bin/env bash
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export HOME="$ROOT/home"
export JAVA_HOME="$ROOT/jdk"
export ANDROID_HOME="$ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ROOT/android-sdk"
export GRADLE_USER_HOME="$ROOT/gradle-home"
export TMPDIR="$ROOT/tmp"
export PATH="$JAVA_HOME/bin:/usr/bin:/bin"
unset KOTLIN_HOME
```

</activation_script_template>

## 6. Prove Isolation

Run the domain gate with an empty inherited environment. This invokes the
committed Gradle wrapper, which downloads pinned Gradle into the temporary
Gradle home. Gradle resolves the pinned Kotlin plugin into the same cache.

<isolation_check>

```bash
env -i \
  HOME="$ROOT/home" \
  USER="$(id -un)" \
  LOGNAME="$(id -un)" \
  PATH="$ROOT/jdk/bin:/usr/bin:/bin" \
  JAVA_HOME="$ROOT/jdk" \
  ANDROID_HOME="$ROOT/android-sdk" \
  ANDROID_SDK_ROOT="$ROOT/android-sdk" \
  GRADLE_USER_HOME="$ROOT/gradle-home" \
  TMPDIR="$ROOT/tmp" \
  ./gradlew --no-daemon :domain:test
```

</isolation_check>

Expected: `BUILD SUCCESSFUL`. On first run, the wrapper downloads the Gradle
version owned by `wrapper-properties`; Kotlin and dependency artifacts resolve
from versions owned by `version-catalog`.

## 7. Run Architecture-Appropriate Gates

On x86_64, source the activation script and run the repository build gates.

<x86_64_gate_commands>

```bash
source /tmp/boreas-android-dev/activate.sh
./gradlew --no-daemon :domain:test :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
./gradlew --no-daemon :app:lintDebug :domain:lint
```

</x86_64_gate_commands>

On aarch64, stop after pure JVM tasks such as `:domain:test` or
`:domain:compileKotlin`. Google's Linux `aapt2` in build-tools 37.0.0 reports
ELF machine `Advanced Micro Devices X86-64`; app resource processing cannot run
natively. Use CI or an x86_64 Linux host for app assembly and lint.

## Reuse

If `/tmp/boreas-android-dev` already exists, verify before reuse:

<reuse_check>

```bash
test -x /tmp/boreas-android-dev/jdk/bin/java
test -x /tmp/boreas-android-dev/android-sdk/cmdline-tools/latest/bin/sdkmanager
test -f /tmp/boreas-android-dev/android-sdk/platforms/android-37.0/android.jar
test -d /tmp/boreas-android-dev/android-sdk/build-tools/37.0.0
source /tmp/boreas-android-dev/activate.sh
./gradlew --no-daemon :domain:test
```

</reuse_check>

Rebuild when `ci-workflow` changes SDK package names, `build-inputs` changes the
JDK policy, or command-line tools fail repository access. Never repair a stale
temporary environment with global package installation.

## Gotchas

- `local.properties` overrides SDK environment variables. Check it first.
- Google's repository XML is not newest-last. Never select command-line tools
  with an unsorted `tail` over metadata.
- The current `android` replacement CLI is x86-64. It fails to execute on
  aarch64 even though Java-based `sdkmanager` works there.
- `aapt2` is x86-64. ARM64 can compile and test `:domain`, not package `:app`.
- `--no-daemon` still creates a single-use Gradle daemon. Its files remain under
  the isolated Gradle home.
- `/tmp` is ephemeral. Reboot or cleanup removes the entire environment by
  design.
- Do not add Kotlin to PATH. The Gradle plugin is the compiler source of truth.
