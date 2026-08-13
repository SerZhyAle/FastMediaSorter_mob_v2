# Phase 01 - Profile model

**Strategic spec:** [`../S1262_camera-photo-profile-menu.md`](../S1262_camera-photo-profile-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 3 / 3
**Started:** 2026-07-29
**Completed:** 2026-07-29

---

## Objective

Introduce the `PhotoProfile` model (closed set of profiles with per-device availability predicates) and the `supportsBokehExtension` capability flag; no session or UI changes yet.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] `scripts/utils/lock-status.ps1 -Name Build` shows no live build; acquire `CODE.LOCK` before edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/PhotoProfile.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 250 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/PhotoProfileTest.kt` | New | ≤ 200 |

---

## Steps

### Step 01.1 - Add `supportsBokehExtension` to the capabilities snapshot

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val supportsBokehExtension: Boolean = false` to `CameraRuntimeCapabilities` next to `supportsHdrExtension`, KDoc: BOKEH extension availability on the active lens (S1262, research 01). Default false keeps every existing call site compiling.

**Verification:**

- `Grep` - `supportsBokehExtension` matches in `CameraRuntimeCapabilities.kt` exactly once as a constructor property.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

### Step 01.2 - Create the `PhotoProfile` model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/PhotoProfile.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create enum `PhotoProfile` with entries `NORMAL, NIGHT, PORTRAIT, SELFIE, MACRO, SPORT`. Give it a single pure function `isAvailable(capabilities: CameraRuntimeCapabilities): Boolean` per entry (exhaustive `when`): NORMAL always true; NIGHT = `supportsNightMode`; PORTRAIT = `supportsBokehExtension`; SELFIE = `availableLensFacings` contains the front facing; MACRO = `supportsMacro || macroLensAvailable`; SPORT = `supportsManualSensor && shutterRangeNs != null && isoRange != null && shutterRangeNs.lower <= SPORT_TARGET_EXPOSURE_NS` (companion const `SPORT_TARGET_EXPOSURE_NS = 4_000_000L`, KDoc: 1/250 s target from research 02). Also add companion `available(capabilities): List<PhotoProfile>` preserving declaration order. View-agnostic: no android.view imports.

**Verification:**

- `Glob` - `PhotoProfile.kt` exists.
- `Grep` - `enum class PhotoProfile` matches exactly once.
- `Grep` - `SPORT_TARGET_EXPOSURE_NS` present in `PhotoProfile.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Unit-test availability predicates

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/PhotoProfileTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `PhotoProfileTest` covering: NORMAL always offered; each other profile flips with its capability flag; SPORT hidden when `shutterRangeNs.lower` exceeds the 4 ms target; `available()` keeps declaration order and always starts with NORMAL. Pure JVM test, no Robolectric.

**Verification:**

- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfileTest"` - BUILD SUCCESSFUL.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`PhotoProfile.isAvailable` reads only the capabilities snapshot; Phase 02 must make `supportsBokehExtension` actually true on capable devices (today it is always false).

---

## Rollback Plan

Revert phase commit(s) - additive model code, no user-facing surface changed.
