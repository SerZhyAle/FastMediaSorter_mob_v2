# Phase 01 — Manifest Hardening

**Strategic spec:** [`../S0075_device-reach-google-play.md`](../S0075_device-reach-google-play.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Add explicit `<uses-feature android:required="false">` declarations for all hardware features implicitly required by current permissions, and add `anyDensity="true"` to `<supports-screens>`, so Google Play stops excluding devices that can run the app.

---

## Prerequisites

- [x] Strategic §6.2 resolved: add `android.hardware.touchscreen android:required="false"` — confirmed by owner.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 460 |

---

## Steps

### Step 1.1 — Declare `android.hardware.wifi` as not required

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/main/AndroidManifest.xml`, immediately after the `<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />` line, insert:
> ```xml
> <uses-feature android:name="android.hardware.wifi" android:required="false" />
> ```
> This cancels the implicit `required=true` that Google Play infers from `ACCESS_WIFI_STATE`, making the app visible on devices without a Wi-Fi chip (Android TV boxes, some tablets, ChromeOS with Ethernet-only).

**Verification:**

- `Grep` — `android.hardware.wifi` present in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `android:required="false"` on the same line as `android.hardware.wifi`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: app_v2/src/main/AndroidManifest.xml. Dev log recorded.

---

### Step 1.2 — Declare `android.hardware.touchscreen` as not required

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `app_v2/src/main/AndroidManifest.xml`, in the `<uses-feature>` block (near Step 1.1), insert:
> ```xml
> <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
> ```
> By default Android phone APKs have `touchscreen` implicitly required. Declaring it false unlocks ChromeOS and other non-touch form factors for which the app's mouse/keyboard navigation is sufficient.

**Verification:**

- `Grep` — `android.hardware.touchscreen` present in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `android:required="false"` on the same line as `android.hardware.touchscreen`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: app_v2/src/main/AndroidManifest.xml.

---

### Step 1.3 — Add `anyDensity="true"` to `<supports-screens>`

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** — independent of 1.1/1.2

**Prompt for developer:**

> In `app_v2/src/main/AndroidManifest.xml`, locate the existing `<supports-screens>` tag and add `android:anyDensity="true"` to it. The result should be:
> ```xml
> <supports-screens
>     android:smallScreens="true"
>     android:normalScreens="true"
>     android:largeScreens="true"
>     android:xlargeScreens="true"
>     android:anyDensity="true"
>     android:requiresSmallestWidthDp="320" />
> ```
> `anyDensity="true"` tells the platform that the app handles arbitrary screen densities, preventing exclusion on devices with unusual DPI values.

**Verification:**

- `Grep` — `android:anyDensity="true"` present in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `<supports-screens` still contains `android:smallScreens="true"` (not accidentally removed).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: app_v2/src/main/AndroidManifest.xml.

---

### Step 1.4 — Audit ABI coverage (read-only)

**Files:** `app_v2/build.gradle.kts` (read-only audit — no edit if already correct)
**Depends on:** — independent

**Prompt for developer:**

> Verify that all non-VR product flavors (`standard`, `lite`, `photos`, `legacy`) declare `abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")` in their `ndk { }` block inside `build.gradle.kts`. If all four ABIs are already present for every non-VR flavor, mark this step done with no file change. If any ABI is missing, add it.

**Verification:**

- `Grep` — `"arm64-v8a", "armeabi-v7a", "x86", "x86_64"` appears at least 4 times in `app_v2/build.gradle.kts` (once per non-VR flavor calling `disableNativeBuild()`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification PASS (adapted). All 4 non-VR flavors call `disableNativeBuild()` (lines 111/139/164/192) which declares all 4 ABIs at line 103. No file change needed.

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL in 55s (assembleStandardDebug).
- [x] `Grep` for `TODO(phase-01)` returns zero hits in source files.
- [x] Dev log entry added for `app_v2/src/main/AndroidManifest.xml` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Phase 02 requires publishing the build to Play Console before it can begin.
- Phase 03 (docs-catalog-cleanup) can start immediately after Phase 01 is done, without waiting for Phase 02.

---

## Rollback Plan

Revert changes to `app_v2/src/main/AndroidManifest.xml` — no data migration or user-facing surface changed. The additions are purely additive `<uses-feature>` and attribute declarations.
