# Phase 01 - App-side diagnostics

**Strategic spec:** [`../S1261_bugfix-sub-1x-zoom-missing-s25fe.md`](../S1261_bugfix-sub-1x-zoom-missing-s25fe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02 (its verification reads the new report fields)
**Steps done:** 3 / 3
**Started:** 2026-07-28
**Completed:** 2026-07-28

---

## Objective

Extend the System info Cameras section with the data the fix needs and the app-side view (defect D3): per-camera sensor physical size, and the lens set the app actually selected with per-entry multiplier and preset labels.

---

## Prerequisites

- [ ] `scripts/utils/lock-status.ps1 -Name Build` free; acquire `CODE.LOCK`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/CameraHardwareInventory.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/CameraHardwareDataSource.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensSelectionReporter.kt` | New | ≤ 150 |
| system-info section renderer (locate via `GatherCameraDiagnosticsUseCase` usages) | Modified | n/a |

---

## Steps

### Step 01.1 - Sensor physical size in the hardware inventory

**Files:** `CameraHardwareInventory.kt`, `CameraHardwareDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `sensorSizeMm: SizeF?` (from `CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE`) to `CameraHardwareEntry` and render it in the Cameras section line (e.g. `sensor 9.8x7.3 mm`). Defensive read like every other characteristic.

**Verification:**

- `Grep` - `SENSOR_INFO_PHYSICAL_SIZE` present in `CameraHardwareDataSource.kt`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done (Grep: 1 match; fk 17:47 BUILD SUCCESSFUL; label `sysinfo_field_camera_sensor` EN/RU/UK, always rendered - an unknown is itself a finding for the FOV path)

---

### Step 01.2 - Lens-selection reporter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensSelectionReporter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraLensSelectionReporter` that, given a `ProcessCameraProvider`, runs `CameraLensEnumerationManager.expand` + `select` and renders one text line per offered entry: id, facing, focal mm, min zoom, equivalent multiplier, and the zoom-preset labels `buildZoomPresets` would produce for it (after S1260 display rounding). Mark the entry the session would start on. Pure text out; no binding, no UI classes beyond the existing helpers.

**Verification:**

- `Glob` - `CameraLensSelectionReporter.kt` exists.
- `Grep` - `class CameraLensSelectionReporter` matches exactly once.

**Status:** `[x]` done (both predicates pass; multiplier read from `entry.equivalentMultiplier` and start entry from the shared `initialLensIndex` after Phase 02/03 landed in the same run)

---

### Step 01.3 - Wire the app view into the Cameras section

**Files:** system-info renderer for the Cameras section (find via `Grep GatherCameraDiagnosticsUseCase` / catalog)
**Depends on:** Step 01.1, 01.2

**Prompt for developer:**

> Append an `App view` sub-block to the Cameras section: the reporter's lines when camera permission is granted, otherwise a single `App view: camera permission not granted` line. The report must never crash or hang when CameraX initialization fails - degrade to an error line (mirror the section's existing defensive style). Respect layering: the screen composes the sub-block; the domain use case is not made to depend on ui helpers.

**Verification:**

- `.\a.ps1 fk` passes.
- Emulator: System info shows the `App view` sub-block with at least one lens line (`adb.ps1 shot` evidence).

**Status:** `[x]` done - fk 17:47; emulator evidence 18:38 (emulator-5554, API 37): System info ->
Cameras shows `Размер сенсора` per entry and the `Со стороны приложения` sub-block with per-lens
lines incl. `<- start` marker (screenshots `temp/scratch/emulator-5554_20260728_182426.png`,
`_183848.png`). Composition point: `GeneralSettingsLogHelper.showSystemInfoDialog` builds the lines
(permission gate + 3s bounded CameraX init) and hands them to `GatherSystemInfoUseCase(appCameraView)`
- the domain stays ui-free.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk`.
- [ ] Dev log entry added for the phase (batched).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Phase 02's multiplier change must be visible in this report (per-entry multiplier column) so the owner's re-captured report proves the fix without adb.

---

## Rollback Plan

Revert phase commit(s) - report-only change.
