# Phase 01 - Settings model & persistence foundation

**Strategic spec:** [`../S0820_video-fullscreen-open-option.md`](../S0820_video-fullscreen-open-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Introduce `openVideoInFullscreen: Boolean = true` on `AppSettings`, persist it through the DataStore-backed settings repository, and wire it through backup export/import, settings-JSON import, and the Video/Media settings-section reset. No UI row and no player-launch behavior change yet - those come in later phases.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `AppSettings.kt`, `SettingsRepositoryImpl.kt` compile cleanly before this phase starts (baseline).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 360 (currently ~354) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 830 (currently ~824) |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | - |

---

## Steps

### Step 01.1 - Add the field to AppSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val openVideoInFullscreen: Boolean = true` to the `AppSettings` data class, grouped with the other Video-section display fields (near `hideSystemUiInFullscreen` / `showVideoThumbnails` / `nineZoneGridEnabled`, the "Player UI settings" area). One-line trailing comment: `// S0820: video files opened from Browse enter fullscreen immediately when this is on; per-resource showCommandPanel override still wins.`

**Verification:**

- `Grep` - `val openVideoInFullscreen: Boolean = true` in `AppSettings.kt` matches exactly once.
- `Grep` - `S0820` present on the same line or the line above.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt (+3 LOC).

---

### Step 01.2 - Persist through the DataStore repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `private val KEY_OPEN_VIDEO_IN_FULLSCREEN = booleanPreferencesKey("open_video_in_fullscreen")` beside the other video-section keys (near `KEY_SHOW_VIDEO_THUMBNAILS` / `KEY_PLAYER_SHOW_FPS`). In the settings-load mapping add `openVideoInFullscreen = preferences[KEY_OPEN_VIDEO_IN_FULLSCREEN] ?: true` (absent key means "not yet stored" for both a fresh install and an existing install upgrading past this ticket - both get the code default `true`, per strategic §3.2 "Совместимость данных"). In the settings-save block add `preferences[KEY_OPEN_VIDEO_IN_FULLSCREEN] = settings.openVideoInFullscreen`.

**Verification:**

- `Grep` - `KEY_OPEN_VIDEO_IN_FULLSCREEN = booleanPreferencesKey("open_video_in_fullscreen")` matches once.
- `Grep` - `openVideoInFullscreen = preferences\[KEY_OPEN_VIDEO_IN_FULLSCREEN\] \?: true` matches once.
- `Grep` - `preferences\[KEY_OPEN_VIDEO_IN_FULLSCREEN\] = settings.openVideoInFullscreen` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt (+3 LOC).

---

### Step 01.3 - Round-trip through backup export/import and settings-JSON import

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Mirror the existing `videoFrameCopyToClipboard` field end to end (it has the identical shape - plain boolean, no per-resource override, default carried through). Add `val openVideoInFullscreen: Boolean = true` to `BackupData`. In `BackupMapper`, add `openVideoInFullscreen = settings.openVideoInFullscreen` to the settings-to-backup mapping and `openVideoInFullscreen = backup.openVideoInFullscreen` to the backup-to-settings mapping. In `ImportSettingsUseCase`, add `openVideoInFullscreen = data["openVideoInFullscreen"]?.toBoolean() ?: true` to the field-mapping block. Skipping this step leaves the setting silently reset to the hardcoded default on every backup restore or settings-JSON import - it will not round-trip.

**Verification:**

- `Grep` - `openVideoInFullscreen: Boolean = true` in `BackupData.kt` matches once.
- `Grep` - `openVideoInFullscreen = settings.openVideoInFullscreen` in `BackupMapper.kt` matches once.
- `Grep` - `openVideoInFullscreen = backup.openVideoInFullscreen` in `BackupMapper.kt` matches once.
- `Grep` - `openVideoInFullscreen = data\["openVideoInFullscreen"\]` in `ImportSettingsUseCase.kt` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 4/4 PASS. Files: BackupData.kt (+1 LOC), BackupMapper.kt (+2 LOC), ImportSettingsUseCase.kt (+1 LOC).

---

### Step 01.4 - Include in the Video/Media settings-section reset

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `openVideoInFullscreen = defaults.openVideoInFullscreen` inside `resetMediaSection()`'s `current.copy(..)` block, alongside the other Video-screen fields it already resets (`showVideoThumbnails`, `videoSnapshotResourceId`, `videoSnapshotFormat`, `videoFrameCopyToClipboard`). This keeps "Reset to defaults" on the Video settings screen covering every row that screen owns - do not add it to `resetPlaybackSection()`, which resets a different settings group.

**Verification:**

- `Grep` - `openVideoInFullscreen = defaults.openVideoInFullscreen` present inside the `resetMediaSection` function body in `SettingsViewModel.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 1/1 PASS (confirmed via direct Read - placed immediately after videoFrameCopyToClipboard inside resetMediaSection). Files: SettingsViewModel.kt (+1 LOC).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` -> BUILD SUCCESSFUL (3m 30s).
- [x] `Grep` for `Log\.d\(` in every file in "Files Touched" returns zero hits (no logging touched by this phase).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via batched `close-and-log.ps1 -DevLogs` (7 entries, 2026-07-02 20:46).

---

## Handoff Notes to Next Phase

`AppSettings.openVideoInFullscreen` exists, is persisted with default `true`, and survives backup export/import and settings-JSON import and the Video-section reset. Not yet surfaced in any UI (Phase 03), not yet applied to any device-profile preset default (Phase 02), not yet gating any player-launch behavior (Phase 04).

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - a new DataStore key with a code-side default does not require a data migration; no other phase depends on anything beyond the field's existence and default value.
