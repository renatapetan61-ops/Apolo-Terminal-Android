# Guest kernel build

The baseline is the official Linux stable long-term branch 6.18.64 from kernel.org, cross-compiled for ARM64. kernel.org lists 6.18 as longterm maintenance through December 2028; the exact patch release is pinned for repeatable builds. See https://www.kernel.org/releases.html.

On Ubuntu/Debian x86_64:

    sudo apt-get update
    sudo apt-get install -y git make gcc-aarch64-linux-gnu binutils-aarch64-linux-gnu bc bison flex libssl-dev libelf-dev dwarves
    KERNEL_VERSION=6.18.64 ./os/kernel/build-kernel.sh

The build emits Image, kernel.config, source-commit.txt and build-info.txt. The kernel is configured for QEMU virt with PL011 serial, virtio-mmio block/network, ext4 root and Docker's namespaces/cgroups/overlayfs/network requirements.

Do not use this as a device-vendor Android kernel. It is a generic QEMU ARM64 guest kernel. The Android app still needs a QEMU build and lifecycle integration.
