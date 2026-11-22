#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Remakes every PNG icon from art/icon/kurakagi-icon.svg (see art/icon/README.md).
# Needs: a Chromium-based browser for rendering (EDGE=/path/to/browser to override) and Java 11+.
# Run from the repository root: bash art/icon/make-icons.sh
set -euo pipefail

BROWSER="${EDGE:-/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe}"
RES=app/src/main/res
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

to_url() { echo "file:///$(cygpath -m "$1" 2>/dev/null || echo "$1")"; }

# 1. Render the master at 2048 px (opaque: the background is part of the master).
cat > "$TMP/page.html" <<EOF
<html><body style="margin:0"><img src="$(to_url "$PWD/art/icon/kurakagi-icon.svg")" width="2048" height="2048" style="display:block"></body></html>
EOF
"$BROWSER" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --window-size=2048,2048 --screenshot="$(cygpath -m "$TMP/master.png" 2>/dev/null || echo "$TMP/master.png")" \
    "$(to_url "$TMP/page.html")" >/dev/null 2>&1

# 2. Area-average down to every size.
java art/icon/Downscale.java "$TMP/master.png" \
    512 art/icon/kurakagi-icon-512.png \
    512 fastlane/metadata/android/en-US/images/icon.png \
    512 fastlane/metadata/android/ja-JP/images/icon.png \
    48  $RES/mipmap-mdpi/ic_launcher.png \
    72  $RES/mipmap-hdpi/ic_launcher.png \
    96  $RES/mipmap-xhdpi/ic_launcher.png \
    144 $RES/mipmap-xxhdpi/ic_launcher.png \
    192 $RES/mipmap-xxxhdpi/ic_launcher.png \
    48  $RES/drawable-mdpi/ic_launcher.png \
    72  $RES/drawable-hdpi/ic_launcher.png \
    96  $RES/drawable-xhdpi/ic_launcher.png \
    144 $RES/drawable-xxhdpi/ic_launcher.png \
    192 $RES/drawable-xxxhdpi/ic_launcher.png \
    96  $RES/drawable/ic_launcher.png
echo "[OK] icons remade"
