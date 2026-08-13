# Phase 01 — Settings Model

**Strategic spec:** [`../S0159_file-ops-overflow-menu.md`](../S0159_file-ops-overflow-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 7 / 7
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add `fileOpsInOverflowMenu` and `fileOpsOverflowMenuHintShown` to the domain model, DataStore persistence, backup/restore pipeline, and playback-section reset; no UI or adapter changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportSettingsUseCase.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt` | Modified | ≤ 460 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 540 |

> `SettingsRepositoryImpl.kt` is 688 LOC → create timestamped backup in `temp/` before editing.

---

## Steps

### Step 1.1 — Add two flags to `AppSettings` domain model

**Files:** `domain/model/AppSettings.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add two fields to the `AppSettings` data class, after the `hideGridActionButtons` field:
>
> ```kotlin
> val fileOpsInOverflowMenu: Boolean = false, // Collapse file op buttons into a single ⋮ overflow menu per row
> val fileOpsOverflowMenuHintShown: Boolean = false, // True after the one-time "ops moved to menu" Toast was shown
> ```

**Verification:**

- `Grep` — `fileOpsInOverflowMenu: Boolean = false` present in `domain/model/AppSettings.kt`.
- `Grep` — `fileOpsOverflowMenuHintShown: Boolean = false` present in `domain/model/AppSettings.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. Files: `domain/model/AppSettings.kt` (+2 LOC). Dev log recorded.

---

### Step 1.2 — Backup and add DataStore keys to `SettingsRepositoryImpl`

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> 1. Copy `SettingsRepositoryImpl.kt` to `temp/SettingsRepositoryImpl_<timestamp>.kt.backup`.
> 2. In the `companion object`, add:
>    ```kotlin
>    private val KEY_FILE_OPS_IN_OVERFLOW_MENU = booleanPreferencesKey("file_ops_in_overflow_menu")
>    private val KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN = booleanPreferencesKey("file_ops_overflow_menu_hint_shown")
>    ```

**Verification:**

- `Glob` — `temp/SettingsRepositoryImpl_*.kt.backup` exists.
- `Grep` — `KEY_FILE_OPS_IN_OVERFLOW_MENU` present in `SettingsRepositoryImpl.kt`.
- `Grep` — `KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN` present in `SettingsRepositoryImpl.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Backup: `temp/SettingsRepositoryImpl_20260513_161613.kt.backup`. Keys added. Dev log recorded.

---

### Step 1.3 — Wire keys into `getSettings()` read path

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 1.2

**Prompt for developer:**

> In `getSettings()` (the `dataStore.data.map { preferences -> AppSettings(...) }` block), add two lines near the `hideGridActionButtons` read:
>
> ```kotlin
> fileOpsInOverflowMenu = preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU] ?: false,
> fileOpsOverflowMenuHintShown = preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN] ?: false,
> ```

**Verification:**

- `Grep` — `preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU]` present in `SettingsRepositoryImpl.kt`.
- `Grep` — `preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN]` present in `SettingsRepositoryImpl.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. Dev log recorded.

---

### Step 1.4 — Wire keys into `saveSettings()` write path

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 1.3

**Prompt for developer:**

> In `saveSettings()` (the `dataStore.edit { preferences -> ... }` block), add two lines near the `hideGridActionButtons` write:
>
> ```kotlin
> preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU] = settings.fileOpsInOverflowMenu
> preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN] = settings.fileOpsOverflowMenuHintShown
> ```

**Verification:**

- `Grep` — `preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU] = settings.fileOpsInOverflowMenu` present.
- `Grep` — `preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN] = settings.fileOpsOverflowMenuHintShown` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. Dev log recorded.

---

### Step 1.5 — Add fields to `BackupData` and `BackupMapper`

**Files:** `domain/usecase/BackupData.kt`, `domain/usecase/BackupMapper.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `BackupData.kt`, add after `hideGridActionButtons`:
> ```kotlin
> val fileOpsInOverflowMenu: Boolean = false,
> val fileOpsOverflowMenuHintShown: Boolean = false,
> ```
>
> In `BackupMapper.kt`:
> - In `toBackupData()`: add `fileOpsInOverflowMenu = settings.fileOpsInOverflowMenu, fileOpsOverflowMenuHintShown = settings.fileOpsOverflowMenuHintShown,`
> - In `fromBackupData()`: add `fileOpsInOverflowMenu = backup.fileOpsInOverflowMenu, fileOpsOverflowMenuHintShown = backup.fileOpsOverflowMenuHintShown,`

**Verification:**

- `Grep` — `fileOpsInOverflowMenu: Boolean = false` present in `BackupData.kt`.
- `Grep` — `fileOpsInOverflowMenu = settings.fileOpsInOverflowMenu` present in `BackupMapper.kt`.
- `Grep` — `fileOpsInOverflowMenu = backup.fileOpsInOverflowMenu` present in `BackupMapper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: `domain/usecase/BackupData.kt` (+2 LOC), `domain/usecase/BackupMapper.kt` (+4 LOC). Dev log recorded.

---

### Step 1.6 — Add export/import XML lines

**Files:** `domain/usecase/ExportSettingsUseCase.kt`, `domain/usecase/ImportSettingsUseCase.kt`
**Depends on:** Step 1.5

**Prompt for developer:**

> In `ExportSettingsUseCase.kt`, near the `hideGridActionButtons` export line, add:
> ```kotlin
> appendLine("    <fileOpsInOverflowMenu>${settings.fileOpsInOverflowMenu}</fileOpsInOverflowMenu>")
> appendLine("    <fileOpsOverflowMenuHintShown>${settings.fileOpsOverflowMenuHintShown}</fileOpsOverflowMenuHintShown>")
> ```
>
> In `ImportSettingsUseCase.kt`, near the `hideGridActionButtons` parse block, add:
> ```kotlin
> "fileOpsInOverflowMenu" -> data["fileOpsInOverflowMenu"] = value
> "fileOpsOverflowMenuHintShown" -> data["fileOpsOverflowMenuHintShown"] = value
> ```
> And in the `AppSettings(...)` construction block:
> ```kotlin
> fileOpsInOverflowMenu = data["fileOpsInOverflowMenu"]?.toBoolean() ?: false,
> fileOpsOverflowMenuHintShown = data["fileOpsOverflowMenuHintShown"]?.toBoolean() ?: false,
> ```

**Verification:**

- `Grep` — `<fileOpsInOverflowMenu>` present in `ExportSettingsUseCase.kt`.
- `Grep` — `fileOpsInOverflowMenu.*toBoolean` present in `ImportSettingsUseCase.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. Files: `domain/usecase/ExportSettingsUseCase.kt` (+2 LOC), `domain/usecase/ImportSettingsUseCase.kt` (+2 LOC). Dev log recorded.

---

### Step 1.7 — Add `fileOpsInOverflowMenu` to playback section reset in `SettingsViewModel`

**Files:** `ui/settings/SettingsViewModel.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `resetPlaybackSection()`, inside the `updateSettings(current.copy(...))` block, add after `hideGridActionButtons = defaults.hideGridActionButtons`:
> ```kotlin
> fileOpsInOverflowMenu = defaults.fileOpsInOverflowMenu,
> fileOpsOverflowMenuHintShown = defaults.fileOpsOverflowMenuHintShown,
> ```

**Verification:**

- `Grep` — `fileOpsInOverflowMenu = defaults.fileOpsInOverflowMenu` present in `SettingsViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 1/1 PASS. Files: `ui/settings/SettingsViewModel.kt` (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 1.* above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `AppSettings` has `fileOpsInOverflowMenu` (default `false`) and `fileOpsOverflowMenuHintShown` (default `false`).
- Both are persisted in DataStore, included in backup/restore XML, and reset by `resetPlaybackSection()`.
- Phase 02 adds the adapter field and layout changes. Phase 04 adds the settings UI toggle.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. DataStore keys are new; absent keys default to `false`, matching the default `AppSettings` values.
