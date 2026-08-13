#!/usr/bin/env bash
#
# Provision a complete Boreas Android toolchain in userspace, on linux-64 or
# linux-aarch64, installing nothing globally and using no sudo.
#
# Run it, then build. Re-running is safe: every step checks its own postcondition
# and skips work that is already done.
#
#   bash .agents/skills/setup-boreas-android/scripts/setup.sh
#   bash .agents/skills/setup-boreas-android/scripts/setup.sh --reinstall
#
# Assumes bash 4+, GNU coreutils, and: curl, tar, unzip, sha256sum.
#
#
# THE ONE ARCHITECTURE FACT
#
# Google publishes aapt2 for linux as an x86_64 binary only. Every Android
# resource task runs it, so on any other host every task downstream of resource
# compilation is unreachable: no APK, no lint, no unit test that needs R.
#
# That single fact is the only thing in this script that depends on the host
# architecture, and it is named once, in `resource_toolchain`. Both answers are
# first class. An x86_64 host runs aapt2 directly; an aarch64 host runs the same
# binary under x86_64 user-mode emulation. Both materialize one executable at the
# same path and hand Gradle the same property, so nothing downstream of this
# script branches on architecture at all, including the commands a reader types
# afterwards.
#
#
# WHERE THINGS COME FROM
#
# Three sources, each the only sensible one for what it provides:
#
#   conda-forge   The JVM, and on a non-x86_64 host the emulator and the x86_64
#     (micromamba) system libraries aapt2 links against. One package manager, one
#                 channel, versions pinned below. It is needed for emulation
#                 regardless, so letting it own the JVM too removes a second
#                 mechanism rather than adding one.
#
#   Google        The Android SDK, and aapt2 itself. Sole publisher of both.
#
#   This repo     Gradle, through the committed wrapper, and Kotlin, through the
#                 Gradle plugin. Never installed here; the repository already
#                 pins them and is the only correct source.
#
# Every version this script does not derive is pinned as a constant. The two it
# derives are derived so they cannot drift: the SDK packages come from the CI
# workflow, and the aapt2 version comes from the AGP version in the version
# catalog, which is what makes bumping AGP a one-line change.
set -euo pipefail

# --- Pinned inputs -----------------------------------------------------------

readonly MICROMAMBA_VERSION=2.9.0-0
readonly OPENJDK_VERSION=21.0.8
readonly QEMU_VERSION=11.0.3
readonly SYSROOT_VERSION=2.39

# Google publishes no stable digest alongside this archive, so the digest is
# recorded here and the revision is pinned with it. Both change together.
readonly ANDROID_TOOLS_REVISION=15859902
readonly ANDROID_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

readonly GOOGLE_MAVEN=https://dl.google.com/dl/android/maven2
readonly MICROMAMBA_RELEASES=https://github.com/mamba-org/micromamba-releases/releases/download

# --- Boundary ----------------------------------------------------------------

log() { printf '%s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
usage: setup.sh [--reinstall] [--help]

  (no flags)   Provision or repair the toolchain. Safe to re-run.
  --reinstall  Delete the toolchain root first, then provision from scratch.

Everything is installed under $BOREAS_DEV_ROOT, default /tmp/boreas-android-dev.
Nothing is installed globally and no command uses sudo.
USAGE
}

# --- The domain --------------------------------------------------------------

# The closed set of hosts this repository is developed on, named as the conda
# subdirectory that serves each. An unknown machine stops here rather than
# installing something that cannot work.
host_subdir() {
  case "$(uname -s)/$(uname -m)" in
    Linux/x86_64)        printf 'linux-64\n' ;;
    Linux/aarch64 | \
    Linux/arm64)         printf 'linux-aarch64\n' ;;
    *) die "unsupported host $(uname -s)/$(uname -m); this script serves Linux x86_64 and aarch64" ;;
  esac
}

# How this host reaches an x86_64 aapt2. A total function of the subdirectory,
# and the only place any decision depends on the architecture.
resource_toolchain() {
  case "${1:?subdir required}" in
    linux-64)      printf 'native\n' ;;
    linux-aarch64) printf 'emulated\n' ;;
    *) die "no resource toolchain defined for subdir $1" ;;
  esac
}

# --- Layout ------------------------------------------------------------------
#
# One name per path, derived once, so no step invents its own spelling.

ROOT="${BOREAS_DEV_ROOT:-/tmp/boreas-android-dev}"
readonly ROOT
readonly DOWNLOADS="$ROOT/downloads"
readonly MAMBA_BIN="$ROOT/micromamba/bin/micromamba"
readonly HOST_PREFIX="$ROOT/toolchain/host"
# conda-forge's openjdk puts the JDK one level in and leaves symlinks in bin, so
# the prefix is not itself a JAVA_HOME. Tools that read JAVA_HOME need this one.
readonly JAVA_HOME_DIR="$HOST_PREFIX/lib/jvm"
readonly X86_64_PREFIX="$ROOT/toolchain/x86_64"
readonly SYSROOT="$X86_64_PREFIX/x86_64-conda-linux-gnu/sysroot"
readonly ANDROID_SDK="$ROOT/android-sdk"
readonly SDKMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager"
readonly GRADLE_HOME="$ROOT/gradle-home"
readonly AAPT2_DIR="$ROOT/aapt2"
readonly AAPT2_ENTRY="$AAPT2_DIR/aapt2"
readonly AAPT2_REAL="$AAPT2_DIR/aapt2.x86_64"
readonly ACTIVATE="$ROOT/activate.sh"

# Repository root, from this script's own location, so the script works whether
# or not it is run from the checkout and whether or not git is installed.
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
readonly REPO
readonly CI_WORKFLOW="$REPO/.github/workflows/ci.yml"
readonly VERSION_CATALOG="$REPO/gradle/libs.versions.toml"

# --- Shared effects ----------------------------------------------------------

# Fetch to a temporary name and rename on success, so an interrupted download
# never leaves a short file that a later run would trust.
fetch() {
  local url="$1" target="$2"
  [[ -f "$target" ]] && return 0
  curl --disable --fail --silent --show-error --location \
    --retry 3 --retry-delay 2 --retry-connrefused \
    --output "$target.partial" "$url"
  mv -- "$target.partial" "$target"
}

verify_sha256() {
  local file="$1" expected="$2" actual
  actual="$(sha256sum -- "$file" | cut -d' ' -f1)"
  [[ "$actual" == "$expected" ]] ||
    die "digest mismatch for $file
  expected $expected
  actual   $actual"
}

# --- Steps -------------------------------------------------------------------

ensure_prerequisites() {
  local tool
  for tool in curl tar unzip sha256sum; do
    command -v "$tool" >/dev/null || die "missing required command: $tool"
  done
  mkdir -p "$DOWNLOADS" "$ROOT/tmp" "$ROOT/home" "$GRADLE_HOME" "$AAPT2_DIR"
}

# micromamba is a single static binary. Its publisher ships a digest beside each
# one, so the digest is fetched rather than recorded here and the check works the
# same on either architecture without a per-architecture constant.
ensure_micromamba() {
  local subdir="$1" url archive expected
  [[ -x "$MAMBA_BIN" ]] && return 0

  log "installing micromamba $MICROMAMBA_VERSION"
  url="$MICROMAMBA_RELEASES/$MICROMAMBA_VERSION/micromamba-$subdir"
  archive="$DOWNLOADS/micromamba-$subdir"
  fetch "$url" "$archive"
  fetch "$url.sha256" "$archive.sha256"
  expected="$(cut -d' ' -f1 <"$archive.sha256" | tr -d '[:space:]')"
  verify_sha256 "$archive" "$expected"

  mkdir -p "$(dirname "$MAMBA_BIN")"
  install -m 0755 "$archive" "$MAMBA_BIN"
}

# Packages are requested by exact version, and the prefix is created only when
# its postcondition is absent, so a re-run neither re-solves nor drifts.
conda_install() {
  local prefix="$1" subdir="$2"
  shift 2
  MAMBA_ROOT_PREFIX="$ROOT/micromamba/root" \
    "$MAMBA_BIN" create --yes --quiet --prefix "$prefix" \
    --platform "$subdir" --channel conda-forge "$@" >&2
}

# Everything the host runs natively, named as one list so the prefix is built by
# one call. `micromamba create` replaces a prefix rather than adding to it, so
# installing in two calls would silently discard the first: the list is a total
# function of the toolchain precisely so that ordering cannot be got wrong.
host_packages() {
  printf 'openjdk=%s\n' "$OPENJDK_VERSION"
  if [[ "${1:?toolchain required}" == emulated ]]; then
    printf 'qemu-execve-x86_64=%s\n' "$QEMU_VERSION"
  fi
}

ensure_host_toolchain() {
  local subdir="$1" toolchain="$2" packages
  [[ -x "$JAVA_HOME_DIR/bin/javac" ]] && return 0
  mapfile -t packages < <(host_packages "$toolchain")
  log "installing ${packages[*]}"
  conda_install "$HOST_PREFIX" "$subdir" "${packages[@]}"
}

# An x86_64 root filesystem for the emulator to resolve libraries in. aapt2 is
# dynamically linked, so the binary alone is not enough: qemu needs a glibc and a
# libgcc of the emulated architecture, which is what a sysroot is. It is a second
# prefix because it holds packages for a second platform.
ensure_x86_64_sysroot() {
  if [[ ! -f "$SYSROOT/lib64/libc.so.6" ]]; then
    log "installing an x86_64 sysroot"
    conda_install "$X86_64_PREFIX" linux-64 \
      "sysroot_linux-64=$SYSROOT_VERSION" libgcc
  fi

  # libgcc lands in the prefix's own lib directory rather than inside the
  # sysroot, and aapt2 names it directly, so it is placed where qemu will look.
  if [[ ! -f "$SYSROOT/lib64/libgcc_s.so.1" ]]; then
    cp -- "$X86_64_PREFIX/lib/libgcc_s.so.1" "$SYSROOT/lib64/"
  fi
}

ensure_android_sdk() {
  local archive packages
  archive="$DOWNLOADS/commandlinetools-linux-$ANDROID_TOOLS_REVISION.zip"

  if [[ ! -x "$SDKMANAGER" ]]; then
    log "installing Android command-line tools $ANDROID_TOOLS_REVISION"
    fetch "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_TOOLS_REVISION}_latest.zip" \
      "$archive"
    verify_sha256 "$archive" "$ANDROID_TOOLS_SHA256"
    mkdir -p "$ANDROID_SDK/cmdline-tools"
    rm -rf -- "$ANDROID_SDK/cmdline-tools/latest" "$ANDROID_SDK/cmdline-tools/cmdline-tools"
    unzip -q "$archive" -d "$ANDROID_SDK/cmdline-tools"
    mv -- "$ANDROID_SDK/cmdline-tools/cmdline-tools" "$ANDROID_SDK/cmdline-tools/latest"
  fi

  # The CI workflow is the execution owner for SDK provisioning, so the package
  # names are read from it rather than repeated here where they could disagree.
  mapfile -t packages < <(
    grep -oE "'(platforms|build-tools);[^']+'" "$CI_WORKFLOW" | tr -d "'"
  )
  ((${#packages[@]} > 0)) ||
    die "no SDK packages found in $CI_WORKFLOW; expected quoted 'platforms;…' and 'build-tools;…'"

  log "installing SDK packages: ${packages[*]}"
  # Licences are accepted before the install so the install has nothing to ask.
  # Its own failure is not the one worth reporting: the install below says which
  # package could not be had, which is the more useful message.
  JAVA_HOME="$JAVA_HOME_DIR" ANDROID_HOME="$ANDROID_SDK" ANDROID_SDK_ROOT="$ANDROID_SDK" \
    "$SDKMANAGER" --licenses >/dev/null 2>&1 <<<"$(yes y | head -n 50)" || true
  JAVA_HOME="$JAVA_HOME_DIR" ANDROID_HOME="$ANDROID_SDK" ANDROID_SDK_ROOT="$ANDROID_SDK" \
    "$SDKMANAGER" --install "${packages[@]}" >&2
}

# The aapt2 AGP itself would have used, resolved from the AGP version the version
# catalog pins. Deriving it means bumping AGP needs no edit here, and it rules
# out the subtler failure of running a build-tools aapt2 whose build number
# differs from the one AGP expects.
ensure_aapt2() {
  local agp metadata version jar
  agp="$(grep -oE '^agp = "[^"]+"' "$VERSION_CATALOG" | cut -d'"' -f2)"
  [[ -n "$agp" ]] || die "could not read the agp version from $VERSION_CATALOG"

  metadata="$DOWNLOADS/aapt2-maven-metadata.xml"
  fetch "$GOOGLE_MAVEN/com/android/tools/build/aapt2/maven-metadata.xml" "$metadata"
  version="$(grep -oE "<version>$agp-[0-9]+</version>" "$metadata" |
    tail -n1 | sed -E 's#</?version>##g')"
  [[ -n "$version" ]] || die "Google publishes no aapt2 for AGP $agp"

  jar="$DOWNLOADS/aapt2-$version-linux.jar"
  if [[ ! -f "$AAPT2_REAL" ]]; then
    log "installing aapt2 $version"
    fetch "$GOOGLE_MAVEN/com/android/tools/build/aapt2/$version/aapt2-$version-linux.jar" "$jar"
    rm -rf -- "$AAPT2_DIR/extract"
    mkdir -p "$AAPT2_DIR/extract"
    unzip -q -o "$jar" aapt2 -d "$AAPT2_DIR/extract"
    install -m 0755 "$AAPT2_DIR/extract/aapt2" "$AAPT2_REAL"
    rm -rf -- "$AAPT2_DIR/extract"
  fi
}

# One entry point at one path on every host. Only the exec line differs, which is
# the whole of the architecture difference, expressed once.
write_aapt2_entry() {
  local toolchain="$1" launcher
  # The whole architecture difference, in one assignment: nothing in front of
  # aapt2 on a host that can run it, and the emulator in front of it otherwise.
  case "$toolchain" in
    native)   launcher='' ;;
    emulated) launcher="$(printf '%q -L %q ' "$HOST_PREFIX/bin/qemu-x86_64" "$SYSROOT")" ;;
    *) die "unknown resource toolchain: $toolchain" ;;
  esac

  cat >"$AAPT2_ENTRY.partial" <<ENTRY
#!/usr/bin/env bash
# Generated by setup.sh for the $toolchain resource toolchain. Do not edit.
#
# Nothing may be written to stdout: AGP speaks the aapt2 daemon protocol over
# this process's stdin and stdout, so one stray line would corrupt the stream.
# exec keeps the file descriptors and the exit status intact.
set -euo pipefail
exec $launcher$(printf '%q' "$AAPT2_REAL") "\$@"
ENTRY
  chmod +x "$AAPT2_ENTRY.partial"
  mv -- "$AAPT2_ENTRY.partial" "$AAPT2_ENTRY"
}

# Handing Gradle the entry point through its own properties file is what keeps
# the commands a reader types identical on both hosts: no -P flag to remember,
# and nothing to remember differently depending on the machine.
write_gradle_properties() {
  local properties="$GRADLE_HOME/gradle.properties"
  touch "$properties"
  grep -v '^android\.aapt2FromMavenOverride=' "$properties" >"$properties.partial" || true
  printf 'android.aapt2FromMavenOverride=%s\n' "$AAPT2_ENTRY" >>"$properties.partial"
  mv -- "$properties.partial" "$properties"
}

write_activation() {
  cat >"$ACTIVATE.partial" <<ACTIVATION
#!/usr/bin/env bash
# Generated by setup.sh. Source it: source $ACTIVATE
export HOME="$ROOT/home"
export JAVA_HOME="$JAVA_HOME_DIR"
export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_SDK_ROOT="$ANDROID_SDK"
export GRADLE_USER_HOME="$GRADLE_HOME"
export TMPDIR="$ROOT/tmp"
export PATH="$JAVA_HOME_DIR/bin:$HOST_PREFIX/bin:/usr/bin:/bin"
unset KOTLIN_HOME
ACTIVATION
  chmod +x "$ACTIVATE.partial"
  mv -- "$ACTIVATE.partial" "$ACTIVATE"
}

# local.properties overrides the SDK environment variables, so a stale one is the
# first thing to mislead a build. It is gitignored; it is rewritten every run.
bind_checkout() {
  printf 'sdk.dir=%s\n' "$ANDROID_SDK" >"$REPO/local.properties.partial"
  mv -- "$REPO/local.properties.partial" "$REPO/local.properties"
}

# Assert the postconditions rather than trusting that the steps above ran. The
# aapt2 check is the one that matters: it is the whole reason this script exists
# on a non-x86_64 host, and it fails here rather than thirty seconds into a build.
verify() {
  local toolchain="$1"
  [[ -x "$JAVA_HOME_DIR/bin/javac" ]] || die "no JDK at $JAVA_HOME_DIR"
  [[ -x "$SDKMANAGER" ]] || die "no sdkmanager at $SDKMANAGER"
  [[ -x "$AAPT2_ENTRY" ]] || die "no aapt2 entry point at $AAPT2_ENTRY"

  "$JAVA_HOME_DIR/bin/java" -version >/dev/null 2>&1 || die "the installed JDK does not run"
  "$AAPT2_ENTRY" version >/dev/null 2>&1 ||
    die "aapt2 does not run through the $toolchain resource toolchain"
}

# --- Entry point -------------------------------------------------------------

main() {
  local reinstall=0 subdir toolchain

  while (($# > 0)); do
    case "$1" in
      --reinstall) reinstall=1 ;;
      --help | -h) usage; return 0 ;;
      *) usage >&2; die "unknown argument: $1" ;;
    esac
    shift
  done

  subdir="$(host_subdir)"
  toolchain="$(resource_toolchain "$subdir")"

  ((reinstall == 0)) || { log "removing $ROOT"; rm -rf -- "$ROOT"; }

  [[ -f "$CI_WORKFLOW" ]] || die "not a Boreas Android checkout: $CI_WORKFLOW is missing"
  [[ -f "$VERSION_CATALOG" ]] || die "not a Boreas Android checkout: $VERSION_CATALOG is missing"

  log "host $subdir, resource toolchain $toolchain, root $ROOT"
  ensure_prerequisites
  ensure_micromamba "$subdir"
  ensure_host_toolchain "$subdir" "$toolchain"
  if [[ "$toolchain" == emulated ]]; then ensure_x86_64_sysroot; fi
  ensure_android_sdk
  ensure_aapt2
  write_aapt2_entry "$toolchain"
  write_gradle_properties
  write_activation
  bind_checkout
  verify "$toolchain"

  cat <<READY

Toolchain ready under $ROOT ($subdir, $toolchain aapt2).

Run the full gate set, identical on every supported host:

  source $ACTIVATE
  ./gradlew --no-daemon :domain:test :app:testDebugUnitTest
  ./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
  ./gradlew --no-daemon :app:lintDebug :domain:lint
  ./.github/scripts/design-gate.sh
READY
}

main "$@"
