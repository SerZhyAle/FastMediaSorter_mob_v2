# Phase 03 - Theme Application

**Strategic spec:** [`../S0569_custom-color-themes.md`](../S0569_custom-color-themes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (overlay style ids), Phase 02 (theme values)
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Apply the correct theme overlay to every screen on creation: add `ColorThemePrefs.applyThemeOverlay(activity)` and call it from `BaseActivity.onCreate` at the same seam used by the existing dialog-button overlay.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `R.style.ThemeOverlay_FastMediaSorter_*` exist.
- [ ] Phase 02 ✅ Done - `normalizeValue` accepts the six custom values.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified | ≤ 600 |

---

## Steps

### Step 03.1 - Add `applyThemeOverlay(activity)` to `ColorThemePrefs`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun applyThemeOverlay(activity: android.app.Activity)` to the `ColorThemePrefs` object, mirroring `PlayerLayoutModePrefs.applyCompactDialogButtonsOverlay`. Read the current normalized value via `getMode(activity)`, `when`-map the six custom identifiers to their overlay style and call `activity.theme.applyStyle(overlayResId, true)`: `DARK_GREEN -> R.style.ThemeOverlay_FastMediaSorter_DarkGreen`, `DARK_BLUE -> _DarkBlue`, `DARK_RED -> _DarkRed`, `LIGHT_GREEN -> _LightGreen`, `LIGHT_BLUE -> _LightBlue`, `LIGHT_RED -> _LightRed`. For `AUTO`/`LIGHT`/`DARK` do nothing (no overlay - base theme already correct). Do not swallow exceptions; this is a pure synchronous theme call.

**Verification:**

- `Grep` - `fun applyThemeOverlay` in `ColorThemePrefs.kt`.
- `Grep` - `R.style.ThemeOverlay_FastMediaSorter_DarkGreen` and `R.style.ThemeOverlay_FastMediaSorter_LightRed` in `ColorThemePrefs.kt`.
- `Grep` - `activity.theme.applyStyle(` in `ColorThemePrefs.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Added `applyThemeOverlay(activity)` mirroring `applyCompactDialogButtonsOverlay`: maps 6 custom values to `R.style.ThemeOverlay_FastMediaSorter_*`, no-op for AUTO/LIGHT/DARK. Imports `Activity` + `R`.

---

### Step 03.2 - Invoke `applyThemeOverlay` at the established overlay seam in `BaseActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `BaseActivity.onCreate`, add `ColorThemePrefs.applyThemeOverlay(this)` immediately after the existing `PlayerLayoutModePrefs.applyCompactDialogButtonsOverlay(this)` call and before `_binding = getViewBinding()` / `setContentView(binding.root)`. This is the same seam the compact-dialog overlay uses - the overlay must merge into the activity theme before any view or dialog inflates and resolves color attributes. Do NOT place it before `super.onCreate(savedInstanceState)` (the AppCompat night-mode theme must be attached first). Add the `ColorThemePrefs` import.

**Verification:**

- `Grep` - `ColorThemePrefs.applyThemeOverlay(this)` in `BaseActivity.kt`.
- `Grep` - the call sits between `applyCompactDialogButtonsOverlay(this)` and `setContentView(binding.root)` (both anchors present, overlay line after the compact-overlay line).
- `Grep` - `import com.sza.fastmediasorter.core.theme.ColorThemePrefs` present in `BaseActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. `ColorThemePrefs.applyThemeOverlay(this)` placed after `applyCompactDialogButtonsOverlay(this)` and before `setContentView` (ordering asserted); import added.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the change set via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 05 also covers this; safe to defer).

---

## Handoff Notes to Next Phase

- Every Activity extending `BaseActivity` now receives the accent overlay on creation. Any Activity NOT extending `BaseActivity` is out of this seam's reach - flag such screens during device test if any render unthemed.
- Theme selection from Settings (Phase 04) takes effect after the existing restart-prompt relaunch, because the overlay is read once per `onCreate`.

---

## Rollback Plan

Revert changes to `BaseActivity.kt` and the `applyThemeOverlay` addition in `ColorThemePrefs.kt`.
