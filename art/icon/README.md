<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# App icon

`kurakagi-icon.svg` is the master (108 x 108 grid). Change it first, then remake the rest from it.

| File | What |
|---|---|
| `kurakagi-icon.svg` | Master. Navy `#1E3A8A` background, white shield outline, teal `#2DD4BF` keyhole |
| `kurakagi-icon-512.png` | 512 px, for download pages and app stores |
| `fastlane/metadata/android/*/images/icon.png` | 512 px, the same as above |
| `app/src/main/res/mipmap-*/ic_launcher.png` | Android 7.1 and older: the whole master, square. mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192 px |
| `app/src/main/res/drawable*/ic_launcher.png` | The same PNGs. The main screen shows `@drawable/ic_launcher` (title bar and the empty list), so keep them |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Android 8.0 and newer: adaptive icon |
| `app/src/main/res/drawable-v26/ic_launcher_foreground.xml` | Adaptive icon foreground: the master's shield and keyhole, scaled by 72/108 around the centre, because launchers show only the middle 72 x 72 of the layer |
| `app/src/main/res/values/ic_launcher_background.xml` | Adaptive icon background: the navy |
| `old/` | The 1.0 icon (navy gradient with a shadowed rim), kept for the record |

## Remaking the PNGs

```
bash art/icon/make-icons.sh
```

It renders the master at 2048 px with a Chromium-based browser in headless mode (Edge by default; set `EDGE=` to another one) and area-averages it down with `Downscale.java` (Java 11 or newer). With Inkscape instead:

```
inkscape art/icon/kurakagi-icon.svg --export-type=png -w 512 -h 512 --export-filename=art/icon/kurakagi-icon-512.png
```

If the shield or keyhole changes, copy the new path data into `ic_launcher_foreground.xml` by hand.
