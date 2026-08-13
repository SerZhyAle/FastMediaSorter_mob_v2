# Phase 02 - Zoom slider

**Strategic spec:** [`../S0753_camera-zoom-presets-slider-night.md`](../S0753_camera-zoom-presets-slider-night.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Add a horizontal, draggable zoom slider directly under the preset buttons in both orientations, driven in perceptually-linear `linearZoom` space, sharing one zoom source of truth with the presets and pinch so all three stay in sync (strategic ADR-2, §6.3).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `CameraCaptureActivity.kt` is > 500 LOC - the editing step backs it up to `temp/` first (CLAUDE.md Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 95 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 290 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 360 |
| `app_v2/src/main/res/layout-land/activity_camera_capture.xml` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 620 |

> **Landscape parity:** both `layout/` and `layout-land/` variants of `activity_camera_capture.xml` are edited in Step 02.4.

---

## Steps

### Step 02.1 - Add `currentLinearZoom` to the capability snapshot

**Files:** `CameraRuntimeCapabilities.kt`, `CameraCapabilityProbe.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `currentLinearZoom: Float = 0f` field to `CameraRuntimeCapabilities` (alongside `currentZoomRatio`). In `CameraCapabilityProbe.probe()` populate it from `zoom?.linearZoom ?: 0f` (`ZoomState.getLinearZoom()` returns the 0..1 position matching the current ratio). This gives the slider a perceptually-linear thumb position that round-trips with the ratio-based presets.

**Verification:**

- `Grep` - `currentLinearZoom: Float` matches once in `CameraRuntimeCapabilities.kt`.
- `Grep` - `linearZoom` referenced in `CameraCapabilityProbe.kt`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 3/3 PASS (field grep, probe grep, `a.ps1 fk` SUCCESSFUL with 02.2/02.3). Files: CameraRuntimeCapabilities.kt, CameraCapabilityProbe.kt.

---

### Step 02.2 - Expose linear-zoom set/read on the session

**Files:** `CameraCaptureSessionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `fun setLinearZoom(linear: Float) { camera?.cameraControl?.setLinearZoom(linear) }`. Add `fun currentZoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: CameraRuntimeCapabilities.DEFAULT_ZOOM` and `fun currentLinearZoom(): Float = camera?.cameraInfo?.zoomState?.value?.linearZoom ?: 0f`. These let the flow manager read back the resulting ratio after a slider drag (and the resulting linear position after a preset/pinch) so the two controls mirror each other. Keep `setZoomRatio` as is.

**Verification:**

- `Grep` - `fun setLinearZoom(` and `fun currentZoomRatio(` and `fun currentLinearZoom(` each match once in `CameraCaptureSessionManager.kt`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS (3 new fun greps, `a.ps1 fk` SUCCESSFUL). Files: CameraCaptureSessionManager.kt.

---

### Step 02.3 - Single-source the zoom state across ratio and linear

**Files:** `CameraCaptureFlowManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `var liveLinearZoom: Float = 0f  private set`. Add `fun onLinearZoomSelected(linear: Float)`: guard `if (!currentCapabilities.supportsZoom) return`; `session.setLinearZoom(linear.coerceIn(0f, 1f))`; set `liveLinearZoom = linear.coerceIn(0f, 1f)`; set `liveZoomRatio = session.currentZoomRatio().coerceIn(currentCapabilities.minZoomRatio, currentCapabilities.maxZoomRatio)`. In `onZoomRatioSelected`, after `session.setZoomRatio(clamped)`, also set `liveLinearZoom = session.currentLinearZoom()` so a preset tap repositions the slider. In `onCapabilitiesChanged`, after computing `liveZoomRatio`, set `liveLinearZoom = capabilities.currentLinearZoom` so a rebind resets both. `liveZoomRatio` stays the single source of truth for preset highlighting; `liveLinearZoom` is its slider mirror.

**Verification:**

- `Grep` - `fun onLinearZoomSelected(` matches once; `liveLinearZoom` assigned in `onZoomRatioSelected` and `onCapabilitiesChanged`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS (`onLinearZoomSelected` + `liveLinearZoom` mirroring greps, `a.ps1 fk` SUCCESSFUL). Files: CameraCaptureFlowManager.kt.

---

### Step 02.4 - Add the slider to both layouts under the preset row

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`, `app_v2/src/main/res/layout-land/activity_camera_capture.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a `com.google.android.material.slider.Slider` with `android:id="@+id/cameraZoomSlider"`, `android:valueFrom="0"`, `android:valueTo="1"`, `android:visibility="gone"` (`tools:visibility="visible"`), `android:contentDescription="@string/camera_control_zoom"`, focusable for D-pad. Place it horizontally directly under the preset chips in both orientations (strategic §6.3):
> - **Portrait:** constrain top to `@id/cameraZoomPresetGroup` bottom, start/end to parent (with side margins), bottom toward `@id/cameraModeSelector`; keep the chip group constrained above it. Do not let it span full width edge-to-edge - add horizontal margins (memory: no full-width controls in landscape; keep portrait tidy too).
> - **Landscape:** the presets are a vertical chip column on the right edge; place the horizontal slider under that column - constrain its top to `@id/cameraZoomPresetGroup` bottom and its end to parent end with a fixed usable width (e.g. `android:layout_width="200dp"`), not match_parent. Stay inside the system-bar safe area already applied to the bars.
>
> Do not hardcode any `#hex` colour on the slider (CLAUDE.md Rule 19); leave track/thumb tint to Step 03 styling.

**Verification:**

- `Grep` - `@+id/cameraZoomSlider` matches once in each of the two layout files.
- `Grep` - `com.google.android.material.slider.Slider` present in both files.
- `Grep` - no `="#` hardcoded colour added on the slider lines.
- `.\a.ps1 fr` (resources/manifest) passes (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification PASS (cameraZoomSlider + Slider class in both layout/ and layout-land/, no inline #hex; resources merged in `a.ps1 fc`). Portrait re-chains chip->slider->modeSelector; landscape slider sits under the chip row by its width.

---

### Step 02.5 - Wire the slider in the host and keep it in sync

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> First copy `CameraCaptureActivity.kt` to `temp/CameraCaptureActivity.kt.<yyyyMMdd_HHmmss>.bak` (Rule 5, file > 500 LOC), then edit. In `setupCameraControls()` add `binding.cameraZoomSlider.addOnChangeListener { _, value, fromUser -> if (fromUser) { flowManager.onLinearZoomSelected(value); syncZoomSelection() } }`. In `renderCapabilities()`, when `capabilities.supportsZoom` set `binding.cameraZoomSlider.visibility = View.VISIBLE` and `binding.cameraZoomSlider.value = flowManager.liveLinearZoom.coerceIn(0f, 1f)`, else `View.GONE`. In `syncZoomSelection()`, after the chip highlight loop, set `binding.cameraZoomSlider.value = flowManager.liveLinearZoom.coerceIn(0f, 1f)` so preset taps, pinch and double-tap reposition the thumb (the `fromUser` guard stops the listener from looping). Do not collect any Flow here; this is a synchronous control.

**Verification:**

- `Grep` - `cameraZoomSlider.addOnChangeListener` matches once; `fromUser` guard present.
- `Grep` - `cameraZoomSlider.value` assigned in both `renderCapabilities` and `syncZoomSelection`.
- `Glob` - a `temp/CameraCaptureActivity.kt.*.bak` backup exists.
- `.\a.ps1 fc` (code + resources) passes (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 4/4 PASS (addOnChangeListener + fromUser guard; cameraZoomSlider.value set in renderCapabilities & syncZoomSelection; temp backup CameraCaptureActivity.kt.20260627_231134.bak; `a.ps1 fc` BUILD SUCCESSFUL 22s). Files: CameraCaptureActivity.kt.

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] Project compiles - `a.ps1 fc` (compileStandardDebugKotlin + resources) BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [~] Dev log entry - batched into Phase 05 finalization per CLAUDE.md.
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - deferred to Phase 05 (once per ticket).

---

## Handoff Notes to Next Phase

The slider exists and is wired with a `fromUser` guard; presets, pinch, double-tap and slider all read/write `liveZoomRatio` / `liveLinearZoom`. Phase 03 styles the slider plus all overlay controls for legibility on any background.

---

## Rollback Plan

Revert the phase commit and restore `CameraCaptureActivity.kt` from the `temp/` backup. No data migration or persisted state changed.
