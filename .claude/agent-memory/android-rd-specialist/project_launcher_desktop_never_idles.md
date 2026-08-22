---
name: launcher-desktop-never-idles-for-uidump
description: A populated launcher desktop never reaches uiautomator's idle state, so uidump and any raw uiautomator dump fail there - the cause is the live tray clock and gadgets, not the wallpaper, and switching the wallpaper does not help
metadata:
  type: project
---

`uidump`, `tap-label`, and a raw `uiautomator dump` through the shell all fail on the launcher desktop
with `ERROR: could not get idle state`. Label-based testing is unavailable there. Use screenshots and
coordinate taps instead, or test the surface from a screen that does settle.

**Why, and the correction that matters:** the first, obvious suspect is the branded waves wallpaper -
`LauncherWallpaperManager` calls `wavesLayer.startAnimation()` for `LauncherWallpaper.Branded`, and the
pattern visibly keeps moving minutes after launch. **That diagnosis is wrong**, and a device turn was
spent on it before the evidence settled it:

- Switching the wallpaper to `NONE` did **not** unblock the dump. `render()` for `None` does call
  `stopWaves()`, so the setting really applies - it just is not what holds the window busy.
- A raw `uiautomator dump` fails identically to the project's verb, so the idle wait is not the verb's
  doing.
- The decisive contrast: an **empty** desktop dumped fine (a full tree came back on the first attempt,
  with the branded wallpaper still animating), and the **populated** desktop never dumps. Between the
  two, `seedDesktopIfNeeded()` had filled the grid - clock, weather, search, resource rows.

So the blocker is the live content, above all the ticking tray clock (`trayClock`, driven by
`LauncherTrayManager`) and the weather gadget. A view that updates every second never lets the window
go idle, and no wallpaper setting changes that.

**How to apply:**

- Do not spend device budget switching the wallpaper to make `uidump` work. It will not.
- On the launcher desktop, drive by screenshot plus coordinate taps, re-reading coordinates from a
  fresh screenshot before each tap rather than remembering them.
- Settings screens settle normally - `uidump` and `tap-label` work there, including the launcher's own
  settings dialog. Do the parts you can from Settings.
- Rotation checks need no tree at all: `settings put system user_rotation` plus before/after
  screenshots is enough, and that is how the rotation half of a launcher acceptance was taken.
