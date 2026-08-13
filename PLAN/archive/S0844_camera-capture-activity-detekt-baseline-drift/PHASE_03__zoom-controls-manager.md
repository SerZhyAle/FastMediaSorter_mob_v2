# Phase 03 - Zoom Controls Manager

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Extract zoom-preset pill rendering, selection sync, ratio/label formatting and lens-label rendering into a new `CameraZoomControlsManager`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt` | New | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 900 (net shrink) |

---

## Steps

### Step 03.1 - Create CameraZoomControlsManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraZoomControlsManager(context: Context, presetGroup: ViewGroup, zoomSlider: Slider, zoomValue: TextView, lensLabel: TextView, onPresetSelected: (Float) -> Unit)` in package `com.sza.fastmediasorter.ui.cameracapture.helpers`. Move verbatim: `configureZoomControls(capabilities)` (rename param type to the existing `CameraRuntimeCapabilities`), `syncZoomSelection()` (now needs the live values passed in as parameters since it can no longer read `flowManager` directly - change signature to `syncSelection(liveZoomRatio: Float, liveLinearZoom: Float, zoomMultiplier: Float)`), `formatZoomRatio(ratio: Float)`, `formatZoomLabel(ratio: Float)`, and `lensLabel(capabilities: CameraRuntimeCapabilities): String` (rename to a public method, e.g. `fun renderLensLabel(capabilities): String` or keep it feeding an internal `applyLensLabel(capabilities)` that sets `lensLabel.text` and visibility directly - developer's choice, but it must be called from `configureZoomControls`/a new `bindLensLabel(capabilities)` entry point so the Activity does not need a separate call). The `OutlinedTextView` pill click listener calls `onPresetSelected(preset)` then `syncSelection(...)` using values the manager does not have direct access to - accept them as parameters passed by the Activity's callback, or have the Activity supply a small `currentLiveState: () -> Triple<Float, Float, Float>` lambda at construction time if that reads cleaner. Keep the `ZOOM_PILL_MATCH_EPSILON`, `CHIP_*_DP`, `CHIP_TEXT_SP` constants - move them into this new file's companion object (they are used only by `configureZoomControls`).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt` exists.
- `Grep` - `class CameraZoomControlsManager(` matches exactly once.
- `Grep` - `fun formatZoomRatio` and `fun formatZoomLabel` each match exactly once in the new file.
- `Grep` - `ZOOM_PILL_MATCH_EPSILON` matches zero times in `CameraCaptureActivity.kt` after Step 03.2.

**Status:** `[x]` done

---

### Step 03.2 - Wire the Activity to the new manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Remove `configureZoomControls`, `syncZoomSelection`, `formatZoomRatio`, `formatZoomLabel`, `lensLabel`, and the 6 zoom-chip constants from `CameraCaptureActivity`. Add `private lateinit var zoomControlsManager: CameraZoomControlsManager`, constructed in `setupViews()` with the zoom preset group/slider/value/lens-label views and an `onPresetSelected` callback that calls `flowManager.onZoomRatioSelected(it)` then re-syncs selection. Replace every internal call to `syncZoomSelection()` (from `onDoubleTapZoom`, `onPinchZoom`, the zoom-slider change listener) with `zoomControlsManager.syncSelection(flowManager.liveZoomRatio, flowManager.liveLinearZoom, flowManager.currentCapabilities.zoomMultiplier)`. Replace the `renderCapabilities()` block that configures zoom controls and sets the lens label with calls into the new manager. Add the new import in correct ktlint position.

**Verification:**

- `Grep` - `private fun configureZoomControls` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `private fun syncZoomSelection` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `zoomControlsManager = CameraZoomControlsManager(` matches exactly once.
- `Grep` - `zoomControlsManager.syncSelection(` matches at least 3 times (double-tap, pinch, slider listener).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class).

---

## Handoff Notes to Next Phase

`CameraZoomControlsManager` exists. Phase 06's gesture-callback handler will hold a reference to this manager to call `syncSelection(...)` after `onDoubleTapZoom`/`onPinchZoom`.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - pure code relocation, identical zoom-rendering behavior, no data migration or user-facing surface changed.
