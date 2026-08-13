# Phase 02 - Local State And Settings Model

Goal: add typed app-local persistence for the game and a default-off Settings flag.

## Files

Modify:
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SettingsRepository.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportSettingsUseCase.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt`

Create:
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameStateRepository.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameStateSnapshot.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/game/GameStateRepositoryImpl.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/data/game/GameStateRepositoryImplTest.kt`

## Steps

- [x] Back up `SettingsRepositoryImpl.kt` to `temp/` before editing because it is over 500 LOC.
- [x] Add `AppSettings.embeddedGameEnabled: Boolean = false` near general or UI settings with a why-comment only if the placement is not obvious.
- [x] Persist `embedded_game_enabled` in `SettingsRepositoryImpl` and include it in default/reset flows.
- [x] Add `updateEmbeddedGameEnabled(enabled: Boolean)` to `SettingsRepository` and `SettingsViewModel`.
- [x] Include the setting in settings backup/export/import so user configuration survives app migration.
- [x] Implement `GameStateSnapshot` with explicit `schemaVersion`, level, board, actors, stats, difficulty, custom-board metadata, and last-updated timestamp.
- [x] Implement `GameStateRepositoryImpl` using DataStore Preferences plus Gson-backed typed payload. Do not introduce Room.
- [x] Reset incompatible or malformed game state to a new-game state and log a non-ticket Timber warning.
- [x] Add tests for default disabled setting, settings round-trip, schema mismatch reset, malformed payload reset, and save/load of an active level.

## Verification

- `rg "embeddedGameEnabled|embedded_game_enabled" app_v2/src/main/java app_v2/src/test/java` finds settings model, repository, import/export, and tests.
- `rg "Room|@Entity|@Dao" app_v2/src/main/java/com/sza/fastmediasorter/data/game` returns no matches.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` succeeds.
- Focused unit tests for settings and `GameStateRepositoryImpl` succeed or any unrelated failures are recorded.

## Done

- [x] Game enablement defaults to off.
- [x] Game state is versioned, local, typed, and resilient to corruption.
- [x] Backup/export/import include the enablement flag.

## Step Log

- 2026-05-31 - Static verification PASS: `embeddedGameEnabled`/`embedded_game_enabled` coverage found in settings model, repository, ViewModel, backup/import/export; `data/game` has no Room annotations/imports; Problems reports no new Phase 02 file errors; catalog sync PASS. Focused Gradle tests were blocked by Windows generated-output failures (`kapt` generated files and `processStandardDebugResources` unable to delete linked resource output), not by S0316 code.