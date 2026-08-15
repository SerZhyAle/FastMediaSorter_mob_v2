# Phase 01 - Settings flag

**Strategic spec:** [`../S0468_screenshot-clipboard.md`](../S0468_screenshot-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Introduce the persisted `copyScreenshotToClipboard` boolean across the settings model, its DataStore store, the repository mapping, and the device-profile preset matrix. No capture or UI behavior yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 700 |
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | n/a |

> `SettingsRepositoryImpl.kt` is 754 LOC (>500) - take a timestamped backup into `temp/` before editing (CLAUDE.md Rule 5).

---

## Steps

### Step 01.1 - Add `copyScreenshotToClipboard` field to the settings model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val copyScreenshotToClipboard: Boolean = false` to `AppSettings`, placed next to the existing screenshot-gesture fields (`screenshotDestinationResourceId`). Default `false` preserves current behavior on upgrade. No KDoc restating the obvious - one short EN comment only if it adds non-obvious intent.

**Verification:**

- `Grep` - `copyScreenshotToClipboard: Boolean = false` matches once in `AppSettings.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 1/1 PASS. Files: AppSettings.kt (+2 LOC). Post-change PASS. Dev log recorded.

---

### Step 01.2 - Persist the flag in the screenshot settings store

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `ScreenshotSettingsStore`: add `private val KEY_COPY_SCREENSHOT_TO_CLIPBOARD = booleanPreferencesKey("copy_screenshot_to_clipboard")`; add `copyScreenshotToClipboard: Boolean` to `Values`; read it in `read()` defaulting to `false`; write it in `write()` from `settings.copyScreenshotToClipboard`.

**Verification:**

- `Grep` - `copy_screenshot_to_clipboard` matches once in `ScreenshotSettingsStore.kt`.
- `Grep` - `copyScreenshotToClipboard` matches at least 3 times in `ScreenshotSettingsStore.kt` (Values field, read, write).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (`copy_screenshot_to_clipboard` ×1, `copyScreenshotToClipboard` ×3). Files: ScreenshotSettingsStore.kt (+4 LOC). Post-change PASS. Dev log recorded.

---

### Step 01.3 - Map the flag in the repository and register the preset row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`, `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `SettingsRepositoryImpl`, where the `ScreenshotSettingsStore.read(...)` result is mapped into `AppSettings` (near `gestureOverlayEnabled = screenshot.gestureOverlayEnabled`), add `copyScreenshotToClipboard = screenshot.copyScreenshotToClipboard`. The write side already calls `ScreenshotSettingsStore.write(preferences, settings)`, so no write change is needed. Then add the matching CSV row to `device_profile_presets.csv` per S0327 - run `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1 -AddMissing` to scaffold the row, leaving it empty (this flag is not a preset-applied field).

**Verification:**

- `Grep` - `copyScreenshotToClipboard = screenshot.copyScreenshotToClipboard` matches once in `SettingsRepositoryImpl.kt`.
- `Grep` - `copyScreenshotToClipboard` matches once in `device_profile_presets.csv`.
- Run `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` - exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification PASS for own deliverable: repo mapping ×1, CSV row present (line 169), `copyScreenshotToClipboard` no longer in MISSING list. Note: `check_device_profile_presets.ps1` still exits 1 due to a PRE-EXISTING stale CSV row `enableGoogleLens` (no matching AppSettings field; referenced by in-flight S0403 fdroid-foss - not removed per dead-code-as-scaffolding rule). Files: SettingsRepositoryImpl.kt (+1 LOC, backed up to temp/backups/), device_profile_presets.csv (+1 row). Post-change PASS. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`AppSettings.copyScreenshotToClipboard` is readable from any consumer of `SettingsRepository`. Phase 03 reads it in the capture service; Phase 04 binds it in settings UI.

---

## Rollback Plan

Revert phase commit(s) - no data migration; new DataStore key simply re-defaults to `false` if absent.
