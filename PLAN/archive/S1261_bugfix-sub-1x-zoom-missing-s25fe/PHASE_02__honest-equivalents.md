# Phase 02 - Honest equivalents

**Strategic spec:** [`../S1261_bugfix-sub-1x-zoom-missing-s25fe.md`](../S1261_bugfix-sub-1x-zoom-missing-s25fe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-28
**Completed:** -

---

## Objective

Replace raw focal-mm ratios (defect D2) with a sensor-normalized equivalent multiplier, cross-checked against the parent logical camera's zoom floor; carry the multiplier on each lens entry.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Backup of `CameraCaptureSessionManager.kt` to `temp/S1261/` (file > 500 LOC).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/LensEquivalentCalculator.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraLensEntry.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensEnumerationManager.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 900 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/LensEquivalentCalculatorTest.kt` | New | ≤ 200 |

---

## Steps

### Step 02.1 - Pure calculator

**Files:** `LensEquivalentCalculator.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create pure `LensEquivalentCalculator`: input per-lens `(focalMm, sensorWidthMm?, parentLogicalMinZoom?, isWidestInParent)` plus the reference lens `(focalMm, sensorWidthMm?)`; output equivalent multiplier. Priority: (1) widest back lens inside a logical parent whose floor < 1 -> multiplier = parent floor (S25 FE: 0.57); (2) both sensor widths known -> FOV ratio `(focal/sensorWidth) / (refFocal/refSensorWidth)`; (3) fallback raw focal ratio; (4) non-back or missing data -> 1.0. No Android imports.

**Verification:**

- `Grep` - `class LensEquivalentCalculator` (or `object`) matches exactly once.

**Status:** `[x]` done (`object LensEquivalentCalculator` with nested `Lens`/`Reference` inputs instead of a long parameter list; priority chain implemented as specified)

---

### Step 02.2 - Multiplier carried on the lens entry

**Files:** `CameraLensEntry.kt`, `CameraLensEnumerationManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `sensorWidthMm: Float` (0 = unknown, from `SENSOR_INFO_PHYSICAL_SIZE`), `parentLogicalMinZoom: Float` (parent logical camera's floor for physical sub-lens entries; own floor for logical entries) and `equivalentMultiplier: Float` to `CameraLensEntry`. `CameraLensEnumerationManager` fills them during `expand` and computes `equivalentMultiplier` via the calculator once the reference lens is known (either inside `expand` after collecting entries, or in a small post-pass invoked by the session before use). `select()`'s dedup switches from `sameMagnification(focalMm)` to comparing equivalent multipliers with the same relative tolerance - on S25 FE the offered back set must stay `{~0.57, 1.0, tele}`.

**Verification:**

- `Grep` - `equivalentMultiplier` present in `CameraLensEntry.kt` and used in `CameraLensEnumerationManager.kt`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done. Deviation from the step's dedup wording: `select()` treats an entry as
covered on same focal **OR** same equivalent (not equivalent-only) - the standalone ultra-wide and
the fused camera's sub-lens are one physics, but their multipliers diverge when the sensor size is
unreadable (focal fallback 0.32 vs parent floor 0.57), and equivalent-only dedup would then offer
both. Same-focal stays sufficient, so the S25 FE back set is `{~0.57, 1.0, tele}` in both sensor
scenarios. Also: `parentLogicalMinZoom` falls back to the Camera2 `CONTROL_ZOOM_RATIO_RANGE.lower`
when the unbound camera's `zoomState` is empty (that floor is the whole ticket).

---

### Step 02.3 - Probe and session consume the entry multiplier

**Files:** `CameraCapabilityProbe.kt`, `CameraCaptureSessionManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> `probe()` takes the active `CameraLensEntry` (or its multiplier) and uses `entry.equivalentMultiplier` for `zoomMultiplier` instead of `thisFocal / referenceFocal`; `minEquivalentZoom` computes reachable floors as `lens.minZoomRatio * lens.equivalentMultiplier`. `CameraCaptureSessionManager.bindToLifecycle` passes the active entry through. Front lenses keep multiplier 1 (existing behavior). Keep the class at its `TooManyFunctions` baseline - no new functions on the session manager.

**Verification:**

- `Grep` - `thisFocal / referenceFocal` returns zero hits in `CameraCapabilityProbe.kt`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done. `probe()` now takes the active `CameraLensEntry`; `minEquivalentZoom(lenses)`
lost its `referenceFocal` parameter; the session's `referenceFocal` field is gone. Dead code removed
with the switch (Rule 20): `availableCameras`, `focalLengthOf`, both `mainBackFocalLength` overloads
- their only callers were the replaced paths. No new session-manager functions.

---

### Step 02.4 - Unit tests with the S25 FE numbers

**Files:** `LensEquivalentCalculatorTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Test with research-01 data: ultra-wide (1.74 mm, parent floor 0.57, widest) -> 0.57 exactly, not 0.32; tele (7.00 mm) with fabricated sensor widths giving FOV ratio ~3 -> the FOV path, not 1.30; missing sensor data -> focal-ratio fallback; front lens -> 1.0.

**Verification:**

- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.ui.cameracapture.helpers.LensEquivalentCalculatorTest"` - BUILD SUCCESSFUL.

**Status:** `[x]` done (17:58 run over `ui.cameracapture.*`: BUILD SUCCESSFUL, exit 0 - six tests
incl. parent-floor 0.57 exact, FOV tele ~3, focal fallback, front/broken neutral, non-widest guard)

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk`.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate -ChangedFiles "<touched .kt list>"` - PASS.
- [ ] Dev log entry added for the phase (batched).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`minEquivalentZoomRatio` is now trustworthy (0.57 on S25 FE via the parent-floor path); Phase 03 builds the device-floor pill from it.

---

## Rollback Plan

Revert phase commit(s); calculator unused elsewhere.
