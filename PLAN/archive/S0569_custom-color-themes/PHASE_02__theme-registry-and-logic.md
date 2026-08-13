# Phase 02 - Theme Registry and Logic

**Strategic spec:** [`../S0569_custom-color-themes.md`](../S0569_custom-color-themes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Teach `ColorThemePrefs` the six new theme identifiers: accept them in `normalizeValue`, and resolve each to the correct night mode in `toNightMode` (dark-accented -> `MODE_NIGHT_YES`, light-accented -> `MODE_NIGHT_NO`). No overlay or UI logic in this phase.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt` | Modified | ≤ 120 |

---

## Steps

### Step 02.1 - Extend `normalizeValue` to accept the six custom values

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend `ColorThemePrefs.normalizeValue` so it returns the uppercase canonical form for the six new values in addition to the existing `LIGHT`/`DARK`: `DARK_GREEN`, `DARK_BLUE`, `DARK_RED`, `LIGHT_GREEN`, `LIGHT_BLUE`, `LIGHT_RED`. Unknown input still falls back to `DEFAULT` (`AUTO`). Also update the object KDoc line that currently enumerates the raw values (`"AUTO" .. "DARK"`) to list the new identifiers, since that comment is a maintained contract (Rule 8).

**Verification:**

- `Grep` - `"DARK_GREEN" -> "DARK_GREEN"` (and the other five) present in `normalizeValue` of `ColorThemePrefs.kt`.
- `Grep` - the KDoc block mentions `DARK_GREEN` / `LIGHT_RED`.
- `Grep -n "Log\.d\("` on `ColorThemePrefs.kt` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. `normalizeValue` accepts the six custom values (uppercase canonical); KDoc updated; no `Log.d`.

---

### Step 02.2 - Map the six custom values to night mode in `toNightMode`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend `ColorThemePrefs.toNightMode` so that `DARK`, `DARK_GREEN`, `DARK_BLUE`, `DARK_RED` resolve to `AppCompatDelegate.MODE_NIGHT_YES`, and `LIGHT`, `LIGHT_GREEN`, `LIGHT_BLUE`, `LIGHT_RED` resolve to `AppCompatDelegate.MODE_NIGHT_NO`. `AUTO`/unknown stays `MODE_NIGHT_FOLLOW_SYSTEM`. Keep routing input through `normalizeValue` first so `applyMode`/`applySavedMode` continue to work unchanged.

**Verification:**

- `Grep` - `MODE_NIGHT_YES` branch in `toNightMode` covers `DARK_GREEN`, `DARK_BLUE`, `DARK_RED`.
- `Grep` - `MODE_NIGHT_NO` branch in `toNightMode` covers `LIGHT_GREEN`, `LIGHT_BLUE`, `LIGHT_RED`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. `toNightMode` maps dark-accented -> MODE_NIGHT_YES, light-accented -> MODE_NIGHT_NO, AUTO -> FOLLOW_SYSTEM; routes through `normalizeValue`.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `ColorThemePrefs.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `normalizeValue` is now the single source of truth for the nine valid identifiers; `applyThemeOverlay` (Phase 03) and the settings mapping (Phase 04) must reuse the same strings verbatim.
- Persistence and the synchronous startup mirror (`setMode`/`applySavedMode`) already round-trip any normalized string, so no storage change is needed for the new values.

---

## Rollback Plan

Revert changes to `ColorThemePrefs.kt`.
