# Phase 01 — Settings & Platform Gate

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Introduce the `allowSeparateWindow` boolean setting through the full settings pipeline (`AppSettings` → `SettingsRepository` → `SettingsRepositoryImpl` → `SettingsManager` DataStore), defaulting to `BuildConfig.SUPPORT_VR_PLAYER`. Add a toggle in the Settings UI. No multi-window behavior changes in this phase.

---

## Prerequisites

- [ ] S0038 is `Verified` (see INDEX.md Pre-Implementation Blockers).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 900 |
| Settings ViewModel (located at execution time via catalog query) | Modified | — |

> If any file above exceeds 500 lines, create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 01.1 — Add `allowSeparateWindow` to `AppSettings`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `val allowSeparateWindow: Boolean = false` to the `AppSettings` data class. Place it after existing boolean fields, before any collection or complex fields. Do not change any existing fields or their default values.

**Verification:**

- `Grep` — `allowSeparateWindow` matches at least once in `AppSettings.kt`.
- `Grep` — `data class AppSettings` still matches exactly once (class not duplicated).
- `Grep` — `Log\.d\(` returns zero hits in `AppSettings.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Added `val allowSeparateWindow: Boolean = false` after `showBlackScreenButton`. Files: AppSettings.kt (+2 LOC). Dev log recorded.

---

### Step 01.2 — Wire `allowSeparateWindow` into `SettingsRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> `SettingsRepositoryImpl` uses DataStore directly — it does NOT delegate to `SettingsManager`. Follow the same pattern as `KEY_VR_SHOW_FPS` and other boolean keys in this file:
>
> 1. In the companion object, add: `private val KEY_ALLOW_SEPARATE_WINDOW = booleanPreferencesKey("allow_separate_window")`.
> 2. In `getSettings()`, inside the `AppSettings(...)` constructor call, add: `allowSeparateWindow = preferences[KEY_ALLOW_SEPARATE_WINDOW] ?: BuildConfig.SUPPORT_VR_PLAYER`.
> 3. In `updateSettings()`, inside the DataStore `edit` block, add: `preferences[KEY_ALLOW_SEPARATE_WINDOW] = settings.allowSeparateWindow`.
>
> The file is 828 lines — create a timestamped backup in `temp/` before editing.

**Verification:**

- `Grep` — `KEY_ALLOW_SEPARATE_WINDOW` matches at least 3 times in `SettingsRepositoryImpl.kt` (declaration, read, write).
- `Grep` — `allow_separate_window` matches in `SettingsRepositoryImpl.kt` (key string literal).
- `Grep` — `SUPPORT_VR_PLAYER` matches in `SettingsRepositoryImpl.kt` (default value).
- `Grep` — `Log\.d\(` returns zero hits in `SettingsRepositoryImpl.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Added `KEY_ALLOW_SEPARATE_WINDOW = booleanPreferencesKey("allow_separate_window")` to companion object; wired in `getSettings()` with `?: BuildConfig.SUPPORT_VR_PLAYER` default and in `updateSettings()`. Files: SettingsRepositoryImpl.kt (+5 LOC). Dev log recorded.

---

### Step 01.3 — Verify `allowSeparateWindow` flows through `SettingsRepository`

**Files:** *(no code changes — verification only)*
**Depends on:** Step 01.2

**Prompt for developer:**

> The `SettingsRepository` interface already exposes `allowSeparateWindow` through the existing `getSettings(): Flow<AppSettings>` — no new interface methods are needed.
>
> Verify that after Step 01.2:
> - `AppSettings.allowSeparateWindow` exists (Step 01.1).
> - `SettingsRepositoryImpl.getSettings()` populates it from DataStore (Step 01.2).
> - `SettingsRepositoryImpl.updateSettings()` persists it to DataStore (Step 01.2).
>
> Run the three `Grep` predicates below to confirm. No file edits in this step.

**Verification:**

- `Grep` — `allowSeparateWindow` matches in `AppSettings.kt`.
- `Grep` — `KEY_ALLOW_SEPARATE_WINDOW` matches at least 3 times in `SettingsRepositoryImpl.kt`.
- `Grep` — `getSettings` matches in `SettingsRepository.kt` (interface still uses the aggregated pattern).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. No code changes — confirmed allowSeparateWindow in AppSettings.kt (line 191), KEY_ALLOW_SEPARATE_WINDOW 3× in SettingsRepositoryImpl.kt, getSettings() in SettingsRepository.kt interface.

---

### Step 01.4 — Add `allowSeparateWindow` toggle to Settings UI

**Files:** Settings screen file(s) — locate via `dev/CATALOG/query.ps1 -Role "settings UI"` or `Grep pattern "SettingsRepository" in ui/settings/`
**Depends on:** Step 01.3

**Prompt for developer:**

> Locate the Settings screen ViewModel (run `dev/CATALOG/query.ps1 -Role "settings"` or grep `settingsRepository` in `ui/settings/`). Add a boolean toggle for `allowSeparateWindow`:
>
> - Read: `settingsRepository.getSettings().map { it.allowSeparateWindow }` collected as `StateFlow<Boolean>`.
> - Write: call `settingsRepository.updateSettings(currentSettings.copy(allowSeparateWindow = enabled))` — you already have a `currentSettings` pattern from other toggles; follow it exactly.
> - In the UI (Fragment/Composable), add a toggle row. Label: hardcoded string `"Allow opening in a separate window"` marked with `// TODO(phase-05) replace with R.string.setting_allow_separate_window`.
> - Show the toggle unconditionally for now (platform detection per §6 Q6 is deferred).
>
> Do not add the string resource in this step — it will be added together with all other S0028 strings in Phase 05, Step 05.1.

**Verification:**

- `Grep` — `allowSeparateWindow` matches in the Settings ViewModel file (inside `map { it.allowSeparateWindow }` or `copy(allowSeparateWindow`).
- `Grep` — `updateSettings` with `allowSeparateWindow` matches in the Settings ViewModel file.
- `Grep` — `TODO(phase-05)` marks the temporary string literal.
- `Grep` — `Log\.d\(` returns zero hits in all modified settings files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Added `switchAllowSeparateWindow` to `fragment_settings_video.xml` (portrait + landscape), listener and observer in `VideoSettingsFragment.kt`. Hardcoded label marked with `TODO(phase-05)`. Files: fragment_settings_video.xml (+26 LOC), layout-land/fragment_settings_video.xml (+10 LOC), VideoSettingsFragment.kt (+11 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns exactly 1 hit (the deferred string literal in settings UI).
- [ ] Dev log entries added for all modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — `AppSettings`, `SettingsRepository`, `SettingsRepositoryImpl` public API changed.

---

## Handoff Notes to Next Phase

`allowSeparateWindow` is now a first-class setting. Phases 02–06 can read it to gate visibility. Phase 05 will add the proper string resource and remove the `TODO(phase-01)` placeholder.

---

## Rollback Plan

Revert phase commit(s). No persistent user data affected — DataStore key is absent until first write, which only happens after Phase 05 UI is wired up.
