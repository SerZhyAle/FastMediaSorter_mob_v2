# Phase 01 - Domain & Persistence Foundation

**Strategic spec:** [`../S0328_color-theme-setting.md`](../S0328_color-theme-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-02
**Completed:** 2026-06-02 (build: standardDebug SUCCESSFUL)

---

## Objective

Introduce the persisted `colorTheme` setting (values `AUTO` / `LIGHT` / `DARK`, default `AUTO`) in the settings model and its DataStore round-trip; no theme application and no UI yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6 research items are Resolved (both are).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 520 |

> `SettingsRepositoryImpl.kt` is the canonical `AppSettings` ⇄ DataStore serializer (it owns `KEY_LANGUAGE` and the read/write of every field). The setting must round-trip there, not in the legacy `SettingsManager`.

---

## Steps

### Step 01.1 - Add `colorTheme` field to settings model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new field `val colorTheme: String = "AUTO"` to the `AppSettings` data class, grouped near the other UI-state/general settings. Document allowed values in an inline comment: `AUTO` (follow device night-mode), `LIGHT` (force light), `DARK` (force dark). Do not add it to `getGloballyEnabledMediaTypes()`.

**Verification:**

- `Grep` - `val colorTheme: String = "AUTO"` matches exactly once in `AppSettings.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 1/1 PASS. Files: domain/model/AppSettings.kt (+3 LOC). Dev log recorded.

---

### Step 01.2 - Add DataStore key for color theme

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the companion key block (where `KEY_LANGUAGE` lives), add `private val KEY_COLOR_THEME = stringPreferencesKey("color_theme")`.

**Verification:**

- `Grep` - `stringPreferencesKey("color_theme")` matches exactly once in `SettingsRepositoryImpl.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 1/1 PASS. Files: data/repository/SettingsRepositoryImpl.kt. Dev log recorded.

---

### Step 01.3 - Read color theme into AppSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In the `getSettings()` mapping that builds `AppSettings` from `preferences`, read `preferences[KEY_COLOR_THEME] ?: "AUTO"` and assign it to the `colorTheme = ...` constructor argument. Missing value must map to `"AUTO"` (no behavior change for existing installs).

**Verification:**

- `Grep` - `colorTheme = preferences[KEY_COLOR_THEME]` matches in `SettingsRepositoryImpl.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 1/1 PASS. Files: data/repository/SettingsRepositoryImpl.kt.

---

### Step 01.4 - Persist color theme on save

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In the persist/save block that writes each `settings.<field>` into `preferences` (where `preferences[KEY_LANGUAGE] = settings.language` is written), add `preferences[KEY_COLOR_THEME] = settings.colorTheme`.

**Verification:**

- `Grep` - `preferences[KEY_COLOR_THEME] = settings.colorTheme` matches in `SettingsRepositoryImpl.kt`.
- `Grep -n "Log\.d\("` on `SettingsRepositoryImpl.kt` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 2/2 PASS (write present; Log.d=0). Files: data/repository/SettingsRepositoryImpl.kt.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (target `standardDebug`).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`AppSettings.colorTheme` now persists and round-trips through DataStore with default `AUTO`. Phase 02 consumes this value at process start to drive the night-mode.

---

## Rollback Plan

Revert phase commit(s) - new field defaults to `AUTO`, no data migration, no user-facing surface changed.
