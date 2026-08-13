# Phase 02 - Theme Application Point

**Strategic spec:** [`../S0328_color-theme-setting.md`](../S0328_color-theme-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-02
**Completed:** 2026-06-02 (build: standardDebug SUCCESSFUL)

---

## Objective

Introduce a single startup application point that translates the saved color theme into the platform night-mode and applies it before any Activity inflates; no UI change yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 600 |

> Synchronous SharedPreferences mirror pattern mirrors the existing `PlayerLayoutModePrefs` (compact-mode mirror read at startup). The DataStore value is the source of truth; the SP mirror exists only so the night-mode can be applied synchronously before the first Activity.

---

## Steps

### Step 02.1 - Create `ColorThemePrefs` synchronous mirror + applier

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an `object ColorThemePrefs` modelled on `ui/player/helpers/PlayerLayoutModePrefs`. Provide:
> - `fun setMode(context: Context, value: String)` - writes the raw `AUTO`/`LIGHT`/`DARK` string into a dedicated SharedPreferences file synchronously.
> - `fun applySavedMode(context: Context)` - reads the saved string (default `AUTO`) and calls `AppCompatDelegate.setDefaultNightMode(...)` with the mapping `AUTO → MODE_NIGHT_FOLLOW_SYSTEM`, `LIGHT → MODE_NIGHT_NO`, `DARK → MODE_NIGHT_YES`.
> - `fun toNightMode(value: String): Int` - the pure mapping, reused by both callers.
> Use `Timber` only; no `Log.d`. Keep the SP file name distinct (e.g. `color_theme_prefs`).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt` exists.
- `Grep` - `object ColorThemePrefs` matches exactly once in that file.
- `Grep` - `AppCompatDelegate.setDefaultNightMode` matches in that file.
- `Grep` - `fun applySavedMode` and `fun setMode` both present.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 5/5 PASS. Files: core/theme/ColorThemePrefs.kt (New, +47 LOC). Dev log recorded.

---

### Step 02.2 - Apply saved theme at process start + keep mirror in sync

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `FastMediaSorterApp.onCreate()`, call `ColorThemePrefs.applySavedMode(this)` early - before any Activity can start, alongside the other early UI setup (near `DynamicColors.applyToActivitiesIfAvailable(this)`). Additionally, in the existing deferred settings-load coroutine (where `PlayerLayoutModePrefs.setCompact(..., settings.useCompactElements)` is called), add `ColorThemePrefs.setMode(this@FastMediaSorterApp, settings.colorTheme)` so the synchronous mirror tracks the authoritative DataStore value after upgrades / external imports.

**Verification:**

- `Grep` - `ColorThemePrefs.applySavedMode(this)` matches in `FastMediaSorterApp.kt`.
- `Grep` - `ColorThemePrefs.setMode(` matches in `FastMediaSorterApp.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 2/2 PASS. Files: FastMediaSorterApp.kt. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (target `standardDebug`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Saved theme is applied at every cold start. Phase 03 wires the user-facing selector that writes both the DataStore value (via `updateSettings`) and the synchronous mirror (`ColorThemePrefs.setMode`), then prompts for restart.

---

## Rollback Plan

Revert phase commit(s). Without the startup call the app falls back to the previous behavior (system night-mode only); no data migration.
