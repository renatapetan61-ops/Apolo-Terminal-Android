# Product Requirements Document — APOLO Terminal Android

## Product
An Android app that boots and manages a persistent ARM64 Linux VM and exposes its real shell through a multi-tab terminal.

## Users
- Mobile developer running Linux tools without a PC.
- Systems learner exploring Linux users, services, filesystems and networking.
- Container developer building images and running Compose workloads.

## MVP scope
1. Create, boot, stop and restart a persistent ARM64 QEMU VM.
2. Report actual acceleration and kernel capability status.
3. Multiple terminal tabs attached to independent PTYs.
4. tmux sessions that survive terminal detach and app recreation.
5. Debian APT package manager and official repositories.
6. Node.js LTS, npm, npx and Corepack.
7. Docker Engine and Compose v2, gated by guest-kernel checks.
8. Linux users, home directories, shells, sudo and optional docker group.
9. Diagnostics that exclude passwords and secrets.

## Explicit limits
- No native Android-host Docker/cgroups claim.
- No KVM guarantee on stock Android.
- APT package availability depends on Debian release, architecture and enabled repositories. The app queries actual indexes; it does not promise a fixed count of 10,000 packages.
- No module loading into Android host kernel.
- No hidden remote execution.

## Functional requirements
| ID | Requirement | Acceptance |
|---|---|---|
| FR-01 | VM lifecycle | Guest boot marker and uname received |
| FR-02 | Multi-tab terminal | Independent PTY and process group per tab |
| FR-03 | Persistent storage | Files, packages and Docker data survive app restart |
| FR-04 | Capability report | Kernel, cgroups, namespaces, overlayfs, bridge/netfilter, disk and memory |
| FR-05 | Runtime installation | Docker, Compose, Node LTS and APT verified |
| FR-06 | User management | useradd creates passwd entry and home directory |
| FR-07 | Docker security | Unix socket only; no unauthenticated TCP |
| FR-08 | Diagnostics | Boot and daemon logs available |

## Non-functional requirements
- Reproducible builds, pinned Debian release and source checksums.
- Least privilege and app-private storage.
- Atomic OS updates with rollback; never overwrite user data.
- Configurable memory/startup limits.
- Status labels derived from live probes, not assumptions.

## Acceptance tests
- Rootfs build succeeds and records architecture, Debian suite, Node version and package-index count.
- Guest kernel config audit passes.
- VM boots on supported ARM64 Android device.
- Docker info and Compose smoke test succeed inside guest.
- Node/npm/npx/Corepack resolve.
- User creation, passwd, su and sudo work.
- VM and Docker data survive app force-stop/restart.
- Unsupported devices are accurately reported.
