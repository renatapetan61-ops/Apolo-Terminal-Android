#!/usr/bin/env bash
set -Eeuo pipefail

KERNEL_VERSION="${KERNEL_VERSION:-6.18.64}"
WORK_DIR="${WORK_DIR:-$PWD/.kernel-build}"
OUT_DIR="${OUT_DIR:-$PWD/out/kernel}"
SRC_DIR="$WORK_DIR/linux-$KERNEL_VERSION"
JOBS="${JOBS:-$(nproc)}"

mkdir -p "$WORK_DIR" "$OUT_DIR"
if [ ! -d "$SRC_DIR/.git" ]; then
  git clone --depth 1 --branch "v$KERNEL_VERSION" https://git.kernel.org/pub/scm/linux/kernel/git/stable/linux.git "$SRC_DIR"
fi
test "$(git -C "$SRC_DIR" describe --tags --exact-match HEAD)" = "v$KERNEL_VERSION"

make -C "$SRC_DIR" ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- O="$OUT_DIR" defconfig
"$SRC_DIR/scripts/kconfig/merge_config.sh" -m -O "$OUT_DIR" "$OUT_DIR/.config" "$PWD/os/kernel/docker.config"
make -C "$SRC_DIR" ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- O="$OUT_DIR" olddefconfig

# QEMU virt machine support: PL011 serial console, virtio-mmio block/network and ext4 root disk.
"$SRC_DIR/scripts/config" --file "$OUT_DIR/.config" \
  --enable ARM64 \
  --enable PRINTK --enable SERIAL_AMBA_PL011 --enable SERIAL_AMBA_PL011_CONSOLE \
  --enable VIRTIO --enable VIRTIO_MMIO --enable VIRTIO_BLK --enable VIRTIO_NET \
  --enable EXT4_FS --enable DEVTMPFS --enable DEVTMPFS_MOUNT \
  --enable IKCONFIG --enable IKCONFIG_PROC --enable CGROUP2_FS \
  --enable PROC_FS --enable SYSFS --enable TMPFS --enable TMPFS_POSIX_ACL \
  --enable NET --enable INET --enable PACKET --enable UNIX --enable TUN \
  --enable IP_NF_IPTABLES --enable IP_NF_FILTER --enable IP_NF_NAT \
  --enable NETFILTER --enable NF_CONNTRACK --enable NF_NAT \
  --enable NF_TABLES --enable NF_TABLES_INET --enable NFT_CT --enable NFT_NAT \
  --enable NFT_MASQ --enable NFT_REJECT --enable NFT_COUNTER --enable NFT_COMPAT \
  --enable NETFILTER_XTABLES --enable NETFILTER_XT_MATCH_ADDRTYPE \
  --enable NETFILTER_XT_MATCH_CONNTRACK --enable NETFILTER_XT_TARGET_MASQUERADE
make -C "$SRC_DIR" ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- O="$OUT_DIR" olddefconfig
make -C "$SRC_DIR" ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- O="$OUT_DIR" -j"$JOBS" Image

test -s "$OUT_DIR/arch/arm64/boot/Image"
for option in CONFIG_NAMESPACES CONFIG_USER_NS CONFIG_PID_NS CONFIG_NET_NS CONFIG_CGROUPS CONFIG_CGROUP2_FS CONFIG_CGROUP_PIDS CONFIG_MEMCG CONFIG_OVERLAY_FS CONFIG_VETH CONFIG_BRIDGE CONFIG_BRIDGE_NETFILTER CONFIG_VIRTIO_MMIO CONFIG_VIRTIO_BLK CONFIG_SERIAL_AMBA_PL011_CONSOLE CONFIG_EXT4_FS; do
  grep -qE "^$option=y$" "$OUT_DIR/.config" || { echo "Missing required kernel option: $option" >&2; exit 1; }
done
cp "$OUT_DIR/arch/arm64/boot/Image" "$OUT_DIR/Image"
cp "$OUT_DIR/.config" "$OUT_DIR/kernel.config"
git -C "$SRC_DIR" rev-parse HEAD > "$OUT_DIR/source-commit.txt"
printf 'kernel_version=%s\nsource_commit=%s\n' "$KERNEL_VERSION" "$(cat "$OUT_DIR/source-commit.txt")" > "$OUT_DIR/build-info.txt"
