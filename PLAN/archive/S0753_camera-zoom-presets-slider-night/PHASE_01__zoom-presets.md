# Phase 01 - Zoom presets

**Strategic spec:** [`../S0753_camera-zoom-presets-slider-night.md`](../S0753_camera-zoom-presets-slider-night.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Expand the zoom-preset candidate set to 0.5, 1, 2, 3, 10, 20, 30 clamped to the lens range, and always append the lens maximum so a "ceiling" preset is reachable; cover the pure builder with a unit test. No UI or session change yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6.5 (guaranteed-maximum) and §6 research artifact `research/02__zoom-presets-clamping.md` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilitiesTest.kt` | New | ≤ 160 |

> Camera capture lives in `src/main` and ships in all flavors (standard/lite/photos/legacy) - no flavor source-set split for this feature.

---

## Steps

### Step 01.1 - Widen the zoom-preset candidate list and guarantee the lens maximum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `buildZoomPresets(minZoom, maxZoom)` replace the candidate list `listOf(minZoom, 1f, 2f, maxZoom)` with `listOf(minZoom, 1f, 2f, 3f, 10f, 20f, 30f, maxZoom)`. Keep the existing `filter { it in minZoom..maxZoom }`, the one-decimal rounding `(it * 10f).toInt() / 10f`, `distinct()` and `sorted()`. Appending `maxZoom` is deliberate: on a device whose max is e.g. 8x, the table values 10/20/30 are filtered out and `maxZoom` keeps a reachable ceiling preset (strategic ADR-1). Do not add comments restating the list; the existing KDoc already explains intent - update the KDoc wording from "1x, 2x and the maximum" to reflect the new steps.

**Verification:**

- `Grep` - `0.5f` not hardcoded; `listOf(minZoom, 1f, 2f, 3f, 10f, 20f, 30f, maxZoom)` matches exactly once in `CameraRuntimeCapabilities.kt`.
- `Grep` - `it in minZoom..maxZoom` still present (clamp preserved).
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 3/3 PASS (candidate-list grep, clamp grep, `a.ps1 fk` BUILD SUCCESSFUL 24s). Files: CameraRuntimeCapabilities.kt.

---

### Step 01.2 - Unit-test the preset builder

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilitiesTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `CameraRuntimeCapabilitiesTest` (JUnit4, no Android framework needed - `buildZoomPresets` is pure). Cover: (a) a wide lens `min=0.5, max=30` yields exactly `[0.5, 1, 2, 3, 10, 20, 30]`; (b) a mid lens `min=1, max=8` yields `[1, 2, 3, 8]` - table values 10/20/30 dropped, lens max 8 appended; (c) a no-zoom lens `min=1, max=1` yields empty; (d) an ultra-wide-less lens `min=1, max=2` yields `[1, 2]`; (e) the result is sorted and de-duplicated (max equal to a table value, e.g. `min=1, max=3`, yields `[1, 2, 3]` with no duplicate 3). Use exact float assertions on the rounded values.

**Verification:**

- `Glob` - `CameraRuntimeCapabilitiesTest.kt` exists at the path above.
- `Grep` - `class CameraRuntimeCapabilitiesTest` matches once.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*CameraRuntimeCapabilitiesTest"` passes (read the per-class XML in `app_v2/build/test-results/`; ignore unrelated pre-existing failures).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 3/3 PASS (file exists, `class CameraRuntimeCapabilitiesTest` grep, `testStandardDebugUnitTest --tests *CameraRuntimeCapabilitiesTest` BUILD SUCCESSFUL 45s = all 5 cases green). Files: CameraRuntimeCapabilitiesTest.kt (New).

---

## Phase Done Criteria

- [x] Both `Step 01.*` are `[x] done`.
- [x] Project compiles - `a.ps1 fk` (compileStandardDebugKotlin) + `testStandardDebugUnitTest` both BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [~] Dev log entry - batched into Phase 05 finalization (`close-and-log.ps1`) per CLAUDE.md (one entry per logical change).
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - deferred to Phase 05 (`catalog_sync.ps1` once per ticket).

---

## Handoff Notes to Next Phase

`buildZoomPresets` now emits the full step set clamped to the lens, with a guaranteed maximum. Phase 02 consumes the same `CameraRuntimeCapabilities` snapshot and adds a continuous linear-zoom field for the slider.

---

## Rollback Plan

Revert the phase commit - no data migration or user-facing surface changed (preset list only widens; empty-range behaviour unchanged).
