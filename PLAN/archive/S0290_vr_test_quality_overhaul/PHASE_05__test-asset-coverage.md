# Phase 05 - Test Asset Coverage Expansion

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 08
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Expand `setup_test_vr.ps1` so that for every combination of `(projection, layout)` the detector chain can encounter, at least one representative file with an explicit name marker is pushed to the device. Also update `VR_TEST_MEDIA_ORDER` in `DiagnosticXrActivity.kt` to enumerate the expanded set so external files appear in the playlist.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (filename parser now understands all the new markers).
- [ ] Working tree is clean or on a feature branch.
- [ ] Internet access for downloading CC0 / CC-BY samples.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/setup_test_vr.ps1` | Modified | ≤ 250 (current ≈ 140) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 740 |

---

## Steps

### Step 05.1 - Expand asset download set in setup_test_vr.ps1

**Files:** `scripts/utils/setup_test_vr.ps1`
**Depends on:** start of phase

**Prompt for developer:**

> Add entries to the `$assets` array so the final harvested set includes at least one file for each combo below. For each entry, store not only `Url` and `LocalName`, but also `License`, `Sha256`, `ExpectedProjection`, `ExpectedLayout`, and `Required` (`$true` / `$false`). `LocalName` must contain the canonical marker so Phase 03's name parser hits it. The script must be idempotent: if a local cached file exists and its `sha256` matches, skip download; if hash mismatches, re-download once and fail/report if the mismatch persists. Optional assets may fail soft with a clear summary; required assets must surface a non-zero exit after the summary.
>
> Required combos (skip any only if a free source genuinely cannot be located — document the gap in Blockers Log):
> - `mono_360.jpg` (already covered by bundled — push externally too as redundancy check)
> - `360_TB.jpg` (stereo top-bottom 360)
> - `360_SBS.jpg` (stereo side-by-side 360)
> - `VR180_LR.jpg` (180° SBS)
> - `180_TB.jpg` (180° top-bottom)
> - `flat_mono.jpg` (16:9 reference)
> - `dual_fisheye.jpg` (raw 360 camera output)
> - `EAC_360.jpg` (cubemap projection — optional, document the gap in Blockers Log if not found)
> - Short videos: `video_360_mono.mp4`, `video_360_TB.mp4`, `video_180_LR.mp4`, `video_flat_mono.mp4`
>
> Recommended sources (already verified in this project's history):
> - Poly Haven HDRIs (8K tonemapped JPG, CC0): <https://polyhaven.com/a/<name>>
> - Google spatial-media samples: <https://github.com/google/spatial-media/tree/master/spatialmedia/resources/v2>
> - Wikimedia Commons VR-tagged panoramas.
>
> Each entry's comment must include the source URL and license. Do **not** count a synthetic rename / copy of one projection as valid coverage for another projection (for example, a duplicated 360 stereo video renamed as 180) unless it is explicitly marked `fallback` and logged as a known coverage gap. For each asset that uses CC BY-SA, add an attribution block to `THIRD_PARTY_LICENSES.md` only if that asset ever becomes bundled or redistributed with the app; dev-host-only pushed samples stay tracked in the script metadata.

**Verification:**

- `Grep` - `$assets` array in `setup_test_vr.ps1` contains at least 8 entries (count `Url\s*=` matches inside the array).
- `Grep` - `Sha256\s*=` matches at least 8 times inside the `$assets` array.
- `Grep` - `Required\s*=` matches at least 8 times inside the `$assets` array.
- `Grep` - each canonical marker substring (`360_TB`, `360_SBS`, `VR180_LR`, `180_TB`, `flat_mono`, `dual_fisheye`) appears at least once in `LocalName` fields of the script.
- `Grep` - `w3schools` matches zero times in `setup_test_vr.ps1`.
- Manual: run `pwsh -NoProfile -File scripts/utils/setup_test_vr.ps1` on a workstation with a Quest 3 attached; confirm all assets downloaded and pushed without error.

**Status:** `[ ]` not done

---

### Step 05.2 - Update VR_TEST_MEDIA_ORDER to enumerate the expanded set

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Replace the existing `VR_TEST_MEDIA_ORDER` (8 hardcoded filenames) with the expanded list matching the new `setup_test_vr.ps1` output. Order should be: bundled (handled by Phase 02 PlaylistEntry, not in this list) → all 360 mono → all 360 stereo → all 180 → all flat → all video variants. Missing files on device are silently skipped by `scanMediaFiles()` — that behaviour stays. Every filename in `VR_TEST_MEDIA_ORDER` must correspond 1:1 to a `LocalName` entry in the script; do not keep aliases that exist only because of a synthetic duplicate.

**Verification:**

- `Grep` - `VR_TEST_MEDIA_ORDER = listOf` is followed by at least 11 string entries inside the parentheses.
- Each filename in the list is also present in `setup_test_vr.ps1` `LocalName` set (cross-check).

**Status:** `[ ]` not done

---

### Step 05.3 - Add Timber.d probe summarising playlist composition with detected formats

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> After `scanMediaFiles()` finishes building the external file list, iterate once and call `formatDetector.detect(file)` for each, log a summary line per file: `Timber.d("DiagnosticXrActivity: playlist-detect ${file.name} -> ${format.projection}/${format.layout} swap=${format.swapEyes} (${format.explainer})")`. This is a one-time scan at startup, not per-frame — performance acceptable. Keep the log neutral / non-ticket in this phase.

**Verification:**

- `Grep` - `Timber\.d\("DiagnosticXrActivity: playlist-detect` matches exactly once in `DiagnosticXrActivity.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (target: `nd`).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits (or documented gaps in Blockers Log).
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Script summary explicitly reports which required assets were validated by hash and which optional assets, if any, were skipped.
- [ ] On-device: `setup_test_vr.ps1` pushes ≥ 8 image files + ≥ 3 video files; HUD shows correct format label for each as the playlist cycles.

---

## Handoff Notes to Next Phase

The test bench now covers every named marker combination. Subsequent phases (06, 07) focus purely on render quality and don't need new assets. The asset list is data-driven — adding more samples is a one-line change in `setup_test_vr.ps1` + one-line in `VR_TEST_MEDIA_ORDER`.

---

## Rollback Plan

Revert phase commits — script falls back to the smaller asset set, playlist enumerates fewer files. No native or persistence change.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability, stability)
	- Applied: 4. Proposed (DISCUSS): 0.
