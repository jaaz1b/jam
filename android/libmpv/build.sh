#!/bin/bash -e
# Builds libmpv for Android (the YouTube Music engine's audio player, same as desktop) with
# mpv-android's build scripts, plus librubberband so tempo/pitch works like on desktop.
#
# Usage: build.sh <work dir> <output .tgz>   (needs ANDROID_HOME, JDK 17, meson, ninja, nasm …)
# Output: <arch>/lib/*.so (libmpv + the FFmpeg libs it links) for arm64-v8a and x86_64.

here="$(cd "$(dirname "$0")" && pwd)"
. "$here/versions.sh"
work="$1"; out="$2"
[ -n "$work" ] && [ -n "$out" ] || { echo "usage: build.sh <work dir> <out.tgz>"; exit 1; }

rm -rf "$work"; mkdir -p "$work"
git clone -q https://github.com/mpv-android/mpv-android "$work/mpv-android"
git -C "$work/mpv-android" checkout -q "$MPV_ANDROID_REV"
cd "$work/mpv-android/buildscripts"

# SDK + NDK (reuses the runner's SDK)
mkdir -p sdk
[ -n "$ANDROID_HOME" ] && ln -sfn "$ANDROID_HOME" sdk/android-sdk-linux
IN_CI=1 WGET="wget -q" ./include/download-sdk.sh

# Sources: mpv-android's pins for the deps, ours for mpv and rubberband
IN_CI=1 WGET="wget -q" ./include/download-deps.sh
rm -rf deps/mpv && mkdir -p deps/mpv
wget -q "https://github.com/mpv-player/mpv/archive/$MPV_REV.tar.gz" -O - | tar -xz -C deps/mpv --strip-components=1
mkdir -p deps/rubberband
wget -q "https://github.com/breakfastquay/rubberband/archive/refs/tags/v$RUBBERBAND_VER.tar.gz" -O - | \
	tar -xz -C deps/rubberband --strip-components=1
cp "$here/rubberband.sh" scripts/rubberband.sh
sed -i 's/^dep_mpv=(\(.*\))$/dep_mpv=(\1 rubberband)\ndep_rubberband=()/' include/depinfo.sh
grep -q 'rubberband' include/depinfo.sh

for arch in $ARCHS; do
	echo "==> libmpv ($arch)"
	./buildall.sh --arch "$arch" mpv || {
		cat "deps/mpv/_build_$arch/meson-logs/meson-log.txt" 2>/dev/null | tail -80
		exit 1
	}
done

# Collect the shared libraries per Android ABI, plus the NDK's libc++_shared that libmpv needs.
ndk="$(realpath "android-ndk-$(. include/depinfo.sh; echo $v_ndk)")"
pkg="$work/pkg"; rm -rf "$pkg"; mkdir -p "$pkg"
for arch in $ARCHS; do
	case "$arch" in
		arm64) abi=arm64-v8a; triple=aarch64-linux-android ;;
		x86_64) abi=x86_64; triple=x86_64-linux-android ;;
	esac
	mkdir -p "$pkg/$abi"
	cp -L prefix/$arch/lib/lib{mpv,avcodec,avfilter,avformat,avutil,swresample,swscale}.so "$pkg/$abi/"
	[ -f prefix/$arch/lib/libavdevice.so ] && cp -L prefix/$arch/lib/libavdevice.so "$pkg/$abi/"
	cp "$ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$triple/libc++_shared.so" "$pkg/$abi/"
	echo "--- $abi"; ls -la "$pkg/$abi"
	"$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -d "$pkg/$abi/libmpv.so" | grep NEEDED || true
	strings "$pkg/$abi/libmpv.so" | grep -q 'rubberband' && echo "rubberband: yes" || echo "rubberband: NO"
done
mkdir -p "$pkg/include"; cp -r prefix/arm64/include/mpv "$pkg/include/"
tar -czf "$out" -C "$pkg" .
echo "wrote $out"
