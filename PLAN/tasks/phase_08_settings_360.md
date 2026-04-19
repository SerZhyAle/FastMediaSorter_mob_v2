# Phase 8 — VR Settings: Forced 360° Format Options

**Status:** Not started · **Depends on:** Phase 2, 7 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Extend the existing VR settings block in `VideoSettingsFragment` so `vrForcedFormat` accepts spherical values, and add a separate spherical-forced dropdown to keep plat-only users out of 360° defaults.

## Current State

- [VideoSettingsFragment.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt) has:
  - `vr_forced_format_values` string-array: `AUTO, SBS, OU, MONO`.
  - `vr_rendering_mode_values`: `CINEMA, FULL_STEREO`.
- `AppSettings.vrForcedFormat: String = "AUTO"` — single field covers plat only.

## Work

1. Split `AppSettings.vrForcedFormat` into:
   - `vrForcedPlatFormat: String = "AUTO"` (keep old key via alias in repo for migration).
   - `vrForcedSphericalFormat: String = "AUTO"` (new).
2. Room migration / SharedPreferences migration: rename `vrForcedFormat` → `vrForcedPlatFormat`, default new spherical key to `"AUTO"`. Bump DB version if needed.
3. Update `SettingsRepositoryImpl`, `BackupMapper`, `BackupData`.
4. Add `vr_forced_spherical_format_values` string-array: `AUTO, EQUIRECT_360_MONO, EQUIRECT_360_SBS, EQUIRECT_360_OU, EQUIRECT_180_SBS, VR180_FISHEYE_SBS, CYLINDER_180`.
5. Add a second spinner in `VideoSettingsFragment` VR block (below plat spinner).
6. Localise: EN/RU/UK arrays + labels.
7. `StereoDetector` priority order with forced values:
   1. Per-file Room override.
   2. Forced spherical setting (if not AUTO and content is detected as spherical).
   3. Forced plat setting (if not AUTO and content is detected as plat).
   4. MP4 `st3d`/`sv3d` (phase 3).
   5. Filename + Matroska.
   6. AR heuristic.

## Acceptance Criteria

- Users with existing installs retain their `vrForcedFormat` as `vrForcedPlatFormat` after upgrade (no reset to AUTO).
- Setting "Forced spherical: 360° SBS" causes any detected-spherical content to render as SBS equirect even without filename hints.
- Setting "Forced plat: MONO" does not affect spherical content.
- Backup/restore roundtrips both fields.

## Files Touched

- [domain/model/AppSettings.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
- [ui/settings/fragments/VideoSettingsFragment.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt)
- `app_v2/src/main/res/values{,-ru,-uk}/strings.xml`
- `app_v2/src/main/res/values/arrays.xml`
- [ui/player/StereoDetector.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt) — priority order
- [app_v2/src/main/res/layout/fragment_settings_video.xml](../../app_v2/src/main/res/layout/fragment_settings_video.xml)

## Out of Scope

- Cinema distance slider (covered by existing IPD/rendering-mode UI).
- Per-resource defaults (covered by the per-file Room override).
