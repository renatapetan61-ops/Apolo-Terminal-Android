# Phase 2 — Debian ARM64 rootfs starter

Builds a real Debian ARM64 root filesystem with APT, Docker Engine, Compose v2, Node.js LTS, tmux, shells and user administration.

## Build
Requires Docker Buildx and binfmt/QEMU support on the Linux build host:

    docker buildx build --platform linux/arm64 --output type=local,dest=../out .

Output: rootfs.tar.gz and build-info.txt. This is a rootfs archive, not a bootable VM disk. The Android QEMU phase adds a compatible guest kernel and ext4 disk.

## Reproducibility
Set DEBIAN_SUITE to a supported Debian suite (default trixie). For release builds, pin the base image by digest and use a snapshot of APT metadata. Node LTS is selected from the official Node.js index and verified against the official SHA256 manifest.

## Important
A successful rootfs build does not prove Docker can run. Docker readiness depends on guest kernel configuration and runtime mounts. Run /usr/local/sbin/apolo-kernel-check inside the booted guest.
