# Phase 01 - Persistence Flag

**Strategic spec:** [`../S0781_main-resource-type-filter-panel-collapse.md`](../S0781_main-resource-type-filter-panel-collapse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-01
**Completed:** 2026-07-01

---

## Objective

Add a persisted boolean `resourceTypeTabCollapsed` to `AppSettings`, backed by the existing DataStore preferences, mirroring the existing `copyPanelCollapsed`/`movePanelCollapsed` flags end-to-end (model, repository read/write, backup/import round-trip). No UI yet.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved (none).
- [ ] DataStore-backed settings confirmed (no Room migration) - see `research/01__architecture-and-reference-design.md` §3.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt` | Modified | ≤ 500 |

> No layout/flavor implications. `resourceTypeTabCollapsed` is internal UI-state, NOT a Settings-screen toggle, so it is NOT added to the settings manifest (Rule 22 not triggered) - confirm `copy_panel_collapsed` is likewise absent from `docs/settings/settings-manifest.json`.

---

## Steps

### Step 01.1 - Add `resourceTypeTabCollapsed` to AppSettings

**Files:** `domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `AppSettings` add `val resourceTypeTabCollapsed: Boolean = false` immediately after `movePanelCollapsed` (currently ~line 211). Prefix with a short EN comment: `// S0781: main-window resource-type filter strip collapsed state (mirror of copy/movePanelCollapsed).` Keep it a defaulted constructor param so existing call-sites stay source-compatible.

**Verification:**

- `Grep` - `val resourceTypeTabCollapsed: Boolean = false` matches exactly once in `AppSettings.kt`.

**Status:** `[x]` done

---

### Step 01.2 - Persist the flag in SettingsRepositoryImpl

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Mirror the `KEY_COPY_PANEL_COLLAPSED` wiring for the new flag:
> 1. In the `companion object` (next to line ~155) add `private val KEY_RESOURCE_TYPE_TAB_COLLAPSED = booleanPreferencesKey("resource_type_tab_collapsed")`.
> 2. In the preferences-to-`AppSettings` mapping (next to line ~435) add `resourceTypeTabCollapsed = preferences[KEY_RESOURCE_TYPE_TAB_COLLAPSED] ?: false,`.
> 3. In the persist block (next to line ~644) add `preferences[KEY_RESOURCE_TYPE_TAB_COLLAPSED] = settings.resourceTypeTabCollapsed`.

**Verification:**

- `Grep` - `KEY_RESOURCE_TYPE_TAB_COLLAPSED` matches exactly 3 times in `SettingsRepositoryImpl.kt` (declaration + read + write).
- `Grep` - `resource_type_tab_collapsed` matches exactly once (the key string).

**Status:** `[x]` done

---

### Step 01.3 - Round-trip the flag through backup + import

**Files:** `domain/usecase/BackupData.kt`, `domain/usecase/BackupMapper.kt`, `domain/usecase/ImportSettingsUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> `Grep -n "copyPanelCollapsed"` across `domain/usecase/` and add a parallel `resourceTypeTabCollapsed` line at every site so the new flag survives backup/restore and text import:
> 1. `BackupData.kt` (~line 149): add `val resourceTypeTabCollapsed: Boolean = false,`.
> 2. `BackupMapper.kt`: add to BOTH directions - settings-to-backup (~line 219) `resourceTypeTabCollapsed = settings.resourceTypeTabCollapsed,` and backup-to-settings (~line 388) `resourceTypeTabCollapsed = backup.resourceTypeTabCollapsed,`.
> 3. `ImportSettingsUseCase.kt` (~line 265): add `resourceTypeTabCollapsed = data["resourceTypeTabCollapsed"]?.toBoolean() ?: false,`.
> Do NOT touch `DeviceProfilePresetApplier.kt` unless it explicitly assigns `copyPanelCollapsed` (a device preset must not force a UI collapse state) - grep to confirm; if it does not set the existing flags, leave it.

**Verification:**

- `Grep` - `resourceTypeTabCollapsed` matches exactly once in `BackupData.kt`, twice in `BackupMapper.kt`, once in `ImportSettingsUseCase.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk` is sufficient for this symbol-only change).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`AppSettings.resourceTypeTabCollapsed` now persists across restart and survives backup/import. Phase 03 reads the initial value from the collected `AppSettings` and writes it on toggle via `settingsRepository.updateSettings { it.copy(resourceTypeTabCollapsed = ..) }` (verify the exact updateSettings signature used by `DestinationButtonsManager`).

---

## Rollback Plan

Revert phase commit(s) - additive defaulted field, no data migration; absence of the DataStore key reads back as `false`.
