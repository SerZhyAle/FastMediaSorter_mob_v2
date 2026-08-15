# Phase 02 - Session recipes

**Strategic spec:** [`../S1262_camera-photo-profile-menu.md`](../S1262_camera-photo-profile-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04
**Steps done:** 4 / 4
**Started:** 2026-07-29
**Completed:** 2026-07-29

---

## Objective

Teach the capture session the two missing primitives - BOKEH extension binding and the sport short-exposure capture options - and surface `supportsBokehExtension` into the capabilities snapshot.

> **Detekt guard:** `CameraCaptureSessionManager` sits at its `TooManyFunctions` ceiling (~40). This phase may add **at most one** function to it; option-set building goes into the new pure helper of step 02.2.

---

## Prerequisites

- [x] Phase 01 ✅ Done.
- [x] Backup of `CameraCaptureSessionManager.kt` to `temp/S1262/` (file > 500 LOC).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/SportExposureOptionsFactory.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/SportExposureOptionsFactoryTest.kt` | New | ≤ 150 |

---

## Steps

### Step 02.1 - Probe BOKEH availability and surface the flag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Next to `nightExtensionAvailable`/`hdrExtensionAvailable` (around lines 667-671) add `bokehExtensionAvailable = !videoMode && extensionsManager?.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH) == true`, and pass it into the capabilities snapshot as `supportsBokehExtension` (near line 711-714). No new function - inline in the existing rebind path.

**Verification:**

- `Grep` - `ExtensionMode.BOKEH` present in `CameraCaptureSessionManager.kt`.
- `Grep` - `supportsBokehExtension =` present in the snapshot builder.

**Status:** `[x]` done

---

### Step 02.2 - Sport capture-options factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/SportExposureOptionsFactory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create pure helper `SportExposureOptionsFactory` (object or class without Android view deps) that builds Camera2 interop capture options for the sport recipe from research 02: AE off, `SENSOR_EXPOSURE_TIME` = 4 ms clamped into `shutterRangeNs`, `SENSOR_SENSITIVITY` = `min(1600, isoRange.upper)` coerced at least `isoRange.lower`, `CONTROL_AF_MODE_CONTINUOUS_PICTURE`. Reuse `PhotoProfile.SPORT_TARGET_EXPOSURE_NS`; name the ISO cap as a companion const. Return null when the capabilities snapshot fails the sport predicate.

**Verification:**

- `Glob` - `SportExposureOptionsFactory.kt` exists.
- `Grep` - `SENSOR_EXPOSURE_TIME` and `CONTROL_AF_MODE_CONTINUOUS_PICTURE` present in it.

**Status:** `[x]` done

---

### Step 02.3 - Apply and clear sport/bokeh intents at the session

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Add session intents `bokehEnabled: Boolean` and `sportEnabled: Boolean` (fields, mirroring `nightMode`): extend the extension-selector `when` chain so bokeh (when enabled and available) is ranked with HDR/NIGHT - exactly one extension binds; apply sport options through the same interop slot used by the macro focus lock, and re-apply them after rebind like the night fallback does. Lens change and video mode clear both intents (same lines that clear `nightMode`/`macroEnabled`). At most one new function on this class - prefer extending the existing rebind/apply paths.

**Verification:**

- `Grep` - `bokehEnabled` and `sportEnabled` present in `CameraCaptureSessionManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate -ChangedFiles "<this file>"` - PASS (no new `TooManyFunctions`).

**Status:** `[x]` done

---

### Step 02.4 - Unit-test the sport factory

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/SportExposureOptionsFactoryTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Test clamping: exposure clamps into a narrow `shutterRangeNs`; ISO caps at 1600 and floors at `isoRange.lower`; null returned when `supportsManualSensor` is false or ranges are absent.

**Verification:**

- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.ui.cameracapture.helpers.SportExposureOptionsFactoryTest"` - BUILD SUCCESSFUL.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (concurrency: intents mutated on main thread only, like `nightMode`).

---

## Handoff Notes to Next Phase

Session primitives are intent flags reconciled at rebind; Phase 03 orchestrates them but must never set two profile intents at once (extension selector chain picks one winner - keep the invariant at the orchestration level too).

---

## Rollback Plan

Revert phase commit(s); intents default to false so behavior equals today's.


## Deviations from the plan as written

- **The one-function budget was already spent.** The phase allowed at most one new function on `CameraCaptureSessionManager`; the class was at 39 of 40, so adding the extension-selector method tripped `TooManyFunctions` at 40/40. The ranking moved out to a new file, `CameraExtensionSelector`, which also retired three `!!` on `extensionsManager`.
- **`SportExposureOptionsFactory` exposes numbers, not a built options bag.** The session merges manual exposure, macro focus and white balance into one `CaptureRequestOptions.Builder`; a second built bag would have clobbered them. The factory is the pure clamping, the session applies it, and `build()` was dropped rather than left unused.
- **Sport yields to manual exposure.** Applying it is gated on `manualIso == null` - an explicit manual pair is a deliberate user choice and outranks a profile preset.
- **Bokeh is ranked last of the three extensions.** Exactly one can bind; an explicit HDR or night choice is a stronger signal than a profile that also asked for bokeh.
