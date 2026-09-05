#!/usr/bin/env bash
#
# Makes /dev/kvm writable by the job's user.
#
# The runner image ships the device owned by a group this user is not in. Without
# write access the emulator does not fail: it falls back to software emulation
# and boots so slowly that the job hits its timeout, which is a far worse thing
# to read a log about. Absence of the device is fatal here for the same reason.
#
# Assumes bash 4+, an Ubuntu runner, and passwordless sudo. Writes a udev rule
# and prints one line; the exit status is the answer.
set -euo pipefail

(($# == 0)) || {
  printf 'usage: %s\n' "${0##*/}" >&2
  exit 2
}

[[ -e /dev/kvm ]] || {
  printf '/dev/kvm is absent: this runner cannot accelerate an emulator\n' >&2
  exit 1
}

# static_node reapplies the mode to a device node created later in the job, which
# a one-shot chmod would not survive.
printf 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"\n' |
  sudo tee /etc/udev/rules.d/99-kvm-writable.rules >/dev/null
sudo udevadm control --reload-rules
sudo udevadm trigger --name-match=kvm

[[ -w /dev/kvm ]] || {
  printf '/dev/kvm is present but still not writable by %s\n' "$(id -un)" >&2
  exit 1
}

printf 'kvm is writable\n'
