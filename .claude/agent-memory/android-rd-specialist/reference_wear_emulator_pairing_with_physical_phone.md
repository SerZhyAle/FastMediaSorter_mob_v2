---
name: wear-emulator-pairing-with-physical-phone
description: How to get a working phone-watch Data Layer pair without the Galaxy Watch - pair the Wear OS AVD with a physical phone over adb forward 5601, step by step, with the traps
metadata:
  type: reference
---

**A Wear OS AVD can be paired with a physical phone, so phone-watch bridge tickets do NOT need the
Galaxy Watch 7 to be reachable.** Done end to end on 2026-08-21 (S1860): the Data Layer carried a real
browse request and answered it in 125 ms.

The pieces, in order:

1. Start the watch: `emulator -avd Wear_OS_XL_Round`. It takes an `emulator-XXXX` slot like any AVD
   (it took 5554, displacing nothing - check `adb devices -l`, the wear one reports
   `product:sdk_gwear_x86_64`). The image is `android-37.0/android-wear-signed`, and it carries
   `com.google.android.gms` **and** `com.android.vending`, which is what makes pairing possible at all.
2. The phone needs **`com.google.android.wearable.app`** ("Wear OS by Google"), from Play. It is NOT
   on a Samsung phone by default - a Galaxy Watch pairs through Galaxy Wearable
   (`com.samsung.android.app.watchmanager` + a per-model plugin), which cannot pair an emulator. On a
   phone with a Google account already signed in this is a plain Play install; a fresh AVD has no
   account, which is what makes the phone-AVD route expensive and the physical-phone route cheap.
3. `adb -s <phone> forward tcp:5601 tcp:5601`. The companion app listens on the phone's 5601; the
   emulator dials the host. Set this BEFORE opening the app.
4. Open the app, allow the nearby-devices permission, accept the terms, and on the "Подключите часы"
   screen open the **⋮ overflow** - it holds **"Подключиться к эмулятору"**. That menu item is the
   whole trick; the Bluetooth scan below it will never see an emulator.
5. Success reads `Wear_OS_XL_Round - Подключено к эмулятору` in the app's header.

**Traps measured on the way:**

- Install both APKs with the SAME debug key and package (`com.sza.fastmediasorter.debug` on both
  sides, deliberately - see [[wear-data-layer-applicationid-mismatch]]). The wear APK comes from
  `check-standard-fast.ps1 -Mode Assemble -Module wear` -> `wear/build/outputs/apk/debug/wear-debug.apk`.
- `scripts/devtest/adb.ps1` drives the watch AVD fine for `tap`/`tap-label`/`uidump` with
  `-DeviceId emulator-XXXX`; only its app verbs (install/launch) refuse a wear target.
- `tap-label` on the watch often matches several nodes and taps #1 - read its warning line. On the
  phone's Play listing it matched six "Установить" nodes and tapped the wrong one; use explicit
  coordinates from the dump there.
- `adb.ps1 uidump` mangles non-ASCII text, so a Russian UI reads as mojibake in the tree AND in the
  saved file. Read those screens with `adb.ps1 shot` plus an image read instead.
- MSYS rewrites a `/sdcard/..` argument into `C:/Program Files/Git/sdcard/..`; prefix the call with
  `MSYS2_ARG_CONV_EXCL='*'` when pulling a file off the device.

**How to apply:** when a phone-watch ticket is blocked on "no watch attached", this is the way around
it - about 15 minutes of setup. What it does NOT give you is the real watch's screen: see
[[wear-emulator-capture-and-network-traps]], app-window screenshots come back black, so structural
checks via `uidump` are fine and anything whose evidence is a picture still needs the Galaxy Watch 7.
