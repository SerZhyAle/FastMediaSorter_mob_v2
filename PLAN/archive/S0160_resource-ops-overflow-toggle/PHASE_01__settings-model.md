# Phase 01 — settings-model

**Strategic spec:** [`../S0160_resource-ops-overflow-toggle.md`](../S0160_resource-ops-overflow-toggle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 04, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add `resourceOpsInOverflowMenu: Boolean` to the domain `AppSettings` model and wire it through `SettingsRepositoryImpl` so DataStore persists and restores the value.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 695 |

> `SettingsRepositoryImpl.kt` is 688 lines — backup required before edit (timestamped copy in `temp/`).

---

## Steps

### Step 01.1 — Add `resourceOpsInOverflowMenu` field to domain `AppSettings`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `AppSettings` data class, add the field `val resourceOpsInOverflowMenu: Boolean = false` in the UI-State settings section, after the `isResourceGridMode` field. No other changes to this file.

**Verification:**

- `Grep` — `resourceOpsInOverflowMenu: Boolean = false` matches in `domain/model/AppSettings.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 1/1 PASS. Files: domain/model/AppSettings.kt (+1 LOC). Dev log recorded.

---

### Step 01.2 — Wire `resourceOpsInOverflowMenu` through `SettingsRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `SettingsRepositoryImpl`:
>
> 1. In the `companion object`, add:
>    `private val KEY_RESOURCE_OPS_IN_OVERFLOW_MENU = booleanPreferencesKey("resource_ops_in_overflow_menu")`
>    Place it alongside the other UI-state keys near `KEY_IS_RESOURCE_GRID_MODE`.
>
> 2. In `getSettings()` `AppSettings(…)` constructor call, add:
>    `resourceOpsInOverflowMenu = preferences[KEY_RESOURCE_OPS_IN_OVERFLOW_MENU] ?: false,`
>
> 3. In `updateSettings()` `dataStore.edit { … }` block, add:
>    `preferences[KEY_RESOURCE_OPS_IN_OVERFLOW_MENU] = settings.resourceOpsInOverflowMenu`
>    Place it next to `preferences[KEY_IS_RESOURCE_GRID_MODE]`.

**Verification:**

- `Grep` — `KEY_RESOURCE_OPS_IN_OVERFLOW_MENU` matches at least 3 times in `SettingsRepositoryImpl.kt` (declaration + getSettings + updateSettings).
- `Grep` — `"resource_ops_in_overflow_menu"` matches exactly once in `SettingsRepositoryImpl.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `SettingsRepositoryImpl.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 3/3 PASS. Files: data/repository/SettingsRepositoryImpl.kt (+3 LOC). Backup: temp/SettingsRepositoryImpl_20260513_183435.kt.backup. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `AppSettings.resourceOpsInOverflowMenu` is available in the domain model.
- `SettingsRepositoryImpl.getSettings()` emits the flag from DataStore.
- `SettingsRepositoryImpl.updateSettings()` persists the flag.
- Default is `false` — existing behavior unchanged on upgrade.

---

## Rollback Plan

Revert phase commit — no data migration, no Room change, no user-visible surface changed.
