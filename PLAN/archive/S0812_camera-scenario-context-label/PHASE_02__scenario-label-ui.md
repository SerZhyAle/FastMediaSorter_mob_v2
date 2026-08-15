# Phase 02 - Scenario label UI

**Strategic spec:** [`../S0812_camera-scenario-context-label.md`](../S0812_camera-scenario-context-label.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Render the scenario label above the zoom-preset chips in the visible camera, rotating with the other overlay labels, and make the OCR-translate caller pass `CameraScenario.OCR_TRANSLATE`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Backup `CameraCaptureActivity.kt` (>500 LOC) to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt` | Modified | ≤ 450 |

> **Landscape parity:** `res/layout-land/activity_camera_capture.xml` is intentionally absent - the activity is locked to portrait (`requestedOrientation = SCREEN_ORIENTATION_PORTRAIT` in `setupViews`) and rotates overlay views manually via `applyOverlayRotation` / `orientationAwareViews()`. No landscape variant to edit.

---

## Steps

### Step 02.1 - Add scenario label view above zoom presets

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `com.sza.fastmediasorter.ui.cameracapture.OutlinedTextView` with id `cameraScenarioLabel` as a direct child of the root `ConstraintLayout`, styled like `cameraLensLabel` (white text, `13sp`, bold, `wrap_content`, `visibility="gone"`, `tools:text` sample). Constrain it centered above the zoom presets: `layout_constraintBottom_toTopOf="@id/cameraZoomPresetGroup"`, `layout_constraintStart_toStartOf="parent"`, `layout_constraintEnd_toEndOf="parent"`, with a small `layout_marginBottom`. No hardcoded hex - reuse `@android:color/white` as the sibling labels do.

**Verification:**

- `Grep` - `@+id/cameraScenarioLabel` present in the layout.
- `Grep` - `layout_constraintBottom_toTopOf="@id/cameraZoomPresetGroup"` present.
- `.\a.ps1 fr` passes (resources/manifest).

**Status:** `[ ]` not done

---

### Step 02.2 - Register label in orientation-aware overlay list

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Append `binding.cameraScenarioLabel` to the `orientationAwareViews()` list so the label rotates with device orientation like `cameraSaveDestination` / `cameraLensLabel`.

**Verification:**

- `Grep` - `binding.cameraScenarioLabel` present inside `orientationAwareViews()`.

**Status:** `[ ]` not done

---

### Step 02.3 - Bind scenario label from intent

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a private `renderScenarioLabel()` that reads `CameraCaptureContract.readScenario(intent)`; if its `labelRes != 0`, set `binding.cameraScenarioLabel.text = getString(labelRes)`, make it `VISIBLE`, and call `applyOverlayRotation(currentOverlayRotation, animate = false)` (mirroring `refreshSaveDestinationLabel`); otherwise set it `GONE`. Call `renderScenarioLabel()` in `setupViews()` right after `refreshSaveDestinationLabel()`.

**Verification:**

- `Grep` - `fun renderScenarioLabel` present.
- `Grep` - `renderScenarioLabel()` called in `setupViews`.
- Project compiles - `.\a.ps1 fk`.

**Status:** `[ ]` not done

---

### Step 02.4 - OCR caller passes the scenario

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `launchCaptureInternal()`, pass `scenario = CameraScenario.OCR_TRANSLATE` to the `CameraCaptureActivity.createIntent(...)` call (use the new overload from Phase 01). Import `CameraScenario`.

**Verification:**

- `Grep` - `CameraScenario.OCR_TRANSLATE` present in `CameraOcrFlowManager.kt`.
- Project compiles - `.\a.ps1 fk`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `/build` standard debug.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added (batched at ticket close).

---

## Handoff Notes to Next Phase

The scenario label renders for the OCR-translate flow and stays hidden for generic capture. Phase 03 regenerates the catalog (new `CameraScenario` class), records the capability, and closes the ticket.

---

## Rollback Plan

Revert phase commit(s) - no data migration; label defaults to `GONE`, no user data touched.
