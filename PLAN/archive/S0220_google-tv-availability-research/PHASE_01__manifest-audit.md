# Phase 01 — manifest-audit

**Strategic spec:** [`../S0220_google-tv-availability-research.md`](../S0220_google-tv-availability-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation research phase
**Blocks:** Phase 04 (apply-manifest-fixes)
**Steps done:** 6 / 6
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Statically audit `app_v2/src/main/AndroidManifest.xml` against Google TV and Google Play compatibility rules; produce a documented finding for each strategic §6 research item covered by this phase.

---

## Prerequisites

- [x] Strategic §6 items 1–5 are still Open (this phase resolves them).
- [x] Working tree is clean.

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| `app_v2/src/main/AndroidManifest.xml` | Read-only (audit, no edits this phase) | — |

---

## Steps

### Step 1.1 — Audit `<uses-feature>` and Google TV store rules

**Files:** `app_v2/src/main/AndroidManifest.xml` (read-only)
**Depends on:** — start of phase

**Prompt for developer:**

> Read the official Google TV developer guidelines and Play Store compatibility filtering documentation. For each `<uses-feature>` entry in the manifest, determine whether Google TV Play Store treats its absence or the `required="true/false"` setting differently from Android TV. Specifically investigate: (1) `android.software.leanback required="false"` — does Play Store on Google TV require `required="true"` for the app to appear in TV search? (2) Does the combination of `LEANBACK_LAUNCHER` + `leanback required="false"` satisfy Google TV filtering? Document findings.
>
> Reference: https://developer.android.com/training/tv/start/start and https://support.google.com/googleplay/android-developer/answer/12556954

**Verification:**

- Document: confirmed whether `android.software.leanback required="false"` is sufficient for Google TV Play Store listing. Write finding in strategic §6.7 as Resolved.

**Finding (Resolved — §6.7):**
- `android.software.leanback required="false"` — **correct** for a phone+TV dual-target app. Official docs confirm this is the right value; `required="true"` would restrict the app to TV-only devices and exclude phones.
- `android.hardware.touchscreen required="false"` — **present and correct**. Without this, Play Store hides the app from all TV devices.
- `LEANBACK_LAUNCHER` category in `MainActivity` intent-filter — **present and correct**. Without it the app is invisible in Google TV Play Store.
- Conclusion: the three mandatory baseline requirements are all satisfied. **§6.7: Not a blocker.**

**Status:** `[x]` done

---

### Step 1.2 — Audit `<uses-permission>` for implicit hardware filters

**Files:** `app_v2/src/main/AndroidManifest.xml` (read-only)
**Depends on:** Step 1.1

**Prompt for developer:**

> Investigate the following permissions for implicit hardware feature requirements that Play Store injects automatically, even when `android.hardware.<X> required="false"` is declared:
>
> - `MANAGE_EXTERNAL_STORAGE` — Google Play policy for TV: does this permission cause the app to be hidden from TV Play Store? Check Google Play policy at https://support.google.com/googleplay/android-developer/answer/10467955.
> - `RECORD_AUDIO` — despite `android.hardware.microphone required="false"` in the manifest, does Google Play inject an implicit microphone hardware requirement on TV?
> - `FOREGROUND_SERVICE_DATA_SYNC`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — any TV-specific restrictions?
>
> Document confirmed findings for each permission.

**Verification:**

- Document: `MANAGE_EXTERNAL_STORAGE` status on TV — blocked or allowed. Write finding in strategic §6.1 as Resolved.
- Document: `RECORD_AUDIO` implicit filter status on TV. Write finding in strategic §6.5 as Resolved.

**Finding (Resolved — §6.1 and §6.5):**

Permissions with implicit hardware feature mappings found in our manifest:
- `RECORD_AUDIO` → implicitly requires `android.hardware.microphone`. **Overridden** by `<uses-feature android:name="android.hardware.microphone" android:required="false"/>` which is already present. **§6.5: Not a blocker.**
- `ACCESS_WIFI_STATE` → implicitly requires `android.hardware.wifi`. **Overridden** by `<uses-feature android:name="android.hardware.wifi" android:required="false"/>` which is already present.
- `MANAGE_EXTERNAL_STORAGE` — does **not** appear in the Android permission-to-feature implication table. It does not trigger any hardware feature filter. Google Play does require a Permissions Declaration Form approval for this permission, but that is a policy review process, not a TV-specific hardware filter. Apps approved with `MANAGE_EXTERNAL_STORAGE` on the phone Play Store remain eligible for TV. **§6.1: Not a blocker for TV filtering.**
- `FOREGROUND_SERVICE_DATA_SYNC`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — no hardware feature implications. Not TV-restricted.
- No `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `BLUETOOTH`, or telephony permissions present — no camera/GPS/NFC/telephony implicit features to worry about.

**Status:** `[x]` done

---

### Step 1.3 — Audit `android:screenOrientation="sensor"` on all activities

**Files:** `app_v2/src/main/AndroidManifest.xml` (read-only)
**Depends on:** Step 1.1

**Prompt for developer:**

> All activities in the manifest use `android:screenOrientation="sensor"`. Android TV and Google TV do not have an accelerometer / orientation sensor in the conventional sense. Investigate whether Play Store uses `screenOrientation="sensor"` as a compatibility filter that hides the app from TV devices. Check official documentation and Play Console compatibility filters. Determine: (1) does `sensor` trigger a hardware filter? (2) what is the recommended value for TV-compatible apps — `unspecified`, `landscape`, or something else?
>
> Cross-reference: the compatibility matrix at https://developer.android.com/training/tv/start/hardware.

**Verification:**

- Document: confirmed whether `screenOrientation="sensor"` causes Play Store filtering on Google TV. Write finding in strategic §6.2 as Resolved with recommended fix if needed.

**Finding (Resolved — §6.2):**
- `android:screenOrientation` is **not declared on any Activity** in our manifest. No activity sets portrait, reversePortrait, sensorPortrait, userPortrait, or reverseLandscape.
- TV app quality guidelines (TV-LO criterion) require landscape orientation, and the recommended value is `android:screenOrientation="landscape"`. However, omitting the attribute entirely (system default) is not a Play Store hard filter — the filtering occurs only when portrait-family values are explicitly declared.
- `configChanges="orientation|screenSize"` is present on activities — this does not affect Play Store filtering.
- **§6.2: Not a blocker.** Recommendation: explicitly set `android:screenOrientation="landscape"` on TV-launched activities (primarily `MainActivity`) as a quality improvement, not a blocker fix.

**Status:** `[x]` done

---

### Step 1.4 — Audit `<layout>` VR tag impact on TV compatibility

**Files:** `app_v2/src/main/AndroidManifest.xml` (read-only)
**Depends on:** Step 1.1

**Prompt for developer:**

> `MainActivity` contains `<layout android:defaultWidth="1920dp" android:defaultHeight="1080dp" android:gravity="center" android:minWidth="400dp" android:minHeight="300dp" />`. This tag was added for Meta Quest VR panel mode. Investigate whether this tag has any effect on Play Store TV compatibility filtering or on TV device launch behaviour. Specifically: does Play Store interpret `<layout>` as a freeform/desktop-mode requirement? Does it affect the TV launcher entry? Check AOSP documentation for `<layout>` element scope.

**Verification:**

- Document: confirmed whether `<layout>` tag affects TV Play Store filtering. Write finding in strategic §6.3 as Resolved.

**Finding (Resolved — §6.3):**
- `<layout android:defaultWidth="1920dp" android:defaultHeight="1080dp" .../>` in `MainActivity` is a freeform/multi-window mode hint introduced in API 24. It has no Play Store filtering effect and is not evaluated in TV compatibility checks.
- TV devices typically do not support freeform multi-window, so this element is silently ignored on TV.
- Ironically the 1920×1080 default is TV-native resolution, so there is no negative side effect.
- **§6.3: Not a blocker.**

**Status:** `[x]` done

---

### Step 1.5 — Audit `<supports-screens>` and `requiresSmallestWidthDp`

**Files:** `app_v2/src/main/AndroidManifest.xml` (read-only)
**Depends on:** Step 1.1

**Prompt for developer:**

> The manifest declares `<supports-screens android:requiresSmallestWidthDp="320" ... />`. TV screens report very large dp values (typically 960dp+). Verify: (1) does `requiresSmallestWidthDp="320"` create any upper bound that excludes large-dp TV screens? (2) is `<supports-screens>` evaluated by Play Store for TV form factors? Check whether the element should be omitted or adjusted for TV compatibility.

**Verification:**

- Document: confirmed whether `requiresSmallestWidthDp="320"` affects TV Play Store listing. Append finding to Phase 01 summary.

**Finding:**
- `android:requiresSmallestWidthDp="320"` sets a minimum screen width, not a maximum. TV screens report very large dp values (typically 960dp+), so a minimum of 320dp does not exclude them.
- The Android docs note that `requiresSmallestWidthDp` is used for Google Play filtering, but it only excludes devices with dp smaller than the declared value. TVs have much larger dp, so they pass this filter.
- `<supports-screens android:smallScreens="true" ... android:xlargeScreens="true" android:anyDensity="true"/>` — all screen size categories enabled, no upper bound.
- **Not a blocker.**

**Status:** `[x]` done

---

### Step 1.6 — Compile Phase 01 manifest findings

**Files:** none (documentation step)
**Depends on:** Steps 1.1–1.5

**Prompt for developer:**

> Compile all findings from Steps 1.1–1.5 into a prioritised list of manifest issues. For each confirmed blocker, propose the fix (specific attribute change). For each non-issue, document "not a blocker". This list feeds directly into Phase 04 (apply-manifest-fixes) — only confirmed blockers should generate fix steps.

**Verification:**

- Document: a written finding list exists covering all 5 investigated areas, each with status "blocker" or "not a blocker".
- Strategic §6 items 1, 2, 3, 5 — all marked Resolved in `S0220_google-tv-availability-research.md`.

**Finding (Phase 01 Summary — prioritised blockers list):**

Confirmed blockers requiring fixes in Phase 04:
- **BLOCKER A — TV banner is an XML layer-list drawable** (`res/drawable/tv_banner.xml`) instead of a proper raster PNG at `res/drawable-xhdpi/tv_banner.png` (320×180 px). The TV launcher and Google Play TV review process require a 16:9 bitmap banner. An XML layer-list drawable may not render correctly in the TV launcher and can cause Play Store submission issues ("no full-size banner"). Fix: replace with a proper 320×180 PNG at xhdpi and matching sizes at other densities.

Confirmed non-blockers:
- `android.software.leanback required="false"` + `LEANBACK_LAUNCHER` — correct, present. (§6.7)
- `RECORD_AUDIO` implicit microphone filter — overridden by `android.hardware.microphone required="false"`. (§6.5)
- `MANAGE_EXTERNAL_STORAGE` — no hardware filter implication for TV. (§6.1)
- `screenOrientation` not declared — safe default, not a Play Store filter trigger. (§6.2)
- `<layout>` VR tag — ignored on TV, no Play Store effect. (§6.3)
- `requiresSmallestWidthDp="320"` — lower bound only, TV passes. (§6 supplemental)

Phase 04 must address: BLOCKER A only (banner PNG replacement).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every Step 1.* above is `[x] done`.
- [x] Strategic §6.1, §6.2, §6.3, §6.5, §6.7 — all marked Resolved (see findings in each step above).
- [x] Written findings list (from Step 1.6) exists for handoff to Phase 04.
- [x] Dev log entry added.
- [x] No code changes in this phase — no build required.

---

## Handoff Notes to Next Phase

Phase 01 produces a confirmed blockers list. Phase 04 consumes it to generate exact manifest edits. Phases 02 and 03 run independently and in parallel with Phase 01.

---

## Rollback Plan

Read-only phase — no rollback needed.
