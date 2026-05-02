# Phase 01 — Manifest XR Properties

**Strategic spec:** [`../S0036_vr-android-xr-sdk-compat.md`](../S0036_vr-android-xr-sdk-compat.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Declare the `vr`-flavor app as an XR-aware Home-Space app for Android XR Shell by adding the public `<uses-feature android.software.xr.api.spatial>` and `<property android.window.PROPERTY_XR_ACTIVITY_START_MODE>` declarations. No activity-level changes yet.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (see INDEX.md Pre-Implementation Blockers).
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/vr/AndroidManifest.xml` exists and is the active manifest source for both `vr` and `vrUnlicensed` flavors (verified via `app_v2/build.gradle.kts`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/AndroidManifest.xml` | Modified | ≤ 160 |

> File is currently 127 lines, projected ≤ 145 after Phase 01 + Phase 02. No backup required (file is well below 500 LOC).

---

## Steps

### Step 01.1 — Add `tools` namespace to vr manifest

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the `xmlns:tools="http://schemas.android.com/tools"` attribute to the root `<manifest>` element of `app_v2/src/vr/AndroidManifest.xml`. This namespace is required by Phase 02 for `tools:replace` overlay attributes; declaring it here in Phase 01 keeps the manifest header self-contained.

**Verification:**

- `Grep` — `xmlns:tools="http://schemas.android.com/tools"` present in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — `<manifest` line in that file matches both `xmlns:android` and `xmlns:tools`.

**Status:** `[ ]` not done

---

### Step 01.2 — Declare `android.software.xr.api.spatial` uses-feature

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the following `<uses-feature>` element at the top level of `<manifest>` in `app_v2/src/vr/AndroidManifest.xml` (alongside the existing `android.hardware.vr.headtracking` / `android.hardware.xr.immersive` declarations). Use `android:required="false"` so Quest devices (which do not advertise this feature) still install the same APK:
>
> ```xml
> <uses-feature
>     android:name="android.software.xr.api.spatial"
>     android:required="false" />
> ```
>
> Place after the existing `android.hardware.xr.immersive` block to keep XR-related declarations contiguous. Add a single-line comment explaining the property: `<!-- Android XR SDK: marks this APK as Jetpack-XR-aware for Home-Space panel rendering -->`.

**Verification:**

- `Grep` — `android.software.xr.api.spatial` matches exactly once in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — within 5 lines of that match, `android:required="false"` is present.
- `Grep` — comment `Android XR SDK` present in the same file.

**Status:** `[ ]` not done

---

### Step 01.3 — Declare `PROPERTY_XR_ACTIVITY_START_MODE = HOME_SPACE`

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add the following `<property>` element directly inside the existing `<application>` block in `app_v2/src/vr/AndroidManifest.xml`, after the `com.oculus.supportedDevices` `<meta-data>`:
>
> ```xml
> <!--
>   Android XR SDK (Google): declare 2D-panel ("home space") start mode so the XR Shell
>   knows our 2D activities (Welcome, Main, Settings) should render as flat panels and
>   populates xrDesktopMode in EmbeddingMixedHandler instead of leaving it 'undefined'.
>   On Meta Quest the property is ignored by Horizon runtime — Quest reads
>   com.oculus.intent.category.2D / VR from the activity intent-filter instead.
>   See S0036 §6 Q1 for the resolution that established this property as the public API.
> -->
> <property
>     android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE"
>     android:value="XR_ACTIVITY_START_MODE_HOME_SPACE" />
> ```
>
> The `PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED` property is **not** added here — it applies only to apps that move the user through physical space; our 2D-panel app does not need it.

**Verification:**

- `Grep` — `PROPERTY_XR_ACTIVITY_START_MODE` matches exactly once in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — within 5 lines of that match, `XR_ACTIVITY_START_MODE_HOME_SPACE` is present.
- `Grep` — the `<property` element is inside the `<application>` block (Grep with multiline mode for the `<application>..<property...PROPERTY_XR_ACTIVITY_START_MODE` span).
- `Grep` — `PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED` is **absent** from the same file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles for the `vr` flavor — run `/build` (do not invoke gradle directly).
- [ ] Project compiles for the `vrUnlicensed` flavor (shares the same manifest).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `app_v2/src/vr/AndroidManifest.xml` via `pwsh -File scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After Phase 01 the merged `vr` manifest declares spatial-XR awareness and Home-Space start mode at the `<application>` level, but individual activities still inherit the narrow `configChanges` set from `app_v2/src/main/AndroidManifest.xml`. Phase 02 overlays Settings / Welcome / Main with the full XR-friendly `configChanges` set so XR Shell window resizes stop recreating the activities.

---

## Rollback Plan

Revert the single commit on `app_v2/src/vr/AndroidManifest.xml`. No data migration, no user-visible surface — purely a manifest-level declaration that is opt-in for Android XR Shell and ignored by Meta Horizon and standard Android.
