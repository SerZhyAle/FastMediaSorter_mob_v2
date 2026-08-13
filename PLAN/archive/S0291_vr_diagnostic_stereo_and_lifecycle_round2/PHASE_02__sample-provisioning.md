# Phase 02 - Sample Provisioning

**Strategic spec:** [`../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md`](../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Make the diagnostic sample set deterministic, honestly named, motion-capable, and visibly labeled for per-eye checks.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/setup_test_vr.ps1` | Modified | ≤ 750 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 850 |

---

## Steps

### Step 02.1 - Reset generated canonical samples before provisioning

**Files:** `setup_test_vr.ps1`
**Depends on:** start of phase

**Prompt for developer:**

> Delete canonical generated outputs before downloads and derivations so stale local cache cannot preserve a wrong sample under a correct name.

**Verification:**

- `Grep` - `$canonicalSampleNames` appears in `setup_test_vr.ps1`.
- `Grep` - `Removing stale generated sample cache` appears in `setup_test_vr.ps1`.

**Status:** `[x]` done

### Step 02.2 - Replace color boxes with visible L/R text labels

**Files:** `setup_test_vr.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Use explicit fontfile-backed drawtext labels when a Windows font exists, with a drawbox fallback. Apply labels to synthetic stereo and real stereo replacements.

**Verification:**

- `Grep` - `Resolve-LabelFontFile` appears in `setup_test_vr.ps1`.
- `Grep` - `drawtext=fontfile` appears in `setup_test_vr.ps1`.
- `Grep` - `Add-EyeLabelsVideo` appears in `setup_test_vr.ps1`.

**Status:** `[x]` done

### Step 02.3 - Replace weak or static motion samples

**Files:** `setup_test_vr.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Use Bino mono rolling-marbles clips for 360 and 180 mono videos, keep Bino 360 stereo for stereo motion, and upgrade the flat mono clip to the official 1080p Blender trailer.

**Verification:**

- `Grep` - `rolling-marbles-360.mp4` appears in `setup_test_vr.ps1`.
- `Grep` - `rolling-marbles-180.mp4` appears in `setup_test_vr.ps1`.
- `Grep` - `trailer_1080p.mov` appears in `setup_test_vr.ps1`.

**Status:** `[x]` done

### Step 02.4 - Rename misleading Colosseum slot

**Files:** `setup_test_vr.ps1`, `DiagnosticXrActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Replace `colosseum_flat_mono.jpg` with `lakeside_flat_mono.jpg` in generated sample output, deploy order, and runtime playlist order.

**Verification:**

- `Grep` - `lakeside_flat_mono.jpg` appears in `setup_test_vr.ps1`.
- `Grep` - `lakeside_flat_mono.jpg` appears in `DiagnosticXrActivity.kt`.
- `Grep` - `colosseum_flat_mono.jpg` returns zero hits in `setup_test_vr.ps1` and `DiagnosticXrActivity.kt`.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Script dry-run without device exits 0.
- [x] Kotlin catalog sync runs after playlist edit.

## Handoff Notes to Next Phase

The playlist names and sample generation should now match what the owner sees in the headset.

## Rollback Plan

Revert phase commit(s). Generated files live under `temp/` and device storage only.
