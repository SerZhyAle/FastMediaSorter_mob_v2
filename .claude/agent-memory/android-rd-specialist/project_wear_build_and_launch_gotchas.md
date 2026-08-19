---
name: wear-build-and-launch-gotchas
description: a.ps1 fw compiles the wear module but does NOT package an APK, and adb.ps1 launch targets the phone Activity - both silently give you a stale or failed wear device run
metadata:
  type: project
---

Driving the wear app on a device has three traps, none of them announced by an error:

1. **`a.ps1 fw` does not produce an APK.** It is `check-standard-fast.ps1 -Mode Assemble` that packages
   `wear/build/outputs/apk/debug/wear-debug.apk`; `fw` is `-Mode Code` and only compiles. Installing after `fw`
   installs whatever the last assemble left behind - measured 2026-08-18, a 13-minute-stale APK that had none of
   the change under test, while every command reported success.
2. **`adb.ps1 launch` starts the phone Activity**, `com.sza.fastmediasorter.ui.main.MainActivity`, which does not
   exist in the watch build - it fails with "Activity class does not exist" (exit 7). The watch entry point is
   `com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity`, started through
   `adb.ps1 shell -Cmd "am start -n .."`.
3. **`adb.ps1 shot` has no `-OutDir`** and `tap` has no `-X2/-Y2`; a swipe goes through
   `shell -Cmd "input swipe x1 y1 x2 y2 ms"`. Shots land in `temp/scratch/`.

**Why:** all three cost a full install-and-drive cycle each during S1802 (2026-08-18), and none of them fails
loudly - the stale APK is the worst, because the app launches, the screen looks right, and the change is simply
absent.

**How to apply:** for any wear device run, assemble first
(`scripts/builders/check-standard-fast.ps1 -Mode Assemble -Module wear`), check the APK's mtime against your last
edit before installing, then launch by explicit component. See also [[wear-auto-rotation-is-real]] and
[[test-device-galaxy-s21]] for the surfaces themselves.
