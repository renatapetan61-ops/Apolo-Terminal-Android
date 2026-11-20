# Android application shell

The Android app provides a local xterm.js terminal and a foreground VM process controller. It will only launch a real QEMU executable when all three files exist in app-private storage:

- filesDir/bin/qemu-system-aarch64
- filesDir/vm/Image
- filesDir/vm/apolo-rootfs.ext4

If any file is missing, the UI reports the missing runtime and does not create a fake shell.

## Current integration boundary

The guest kernel and ext4 image are built by the Linux VM workflow. The QEMU Android host executable is a separate NDK cross-build deliverable and is not yet bundled. The current app can be compiled, but it cannot boot the guest until that Android-compatible QEMU binary is produced and installed. Serial console is currently one shared session; independent SSH/tmux tabs are a subsequent integration step.
