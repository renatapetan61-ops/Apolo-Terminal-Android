#!/usr/bin/env bash
set -Eeuo pipefail
KERNEL="${1:?usage: qemu-smoke-test.sh Image rootfs.ext4 [log]}"
DISK="${2:?missing ext4 disk}"
LOG="${3:-qemu-boot.log}"
QEMU="${QEMU_SYSTEM_AARCH64:-qemu-system-aarch64}"
command -v "$QEMU" >/dev/null
: > "$LOG"

coproc APOLO_QEMU { exec timeout 600s "$QEMU" \
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
  local pattern="$1" timeout_seconds="$2" line
  local deadline=$((SECONDS + timeout_seconds))
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
cat >&"$QEMU_IN" <<'GUEST_COMMANDS'
set -e
set -o pipefail
echo APOLO_SMOKE_SHELL_READY
uname -a
node --version
npm --version
npx --version
corepack --version
docker compose version
apt-get update
APT_COUNT=$(apt-cache pkgnames | sort -u | wc -l)
echo "APOLO_APT_COUNT=$APT_COUNT"
test "$APT_COUNT" -ge 10000
apolo-useradd apolotest --sudo
su - apolotest -c 'test "$HOME" = /home/apolotest && id && echo APOLO_USER_SWITCH_OK'
for attempt in $(seq 1 90); do
  docker info >/tmp/apolo-docker-info 2>&1 && break
  sleep 2
done
docker info --format '{{.Driver}}|{{.CgroupVersion}}' | grep -qx 'overlay2|2'
echo APOLO_DOCKER_INFO_OK
docker run --rm hello-world | grep -q 'Hello from Docker!'
echo APOLO_DOCKER_RUN_OK
cat >/tmp/apolo-compose.yaml <<'YAML'
services:
  smoke:
    image: hello-world
YAML
docker compose -f /tmp/apolo-compose.yaml up --abort-on-container-exit --exit-code-from smoke
docker compose -f /tmp/apolo-compose.yaml down
echo APOLO_COMPOSE_OK
echo APOLO_SMOKE_DONE
GUEST_COMMANDS

record_until "APOLO_SMOKE_DONE" 540 || {
  echo "Guest validation did not finish. Recent Docker logs:" | tee -a "$LOG"
  printf 'tail -n 120 /var/log/dockerd.log\n' >&"$QEMU_IN"
  exit 1
}
for marker in APOLO_SMOKE_SHELL_READY APOLO_USER_SWITCH_OK APOLO_DOCKER_INFO_OK APOLO_DOCKER_RUN_OK APOLO_COMPOSE_OK; do
  grep -q "$marker" "$LOG" || { echo "Missing validation marker: $marker" >&2; exit 1; }
done
grep -Eq 'APOLO_APT_COUNT=([1-9][0-9]{4,})' "$LOG"
echo "QEMU Linux + APT + Node + users + Docker Engine + Compose smoke tests passed."
