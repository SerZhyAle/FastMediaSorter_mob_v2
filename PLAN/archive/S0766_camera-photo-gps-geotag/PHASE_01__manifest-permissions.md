# Phase 01 - Manifest permissions & features

**Strategic spec:** [`../S0766_camera-photo-gps-geotag.md`](../S0766_camera-photo-gps-geotag.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 1

---

## Objective

Declare location runtime permissions and a non-required GPS hardware feature so the opt-in geotag path can request location at runtime without filtering GPS-less devices off Google Play.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a |

---

## Steps

### Step 01.1 - Add location permissions + non-required GPS feature

**Files:** `app_v2/src/main/AndroidManifest.xml`

**Prompt for developer:**

> After the existing `android.permission.CAMERA` `uses-permission` line, add `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` `uses-permission` entries with a one-line comment referencing S0766 (opt-in geotag, default off). After the existing `android.hardware.camera.any` `uses-feature` line, add `android.hardware.location` and `android.hardware.location.gps` `uses-feature` entries, both `android:required="false"` (mirror the camera/microphone non-required pattern) so devices without GPS are not filtered out on Play.

**Verification:**

- `Grep` - `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` present in the manifest.
- `Grep` - `android.hardware.location.gps` present with `required="false"`.
- `.\a.ps1 fr` - manifest/resources merge clean for `standard`.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `.\a.ps1 fr` passes (manifest merge).
- [ ] No location permission marked `required="true"` for the feature.

---

## Rollback Plan

Remove the two `uses-permission` and two `uses-feature` lines.
