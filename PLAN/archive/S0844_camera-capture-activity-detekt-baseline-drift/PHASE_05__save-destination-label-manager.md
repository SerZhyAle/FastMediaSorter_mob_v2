# Phase 05 - Save Destination Label Manager

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 (needs `CameraOverlayRotationManager`), Phase 04
**Blocks:** Phase 08
**Steps done:** 2 / 2
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Extract the save-destination-name resolution and the scenario/destination label rendering into a new `CameraCaptureSaveDestinationLabelManager`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (this manager depends on `CameraOverlayRotationManager`).
- [ ] Phase 04 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSaveDestinationLabelManager.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 800 (net shrink) |

---

## Steps

### Step 05.1 - Create CameraCaptureSaveDestinationLabelManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSaveDestinationLabelManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraCaptureSaveDestinationLabelManager(intent: Intent, flowManager: CameraCaptureFlowManager, settingsRepository: SettingsRepository, resourceRepository: ResourceRepository, rotationManager: CameraOverlayRotationManager, destinationLabel: TextView, scenarioLabel: TextView, lifecycleScope: CoroutineScope)` in package `com.sza.fastmediasorter.ui.cameracapture.helpers`. Move verbatim: `refreshSaveDestinationLabel()`, `resolveSaveDestinationName()` (already reduced to 2 returns in Phase 01 - keep that shape), `renderScenarioLabel()`. Both `refreshSaveDestinationLabel` and `renderScenarioLabel` currently call `applyOverlayRotation(currentOverlayRotation, animate = false)` after making the label visible - replace with `rotationManager.reapply()` (the manager from Phase 02). Expose two public entry points, `fun refresh()` and `fun renderScenario()`, that the Activity calls; keep `resolveSaveDestinationName` `private suspend` inside this class.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSaveDestinationLabelManager.kt` exists.
- `Grep` - `class CameraCaptureSaveDestinationLabelManager(` matches exactly once.
- `Grep` - `private suspend fun resolveSaveDestinationName` matches exactly once in the new file.
- `Grep` - `rotationManager.reapply()` matches exactly 2 times in the new file.

**Status:** `[x]` done

---

### Step 05.2 - Wire the Activity to the new manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Remove `refreshSaveDestinationLabel`, `resolveSaveDestinationName`, `renderScenarioLabel` from `CameraCaptureActivity`. Add `private lateinit var saveDestinationLabelManager: CameraCaptureSaveDestinationLabelManager`, constructed in `setupViews()` after `rotationManager` exists (this manager depends on it). Replace the 2 call sites in `setupViews()` (`refreshSaveDestinationLabel()`, `renderScenarioLabel()`) with `saveDestinationLabelManager.refresh()` / `.renderScenario()`, and the call site in `selectMode()` (`refreshSaveDestinationLabel()`) likewise.

**Verification:**

- `Grep` - `private fun refreshSaveDestinationLabel` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `private suspend fun resolveSaveDestinationName` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `saveDestinationLabelManager = CameraCaptureSaveDestinationLabelManager(` matches exactly once.
- `Grep` - `saveDestinationLabelManager.refresh()` matches at least 2 times.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class).
- [ ] `./gradlew.bat :app_v2:detekt --rerun-tasks` - `CameraCaptureActivity.kt` function count and significant-line count spot-checked (not yet required to be under threshold; confirm downward trend only).

---

## Handoff Notes to Next Phase

All 3 "state-owning" extractions (rotation, zoom, result, save-destination) are done. Remaining extractions (Phase 06, 07) are interface-delegation handlers, independent of each other and of this phase except for their shared dependency on `flowManager`/`sessionManager` (unchanged) and, for Phase 06, on `CameraZoomControlsManager` (Phase 03).

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - pure code relocation, identical label-rendering behavior, no data migration or user-facing surface changed.
