# Architecture Decision Record — APOLO Terminal Android

## Runtime topology
Android owns the UI, app-private storage and QEMU process. QEMU boots an ARM64 Linux kernel with a persistent ext4 disk containing Debian ARM64. This is a VM, not a chroot or a simulated shell.

### Android layer
- Kotlin + Jetpack Compose for navigation, VM controls and capability reporting.
- Locally bundled xterm.js in WebView; no CDN.
- Restricted terminal bridge for PTY input, output and resize only.
- QEMU system-aarch64 built with Android NDK. TCG is baseline; KVM is opportunistic only after /dev/kvm is actually opened.
- VM files remain in app-private storage.

### Guest layer
- Real Linux ARM64 guest kernel.
- Debian stable ARM64 rootfs and official APT repositories.
- Docker Engine, containerd, Buildx and Compose v2 from Docker's official Debian repository.
- Node.js LTS from the official Node.js release index, verified against SHASUMS256.
- tmux for persistent terminal sessions.

## Why QEMU TCG first
Stock Android generally does not expose KVM to ordinary apps. TCG is software CPU emulation: slower, but it boots a real Linux kernel executing real Linux syscalls. Do not assume /dev/kvm, request root, or label a chroot as a VM. A future remote provider must be separate and opt-in.

## Docker prerequisites
The guest init mounts proc, sysfs, devtmpfs and cgroup2. The guest kernel needs namespaces, cgroup v2 controllers, overlayfs, veth, bridge, bridge netfilter, conntrack, NAT, iptables-compatible netfilter, seccomp and keys. See os/kernel/docker.config.

Prefer built-in kernel features. Modules, if used, must match the exact guest kernel and be loaded inside the guest. Android host modules cannot be loaded by this app. Docker must use overlay2 and its Unix socket; never expose an unauthenticated TCP API.

## Networking
QEMU user-mode networking provides guest outbound connectivity. Docker creates bridge/veth networks inside the guest and applies NAT there. Container port forwarding to Android must be explicit and visible.

## Storage and lifecycle
- Base rootfs: immutable build artifact.
- VM disk: persistent ext4 in app-private storage.
- Docker data: /var/lib/docker on persistent disk.
- User data: /home on persistent disk.
- Updates: build a new base image, preserve data disk, verify boot, retain rollback metadata.
- Deletion distinguishes OS image from user/Docker data.

## Security
The guest is a useful isolation boundary, not a guarantee against QEMU vulnerabilities. Keep QEMU updated, avoid host directory passthrough, restrict virtual devices and never expose Docker socket. Guest docker-group membership is root-equivalent.

## Runtime states
- VM_READY_TCG / VM_READY_KVM only after QEMU starts and guest boot marker is received.
- DOCKER_READY only after docker info succeeds and reports overlay2/cgroup v2.
- UNSUPPORTED when a required host/guest capability is unavailable.
- DEGRADED when Linux boots but optional facilities fail.
