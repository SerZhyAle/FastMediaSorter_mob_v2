# Phase 03 - Photo controls UI

**Strategic spec:** [`../S0545_camera-capabilities-expansion.md`](../S0545_camera-capabilities-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Render the owner-approved Samsung-familiar photo controls in portrait and landscape, driven by runtime capabilities and without dead controls.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] UI placement contract resolved - strategic §3.4 (owner gate 2026-06-20): zoom = preset chips + slider, overflow = "more" menu for secondary controls, unsupported controls hidden, focus = tap focus-ring. No in-screen photo/video switch (mode fixed by entry point).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 190 |
| `app_v2/src/main/res/layout-land/activity_camera_capture.xml` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/FocusRingOverlayView.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +12 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +12 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +12 lines |
| `app_v2/src/main/res/drawable/ic_camera_switch.xml` | New | ≤ 80 |
| `app_v2/src/main/res/drawable/ic_camera_flash_on.xml` | New | ≤ 80 |
| `app_v2/src/main/res/drawable/ic_camera_flash_off.xml` | New | ≤ 80 |

---

## Steps

### Step 3.1 - Apply the approved photo-control layout in portrait and landscape

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`, `app_v2/src/main/res/layout-land/activity_camera_capture.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`, `app_v2/src/main/res/drawable/ic_camera_switch.xml`, `app_v2/src/main/res/drawable/ic_camera_flash_on.xml`, `app_v2/src/main/res/drawable/ic_camera_flash_off.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Apply the `/ui-clarify` placement contract to both `activity_camera_capture.xml` variants: add the flash entry, lens-switch control, zoom preset group, and any owner-approved container needed for a Samsung-familiar layout. Add the required EN/RU/UK labels, content descriptions, and tooltips for the new photo controls. Before merging the strings, check `docs/COMMUNICATION_POLICY.md` §2 for the relevant message shape and §6 for the tone checklist.

**Verification:**

- `Grep` - `@+id/btnCameraFlash` and `@+id/btnCameraLensSwitch` are present in both layout variants.
- `Grep` - `@+id/cameraZoomPresetGroup` is present in both layout variants.
- `Grep` - `camera_control_flash`, `camera_control_switch_lens`, and `camera_control_zoom` are present in `values/strings.xml`, `values-ru/strings.xml`, and `values-uk/strings.xml`.
- `Verification predicate` - Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[ ]` not done

---

### Step 3.2 - Wire capability-driven visibility, zoom presets, and tap-to-focus

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/FocusRingOverlayView.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`, `app_v2/src/main/res/layout/activity_camera_capture.xml`, `app_v2/src/main/res/layout-land/activity_camera_capture.xml`
**Depends on:** Step 3.1

**Prompt for developer:**

> Bind the new controls to `CameraRuntimeCapabilities`: hide unsupported flash or lens controls, keep zoom presets in sync with the active camera, and trigger tap-to-focus only when the active lens supports it. Add a lightweight focus-ring overlay view for the confirmation affordance instead of baking focus visuals into the Activity. Preserve keyboard, D-pad, and TalkBack reachability for every new control.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/FocusRingOverlayView.kt` exists.
- `Grep` - `supportsTapToFocus` is referenced from `CameraCaptureFlowManager.kt`.
- `Grep` - `setZoomRatio` and `switchCamera` are referenced from `CameraCaptureFlowManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits across the touched Kotlin files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Photo mode now exposes only supported controls and already follows the approved layout contract. Phase 04 should reuse the same surface for video instead of creating a parallel screen.

---

## Rollback Plan

Revert phase commit(s) - UI-only surface changes on the existing camera screen, no save-routing or database impact.
