#!/usr/bin/env bash
set -Eeuo pipefail
KERNEL="${1:?usage: qemu-smoke-test.sh Image rootfs.ext4 [log]}"
DISK="${2:?missing ext4 disk}"
LOG="${3:-qemu-boot.log}"
QEMU="${QEMU_SYSTEM_AARCH64:-qemu-system-aarch64}"
command -v "$QEMU" >/dev/null
: > "$LOG"

coproc APOLO_QEMU { timeout 300s "$QEMU" \
  -machine virt -cpu cortex-a57 -m 2048 -smp 2 \
  -kernel "$KERNEL" \
  -drive if=none,id=rootdisk,file="$DISK",format=raw \
  -device virtio-blk-device,drive=rootdisk \
  -netdev user,id=net0 -device virtio-net-device,netdev=net0 \
  -nographic -monitor none -serial stdio \
  -append "console=ttyAMA0,115200 root=/dev/vda rw rootwait init=/sbin/apolo-init panic=10"; }
QEMU_PID="$APOLO_QEMU_PID"
exec {QEMU_IN}>&"${APOLO_QEMU[1]}"
exec {QEMU_OUT}<&"${APOLO_QEMU[0]}"

record_until() {
  local pattern="$1" timeout_seconds="$2" deadline=$((SECONDS + timeout_seconds)) line
  while (( SECONDS < deadline )); do
    if IFS= read -r -t 1 line <&"$QEMU_OUT"; then
      printf '%s\n' "$line" | tee -a "$LOG"
      [[ "$line" == *"$pattern"* ]] && return 0
    fi
    kill -0 "$QEMU_PID" 2>/dev/null || break
  done
  return 1
}

cleanup() {
  kill "$QEMU_PID" 2>/dev/null || true
  wait "$QEMU_PID" 2>/dev/null || true
}
trap cleanup EXIT

record_until "APOLO_GUEST_BOOTED" 180 || { echo "Guest did not reach init marker" >&2; exit 1; }
sleep 3
printf 'echo APOLO_SMOKE_SHELL_READY\nuname -a\nnode --version\ndocker compose version\n' >&"$QEMU_IN"
record_until "APOLO_SMOKE_SHELL_READY" 30 || { echo "Guest shell did not accept commands" >&2; exit 1; }
record_until "Linux" 10 || true
record_until "APOLO_SMOKE_DONE" 2 || true
printf 'echo APOLO_SMOKE_DONE\n' >&"$QEMU_IN"
record_until "APOLO_SMOKE_DONE" 10 || true
echo "QEMU guest kernel/rootfs boot smoke test passed."
