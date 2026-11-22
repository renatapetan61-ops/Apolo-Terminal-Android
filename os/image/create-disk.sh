#!/usr/bin/env bash
set -Eeuo pipefail
ROOTFS_TAR="${1:?usage: create-disk.sh rootfs.tar.gz kernel-Image output-dir [size]}"
KERNEL_IMAGE="${2:?missing kernel Image}"
OUT_DIR="${3:?missing output directory}"
DISK_SIZE="${4:-6G}"
ROOTFS_DIR="$OUT_DIR/rootfs"
mkdir -p "$ROOTFS_DIR"
sudo tar --numeric-owner --xattrs --acls -xzf "$ROOTFS_TAR" -C "$ROOTFS_DIR"
sudo mkdir -p "$ROOTFS_DIR"/{proc,sys,dev,dev/pts,run,tmp}
sudo chmod 1777 "$ROOTFS_DIR/tmp"
sudo install -m 0644 "$KERNEL_IMAGE" "$OUT_DIR/Image"
truncate -s "$DISK_SIZE" "$OUT_DIR/apolo-rootfs.ext4"
sudo mkfs.ext4 -F -q -L APOLO_ROOT -d "$ROOTFS_DIR" "$OUT_DIR/apolo-rootfs.ext4"
sudo e2fsck -fn "$OUT_DIR/apolo-rootfs.ext4"
sha256sum "$OUT_DIR/Image" "$OUT_DIR/apolo-rootfs.ext4" > "$OUT_DIR/SHA256SUMS"
