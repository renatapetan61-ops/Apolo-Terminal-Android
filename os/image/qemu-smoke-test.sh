#!/usr/bin/env bash
set -Eeuo pipefail
KERNEL="${1:?usage: qemu-smoke-test.sh Image rootfs.ext4}"
DISK="${2:?missing ext4 disk}"
LOG="${3:-qemu-boot.log}"
QEMU="${QEMU_SYSTEM_AARCH64:-qemu-system-aarch64}"
command -v "$QEMU" >/dev/null
set +e
timeout 180s "$QEMU" \
  -machine virt -cpu cortex-a57 -m 2048 -smp 2 \
  -kernel "$KERNEL" \
  -drive if=none,id=rootdisk,file="$DISK",format=raw \
  -device virtio-blk-device,drive=rootdisk \
  -netdev user,id=net0 -device virtio-net-device,netdev=net0 \
  -nographic -monitor none -serial stdio \
  -append "console=ttyAMA0,115200 root=/dev/vda rw rootwait init=/sbin/apolo-init panic=10" \
  < <(printf 'echo APOLO_SMOKE_SHELL_READY\nuname -a\nnode --version\ndocker compose version\n') >"$LOG" 2>&1
status=$?
set -e
cat "$LOG"
grep -q 'APOLO_GUEST_BOOTED' "$LOG"
grep -q 'APOLO_SMOKE_SHELL_READY' "$LOG"
grep -q 'Linux' "$LOG"
grep -q '^v[0-9]' "$LOG" || grep -Eq 'v[0-9]+\.' "$LOG"
echo "QEMU guest boot smoke test passed (qemu exit=$status)"
