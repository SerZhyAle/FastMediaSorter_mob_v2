# Phase 03 - Asset License

**Strategic spec:** [`../S0249_vr-test-immersive-image-stage1a.md`](../S0249_vr-test-immersive-image-stage1a.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19
**Started:** -
**Completed:** -

---

## Objective

Bundle one permissively licensed stereo top-bottom equirect diagnostic image and record attribution in the project.

---

## Prerequisites

- [ ] Phase 02 is Done.
- [ ] Asset source, license, dimensions, and re-encode decision blocker in `INDEX.md` is closed.
- [ ] Attribution path blocker in `INDEX.md` is closed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/res/drawable-nodpi/vr_diagnostic_stereo_tb.jpg` | New | <= 3 MB |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/assets/DiagnosticXrAssetProvider.kt` | New | <= 160 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt` | Modified | <= 160 |
| `THIRD_PARTY_LICENSES.md` or approved existing attribution file | Modified/New | <= 500 |

---

## Steps

### Step 03.1 - Add licensed diagnostic asset

**Files:** `app_v2/src/vr/res/drawable-nodpi/vr_diagnostic_stereo_tb.jpg`
**Depends on:** start of phase

**Prompt for developer:**

> Download the bundled diagnostic image from `https://raw.githubusercontent.com/Navier8/Godot-Simple-Stereoscopic-360VR-Panorama/main/blender_test.jpg` (MIT license, ~651 KB, stereo top-bottom equirect, Blender-rendered, OpenXR-validated). Save **as-is** to `app_v2/src/vr/res/drawable-nodpi/vr_diagnostic_stereo_tb.jpg`. Do NOT re-encode unless the file exceeds the 3 MB budget — Navier8 source is already optimized. Record exact pixel dimensions in `temp/S0249_asset_dimensions.txt` for the strategic spec audit trail.

**Verification:**

- `Glob` - `app_v2/src/vr/res/drawable-nodpi/vr_diagnostic_stereo_tb.jpg` exists.
- `PowerShell` - file size is `<= 3145728` bytes (3 MB budget) AND `>= 600000` bytes (sanity: not corrupted/empty).
- `PowerShell` - image dimensions recorded in `temp/S0249_asset_dimensions.txt` — expected ≥ 2048×2048 (per-eye ≥ 2048×1024 in TB layout).
- `Manual` - the source URL (`github.com/Navier8/Godot-Simple-Stereoscopic-360VR-Panorama`) and MIT license terms are noted alongside in the same evidence file.

**Status:** `[x]` done (2026-05-19)

---

### Step 03.2 - Add asset provider

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/assets/DiagnosticXrAssetProvider.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a VR-only asset provider that opens the bundled image resource and exposes the stereo layout metadata as top-bottom equirect. Keep decoding/upload responsibilities outside Settings UI.

**Verification:**

- `Glob` - `DiagnosticXrAssetProvider.kt` exists.
- `Grep` - `vr_diagnostic_stereo_tb` appears in `DiagnosticXrAssetProvider.kt`.
- `Grep` - `TOP_BOTTOM` or equivalent layout token appears in `DiagnosticXrAssetProvider.kt`.
- `Grep` - `Log.d(` returns zero hits in `DiagnosticXrAssetProvider.kt`.

**Status:** `[x]` done (2026-05-19)

---

### Step 03.3 - Bind asset provider

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Bind or provide the asset provider through the VR Hilt module and inject it into the runtime facade. The native runtime must receive decoded image data or a safe descriptor without direct Settings coupling.

**Verification:**

- `Grep` - `DiagnosticXrAssetProvider` appears in `XrModule.kt`.
- `Grep` - `DiagnosticXrAssetProvider` appears in `NativeDiagnosticXrRuntime.kt`.
- `Grep` - `R.drawable.vr_diagnostic_stereo_tb` appears in one VR-only Kotlin file.

**Status:** `[x]` done (2026-05-19)

---

### Step 03.4 - Record attribution

**Files:** `THIRD_PARTY_LICENSES.md` or approved existing attribution file
**Depends on:** Step 03.1

**Prompt for developer:**

> Add the required license notice for the selected image asset. If the approved attribution path is an existing app credits surface, update that file instead and keep wording in English.

**Verification:**

- `Grep` - selected asset author or project name appears in the attribution file.
- `Grep` - selected license identifier appears in the attribution file.
- `Grep` - selected source URL appears in the attribution file.

**Status:** `[x]` done (2026-05-19)

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x]` done.
- [ ] Project compiles - run `/build` for VR debug.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog scan/render run after Kotlin changes.

---

## Handoff Notes to Next Phase

The runtime can load a packaged, licensed diagnostic image without any network or file-picker dependency.

---

## Rollback Plan

Revert Phase 03 commit(s) and remove the asset plus attribution entry.
