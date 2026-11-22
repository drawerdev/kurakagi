<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# App icon

`kurakagi-master-1024.png` is the master. It is a raster image, not an SVG: the storehouse has a washi-paper texture and the key has a gradient, so there is no vector source. Make every other icon file from the master and the two layer PNGs.

The icon: a cream storehouse (`#F7F5EF`, washi texture) with a navy keyhole on a dark navy `#16233C` background, and a slanted key with a `#1BBDF6` to `#22DFAA` gradient sticking out at the top left.

| File | What |
|---|---|
| `kurakagi-master-1024.png` | Master, 1024 x 1024 |
| `kurakagi-icon-512.png` | 512 px, for download pages and app stores |
| `kurakagi-layer-bg-1024.png` | Adaptive icon background layer, 1024 x 1024, solid `#16233C` |
| `kurakagi-layer-fg-1024.png` | Adaptive icon foreground layer, 1024 x 1024, transparent outside the storehouse and key. Everything fits inside the launcher's safe zone (the middle 66 %) |
| `fastlane/metadata/android/*/images/icon.png` | 512 px, the same as `kurakagi-icon-512.png` |
| `app/src/main/res/mipmap-*/ic_launcher.png` | Android 7.1 and older: the whole master, square. mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192 px |
| `app/src/main/res/drawable*/ic_launcher.png` | The same PNGs. The main screen shows `@drawable/ic_launcher` (title bar and the empty list), so keep them |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Android 8.0 and newer: adaptive icon |
| `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` | Adaptive icon foreground: the same file as `kurakagi-layer-fg-1024.png` |
| `app/src/main/res/values/ic_launcher_background.xml` | Adaptive icon background: the colour `#16233C` |
| `old/` | Earlier icons, kept for the record: the 1.0 icon (navy gradient with a shadowed rim) and the unused flat navy shield (`kurakagi-icon.svg` with its `make-icons.sh` and `Downscale.java`). Do not run `old/make-icons.sh`: it was written for the shield and would overwrite the current icons |

## Changing the icon

Change the master and the two layers first, then scale the master down to every size in the table with an area-averaging resize. Do not add a vector `ic_launcher_foreground.xml` (in `drawable-v26/` or anywhere else): Android picks it over the PNG.
