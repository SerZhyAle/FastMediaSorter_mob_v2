# Phase 03 - Device-floor zoom row

**Strategic spec:** [`../S1261_bugfix-sub-1x-zoom-missing-s25fe.md`](../S1261_bugfix-sub-1x-zoom-missing-s25fe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-28
**Completed:** 2026-07-28

---

## Objective

Open the screen on the main back lens (defect D1) and put the device's widest equivalent into the zoom row as a cross-lens pill whose tap switches lens and zoom in one action.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Backups of `CameraCaptureSessionManager.kt`, `CameraCaptureFlowManager.kt` to `temp/S1261/` if > 500 LOC.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 600 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilitiesTest.kt` | Modified | ≤ 250 |

---

## Steps

### Step 03.1 - Start on the main back lens

**Files:** `CameraCaptureSessionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `bind()` (lines ~163-166) replace `indexOfFirst { back }` with the main back entry: the back lens whose `equivalentMultiplier` is closest to 1.0 (tie-break: has flash, then logical over physical); fall back to first back, then 0. On S25 FE that is logical camera 0 (floor 0.57); single-camera devices are unaffected (their only back lens is the main).

**Verification:**

- `Grep` - the old `indexOfFirst { it.lensFacing == CameraSelector.LENS_FACING_BACK }` initial-bind expression is gone from `bind()`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done. The rule lives in `CameraLensEnumerationManager.initialLensIndex` (not a new
session-manager function - the class is at its 40-function ceiling) so the session and the S1261
report share one truth about the start lens: multiplier closest to 1, tie-breaks flash then logical.

---

### Step 03.2 - Device-floor pill in the preset model

**Files:** `CameraRuntimeCapabilities.kt`, `CameraRuntimeCapabilitiesTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `deviceFloorEquivalent: Float` next to `minEquivalentZoomRatio` semantics (may reuse the existing field) and a computed `showsCrossLensFloor: Boolean` = device floor is below the bound lens's own reachable equivalent floor by more than the zoom epsilon. The zoom-row model exposes the floor pill value in equivalent space (display-rounded by the S1260 rule, e.g. 0.57 -> 0.5). Unit tests: bound main (floor equivalent 0.57 via own range) -> no extra pill (reachable natively); bound lens floor 1.0 with device floor 0.57 -> cross-lens pill shown; equal floors -> hidden (POCO guard, strategic goal 4).

**Verification:**

- Targeted test run `CameraRuntimeCapabilitiesTest` - BUILD SUCCESSFUL.

**Status:** `[x]` done (17:58 `ui.cameracapture.*` run BUILD SUCCESSFUL). Model: `minEquivalentZoomRatio` reused as the device floor
(per the step's "may reuse"), computed `showsCrossLensFloor` (device floor below the bound lens's
`ownEquivalentFloor` by more than the epsilon, front excluded) and `crossLensFloorDisplay` (S1260
rounding). Five new tests incl. the POCO equal-floors guard and a tele-bound case.

---

### Step 03.3 - Render the floor pill and route its tap

**Files:** `CameraZoomControlsManager.kt`, `CameraCaptureFlowManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> `CameraZoomControlsManager.configure` renders the cross-lens floor pill first in the row when `showsCrossLensFloor`, amber like a native bound (it is the device's native minimum), contentDescription via the existing native-step string. Its tap goes to a new callback (equivalent-space value); `CameraCaptureFlowManager` resolves it: pick the back lens whose equivalent range covers the value (prefer the logical camera whose own floor is below 1 - on S25 FE logical 0 - so the platform does the optics switching), switch to it and set the native ratio = equivalent / lens multiplier. Selection highlight must track it like any other pill after the rebind.

**Verification:**

- `Grep` - `showsCrossLensFloor` used in `CameraZoomControlsManager.kt`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done. Pill construction extracted into a shared `buildPill` (floor pill and preset
pills differ only in tag/callback); the floor pill's tag is a string so `syncSelection`'s Float-tag
matching skips it (its value lives in another lens's native space). Tap routing:
`CameraCaptureActivity` -> `CameraCaptureFlowManager.onCrossLensFloorSelected` ->
`session.switchCamera(targetEquivalentFloor)`; the target lens is picked by top-level
`lensReaching` (covers the value, prefers sub-1x logical, then logical over physical), the zoom
lands as `equivalent / lens multiplier`, and the flow re-reads live values after the switch so the
rebuilt row highlights the landed pill.

---

### Step 03.4 - Emulator sanity + probes

**Files:** touched `.kt` from this phase
**Depends on:** Step 03.3

**Prompt for developer:**

> On the local AVD (single back camera): capture screen opens as before, zoom row unchanged, no extra pill (degradation rule). Insert `Timber.d("S1261: ...")` probes at the changed flow entries (initial lens choice, floor-pill tap) - single-line, one per flow - immediately before the ticket flips to `BlockNeedUserTest` in Phase 04.

**Verification:**

- `adb.ps1 launch` + `shot` on AVD: capture screen renders; `S1261:` probes appear in logcat on open.

**Status:** `[x]` done (emulator-5554, 18:36-18:37). Capture screen opens via main menu -> Камера
(the quick-photo setting had to be enabled first); probe `S1261: initial lens 10` in logcat (x2 -
initial + extension rebind); zoom row empty exactly as before on the fixed-zoom AVD camera
(1.00-1.00 -> supportsZoom false), no cross-lens pill (device floor == own floor). Screenshot
`temp/scratch/emulator-5554_20260728_183711.png`. Bonus finding fixed during this pass: the
equivalent-dedup leg in `select()` collapsed every front sub-lens (all front multipliers are 1) -
now back-only; the 5.84 mm front sub-lens reappears in the App view report (`_183848.png`).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc`.
- [ ] `assert-detekt.ps1 -Gate -ChangedFiles "<touched .kt list>"` - PASS.
- [ ] Dev log entry added for the phase (batched).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (rebind loops: floor-pill tap must not re-trigger itself after reconcile).

---

## Handoff Notes to Next Phase

Behavior complete; Phase 04 documents, syncs the catalog and parks the ticket for the owner's S25 FE pass (0.5 pill + wide picture + honest tele label + main-lens start + re-captured report).

---

## Rollback Plan

Revert phase commit(s); Phases 01-02 (report + multipliers) may ship alone - they are independently correct.
