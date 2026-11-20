# Build status — 2026-11-20

## Guest kernel target

- Upstream: official Linux stable tree on kernel.org.
- Pinned tag: v6.18.64 (longterm series 6.18).
- Target: ARM64 QEMU virt machine.
- CPU: Cortex-A57 under TCG for CI smoke test.
- Devices: PL011 serial, virtio-mmio block and network.
- Root filesystem: Debian trixie ARM64, ext4.
- Kernel requirements: namespaces, cgroup v2, overlayfs, veth/bridge/netfilter, seccomp, virtio and ext4.

Kernel.org's release index identifies 6.18 as a longterm-maintained series. Verify current patch updates before every production release: https://www.kernel.org/releases.html.

## Pipeline

1. Build Debian ARM64 rootfs and install Docker Engine, Compose v2, Node.js LTS and userland.
2. Cross-compile the pinned Linux ARM64 kernel.
3. Populate a persistent ext4 disk from the rootfs archive.
4. Boot the disk and kernel with QEMU TCG on GitHub Actions.
5. Upload kernel, disk, checksums and boot log only after the smoke test passes.

## Current validation

The first CI attempts exposed a script executable-bit problem in the kernel build job. The workflow now invokes the script through Bash, so executable mode is not required. The next run must confirm the cross-compile and rootfs build; no VM artifact is considered validated until the QEMU boot smoke test passes.

## Runtime warning

The generic QEMU guest kernel is not a Motorola/Android host kernel. The Android app must run QEMU in its own process and use TCG unless KVM access is proven. Docker readiness is determined inside the guest, not inferred from Android capabilities.
