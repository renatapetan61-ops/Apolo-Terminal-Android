# Android application shell

The Android app provides a locally bundled xterm.js terminal, a foreground VM process controller and import controls for the runtime files.

## Import files

1. Import QEMU: select the ZIP artifact named `apolo-qemu-android-arm64.zip`. The app extracts the Android/Bionic executable and its shared libraries into app-private storage.
2. Import Kernel: select the guest `Image` produced by the Linux VM build.
3. Import Disk: select `apolo-rootfs.ext4` produced by the Linux VM build.
4. Tap **Iniciar Linux**.

The QEMU runtime ZIP is built by the pinned Termux package recipe. It is not a Linux x86_64 executable. The app launches it with an explicit TCG accelerator and sets its library path to the imported runtime libraries.

## Runtime checks

Expected app-private paths:

- filesDir/bin/qemu-system-aarch64
- filesDir/lib/*.so*
- filesDir/vm/Image
- filesDir/vm/apolo-rootfs.ext4

If any required file is missing, the UI reports the missing component and does not create a fake shell.

## Current integration boundary

The Android app source and build workflow are present. The QEMU Android runtime, Linux kernel and ext4 guest disk are separate CI artifacts that must be successfully built and imported. The app's current terminal is one serial-console session; independent SSH/tmux tabs are the next feature after the VM boot is verified on a real Android device.
