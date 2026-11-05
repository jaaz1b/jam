#!/bin/bash -e
# mpv-android-style build script for librubberband (static), so mpv gets af=rubberband.

. ../../include/path.sh

build=_build$ndk_suffix

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf $build
	exit 0
else
	exit 255
fi

unset CC CXX
meson setup $build --cross-file "$prefix_dir"/crossfile.txt \
	-Dfft=builtin -Dresampler=builtin \
	-Djni=disabled -Dladspa=disabled -Dlv2=disabled -Dvamp=disabled -Dcmdline=disabled -Dtests=disabled

ninja -C $build -j$cores
DESTDIR="$prefix_dir" ninja -C $build install

# C++ inside a static lib: whoever links it needs the C++ runtime too.
${SED:-sed} '/^Libs:/ s|$| -lc++|' "$prefix_dir/lib/pkgconfig/rubberband.pc" -i
