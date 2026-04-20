# Phase 7 — PlaybackControlDialog Spherical RadioGroup

**Status:** Complete · **Depends on:** Phase 2 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Add a second RadioGroup (Spherical: 360°/VR180/Cylinder/OFF) alongside the existing Plat (SBS/OU/MONO) group so users can override detection for any format. Per frozen decision, both groups are always visible; the inactive one is disabled.

## Current State

- [PlaybackControlDialogFragment.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt) now binds two stereo families at once: flat (`AUTO/SBS/OU/MONO`) and spherical (`AUTO/360 Mono/360 SBS/360 OU/VR180/Cylinder 180°`).
- Both layout variants expose the same controls, with the inactive family dimmed by default and unlocked by the `Override format type` switch.
- Manual selections are remembered per file in Room when `vrRememberFileFormat` is enabled and restore on reopen; auto-detect stays silent.

## Work

1. Extend STEREO tab layout (`dialog_playback_control.xml` portrait + `layout-land/dialog_playback_control.xml`) with new section:
   - Label: "Spherical format"
   - RadioGroup: `AUTO · 360° Mono · 360° SBS · 360° OU · VR180 · Cylinder 180°`.
2. Add strings EN/RU/UK (`strings.xml` trio).
3. UX rules per frozen decision:
   - Both groups always visible.
   - Spherical group disabled (alpha 0.5, clickable=false) when detected mode is plat; enabled when spherical.
   - Plat group disabled when detected mode is spherical.
   - User can enable the inactive one via a small "Override format type" switch above both groups.
4. Wire selection to ViewModel:
   - Persist remembered manual overrides per file in Room when `vrRememberFileFormat=true`.
   - Keep the dedicated VR settings screen in sync with the global flat/spherical forced-format defaults.
   - Emit updated `StereoMode` to the active renderer.
5. Toast on manual change (per frozen decision: "Format: 360° SBS"); silent on auto.

## Acceptance Criteria

- Opening dialog on a 360° equirect video shows Spherical group enabled with "360° Mono" checked.
- Opening dialog on SBS video shows Plat group enabled with "SBS" checked, Spherical disabled.
- Manual override → renderer switches mid-playback without buffering pause (stereo only — layer-type changes require a session reconfigure; document the pause if unavoidable).
- Selection persists across device rotation and app restart when remember-flag is on.
- Auto-detect transition (e.g. file change) silently updates the UI without a toast.

## Files Touched

- [ui/player/PlaybackControlDialogFragment.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt)
- `app_v2/src/main/res/layout/dialog_playback_control.xml`
- `app_v2/src/main/res/layout-land/dialog_playback_control.xml`
- `app_v2/src/main/res/values{,-ru,-uk}/strings.xml`
- [ui/player/PlayerViewModel.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt) — accept new mode
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StereoFormatOverride*.kt`

## Out of Scope

- New rendering modes beyond CINEMA/FULL_STEREO (none needed for spherical).
- IPD slider changes (unchanged — already implemented).
