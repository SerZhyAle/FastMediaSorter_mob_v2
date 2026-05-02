# Phase 04 — Docs and Catalog Cleanup

**Status:** ⬜ Not Started
**Phase slug:** docs-catalog-cleanup
**Ticket:** S0041
**Depends on:** Phase 03 complete

---

## Goal

After fix is confirmed: remove debug logging, update spec to Implemented, sync catalog and dev log.

---

## Steps

### Step 4.1 — Remove VR_QUALITY_DEBUG log lines

**Status:** `[ ] not done`

**Files touched:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt`

**Prompt for developer:**
Remove the two `VR_QUALITY_DEBUG` Timber.d blocks added in Phase 01 (Steps 1.2 and 1.3). Also remove the `if (dbgRenderEyeCount == 0L)` guard block in `renderFisheyeQuad`.

**Verification:**
- `Select-String -Path "app_v2/src/**/*.kt" -Pattern "VR_QUALITY_DEBUG" -Recurse` — 0 matches.

---

### Step 4.2 — Sync dev catalog

**Status:** `[ ] not done`

**Prompt for developer:**
```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

**Verification:**
- `Test-Path "dev/CATALOG/app_v2.md"` returns `True`.

---

### Step 4.3 — Advance spec status to Implemented

**Status:** `[ ] not done`

**Prompt for developer:**
```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0041 -Status Implemented
```
Also update `PLAN/S0041_debug-vr180-fisheye-quality-regression.md`:
- Replace `**Status:** Approved` with `**Status:** Implemented`
- Add `**Implemented date:** 2026-<MM-DD>`

**Verification:**
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0041 -Format json | ConvertFrom-Json | Select-Object status` shows `Implemented`.

---

## Phase Done Criteria

- [ ] 1. Zero `VR_QUALITY_DEBUG` matches in `app_v2/src/`.
- [ ] 2. Catalog synced (`dev/CATALOG/app_v2.md` updated).
- [ ] 3. S0041 status = `Implemented` in catalog.

---

## Step Log

<!-- append entries after each step completes -->
