# S0918 - Restore Play device reach lost to implied screen.portrait feature

**Status:** Archived

## 1. Problem

- Play Console device reach dropped from 20,468 (bundle 260622232 / 2.60.6222.324) to 17,510 (bundle 260703131 / 2.60.7031.316) - a loss of 2,958 devices (~14.5%).
- The next release must restore full device reach.

## 2. Root cause (authoritative)

- `aapt2 dump badging` on the shipped 2.60.7031 AAB reports:
  - `uses-feature: name='android.hardware.screen.portrait'` (i.e. required=true)
  - `uses-implied-feature: name='android.hardware.screen.portrait' reason='one or more activities have specified a portrait orientation'`
- `CameraCaptureActivity` (added in the capture family, S0754) declares `android:screenOrientation="portrait"` in `app_v2/src/main/AndroidManifest.xml`.
- A portrait `screenOrientation` makes Android imply `android.hardware.screen.portrait` as **required=true**, so Google Play filters out every device without a portrait-capable screen (landscape-only TVs, some tablets and auto displays).
- Feature count corroborates: 6222 declared 9 features (all not-required); 7031 declares 11 not-required + 1 required (`screen.portrait`) = 12, matching the Play Console diff (9 -> 12 features).
- All other explicit `<uses-feature>` entries are already `required="false"`; the location permissions added by S0766 are correctly overridden and are not the cause.

## 3. Fix

- Add to `app_v2/src/main/AndroidManifest.xml` next to the other feature overrides:
  - `<uses-feature android:name="android.hardware.screen.portrait" android:required="false" />`
  - `<uses-feature android:name="android.hardware.screen.landscape" android:required="false" />`
- This is the canonical Google-recommended override: an explicit not-required declaration cancels the orientation-implied requirement while the activity stays portrait-locked for UX.
- The symmetric landscape entry pre-empts the mirror regression if any future activity locks landscape.
- Do not remove the `CameraCaptureActivity` portrait lock - it is an intentional UX constraint (S0754).

## 4. Validation

- Local proof (no device needed): rebuild the merged manifest and re-run `aapt2 dump badging` on a universal APK - `android.hardware.screen.portrait` must appear as `uses-feature-not-required`, and the `uses-implied-feature` line must disappear.
- Local proof DONE (2026-07-03, standard debug APK 2.60.7031.633, aapt2 36.1.0): badging reports `uses-feature-not-required: android.hardware.screen.portrait` and `uses-feature-not-required: android.hardware.screen.landscape`; no `uses-implied-feature` line for either screen feature remains.
- Release proof (pending next upload): the Play Console bundle must report device reach back at ~20k, matching or exceeding 2.60.6222.

## 5. Prevention

- Recurring class: any activity that locks `screenOrientation` to a portrait/landscape value silently re-introduces an implied required `screen.*` feature and shrinks device reach.
- Mechanical gate DONE: `scripts/quality/assert-orientation-implied-feature.ps1` fails when any `app_v2` manifest pins `screenOrientation` to a portrait/landscape value without the matching `android.hardware.screen.*` not-required override in `src/main`. Wired into `post-change.ps1` (fires on any `AndroidManifest.xml` touch) and the `assert-fast-gates.ps1` batch (`.\a.ps1 fg`).
