# Phase 8 — VR Settings: Forced 360° Format Options

**Status:** Implemented (2026-04-19) · **Depends on:** Phase 2, 7 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Split the legacy global `vrForcedFormat` into flat and spherical defaults so the VR settings block can override both families independently without leaking flat overrides into 360°/VR180 playback.

## Current State

- `VideoSettingsFragment.kt` now exposes separate flat and spherical spinners in the existing VR block.
- `AppSettings` now stores `vrForcedPlatFormat` plus `vrForcedSphericalFormat`.
- `SettingsRepositoryImpl` reads the new keys and falls back to the legacy `vr_forced_format` key for upgrade compatibility.
- `PlayerViewModel` resolves forced formats by family after the per-file Room override.

## Work

1. Split `AppSettings.vrForcedFormat` into:
   - `vrForcedPlatFormat: String = "AUTO"`.
   - `vrForcedSphericalFormat: String = "AUTO"`.
2. Migrate legacy DataStore installs by reading the old `vr_forced_format` key until the new keys are written.
3. Update `SettingsRepositoryImpl`, `BackupMapper`, and `BackupData` for the new schema.
4. Add `vr_forced_spherical_format_values` plus the matching EN/RU/UK labels and descriptions.
5. Add a second spinner in `VideoSettingsFragment` VR block below the flat spinner.
6. Apply family-aware forced-format priority in playback:
   1. Per-file Room override.
   2. Forced spherical setting (if not AUTO and content is detected as spherical).
   3. Forced plat setting (if not AUTO and content is detected as plat).
   4. MP4 `st3d`/`sv3d` (phase 3).
   5. Filename + Matroska.
   6. AR heuristic.
7. Cover the new resolver with a focused unit test.

## Acceptance Criteria

- Existing installs retain their old forced-format preference through the legacy-key fallback until the new split settings are saved.
- Setting "Forced spherical: 360° SBS" causes detected spherical content to render as SBS equirect even without filename hints.
- Setting "Forced plat: MONO" does not affect spherical content.
- Backup/restore roundtrips both fields.
- Focused unit coverage exists for the new split-family resolver.

## Files Touched

- [domain/model/AppSettings.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
- [ui/settings/fragments/VideoSettingsFragment.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt)
- `app_v2/src/main/res/values{,-ru,-uk}/strings.xml`
- [ui/player/PlayerViewModel.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt) — priority order
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolver.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolverTest.kt`
- [app_v2/src/main/res/layout/fragment_settings_video.xml](../../app_v2/src/main/res/layout/fragment_settings_video.xml)

## Validation

- `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests com.sza.fastmediasorter.ui.player.VrForcedFormatResolverTest`

## Out of Scope

- Cinema distance slider (covered by existing IPD/rendering-mode UI).
- Per-resource defaults (covered by the per-file Room override).
