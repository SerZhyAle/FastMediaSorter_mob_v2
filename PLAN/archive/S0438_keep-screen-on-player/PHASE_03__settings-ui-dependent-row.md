# Phase 03 - Settings UI dependent row

**Strategic spec:** [`../S0438_keep-screen-on-player.md`](../S0438_keep-screen-on-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Add the dependent "keep screen on while player works" toggle row to the settings screen, directly under the global keep-awake row, visible only when the global row is off, with trilingual strings.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 800 |

> Landscape parity: `res/layout-land/fragment_settings_destinations.xml` exists and is listed - the new row must be added in both layouts.

---

## Steps

### Step 03.1 - Add trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two keys in lockstep across EN/RU/UK via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add`: `settings_keep_screen_on_player_title` and `settings_keep_screen_on_player_summary`. Title is the toggle label ("Keep screen on while player works" / RU / UK). Summary states it applies only to the player and standalone players, and only when the global keep-screen-on setting is off. Strings must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist (plain, no exclamation, consistent terminology with the existing global row label).

**Verification:**

- `Grep` - `settings_keep_screen_on_player_title` present in all three `strings.xml` files.
- `Grep` - `settings_keep_screen_on_player_summary` present in all three `strings.xml` files.
- Script - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_keep_screen_on_player"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS (title+summary in EN/RU/UK; check_strings_localized exit 0; Cyrillic intact via UTF-8 .ps1). Dev log recorded.

---

### Step 03.2 - Add the dependent row to both layouts

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> In both the portrait and landscape layouts, add a new toggle row with id `rowKeepScreenOnPlayer` directly after `rowPreventSleep`, mirroring the existing row's widget type and style (same custom toggle-row component, focusable/keyboard/D-pad attributes). Bind its label to `@string/settings_keep_screen_on_player_title` and summary to `@string/settings_keep_screen_on_player_summary`. Do not hardcode any colors - reuse the same `?attr/`/`@color/` references the neighbouring row uses. Default visibility `gone` (Step 03.3 controls it reactively).

**Verification:**

- `Grep` - `@+id/rowKeepScreenOnPlayer` present in `res/layout/fragment_settings_destinations.xml`.
- `Grep` - `@+id/rowKeepScreenOnPlayer` present in `res/layout-land/fragment_settings_destinations.xml`.
- `Grep` - `="#` returns zero new hardcoded-hex hits in the added row of both files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS (rowKeepScreenOnPlayer in portrait + landscape; no hardcoded hex). Dev log recorded.

---

### Step 03.3 - Bind toggle, persist, and gate visibility on the global row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `OperationsSettingsFragment`, add a checked-change listener on `binding.rowKeepScreenOnPlayer` mirroring `rowPreventSleep`: guard with `isUpdatingFromSettings`, then `viewModel.updateSettings(current.copy(keepScreenOnPlayer = isChecked))`. In the settings-render block, set the row's checked state silently from `settings.keepScreenOnPlayer`, and set its visibility to visible only when `!settings.preventSleep` (hidden and treated as on when the global setting is on). Visibility must update reactively from the same settings emission that already drives `rowPreventSleep`, so toggling the global row shows/hides the dependent row without leaving the screen.

**Verification:**

- `Grep` - `rowKeepScreenOnPlayer.setOnCheckedChangeListener` present.
- `Grep` - `keepScreenOnPlayer = isChecked` present.
- `Grep` - `setCheckedSilently(settings.keepScreenOnPlayer)` present.
- `Grep` - `!settings.preventSleep` present (visibility gate).

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. Listener + silent render + visibility gate on rowKeepScreenOnPlayer. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_keep_screen_on_player"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- The dependent row is user-facing and functional: visible when global keep-awake is off, hidden when on, persisted via the field from Phase 01, and effective via the rule from Phase 02.
- Strategic §8 mandates a FEATURES update - handled in Phase 04.

---

## Rollback Plan

Revert phase commit(s). No data migration; removing the row leaves the persisted field harmless (defaults `true`).
