---
name: play-device-reach-screen-portrait
description: Diagnose Play device-reach regressions via aapt2 implied-feature badging; orientation locks imply required screen.portrait/landscape
metadata:
  type: project
---

Play device-reach dropped 20,468 -> 17,510 (-2,958) between bundles 2.60.6222 and 2.60.7031. Root cause (S0918): `CameraCaptureActivity` (S0754) declares `android:screenOrientation="portrait"`, so Android implies `android.hardware.screen.portrait` as **required=true**, and Play filters out every landscape-only device (TVs, some tablets/auto displays). Fixed by declaring both `android.hardware.screen.portrait` and `.landscape` as `required="false"` in `app_v2/src/main/AndroidManifest.xml` (portrait lock kept - it is intentional UX).

**Why:** Any activity that locks `screenOrientation` to a portrait/landscape value silently re-introduces an implied required `android.hardware.screen.*` feature and shrinks Play device reach. All our explicit `<uses-feature>` are already `required="false"`; only implied ones bite. Ties to the release gate [[feedback_release_no_coverage_regression]].

**How to apply:**
- Diagnostic playbook for "Play shows fewer supported devices": dump the shipped AAB's implied features locally, do not guess.
  - `bundletool-all` self-contained jar runs headless; the gradle-cached `bundletool-*.jar` lacks transitive deps and fails with NoClassDefFound.
  - `java -jar bundletool-all.jar build-apks --bundle=X.aab --output=temp/u.apks --mode=universal --overwrite` (signs with debug keystore, no config needed), unzip `universal.apk`, then `aapt2 dump badging universal.apk | grep -i feature`.
  - The line `uses-feature: name='...'` (NOT `uses-feature-not-required:`) is a **required** feature; `uses-implied-feature: ... reason='...'` names the trigger (e.g. a portrait activity). That reason is the exact device filter.
  - `bundletool dump manifest` shows only EXPLICIT features - it will NOT reveal implied ones, so it cannot diagnose this class. Use `aapt2 dump badging` on a universal APK.
- Fix pattern: for any implied required `screen.*` (or camera/microphone/location/etc.) feature, add an explicit `<uses-feature android:name="..." android:required="false" />` in src/main. Explicit not-required always overrides the implied requirement while keeping the activity behaviour.
- Prevention DONE (S0918 impl 2026-07-03): `scripts/quality/assert-orientation-implied-feature.ps1` fails when any `app_v2` manifest pins `screenOrientation` portrait/landscape without the matching `screen.*` not-required override in src/main. Wired into `post-change.ps1` (fires on any `AndroidManifest.xml` touch) + `assert-fast-gates.ps1` batch (`.\a.ps1 fg`). Note `app_v2/src/vr/AndroidManifest.xml` landscape-locks an activity, so the symmetric `screen.landscape` override is load-bearing for noLegal/VR builds.
- Screen orientation values that imply a required feature: portrait/reversePortrait/sensorPortrait/userPortrait -> screen.portrait; landscape/reverseLandscape/sensorLandscape/userLandscape -> screen.landscape. sensor/user/unspecified/locked/nosensor imply nothing.
- Still open: release proof (Play Console device reach back to ~20k) can only be confirmed after the next release upload; S0918 stays Implemented (not Verified) until then.
