# Phase 02 - Lens pills on the fixed-range lens

**Strategic spec:** [`../S1675_camera-sub-1x-zoom-not-offered.md`](../S1675_camera-sub-1x-zoom-not-offered.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Replace the branch that hides the whole zoom row on a lens without its own range with a row of rear-lens pills, and let a tap on one actually switch the optics.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `capabilities.rearLensEquivalentFloors` is populated.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 840 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 500 |

> `CameraCaptureActivity.kt` is 817 LOC - over the 500-LOC threshold, so take the Rule 5 timestamped backup before editing it. No `res/layout*` file is touched: the pill row is the existing `cameraZoomPresetGroup`, so CLAUDE.md Rule 11 landscape parity does not apply.

---

## Steps

### Step 02.1 - Build a pill per rear lens

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun configureLensPills(capabilities: CameraRuntimeCapabilities)`. It clears the preset group and adds one pill per value in `capabilities.rearLensEquivalentFloors`, in list order, reusing `buildPill` and `addPill`.
>
> Each pill prints its own value, carries a non-Float tag of the form `"$LENS_FLOOR_TAG_PREFIX$value"` so `syncSelection`'s numeric matching skips it, and calls `onCrossLensFloorSelected(value)` on tap. Mark the pill for the currently bound lens as selected by comparing the value against `capabilities.ownEquivalentFloor` rounded the same way, within `ZOOM_EPSILON`; that pill uses the same amber treatment the cross-lens floor pill uses today.
>
> Declare `LENS_FLOOR_TAG_PREFIX` next to `CROSS_LENS_FLOOR_TAG`. Do not change `configure()` - the lens-pill row is a separate path used only where the row would otherwise vanish.

**Why:**

Owner decision (strategic §3.2.1) is variant A - a pill per rear lens in equivalent values with the active lens highlighted - restricted to the lens whose own range is a single point, so the existing `configure()` path for lenses with a real range must stay untouched.

**Verification:**

- `Grep` - `fun configureLensPills(` matches exactly once.
- `Grep` - `LENS_FLOOR_TAG_PREFIX` declared once and used in the pill tag.
- `Grep` - `configure(` body unchanged: `showsCrossLensFloor` still referenced exactly once in the file's `configure` path.
- `.\a.ps1 fk` exits 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 02 in one pass: configureLensPills builds one string-tagged pill per rear-lens floor with the bound lens preselected; renderCapabilities gains a middle branch that shows the row on a non-front lens with more than one rear floor and keeps slider and readout hidden; the flow guard now admits the front-camera-excluded pair of cases (cross-lens pill, or a lens with no range of its own) instead of a condition that is false by definition where the new row lives. Two S1675 probes inserted before the single build. a.ps1 fk exit 0, a.ps1 dq exit 0, APK v2.60.8151.612.

---

### Step 02.2 - Render the row instead of hiding it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `renderCapabilities()`, rewrite the `else` branch that currently hides the whole zoom row. The slider and the numeric value stay `GONE` there - with `minZoomRatio == maxZoomRatio` they would be dead controls.
>
> When the bound lens is not the front camera and `capabilities.rearLensEquivalentFloors` holds at least two entries, make `cameraZoomPresetGroup` visible and call `zoomControlsManager.configureLensPills(capabilities)`. Otherwise keep today's behaviour exactly: hide the group and clear its children.
>
> Take the Rule 5 timestamped backup of the file before editing it.

**Why:**

Strategic §2 identifies this branch as the root cause - it hides the group, the slider and the value together when `supportsZoom` is false - and §3.2.1 limits the new row to that branch so lenses with a working row keep it unchanged.

**Verification:**

- `Grep` - `configureLensPills(` called exactly once in `CameraCaptureActivity.kt`.
- `Grep` - `cameraZoomSlider.visibility = View.GONE` still present in the same branch.
- `Grep` - `isFront` referenced in `renderCapabilities`.
- `.\a.ps1 fk` exits 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 02 in one pass: configureLensPills builds one string-tagged pill per rear-lens floor with the bound lens preselected; renderCapabilities gains a middle branch that shows the row on a non-front lens with more than one rear floor and keeps slider and readout hidden; the flow guard now admits the front-camera-excluded pair of cases (cross-lens pill, or a lens with no range of its own) instead of a condition that is false by definition where the new row lives. Two S1675 probes inserted before the single build. a.ps1 fk exit 0, a.ps1 dq exit 0, APK v2.60.8151.612.

---

### Step 02.3 - Let the tap through

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Rewrite the guard in `onCrossLensFloorSelected`. It currently returns unless `currentCapabilities.showsCrossLensFloor`, which is false by definition on the widest lens, so every tap from the new pills would be a no-op.
>
> Admit exactly the two cases that own a pill: the bound lens is not the front camera, and either it shows the cross-lens floor pill or it has no zoom range of its own (`!supportsZoom`). Keep the rest of the body as it is - `switchCamera(targetEquivalentFloor = equivalent)` followed by the two live-value reads. Explain the widened condition in one comment naming both callers.

**Why:**

Strategic §3.2.1 requires lifting the `showsCrossLensFloor` restriction, and §2 proves the property is false on the widest lens by its own definition, so a guard left as it is would let the new pills compile and never work.

**Verification:**

- `Grep` - `showsCrossLensFloor` still referenced inside `onCrossLensFloorSelected` (the widened condition keeps it as one alternative).
- `Grep` - `supportsZoom` referenced inside `onCrossLensFloorSelected`.
- `Grep` - `isFront` referenced inside `onCrossLensFloorSelected`.
- `.\a.ps1 fk` exits 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 02 in one pass: configureLensPills builds one string-tagged pill per rear-lens floor with the bound lens preselected; renderCapabilities gains a middle branch that shows the row on a non-front lens with more than one rear floor and keeps slider and readout hidden; the flow guard now admits the front-camera-excluded pair of cases (cross-lens pill, or a lens with no range of its own) instead of a condition that is false by definition where the new row lives. Two S1675 probes inserted before the single build. a.ps1 fk exit 0, a.ps1 dq exit 0, APK v2.60.8151.612.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The five checks in strategic §3.4 are now all reachable on `SM-G996U1`; the ticket's remaining gate is that device run.

---

## Rollback Plan

Revert the phase commit - three modified files, no persisted state; restore `CameraCaptureActivity.kt` from its timestamped backup if the edit went wrong.
