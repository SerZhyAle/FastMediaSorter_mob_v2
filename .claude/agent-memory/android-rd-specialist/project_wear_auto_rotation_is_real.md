---
name: wear-auto-rotation-is-real
description: Galaxy Watch 7 really does auto-rotate the app window - measured, contradicting the natural assumption that Wear OS never rotates; the app declares no orientation and no configChanges, so the platform decides alone
metadata:
  type: project
---

Wear OS on the owner's Galaxy Watch 7 (SM-L310, Android 16 / SDK 36) rotates the app window by the accelerometer, and the setting is on. Measured 2026-08-16 via `dumpsys window` on the attached watch:

- `mSupportAutoRotation=true`, `accelerometer_rotation=1`, `mUserRotationMode=USER_ROTATION_FREE`
- the window was sitting at `mRotation=ROTATION_90` at the moment of the probe
- `feature:android.hardware.sensor.accelerometer` is present

**Changed 2026-08-16, later the same day:** at the owner's request rotation was pinned to ROTATION_0 on that watch and auto-rotation turned off.

Three settings were needed, and the first two alone did **nothing** on this Samsung firmware:

- `settings put system accelerometer_rotation 0` + `user_rotation 0` -> `mUserRotationMode=USER_ROTATION_LOCKED`, yet `mRotation` stayed at 1 and the Samsung listener kept proposing ROTATION_90 (`WindowOrientationListener mSensor="Samsung auto rotation Sensor"`, type 27 - a vendor fused sensor, not the raw accelerometer, which is why the standard toggle does not silence it).
- `cmd window fixed-to-user-rotation -d 0 enabled` is what actually landed it: `mFixedToUserRotation=true`, `mRotation=0`. **Not persistent** - it is WindowManager runtime state and resets on reboot.

`mPortraitRotation=ROTATION_0` on this device, so ROTATION_0 is the upright panel orientation. A `screencap` PNG is taken in already-derotated surface coordinates, so **a watch screenshot looks upright at any `mRotation`** - never use one to judge rotation, read `mRotation` (related: [[avd-evidence-traps-width-and-logs]]).

The screen is square, 480x480 @ 340dpi, so the configuration always reports `port` and `w==h`. That is why the rotation is invisible as an aspect change and reads to a user as "the picture fell on its side" rather than "the screen rotated".

`wear/src/main/AndroidManifest.xml` declares `.MainActivity` with **no** `android:screenOrientation` and **no** `android:configChanges`, and no wear code reads rotation or sets `requestedOrientation`. So the platform decides alone, and every rotation recreates the activity with its whole Compose tree and nav host.

**Why:** the intuitive assumption - "watches do not rotate, so an orientation ticket on wear is a no-op" - is false here, and acting on it would have closed S1718 as pointless. The measurement is what made S1718 a real feature instead of a cosmetic one, and it spawned S1721 for the recreation half.

**How to apply:**

- Never answer a wear orientation/rotation question from the assumption that Wear OS is portrait-only. Probe the attached watch: `scripts/devtest/adb.ps1 shell -DeviceId <watch> -Cmd "dumpsys window | grep -iE 'mRotation|SupportAutoRotation|UserRotationMode'"`.
- A square screen hides rotation from screenshots and from `Configuration.orientation` alike - verify with the window state, not with the eye. Related: [[avd-evidence-traps-width-and-logs]].
- Two tickets carry this: S1718 (the app's own rotation setting) and S1721 (the activity recreation the missing `configChanges` causes).
