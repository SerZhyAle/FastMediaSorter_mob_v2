# Phase 01 - Shared: VR launch point + transport-row button

**Status:** ✅ Done
**Completed:** 2026-07-19

## Step 01.1 - Add CONTROLS_ROW to VrLaunchPoint

**Status:** `[x] done`

**Files Touched:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt`

New analytics source for VR entry from the transport controls row (both hosts).

**Step Log:**
- 2026-07-19 - added `CONTROLS_ROW`. PASS (compiles).

## Step 01.2 - Add btnVrLaunch to both controller layouts

**Status:** `[x] done`

**Files Touched:**
- `app_v2/src/main/res/layout/custom_player_controls.xml`
- `app_v2/src/main/res/layout/custom_player_controls_large.xml`

`ImageButton @+id/btnVrLaunch` in `exoPlayerButtonRow` after PiP; `ic_vr_headset`, `action_open_in_vr_cinema` desc, focusable, `visibility=gone`. No landscape variant exists for these controller overlays. Shared by both hosts via `?attr/customPlayerControlsLayout`.

**Step Log:**
- 2026-07-19 - added to both variants. PASS (grep + build).
