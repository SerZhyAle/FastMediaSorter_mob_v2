# Phase 02 - Overlay Rotation Manager

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Extract the orientation-aware overlay-rotation application (currently `applyOverlayRotation()` + `orientationAwareViews()` on the Activity) into a new stateless-per-instance helper, `CameraOverlayRotationManager`, that owns the current angle and the fixed view list.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOverlayRotationManager.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 964 (net shrink) |

---

## Steps

### Step 02.1 - Create CameraOverlayRotationManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOverlayRotationManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraOverlayRotationManager(views: List<View>)` in package `com.sza.fastmediasorter.ui.cameracapture.helpers`. Move the exact bodies of `CameraCaptureActivity.applyOverlayRotation(degrees, animate)` and the animation duration constant reference (`OVERLAY_ROTATION_ANIMATION_MS`, defined in Phase 01) into a public method `fun apply(degrees: Float, animate: Boolean = true)`, and add a private `var currentDegrees: Float = 0f` field updated at the top of `apply()` (replacing the Activity's `currentOverlayRotation` field). Add `fun reapply(animate: Boolean = false) = apply(currentDegrees, animate)` for the "just redraw at current angle" call sites. Constructor takes the fixed view list verbatim from the current `orientationAwareViews()` body (same 18 views, same order) - the caller (Activity) builds and passes this list once, referencing `binding.*` views. Move the animation-duration constant `OVERLAY_ROTATION_ANIMATION_MS` from the Activity's companion object into this new file as a private top-level `const val` (only this class uses it after Phase 05).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOverlayRotationManager.kt` exists.
- `Grep` - `class CameraOverlayRotationManager(` matches exactly once in that file.
- `Grep` - `fun apply(degrees: Float, animate: Boolean = true)` matches exactly once.
- `Grep` - `fun reapply(animate: Boolean = false)` matches exactly once.

**Status:** `[x]` done

---

### Step 02.2 - Wire the Activity to the new manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Remove `applyOverlayRotation()`, `orientationAwareViews()`, the `currentOverlayRotation` field, and the `OVERLAY_ROTATION_ANIMATION_MS` constant from `CameraCaptureActivity`. Add `private lateinit var rotationManager: CameraOverlayRotationManager` and construct it in `setupViews()` (after `binding` is available) with the same 18-view list `orientationAwareViews()` used to return. Replace every call site: `orientationManager`'s `onIconRotationChanged = ::applyOverlayRotation` becomes `onIconRotationChanged = { rotationManager.apply(it) }`; the three `applyOverlayRotation(currentOverlayRotation, animate = false)` call sites (in `renderCapabilities`, `refreshSaveDestinationLabel`, `renderScenarioLabel`) become `rotationManager.reapply()`. Add the new import for `CameraOverlayRotationManager` in the correct ktlint position (alongside the other `ui.cameracapture.helpers.*` imports, ordinal-sorted).

**Verification:**

- `Grep` - `private fun applyOverlayRotation` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `private fun orientationAwareViews` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `rotationManager = CameraOverlayRotationManager(` matches exactly once.
- `Grep` - `rotationManager.reapply()` matches exactly 3 times.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new public class).

---

## Handoff Notes to Next Phase

`CameraOverlayRotationManager` exists and is consumed by the Activity. Phase 05's `CameraCaptureSaveDestinationLabelManager` will take this manager as a constructor dependency (to call `reapply()` after changing label text/visibility) instead of calling the Activity's removed `applyOverlayRotation`.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - pure code relocation, identical rotation behavior, no data migration or user-facing surface changed.
