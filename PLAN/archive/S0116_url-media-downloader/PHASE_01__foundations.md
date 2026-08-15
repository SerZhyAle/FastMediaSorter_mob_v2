# Phase 01 — Foundations

**Strategic spec:** [`../S0116_url-media-downloader.md`](../S0116_url-media-downloader.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06, 07
**Steps done:** 8 / 8
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Introduce data classes, BuildConfig flags, AppSettings extension for quality policy, sealed result types, and Hilt module skeleton. No user-facing behaviour is wired yet, but this phase **does** add temporary compile-safe coordinator branches for the newly-added sealed outcomes so later phases can land independently without breaking exhaustive `when` sites.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none)
- [ ] Strategic §6 research items: all Closed.
- [ ] Working tree is clean or on a feature branch.
- [ ] `/build` passes on `standardDebug` baseline.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/link/StreamingManifest.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/link/MediaQualityPreference.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/log/LinkDownloadTrace.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` | Modified | ≤ 150 |

> No file projected >500 lines after change. `SettingsRepositoryImpl.kt` (currently 674) requires backup before edit.

---

## Steps

### Step 01.1 — Add `LOG_LINK_DOWNLOAD` BuildConfig flag

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `buildTypes`, add `buildConfigField("boolean", "LOG_LINK_DOWNLOAD", "true")` to `debug` block and `"false"` to `release` block. Place adjacent to the existing `LOG_NETWORK_THUMBNAILS` field for visual consistency.

**Verification:**

- `Grep` — `LOG_LINK_DOWNLOAD.*true` matches once in `debug { ... }` block.
- `Grep` — `LOG_LINK_DOWNLOAD.*false` matches once in `release { ... }` block.
- `Grep` — `LOG_NETWORK_THUMBNAILS` and `LOG_LINK_DOWNLOAD` co-occur within 5 lines (`Grep -A 5`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: build.gradle.kts (+2 LOC). Dev log recorded.

---

### Step 01.2 — Add streaming dependencies to video-supporting flavors

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `dependencies` block, add `"standardImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")`, `"standardImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")` and equivalent for `legacyImplementation`, `vrImplementation`, `vrUnlicensedImplementation`. Do NOT add for `lite` or `photos`. Remove the `exclude` directives for `media3-exoplayer-dash` and `media3-exoplayer-hls` from the `media3-exoplayer:1.2.1` declaration.

**Verification:**

- `Grep` — `media3-exoplayer-hls:1.2.1` matches exactly 4 times (one per video flavor).
- `Grep` — `media3-exoplayer-dash:1.2.1` matches exactly 4 times.
- `Grep` — `exclude.*media3-exoplayer-hls` returns 0 hits.
- `Grep` — `exclude.*media3-exoplayer-dash` returns 0 hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: build.gradle.kts (+8 LOC). Dev log recorded.

---

### Step 01.3 — Backup `SettingsRepositoryImpl.kt` and `AppSettings.kt`

**Files:** `temp/S0116_phase01_backups/`
**Depends on:** Step 01.2

**Prompt for developer:**

> Copy `SettingsRepositoryImpl.kt` (>500 lines per project rule) and `AppSettings.kt` to `temp/S0116_phase01_backups/<YYYY-MM-DD-HHmm>/` before editing in subsequent steps.

**Verification:**

- `Glob` — `temp/S0116_phase01_backups/*/SettingsRepositoryImpl.kt` matches at least 1 file.
- `Glob` — `temp/S0116_phase01_backups/*/AppSettings.kt` matches at least 1 file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Files: temp/S0116_phase01_backups/2026-05-08-1321/{SettingsRepositoryImpl,AppSettings}.kt. Dev log recorded.

---

### Step 01.4 — Add quality preference fields to `AppSettings`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add two fields to `AppSettings` data class adjacent to `linkAutoDownloadOpenInPlayer`: `val linkDownloadMaxResolution: String = "1080p"` (allowed values: `"480p"`, `"720p"`, `"1080p"`, `"best"`) and `val linkDownloadAudioOnly: Boolean = false`. Add KDoc comments referencing S0116 §5.1 pillar J.

**Verification:**

- `Grep` — `val linkDownloadMaxResolution: String = "1080p"` matches once.
- `Grep` — `val linkDownloadAudioOnly: Boolean = false` matches once.
- `Grep` — `S0116 §5.1 pillar J` matches once in `AppSettings.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: AppSettings.kt (+8 LOC). Dev log recorded.

---

### Step 01.5 — Persist quality preferences in `SettingsRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add DataStore preference keys `KEY_LINK_DOWNLOAD_MAX_RESOLUTION` (`stringPreferencesKey`) and `KEY_LINK_DOWNLOAD_AUDIO_ONLY` (`booleanPreferencesKey`). Place declarations adjacent to existing `KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER`. In the read mapping (next to `linkAutoDownloadOpenInPlayer = ..` near line 362), follow the project's existing whitelist-validation pattern (see `videoSnapshotFormat` lines 356-357): `linkDownloadMaxResolution = preferences[KEY_LINK_DOWNLOAD_MAX_RESOLUTION]?.takeIf { it in setOf("480p", "720p", "1080p", "best") } ?: "1080p"` and `linkDownloadAudioOnly = preferences[KEY_LINK_DOWNLOAD_AUDIO_ONLY] ?: false`. In the write block (around line 542), persist via direct `preferences[KEY] = settings.field` (matching the pattern of adjacent S0003 keys, no `setOrRemove` since values are non-nullable).

**Verification:**

- `Grep` — `KEY_LINK_DOWNLOAD_MAX_RESOLUTION` matches at least 3 times in `SettingsRepositoryImpl.kt` (declaration + read + write).
- `Grep` — `KEY_LINK_DOWNLOAD_AUDIO_ONLY` matches at least 3 times in `SettingsRepositoryImpl.kt`.
- `Grep` — `it in setOf\("480p", "720p", "1080p", "best"\)` matches once (whitelist guard).
- `Grep` — `linkDownloadMaxResolution = preferences\[KEY_LINK_DOWNLOAD_MAX_RESOLUTION\]` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: SettingsRepositoryImpl.kt (+8 LOC). Dev log recorded.

---

### Step 01.6 — Extend `BackupData` and `BackupMapper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> Add `val linkDownloadMaxResolution: String? = null` and `val linkDownloadAudioOnly: Boolean? = null` to `BackupData`. In `BackupMapper.toBackup` and `fromBackup`, propagate the new fields with current-settings fallback for nulls.

**Verification:**

- `Grep` — `linkDownloadMaxResolution: String\? = null` matches once in `BackupData.kt`.
- `Grep` — `linkDownloadAudioOnly: Boolean\? = null` matches once in `BackupData.kt`.
- `Grep` — `linkDownloadMaxResolution =` matches at least 2 times in `BackupMapper.kt` (toBackup + fromBackup).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: BackupData.kt (+3 LOC), BackupMapper.kt (+5 LOC). Dev log recorded.

---

### Step 01.7 — Add `StreamingManifest`, `MediaQualityPreference`, extend `UrlExtractionStrategy` sealed types

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/link/StreamingManifest.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/link/MediaQualityPreference.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 01.6

**Prompt for developer:**

> Create `StreamingManifest` sealed class with two subclasses: `Hls(val manifestUrl: String, val isDrmProtected: Boolean)` and `Dash(val manifestUrl: String, val isDrmProtected: Boolean)`. Create `MediaQualityPreference` data class with `maxResolutionPx: Int` and `audioOnly: Boolean`, plus a companion `fromSettings(maxResolution: String, audioOnly: Boolean): MediaQualityPreference` mapper. In `UrlExtractionStrategy.kt`, extend the `BlockedReason` enum with `DrmProtected`, `StreamingDisabled`, `MuxFailed`, `AuthRequired`. Add a new `OpenResult.Streaming(val manifest: StreamingManifest, val tentativeFileName: String)` sealed variant alongside existing `Stream`/`NotFound`/`Blocked`/`Error`.
>
> In the **same step**, update the exhaustive `when` branches in `LinkAutoDownloadCoordinator.kt` so Phase 01 still compiles after the sealed hierarchy expands: add a temporary `is OpenResult.Streaming -> Result.Failed.Other(IllegalStateException("s0116_streaming_not_wired"))` branch and temporary `BlockedReason.DrmProtected` / `StreamingDisabled` / `MuxFailed` / `AuthRequired` mappings to an existing generic failure (`Result.Failed.Other(...)` is preferred over inventing new coordinator variants here). Do **not** add new `Result.Failed.*` variants in Phase 01 — specialized mappings land in Phases 03 and 05.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/link/StreamingManifest.kt` exists.
- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/link/MediaQualityPreference.kt` exists.
- `Grep` — `sealed class StreamingManifest` matches once.
- `Grep` — `data class Hls\(` matches once.
- `Grep` — `data class Dash\(` matches once.
- `Grep` — `DrmProtected,` and `StreamingDisabled,` and `MuxFailed,` and `AuthRequired` all present in `UrlExtractionStrategy.kt`.
- `Grep` — `data class Streaming\(` matches once in `UrlExtractionStrategy.kt`.
- `Grep` — `is OpenResult\.Streaming` matches once in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `s0116_streaming_not_wired` matches once in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `data class AuthRequired\(` returns 0 hits in `LinkAutoDownloadCoordinator.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 9/9 PASS. Files: StreamingManifest.kt (NEW 26 LOC), MediaQualityPreference.kt (NEW 28 LOC), UrlExtractionStrategy.kt (+24 LOC), LinkAutoDownloadCoordinator.kt (+24 LOC). Dev log recorded.

---

### Step 01.8 — Add `LinkDownloadTrace` privacy-safe logger and wire into Hilt module

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/log/LinkDownloadTrace.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`
**Depends on:** Step 01.7

**Prompt for developer:**

> Create `LinkDownloadTrace` object with two top-level functions: `tag(message: String)` calling `Timber.d("S0116: %s", message)`, and `verbose(message: String)` calling `if (BuildConfig.LOG_LINK_DOWNLOAD) Timber.v("[link-dl] %s", message)`. Add a third helper `truncateUrl(url: String): String` that strips query string, fragment, and any path segment beyond index 2. In `LinkDownloadModule`, ensure no provides changes are required yet — add a comment marker `// S0116: streaming/cookie/auth bindings appended in later phases`. No `Log.d()` calls anywhere.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/log/LinkDownloadTrace.kt` exists.
- `Grep` — `object LinkDownloadTrace` matches once.
- `Grep` — `fun truncateUrl\(url: String\): String` matches once.
- `Grep` — `if \(BuildConfig\.LOG_LINK_DOWNLOAD\)` matches once in `LinkDownloadTrace.kt`.
- `Grep` — `Log\.d\(` returns 0 hits in `LinkDownloadTrace.kt` and `LinkDownloadModule.kt`.
- `Grep` — `S0116: streaming/cookie/auth bindings` matches once in `LinkDownloadModule.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 6/6 PASS. Files: LinkDownloadTrace.kt (NEW 49 LOC), LinkDownloadModule.kt (+6 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `/build standardDebug` and `/build liteDebug` both succeed (`media3-exoplayer-hls/dash` only added to video flavors).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (3 new files: `StreamingManifest.kt`, `MediaQualityPreference.kt`, `LinkDownloadTrace.kt`).

---

## Handoff Notes to Next Phase

- `BuildConfig.LOG_LINK_DOWNLOAD` available; later phases use `LinkDownloadTrace.verbose(..)` instead of raw `Timber.v`.
- `LinkAutoDownloadCoordinator` already has temporary compile-safe placeholders for `OpenResult.Streaming` and the new `BlockedReason` cases. Phase 03 replaces the streaming placeholder with the real `StreamingPipeline` branch; Phase 05 replaces the temporary `BlockedReason.AuthRequired` fallback with the dedicated auth flow.
- `StreamingManifest` and `MediaQualityPreference` types ready for consumers in Phase 02 and 03.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. New sealed-class entries and the temporary coordinator placeholder branches must roll back together so the compile-safe baseline stays internally consistent.

## Revision History

- **2026-05-08** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability)
	- Applied: compile-safe placeholder handling for new sealed outcomes, corrected handoff/rollback wording. Proposed (DISCUSS): 0.
