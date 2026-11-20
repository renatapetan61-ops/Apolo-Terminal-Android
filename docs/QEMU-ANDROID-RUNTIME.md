# Android QEMU host runtime

The Android app needs a QEMU executable compiled for Android's ARM64/Bionic userspace. A Linux x86_64 QEMU binary cannot run as an Android app process, and a Linux guest kernel image is not itself the QEMU host executable.

## Build source

The runtime build uses the official Termux package recipe for QEMU, pinned to termux/termux-packages commit:

    0134674a8ea0cccea4bebbe6ac43750b46f16194

The package recipe builds QEMU 11.1.3 for Android ARM64/Bionic and includes the aarch64-softmmu target. It is built in Termux's official package-builder container with dependencies downloaded from Termux package repositories. The build recipe verifies the upstream QEMU source SHA256.

## Artifact layout

The build emits apolo-qemu-android-arm64.zip:

    apolo-qemu-runtime/
    ├── bin/qemu-system-aarch64
    ├── lib/*.so*
    ├── share/qemu/       (when provided by qemu-common)
    ├── build-info.txt
    └── qemu-needed-libraries.txt

The app imports this ZIP into app-private storage. It verifies the executable exists, sets its executable bit and sets LD_LIBRARY_PATH to the bundled libraries before launching it.

## Runtime mode

The app explicitly requests QEMU TCG. KVM is not assumed on stock Android and will not be enabled just because a QEMU binary was compiled with KVM support. The QEMU process runs inside the Android app sandbox.

## Security and portability

Only the required QEMU binary and runtime libraries are bundled. The VM disk and kernel are imported separately. No host directories are passed through to the guest. The Docker API remains inside the guest and is not exposed on Android host ports.
