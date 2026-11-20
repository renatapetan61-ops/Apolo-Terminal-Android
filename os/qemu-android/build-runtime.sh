#!/usr/bin/env bash
set -Eeuo pipefail

# Run inside the official termux/package-builder container from the
# pinned termux/termux-packages checkout.
./build-package.sh -I qemu-system-x86-64-headless

source scripts/properties.sh
PREFIX="${TERMUX__PREFIX}"
OUT="$PWD/output"
STAGE="$OUT/apolo-qemu-runtime"
DEB="$(find "$OUT" -maxdepth 1 -type f -name 'qemu-system-aarch64-headless_*_aarch64.deb' -print -quit)"

if [ -z "$DEB" ]; then
  echo "qemu-system-aarch64-headless .deb was not produced." >&2
  find "$OUT" -maxdepth 1 -type f -name '*.deb' -printf '%f\n' >&2 || true
  exit 1
fi

rm -rf "$STAGE"
mkdir -p "$STAGE/bin" "$STAGE/lib" "$STAGE/share"
dpkg-deb -x "$DEB" "$STAGE"
test -s "$STAGE/bin/qemu-system-aarch64"

# Bundle the actual runtime libraries from the dependency prefix. Dereference
# symlinks so the ZIP remains portable and does not depend on symlink metadata.
for library in "$PREFIX"/lib/*.so*; do
  [ -f "$library" ] || continue
  cp -L "$library" "$STAGE/lib/$(basename "$library")"
done

if [ -d "$PREFIX/share/qemu" ]; then
  cp -RL "$PREFIX/share/qemu" "$STAGE/share/qemu"
fi

# NDK libc++ runtime, when QEMU's ELF dependency table requires it.
if [ -n "${NDK:-}" ] && [ -f "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" ]; then
  cp "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$STAGE/lib/"
fi

readelf -h "$STAGE/bin/qemu-system-aarch64" | grep -q 'AArch64'
readelf -l "$STAGE/bin/qemu-system-aarch64" | grep -q '/system/bin/linker64'
readelf -d "$STAGE/bin/qemu-system-aarch64" > "$STAGE/qemu-needed-libraries.txt"
while IFS= read -r needed; do
  case "$needed" in
    libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libz.so|libjnigraphics.so|libnativewindow.so|libvulkan.so|libEGL.so)
      continue
      ;;
  esac
  if [ ! -s "$STAGE/lib/$needed" ]; then
    echo "Missing Android QEMU runtime dependency: $needed" >&2
    exit 1
  fi
done < <(sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p' "$STAGE/qemu-needed-libraries.txt")
{
  echo "qemu_source_version=11.1.3"
  echo "termux_packages_commit=$(git rev-parse HEAD)"
  echo "target=aarch64-android-bionic"
  echo "accelerator=tcg"
  echo "binary=qemu-system-aarch64"
  echo "binary_sha256=$(sha256sum "$STAGE/bin/qemu-system-aarch64" | awk '{print $1}')"
} > "$STAGE/build-info.txt"

cd "$OUT"
rm -f apolo-qemu-android-arm64.zip
zip -qr apolo-qemu-android-arm64.zip apolo-qemu-runtime
sha256sum apolo-qemu-android-arm64.zip > apolo-qemu-android-arm64.zip.sha256
test -s apolo-qemu-android-arm64.zip
