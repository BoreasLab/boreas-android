#!/usr/bin/env bash
#
# Makes /dev/kvm writable by the job's user.
#
# The runner image ships the device owned by a group this user is not in. Without
# write access the emulator does not fail: it falls back to software emulation
# and boots so slowly that the job hits its timeout, which is a far worse thing
# to read a log about. Absence of the device is fatal here for the same reason.
#
# chmod rather than a udev rule. `udevadm trigger` returns before the rule has
# been applied, so the check below raced it and the same runner answered both
# ways on consecutive pushes. Nothing recreates the node inside a job, so the
# rule was guarding against something that does not happen.
#
# Assumes bash 4+, an Ubuntu runner, and passwordless sudo. Prints one line; the
# exit status is the answer.
set -euo pipefail

(($# == 0)) || {
  printf 'usage: %s\n' "${0##*/}" >&2
  exit 2
}

[[ -e /dev/kvm ]] || {
  printf '/dev/kvm is absent: this runner cannot accelerate an emulator\n' >&2
  exit 1
}

sudo chmod 0666 /dev/kvm

[[ -w /dev/kvm ]] || {
  printf '/dev/kvm is present but still not writable by %s\n' "$(id -un)" >&2
  exit 1
}

printf 'kvm is writable\n'
