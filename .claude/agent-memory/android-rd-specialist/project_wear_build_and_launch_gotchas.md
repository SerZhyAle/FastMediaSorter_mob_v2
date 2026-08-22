---
name: wear-build-and-launch-gotchas
description: Build the wear device APK with build-wear-debug.PS1 (stamps the build timestamp) - never with check-standard-fast -Mode Assemble, which freezes the version; plus adb.ps1 launch targets the phone Activity
metadata:
  type: project
---

Driving the wear app on a device has four traps, none of them announced by an error:

1. **`a.ps1 fw` does not produce an APK.** It is `-Mode Code` and only compiles. Installing after `fw`
   installs whatever the last assemble left behind - measured 2026-08-18, a 13-minute-stale APK that had none of
   the change under test, while every command reported success.
2. **The only correct wear device build is `scripts/builders/build-wear-debug.PS1`.** It stamps the real build
   timestamp into `versionCode`/`versionName` by default (`-AutoVersion` defaults to `$true`, S1816).
   **`check-standard-fast.ps1 -Mode Assemble -Module wear` is NOT a substitute**: it packages a real, installable
   APK but passes no `-Pfms.version*`, so the APK carries the frozen checked-in constant
   (`wear/build.gradle.kts` `defaultAppVersionCode`/`defaultAppVersionName`). The install then *lowers* the
   version on the watch and needs `adb install -d`, and the owner cannot tell from the device which build he is
   testing. Measured 2026-08-21: an earlier revision of THIS memory named the fast-check script as the assemble
   step, and following it shipped a watch build whose version read six days older than the one it replaced.
3. **`adb.ps1 launch` starts the phone Activity**, `com.sza.fastmediasorter.ui.main.MainActivity`, which does not
   exist in the watch build - it fails with "Activity class does not exist" (exit 7). The watch entry point is
   `com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity`, started through
   `adb.ps1 shell -Cmd "am start -n .."`. `adb.ps1` cannot target the watch for app verbs at all (its `-Flavor`
   set has no `wear` value) - use raw adb with `-s <mdns service name>`.
4. **`adb.ps1 shot` has no `-OutDir`** and `tap` has no `-X2/-Y2`; a swipe goes through
   `shell -Cmd "input swipe x1 y1 x2 y2 ms"`. Shots land in `temp/scratch/`.

**Why:** all of them cost a full install-and-drive cycle each, and none fails loudly. Trap 2 is the worst and the
owner's standing requirement is the reason it matters: every artifact he can install must carry its build
date-time, because that string is how he knows which build he is testing. See
[[feedback-every-installable-artifact-carries-its-build-timestamp]].

**How to apply:** for any wear device run, build with `scripts/builders/build-wear-debug.PS1`, check the APK mtime
AND the printed `Version override:` line before installing, then launch by explicit component. See also
[[wear-auto-rotation-is-real]] and [[test-device-galaxy-s21]] for the surfaces themselves.
