# Phase 01 - Capture host foundation

**Strategic spec:** [`../S0545_camera-capabilities-expansion.md`](../S0545_camera-capabilities-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04, Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Introduce a mode-aware, helper-managed capture host so `CameraCaptureActivity` can own both photo and video without accumulating business logic.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureContract.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraCaptureMode.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | New | ≤ 320 |

---

## Steps

### Step 1.1 - Add an explicit capture contract and mode enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureContract.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraCaptureMode.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Introduce one contract file that owns every intent extra and result extra for `CameraCaptureActivity`: output `Uri`, output path, requested mode, microphone default, and result media kind. Add `CameraCaptureMode` as a small enum with at least `PHOTO` and `VIDEO`. Keep the contract backward-compatible for existing photo callers by defaulting the mode to `PHOTO` when the extra is absent.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureContract.kt` exists.
- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraCaptureMode.kt` exists.
- `Grep` - `enum class CameraCaptureMode` matches once in `CameraCaptureMode.kt`.
- `Grep` - `EXTRA_CAPTURE_MODE` and `EXTRA_RESULT_MEDIA_KIND` are present in `CameraCaptureContract.kt`.

**Status:** `[ ]` not done

---

### Step 1.2 - Move capture host decisions out of the Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Create `CameraCaptureFlowManager` to own output-file resolution, initial mode selection, permission callbacks, result packing, and close/error decisions. Reduce `CameraCaptureActivity` to view binding, launcher registration, and delegating button/touch events into the helper. Do not move heavy business logic into the Activity while expanding the capture feature.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` exists.
- `Grep` - `class CameraCaptureFlowManager` matches once.
- `Grep` - `private lateinit var flowManager: CameraCaptureFlowManager` is present in `CameraCaptureActivity.kt`.
- `Grep` - `Log\.d\(` returns zero hits across the touched Kotlin files.

**Status:** `[ ]` not done

---

### Step 1.3 - Route the existing photo flow through the new host contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`
**Depends on:** Step 1.2

**Prompt for developer:**

> Rewire the current in-app photo bind/capture path through `CameraCaptureFlowManager` without changing its observable behavior: CAMERA permission on demand, same close button behavior, same `RESULT_OK`/`RESULT_CANCELED` semantics, and same temp-file target contract. Preserve the existing photo-only entry points until Phase 05 migrates every caller to explicit mode selection.

**Verification:**

- `Grep` - `CameraCaptureContract` is referenced from both `CameraCaptureActivity.kt` and `CameraCaptureFlowManager.kt`.
- `Grep` - `CameraCaptureMode.PHOTO` is present in the default-flow path.
- `Grep` - `putExtra(EXTRA_RESULT_MEDIA_KIND` is present in the result-packing path.
- `Grep` - `Log\.d\(` returns zero hits across the touched Kotlin files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`CameraCaptureActivity` is now a thin host with an explicit photo/video contract. Phase 02 can extend session capabilities without re-architecting the host again.

---

## Rollback Plan

Revert phase commit(s) - host-only refactor, no Room schema, no user-facing camera controls yet.
