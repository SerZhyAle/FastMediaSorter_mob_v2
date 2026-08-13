# Phase 06 - Gesture Callback Handler

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 08
**Steps done:** 2 / 2
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Extract the `CameraCaptureGestureManager.Callbacks` implementation into a dedicated handler class that the Activity constructs and hands to `CameraCaptureGestureManager` in place of itself - the Activity stops implementing this interface entirely (per strategic ADR-1: a standalone object is substituted for `this`, not Kotlin `by` delegation, since the handler's dependencies are only available after `setupViews()` runs).

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (this handler depends on `CameraZoomControlsManager`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureGestureCallbackHandler.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 780 (net shrink) |

---

## Steps

### Step 06.1 - Create CameraCaptureGestureCallbackHandler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureGestureCallbackHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class CameraCaptureGestureCallbackHandler(flowManager: CameraCaptureFlowManager, sessionManager: CameraCaptureSessionManager, focusRingOverlay: FocusRingOverlayView, zoomControlsManager: CameraZoomControlsManager, private val selectMode: (CameraCaptureMode) -> Unit) : CameraCaptureGestureManager.Callbacks` in package `com.sza.fastmediasorter.ui.cameracapture.helpers`. Move the exact bodies of `onTapToFocus`, `onDoubleTapZoom`, `onPinchZoom`, `onSwipeLensSwitch`, `onSwipeModeSwitch` from `CameraCaptureActivity` as overrides here. `onTapToFocus` calls `focusRingOverlay.showAt(x, y)` directly (was `binding.focusRingOverlay.showAt(x, y)`) - check the actual generated binding type for `binding.focusRingOverlay` before writing the constructor parameter type, do not assume `FocusRingOverlayView` without confirming against the layout binding class. `onDoubleTapZoom`/`onPinchZoom` call `zoomControlsManager.syncSelection(flowManager.liveZoomRatio, flowManager.liveLinearZoom, flowManager.currentCapabilities.zoomMultiplier)` after the flow-manager call (same pattern as the Activity's post-Phase-03 zoom sync). `onSwipeModeSwitch` calls the injected `selectMode` lambda instead of the Activity's private `selectMode` method directly.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureGestureCallbackHandler.kt` exists.
- `Grep` - `class CameraCaptureGestureCallbackHandler(` matches exactly once.
- `Grep` - `: CameraCaptureGestureManager.Callbacks` matches exactly once in the new file (interface implemented here, not on the Activity).
- `Grep` - `override fun onTapToFocus` through `override fun onSwipeModeSwitch` - all 5 overrides present exactly once each in the new file.

**Status:** `[x]` done

---

### Step 06.2 - Remove the Callbacks implementation from the Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Remove the 5 `override fun onTapToFocus/onDoubleTapZoom/onPinchZoom/onSwipeLensSwitch/onSwipeModeSwitch` methods (and their `// region CameraCaptureGestureManager.Callbacks` / `// endregion` comment block) from `CameraCaptureActivity`. Remove `CameraCaptureGestureManager.Callbacks` from the class's supertype list entirely (the Activity no longer implements this interface). Add `private lateinit var gestureCallbackHandler: CameraCaptureGestureCallbackHandler`, constructed in `setupCameraControls()` (right before the existing `gestureManager = CameraCaptureGestureManager(...)` line, since it needs `zoomControlsManager` and `flowManager` which already exist by that point) with `CameraCaptureGestureCallbackHandler(flowManager, sessionManager, binding.focusRingOverlay, zoomControlsManager, ::selectMode)`. Change the existing `gestureManager = CameraCaptureGestureManager(binding.previewViewCamera, this)` call to pass `gestureCallbackHandler` instead of `this` as the second argument.

**Verification:**

- `Grep` - `override fun onTapToFocus` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `CameraCaptureGestureManager.Callbacks` returns zero hits in `CameraCaptureActivity.kt`'s class declaration (supertype list).
- `Grep` - `CameraCaptureGestureManager(binding.previewViewCamera, gestureCallbackHandler)` matches exactly once.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class).

---

## Handoff Notes to Next Phase

The Activity's supertype list has shrunk from 4 interfaces to 3 (`BaseActivity<...>()`, `CameraCaptureFlowManager.Host`, `CameraSettingsDialogFragment.Callbacks`, `SelfManagedScreenOrientation`). Phase 07 removes `CameraSettingsDialogFragment.Callbacks` the same way (standalone handler substituted for `this`, not `by`), shrinking it further to 3 entries total.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - the handler is a plain constructor-substitution, not a language-level delegation mechanism; if gestures regress, revert and re-verify via `/build` before moving on.
