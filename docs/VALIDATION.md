# APOLO Terminal acceptance tests

Run inside the booted Debian ARM64 guest as root:

    /usr/local/sbin/apolo-acceptance-test

The test verifies the actual guest kernel, namespaces, cgroup v2 controllers, OverlayFS, Node.js LTS, npm, npx, Corepack, APT package index count, Linux user creation and switching, Docker daemon, overlay2, cgroup v2, a real hello-world container, and Docker Compose v2.

A passing result ends with:

    APOLO_ACCEPTANCE_TEST=PASS

For manual diagnostics:

    uname -a
    cat /etc/os-release
    grep cgroup2 /proc/mounts
    cat /sys/fs/cgroup/cgroup.controllers
    node --version && npm --version && npx --version && corepack --version
    apt-cache pkgnames | sort -u | wc -l
    docker version
    docker info
    docker compose version
    pgrep -a dockerd
    getent passwd apolo-validation
    su - apolo-validation

The acceptance test downloads the hello-world image and refreshes APT indexes, so the guest needs outbound network access through QEMU user-mode networking. A failed network test must be diagnosed; do not mark Docker as ready based only on the client version.
