# APOLO Terminal Android

A real Linux virtual machine for Android, with an interactive terminal. This project distinguishes guest Linux capabilities from capabilities of the Android host.

## Project status

- [x] Architecture and product requirements documented.
- [x] Reproducible Debian ARM64 rootfs build starter.
- [ ] Android QEMU runtime integration.
- [ ] Guest kernel build, signed VM image, and on-device acceptance tests.

This is not a chroot or a simulated command runner. The target is a Linux ARM64 guest kernel booted by QEMU. Docker runs inside that guest.

## Critical Android constraint

Most stock Android 16 devices do not expose /dev/kvm to ordinary apps. The reliable baseline is QEMU TCG software emulation, not KVM. TCG boots a real Linux kernel but is slower. Docker inside the guest requires a guest kernel with namespaces, cgroups v2, overlayfs, bridge networking, veth and netfilter. It does not require Android's host kernel to expose these features to the app.

The app must report VM_READY_TCG, VM_READY_KVM, or UNSUPPORTED based on runtime checks. Docker is not READY until the guest daemon answers docker info.

## Architecture

    Android 16 (unrooted)
    └── APOLO Terminal Android (Kotlin + Jetpack Compose)
        ├── VM lifecycle / storage / capability manager
        ├── Terminal UI (xterm.js in a local WebView)
        ├── PTY + WebView bridge (strictly local, allowlisted)
        └── QEMU ARM64 process (NDK build; TCG baseline, KVM opportunistic)
            └── Linux ARM64 guest kernel
                └── Debian stable ARM64 root filesystem (ext4 image)
                    ├── minimal init / VM supervisor
                    ├── APT official Debian repositories
                    ├── Docker Engine + Compose v2 plugin
                    ├── Node.js current LTS + npm/npx/Corepack
                    ├── Bash, Zsh, tmux, sudo, shadow
                    └── persistent users, homes and /var/lib/docker

### Technology decisions

| Area | Decision |
|---|---|
| Android UI | Kotlin, Jetpack Compose, Android WebView |
| Terminal renderer | xterm.js bundled locally; no CDN |
| VM | QEMU system-aarch64; TCG default, KVM only if accessible |
| Guest OS | Debian stable ARM64 minimal rootfs |
| Packages | APT + official Debian repositories |
| Containers | Docker Engine daemon and Compose v2 inside guest |
| Persistence | App-private VM disk and persistent Docker data |
| Sessions | tmux in guest; reconnect to named sessions |
| Security | Android app sandbox, VM boundary, no unauthenticated host ports |

## Product requirements (PRD)

### Goals
1. Boot a genuine ARM64 Linux kernel in a VM from Android.
2. Provide an interactive shell with PTY semantics, signals, resize, UTF-8 and job control.
3. Persist guest filesystem, users, packages, Docker images/volumes and tmux sessions.
4. Run Docker Engine and Compose v2 when guest kernel prerequisites pass.
5. Install Node.js LTS with node, npm, npx and Corepack.
6. Support Linux users, groups, home directories, sudo and Bash/Zsh.
7. Make VM capabilities and health observable.

### Explicit limits
- No claim of native Android-host Docker or cgroups.
- No guarantee of KVM on stock Android.
- Package count depends on the Debian release, architecture and enabled official repositories; the app must query the real APT index rather than promise a fixed count.
- No kernel module loading from an unprivileged Android app. Modules must be built into the guest kernel or shipped as matching guest modules.
- No hidden remote execution. A future remote backend must be separate and opt-in.

### Functional requirements
- FR-01: Create, boot, stop, restart and delete a VM with confirmation for destructive operations.
- FR-02: Attach terminal tabs to independent PTYs and reconnect to tmux sessions.
- FR-03: Keep VM disk and Docker data across app restarts and upgrades.
- FR-04: Report CPU acceleration, guest kernel, cgroups, namespaces, overlayfs, bridge/netfilter, disk and memory.
- FR-05: Install and verify Docker Engine, Compose plugin, Node LTS and APT.
- FR-06: Create users with unique UID/GID, home, shell, password setup and optional sudo/docker group membership.
- FR-07: Never expose the Docker TCP API on 0.0.0.0. Use the Unix socket inside the guest.
- FR-08: Provide boot and daemon diagnostics.

### Non-functional requirements
- Reproducible builds, pinned Debian release, source checksums and build metadata.
- Least privilege, app-private storage and no embedded credentials.
- Atomic image updates with rollback; preserve user data.
- Configurable memory and startup limits.
- All status labels derived from actual guest probes.

## Docker guest-kernel requirements

The guest kernel must enable:
- CONFIG_NAMESPACES, CONFIG_UTS_NS, CONFIG_IPC_NS, CONFIG_USER_NS, CONFIG_PID_NS, CONFIG_NET_NS
- CONFIG_CGROUPS, CONFIG_CGROUP_PIDS, CONFIG_CGROUP_CPUACCT, CONFIG_CGROUP_DEVICE, CONFIG_CGROUP_FREEZER, CONFIG_CGROUP_SCHED, CONFIG_MEMCG
- CONFIG_OVERLAY_FS, CONFIG_VETH, CONFIG_BRIDGE, CONFIG_BRIDGE_NETFILTER
- CONFIG_NF_NAT, CONFIG_NF_CONNTRACK, CONFIG_NETFILTER, CONFIG_IP_NF_FILTER, CONFIG_IP_NF_NAT
- CONFIG_SECCOMP, CONFIG_SECCOMP_FILTER, CONFIG_KEYS

Prefer built-in guest-kernel features. If modular, ship matching modules and load them inside the guest before dockerd. Android host modules are unrelated.

Use cgroup v2 unified hierarchy mounted at /sys/fs/cgroup. This starter uses Docker cgroupfs driver because it does not assume systemd is running.

## Phase 2 build

On a Linux build machine with Docker BuildKit:

    git clone https://github.com/renatapetan61-ops/Apolo-Terminal-Android
    cd Apolo-Terminal-Android
    docker buildx build --platform linux/arm64 --output type=local,dest=out ./os

This produces a Debian ARM64 rootfs tarball and metadata. It is a rootfs layer, not yet a bootable VM disk. A later phase adds an ext4 disk, guest kernel and Android QEMU runtime.

See docs/ARCHITECTURE.md, docs/PRD.md and os/README.md.

## Security

The Docker group grants root-equivalent control inside the guest. It is optional and must carry a warning. Never publish the Docker socket. Do not mount arbitrary host paths into an untrusted guest. QEMU must use app-private paths and a restricted network/device configuration.

## License

Select a license before public distribution.
