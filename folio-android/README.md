# Folio: infinite-canvas sketchbook for Android

A fast, native drawing app built for the **Xiaomi Pad + Xiaomi Focus Pen / Focus Pen Pro**
(and any Android phone or tablet with a stylus). Kotlin, zero third-party dependencies, ~300 KB APK.

## Install

1. Open the latest **Folio Android** run under GitHub → Actions and download the `Folio-apk` artifact.
2. Copy `Folio.apk` to the phone/tablet and open it (allow "Install unknown apps" for your file manager).
3. On first pen touch Folio switches to *pen draws, fingers navigate* automatically.

Use **Pen Lab** (top bar or gallery) once with your Focus Pen to see what it reports, then tune
pressure and palm rejection in **Settings**.

## Features

| Area | What's there |
|---|---|
| Canvas | Infinite canvas, infinite zoom (10⁻⁶× – 10⁶×) with double-precision coordinates, two-finger rotate with 90° snapping, animated zoom-to-fit / reset |
| Brushes | Pen, Fineliner, Technical, Pencil (paper grain + tilt shading), Marker (chisel), Highlighter (multiply), Ink Brush, Calligraphy (flat nib), Airbrush, Watercolor (wet edges), Charcoal, Crayon, Fill |
| Pro brush settings | Size, opacity, smoothing, pressure→size, pressure→opacity, pressure curve, min size, tilt→size, speed thinning, taper start/end, nib roundness/angle, grain, flow, spacing, size/opacity/angle jitter, scatter, wet edges, stamp rotation, blend mode |
| Color | Hue ring + saturation/brightness square, HSB & RGB gradient sliders, hex entry, recents, custom palette, 9 built-in palettes |
| Tool wheel | Center = color (tap) / brush settings (long-press); arcs for size, opacity, smoothing |
| Paper | Blank, dots, grid, graph, ruled, isometric, iso-dots, triangles, hexagons, 1/2/3-point perspective; paper and line colors, spacing, weight, major lines, infinite adaptive grid |
| Precision | Draw & hold → perfect line / circle / ellipse / arc / rectangle / triangle / polygon; snapped lines rubber-band with 45° snapping |
| Edit | Vector slice eraser or whole-stroke eraser, lasso select → move / scale / rotate / duplicate / recolor / delete / bring to front, unlimited undo/redo |
| Layers | Add, duplicate, merge down, reorder, hide, lock, opacity, rename |
| Pen | Pressure curve + floor/ceiling calibration, tilt, hover cursor, motion prediction (Android 14+), unbuffered input, high refresh rate request, pen buttons / eraser end mapping |
| Palm rejection | Contact-size threshold, reject while pen hovers, cool-down after pen lift, system palm (cancel) flag, edge rejection, pen always overrides finger strokes |
| Gestures (all remappable) | Pinch zoom/rotate, 1-finger pan, 2-finger tap (undo), 3-finger tap (redo), 4-finger tap (hide UI), double-tap (reset zoom), 2-finger double-tap (fit), long-press (eyedropper), 3-finger swipe (undo/redo scrub), pen double-tap, pen press & hold, volume keys |
| Files | Gallery with thumbnails, rename / duplicate / delete, autosave, PNG (paper / transparent / 4K) and SVG export |
| Pen Lab | Live readout of pressure, tilt, orientation, hover distance, buttons, sample rate, contact size, plus a log of any key/button events: use it to discover what your Xiaomi pen sends |

## Build locally

```bash
cd folio-android
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest # geometry unit tests
```

## Automated demo / test

`adb shell am start -n com.akshit.folio/.CanvasActivity --es demo all` plays a scripted
session that injects real pen / finger / palm events, checks ~30 assertions, and prints
`SHOT:` markers. CI (`.github/workflows/folio-android.yml`) runs it on a tablet emulator and
uploads screenshots, a screen recording and the assertion summary.
