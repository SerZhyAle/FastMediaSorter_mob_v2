# Phase 04 - Settings UI

**Strategic spec:** [`../S0569_custom-color-themes.md`](../S0569_custom-color-themes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (spinner item order), Phase 02 (valid theme values)
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Wire the six new spinner positions (3..8) to their theme values in `GeneralSettingsColorThemeHelper`, in both directions, so the existing select -> restart-prompt -> persist flow handles the new themes unchanged.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `color_theme_options` has 9 items in the frozen order.
- [ ] Phase 02 ✅ Done - `ColorThemePrefs.normalizeValue` accepts the six values.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt` | Modified | ≤ 120 |

> No layout edit: `binding.spinnerColorTheme` and its adapter wiring already exist; only the position<->value maps grow.

---

## Steps

### Step 04.1 - Extend `positionToValue` (position -> theme string)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend `positionToValue` so positions 3..8 return the new theme strings in the frozen order: `3 -> "DARK_GREEN"`, `4 -> "DARK_BLUE"`, `5 -> "DARK_RED"`, `6 -> "LIGHT_GREEN"`, `7 -> "LIGHT_BLUE"`, `8 -> "LIGHT_RED"`. Keep `0 -> AUTO`, `1 -> LIGHT`, `2 -> DARK` and the `else -> "AUTO"` fallback. The order MUST match the `color_theme_options` items appended in Phase 01.

**Verification:**

- `Grep` - `3 -> "DARK_GREEN"` and `8 -> "LIGHT_RED"` in `GeneralSettingsColorThemeHelper.kt`.
- `Grep` - `5 -> "DARK_RED"` and `6 -> "LIGHT_GREEN"` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. `positionToValue` maps positions 3..8 to the six custom values in the frozen order matching Phase 01.

---

### Step 04.2 - Extend `valueToPosition` (theme string -> position)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Extend `valueToPosition` as the inverse of Step 04.1: `"DARK_GREEN" -> 3`, `"DARK_BLUE" -> 4`, `"DARK_RED" -> 5`, `"LIGHT_GREEN" -> 6`, `"LIGHT_BLUE" -> 7`, `"LIGHT_RED" -> 8`. Keep `LIGHT -> 1`, `DARK -> 2`, `else -> 0`. This keeps the spinner pre-selected correctly when settings emit a persisted custom theme.

**Verification:**

- `Grep` - `"DARK_GREEN" -> 3` and `"LIGHT_RED" -> 8` in `GeneralSettingsColorThemeHelper.kt`.
- `Grep` - `"DARK_RED" -> 5` and `"LIGHT_GREEN" -> 6` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. `valueToPosition` is the exact inverse of `positionToValue` for all nine entries; order matches the Phase 01 `color_theme_options` items.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `positionToValue` and `valueToPosition` are exact inverses for all nine entries (manual read-through).
- [ ] Dev log entry added for `GeneralSettingsColorThemeHelper.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- The settings flow now persists any of the nine values via the existing `showRestartDialog` path (writes DataStore + `ColorThemePrefs.setMode` + `applyMode`, then relaunches). No new persistence code was required.

---

## Rollback Plan

Revert changes to `GeneralSettingsColorThemeHelper.kt`.
