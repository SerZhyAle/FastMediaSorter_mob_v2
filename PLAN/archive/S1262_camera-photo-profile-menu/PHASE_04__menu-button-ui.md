# Phase 04 - Menu button UI

**Strategic spec:** [`../S1262_camera-photo-profile-menu.md`](../S1262_camera-photo-profile-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Replace the macro button with the profile-menu button, drop the separate night button, wire the anchored popup menu, trilingual strings and accessibility.

> Scheduling note from INDEX: prefer starting this phase after the device verdicts on S1260/S1261 (same panel).

---

## Prerequisites

- [x] Phase 03 ✅ Done.
- [x] Backup of `CameraCaptureActivity.kt` to `temp/S1262/` (file > 500 LOC).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk` via tool) | Modified | n/a |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | n/a |
| `app_v2/src/main/res/drawable/ic_camera_profile_portrait.xml`, `ic_camera_profile_selfie.xml` | New - see Deviations | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfilePresentation.kt` | New - see Deviations | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified - retired the two dead mirror flags | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOverlayRotationManager.kt` | Modified | ≤ 150 |

> Landscape variant absent - `activity_camera_capture.xml` has no `layout-land` counterpart (verified 2026-07-28); rotation is handled by `CameraOverlayRotationManager`.

---

## Steps

### Step 04.1 - Trilingual strings

**Files:** `app_v2/src/main/res/values*/strings.xml` (via `scripts/utils/set-android-string.ps1`)
**Depends on:** - start of phase

**Prompt for developer:**

> Add via `set-android-string.ps1 -Action add -Key ... -En -Ru -Uk` (one lockstep call per key): `camera_profile_button` (button contentDescription, states the active profile via placeholder), `camera_profile_normal|night|portrait|selfie|macro|sport` (short nouns), `camera_profile_sport_notice` (one-line toast: short exposure freezes motion, frames darker in low light - COMMUNICATION_POLICY §2 info formula). Check §6 tone checklist before integration. Then run `scripts/check_strings_localized.ps1 -KeyPrefix "camera_profile"`.

**Verification:**

- `Grep` - `camera_profile_sport` present in all three `strings.xml`.
- `scripts/check_strings_localized.ps1 -KeyPrefix "camera_profile"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 04.2 - Layout: profile button replaces macro and night

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`, `app_v2/src/main/res/drawable/ic_camera_profile.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `activity_camera_capture.xml` replace `btn_camera_macro` with `btn_camera_profile` (same slot, same size/touch target, style consistent with neighbouring camera buttons, no hardcoded `#hex`) and delete `btn_camera_night`. Add vector `ic_camera_profile.xml`; reuse existing night/macro icons for per-profile button states, add minimal vectors for portrait/selfie/sport only if no existing drawable fits. `focusable`/`clickable`/`nextFocus*` preserved for D-pad.

**Verification:**

- `Grep` - `btn_camera_profile` present, `btn_camera_macro` and `btn_camera_night` absent from the layout.
- `.\a.ps1 fr` passes.

**Status:** `[x]` done

---

### Step 04.3 - Activity wiring: anchored popup menu

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Replace macro/night click wiring with one `btnCameraProfile` listener that builds a `PopupMenu` anchored to the button (use the themed activity context - Material widgets inflated with a bare context crash) listing `profileManager.availableProfiles(capabilities)` only; checked item = active profile (checkable group). Item click -> `profileManager.apply(profile)`; SPORT activation additionally shows the `camera_profile_sport_notice` toast once per screen session. Button icon + contentDescription reflect the active profile after every apply/reconcile. Video mode hides the button (same branch that hid macro/night); hide it too when NORMAL is the only available profile. Delegate any non-trivial logic to the managers - zero business logic in the Activity. Check the generated binding field type for `btnCameraProfile` before casting.

**Verification:**

- `Grep` - `btnCameraMacro` and `btnCameraNight` return zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `PopupMenu` present in `CameraCaptureActivity.kt`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

### Step 04.4 - Rotation manager and TalkBack

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOverlayRotationManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Replace `binding.btnCameraMacro` (and any `btnCameraNight` entry) with `binding.btnCameraProfile` in the rotating-views list. Confirm the button rotates with the rest of the overlay and its contentDescription (from step 04.3) reads the active profile name - state distinguishable without color.

**Verification:**

- `Grep` - `btnCameraProfile` present in `CameraOverlayRotationManager.kt`; `btnCameraMacro` absent.

**Status:** `[x]` done

---

### Step 04.5 - Probe tags for device test

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Before the ticket enters `BlockNeedUserTest`, insert `Timber.d("S1262: profile=<name> available=<list>")` at the apply entry point (one tag per changed flow, single-line so the removal grep matches). Do not add per-phase tags earlier - a tag with the ticket not in `BlockNeedUserTest` fails the ticket-log gate.

**Verification:**

- `Grep` - `"S1262:` matches in `.kt` only when the catalog status is `BlockNeedUserTest`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL, zero warnings (2026-07-31 00:36).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Orphaned resources removed in the same change: `camera_control_night` / `camera_control_macro`
      in all three locales and the `ic_camera_night_off` / `ic_camera_macro_off` drawables (the `_on`
      pair stays - it is now the night and macro menu icon). Proof: a repo-wide grep over `app_v2/src`
      **and** `wear/src` for all six names returns zero hits, and `processStandardDebugResources` -
      the task that fails on a dangling `@string`/`@drawable` reference - passed. Every reference lived
      in `src/main`; no flavor source set names them, so a release-variant build has nothing extra to
      prove here.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The `PopupMenu` is built per tap and
      its only listener dies with it, so nothing outlives the dismiss; the Activity keeps no reference
      to the popup or its items.

---

## Deviations from the plan as written

- **No `ic_camera_profile.xml`.** `ic_tune` already carries exactly the "adjust the shot" glyph and is
  a plain white vector, so the NORMAL state reuses it rather than shipping a near-identical twin
  (Rule 20). Sport reuses `ic_speed` for the same reason. Only portrait and selfie needed new vectors.
- **Resource mapping moved out of the Activity.** `CameraProfilePresentation` owns label and icon per
  profile, so the button and the menu row cannot disagree and the Activity keeps no `when` over the
  enum (Rule 3).
- **`nightModeEnabled` / `macroEnabled` deleted from the flow manager.** They existed only to drive the
  two retired button icons; after the swap nothing read them (Rule 20). The session's own `nightMode`
  and `macroEnabled` remain the truth.

---

## Handoff Notes to Next Phase

User-visible surface changed: new icons and strings exist - Phase 05 records the capability and syncs icon-related docs.

---

## Rollback Plan

Revert phase commit(s); Phases 01-03 remain dormant (no UI entry) and harmless.
