# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Research Entrypoints

Before making changes, read these files in order based on task type:
- **All tasks**: Start with `dev/PROJECT_OPERATIONS_INDEX.md` (workspace/module routing + Feature-to-Path Map)
- **Architecture/data flow**: `docs/ARCHITECTURE.md`
- **Build/scripts/flags**: `docs/DEV_OPS.md` + `app_v2/build.gradle.kts`
- **Dependencies/protocols**: `docs/TECH_STACK.md` + `dev/TECH_REQUIREMENTS.md`
- **Multi-step tasks**: `dev/AGENT_WORKFLOW.md` (mandatory 5-step process)

## Build Commands (PowerShell)

```powershell
# Debug builds
.\build-debug.PS1                          # fast standard debug
.\dev\build-with-version.ps1               # standard debug with version bump
.\gradlew.bat assembleStandardDebug        # gradle direct
.\gradlew.bat assembleLiteDebug
.\gradlew.bat assemblePhotosDebug
.\gradlew.bat assembleLegacyDebug
.\gradlew.bat :wear:assembleDebug

# Release
.\gradlew.bat assembleStandardRelease
.\scripts\builders\build-standard-release.ps1

# Unit tests
.\gradlew.bat testStandardDebugUnitTest

# Lint
.\gradlew.bat lintStandardDebug
```

## Testing (Maestro E2E)

```powershell
.\scripts\utils\run-maestro-smoke.ps1               # smoke tests (~2-3 min)
.\scripts\utils\run-maestro-smoke.ps1 -Suite critical
```

Tests live in `maestro/smoke/` and `maestro/critical/`. Do **not** use `npm install -g maestro-cli`.

## Device / Log Scripts

```powershell
.\scripts\utils\extract-device-logs.ps1             # pull logcat + prefs
.\scripts\builders\build-standard-device.ps1        # build + ADB install
.\scripts\utils\search-log.ps1-Errors              # search temp/current.log
```

## Modules & Package Structure

| Module | Root | Purpose |
|--------|------|---------|
| `app_v2/` | `app_v2/src/main/java/com/sza/fastmediasorter/` | Main Android app |
| `wear/` | `wear/src/main/java/com/sza/fastmediasorter/wear/` | Wear OS companion |

**Main app layers** (Clean Architecture + MVVM, data flow: `UI → ViewModel → UseCase → Repository → DataSource`):
- `ui/` — Fragments, Activities, ViewModels (zero business logic). Heavy Activity logic delegated to `ui/<feature>/helpers/*Manager.kt`.
- `domain/` — `VerbNounUseCase` classes, repository interfaces.
- `data/` — Repository impls, Room DB, network (SMB/SFTP/FTP), cloud (Drive/OneDrive/Dropbox), transfer strategies.
- `di/` — Hilt modules.
- `core/`, `utils/`, `worker/`, `widget/` — shared infra.

**Key feature paths** (relative to package root):
- App entry: `ui/main/MainActivity.kt`, `ui/main/MainViewModel.kt`
- Browse/media list: `ui/browse/` + `ui/browse/managers/`
- Player: `ui/player/PlayerActivity.kt` + `ui/player/helpers/` (heavy logic here, NOT in the Activity)
- Settings: `ui/settings/SettingsActivity.kt`
- Cloud: `data/cloud/datasource/`
- Network protocols: `data/network/datasource/`
- File transfer: `data/transfer/strategy/`

## Product Flavors

| Flavor | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM |
|--------|:-----:|:-----:|:------:|:-----:|:----:|:----:|
| `standard` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `lite` | ✓ | — | ✓ | — | — | — |
| `photos` | — | — | ✓ | — | — | ✓ |
| `legacy` | ✓ | ✓ | ✓ | — | — | ✓ |

Features are gated via `BuildConfig` fields in `app_v2/build.gradle.kts`.

## Tech Stack Highlights

- **Language**: Kotlin 1.9+, Java 17, `compileSdk 35`, `minSdk 28`
- **DI**: Hilt
- **DB**: Room v6 — increment version on every schema change; migrations in `AppDatabase.kt`
- **Media**: ExoPlayer (Media3 1.2.1)
- **Image loading**: Glide 4.15.1 + custom `NetworkFileModelLoader`
- **Network**: SMBJ (SMB), SSHJ (SFTP), Apache Commons Net (FTP)
- **Cloud**: Google Drive API, MSAL (OneDrive), Dropbox SDK
- **Logging**: **Timber only** — `Log.d()` is prohibited

## Strict Rules

1. **No writes to project root** — all logs, temp artifacts, and pre-modification backups go to `temp/`.
2. **File size limit**: 1000 lines max. Extract excess logic to `helpers/*Manager.kt`.
3. **Activity logic**: prohibited — delegate to Manager/Helper classes.
4. **Read-only zones**: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/` — do not modify.
5. **Backup rule**: if modifying a file >500 lines, create a timestamped backup in `temp/` first.
6. **Naming**: `VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`.

## Feature Inventory

`docs/FEATURES.md` is the **canonical** up-to-date list of all 21 user-facing feature areas (Resource Management, Browsing, File Ops, Player, Audio, Slideshow, PDF/EPUB/Text viewers, OCR/Translation, Network, Cloud, Favorites, Widgets, Settings, Wear OS, Background Services). Read it before implementing anything to understand existing scope and avoid duplicating work. Russian and Ukrainian mirrors: `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

## Mandatory Post-Change Steps

After **every** code/config change:
```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<class_or_target>" "<short_description>"
```
This appends a timestamped row to `dev/CHANGELOG.md`.

After implementing any **new user-facing feature**, update all three:
- `docs/FEATURES.md` (EN)
- `docs/FEATURES_RU.md` (RU)
- `docs/FEATURES_UK.md` (UK)

## Version Format

`Y.YM.MDDH.Hmm` — e.g., `2.60.1102.207` = 2026/01/10 20:07. See `dev/CHANGELOG.md` for history.
