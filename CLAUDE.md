# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication

- **Language**: RUSSIAN in chat/conversation, ENGLISH in code, docs, logs, and commit messages.
- **Tone**: PROFESSIONAL / DRY / CONCISE. No filler, no guessing — ask if ambiguous.
- **Missing input**: Request the file or data needed. DO NOT hallucinate values or paths.

## Author Style Rules (MUST be preserved in all text output)

The author has a deliberate writing style. All AI agents MUST follow these rules when writing or editing any user-facing text, documentation (`.md` files), or UI strings (`strings.xml`):

1. **Ellipsis**: Use `..` (two dots) — NEVER `...` (three dots). This applies everywhere: docs, UI strings, chat responses, specs.
2. **Ё/ё**: Always use the letter `ё` (and `Ё`) in Russian text where it is grammatically correct — NEVER substitute with `е`/`Е`. Examples: `всё`, `ещё`, `чёрный`, `объём`, `тёмный`, `нельзя` → keep as-is; `твердо` → `твёрдо`, `прием` → `приём`, `кино` → remains `кино` (no ё).

These are non-negotiable stylistic preferences, not typos.

---

## Skill Rules

These skills MUST be used automatically — do not handle these tasks manually:

| Trigger | Skill | Rule |
|---------|-------|------|
| Creating or updating any `PLAN/spec_*.md` file | `/spec` | **Mandatory** — enforces full project template including flavor scope, API-level analysis, architecture compliance, testing plan, accessibility, and ADRs |
| Updating documentation files (`docs/FEATURES*.md`, `docs/TECH_STACK.md`, or any feature docs) | `/doc-update` | **Mandatory** — ensures EN/RU/UK mirrors stay in sync and format is consistent |
| User asks to analyze logs, read `logs/current.log`, or diagnose a runtime issue from logcat | `/log-reader` | **Mandatory** — provides structured Android logcat analysis |
| User asks how to build, which build command to use, or wants to trigger a build | `/build` | **Mandatory** — routes to the correct flavor/variant build command |
| User asks about git commits, staging, pushing, diffs, old file versions, or "what should I commit" | `/git` | **Mandatory** — provides project-aware git workflow guidance |

---

## Research Entrypoints

Before making changes, read these files in order based on task type:
- **All tasks**: Start with `dev/PROJECT_OPERATIONS_INDEX.md` (workspace/module routing + Feature-to-Path Map)
- **Architecture/data flow**: `docs/ARCHITECTURE.md`
- **Build/scripts/flags**: `docs/DEV_OPS.md` + `app_v2/build.gradle.kts`
- **Dependencies/protocols**: `docs/TECH_STACK.md` + `dev/TECH_REQUIREMENTS.md`
- **Network tasks**: `dev/NETWORK_SPECS.md` (SMB/SFTP/FTP/protocol-specific constraints)
- **Multi-step tasks**: `dev/AGENT_WORKFLOW.md` — **MUST be read BEFORE execution** for any task larger than a single-file fix (mandatory 5-step process)

**Research order**: `dev/PROJECT_OPERATIONS_INDEX.md` → domain-specific doc → implementation files. Use the `Feature-to-Path Map` section before doing a global search.

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

- **Language**: Kotlin 1.9+, Java 17, `compileSdk 35`, `minSdk 26` (Android 8+); `legacy` flavor `minSdk 23`
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
7. **Lint**: ALWAYS resolve warnings in files you touch. Use canonical naming only.
8. **Backup files**: Ignore `*.backup` files in primary analysis unless the user explicitly requests a historical comparison.
9. **Code comments — read before modify**: Before editing any file, read ALL existing inline comments and KDoc/Javadoc in the affected area. Comments explain intent, constraints, and non-obvious decisions — treat them as requirements, not noise.
10. **Code comments — write on modify**: When adding or changing logic, add an inline comment explaining WHY (not what) whenever the reason is not immediately obvious from the code. Update or remove stale comments that no longer reflect reality.

## Feature Inventory

`docs/FEATURES.md` is the **canonical** up-to-date list of all 21 user-facing feature areas (Resource Management, Browsing, File Ops, Player, Audio, Slideshow, PDF/EPUB/Text viewers, OCR/Translation, Network, Cloud, Favorites, Widgets, Settings, Wear OS, Background Services). Read it before implementing anything to understand existing scope and avoid duplicating work. Russian and Ukrainian mirrors: `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

## Mandatory Post-Change Steps

**Applies to ALL agents (Copilot, Cursor, Windsurf, CLI agents). NO exceptions.**

### 1. Dev Changelog — after EVERY code/config file change

Run at the end of each implementation step, BEFORE moving to the next task:
```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<class_or_target>" "<short_description>"
```
This appends a timestamped row to `dev/CHANGELOG.md`. Never edit `CHANGELOG.md` directly.

Example:
```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/.../GlideAppModule.kt" "GlideAppModule" "Fixed memory cache formula to heap×10%"
```

### 2. Feature Docs — after implementing any new user-facing feature

At the end of Step 4 (Implementation), BEFORE marking the task complete, update all three language variants:
- `docs/FEATURES.md` (EN — canonical)
- `docs/FEATURES_RU.md` (RU)
- `docs/FEATURES_UK.md` (UK)

Add a concise bullet under the relevant section. Keep consistent style with existing entries.

## Version Format

`Y.YM.MDDH.Hmm` — e.g., `2.60.1102.207` = 2026/01/10 20:07. See `dev/CHANGELOG.md` for history.
