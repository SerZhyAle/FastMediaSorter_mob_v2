# Phase 04 - Settings toggle, visibility and strings

**Strategic spec:** [`../S0620_optional-nine-zone-grid.md`](../S0620_optional-nine-zone-grid.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Add the user-facing switch "Disable 9-zone tracking" (default off = grid on) at the very top of the "Touch zones and hints" block, bind it to the inverse of `nineZoneGridEnabled`, hide the 9-zone-specific rows when it is on, and show a short 3-zone explanation in their place. Trilingual EN/RU/UK.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`nineZoneGridEnabled` persisted + exposed via the settings VM).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 04.1 - Add the two trilingual strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two keys in lockstep across EN/RU/UK via `scripts/utils/set-android-string.ps1 -Action add`:
> - `disable_nine_zone_tracking` - EN "Disable 9-zone tracking", RU "Отключить отслеживание 9 зон", UK "Вимкнути відстеження 9 зон".
> - `three_zone_layout_explanation` - EN "With the 9-zone grid off, the player uses 3 zones: previous / zoom / next. A swipe from the left edge opens the command panel for file actions.", RU/UK equivalents.
> Verify against `docs/COMMUNICATION_POLICY.md` §2 (settings-label formula) and §6 (tone checklist).

**Verification:**

- `scripts/utils/set-android-string.ps1 -Action get -Key disable_nine_zone_tracking` exits 0 (present EN/RU/UK).
- `scripts/utils/set-android-string.ps1 -Action get -Key three_zone_layout_explanation` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 04.2 - Add the toggle + explanation views to the portrait layout

**Files:** `res/layout/fragment_settings_playback.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> At the top of the "Touch zones and hints" block (before the `always_show_touch_zones_overlay` row), add a `SwitchMaterial`/switch row id `switchDisableNineZone` bound to `@string/disable_nine_zone_tracking`, and a `TextView` id `tvThreeZoneExplanation` bound to `@string/three_zone_layout_explanation` (initially `gone`). Use `?attr/` colors, never hex. Wrap content; no full-bleed width (per the no-edge-to-edge / no-full-width-in-landscape conventions).

**Verification:**

- `Grep` - `switchDisableNineZone` and `tvThreeZoneExplanation` present in `res/layout/fragment_settings_playback.xml`.
- `Grep` - no new `="#` hex color in the added views.

**Status:** `[ ]` not done

---

### Step 04.3 - Mirror into the landscape layout

**Files:** `res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Apply the identical two views (`switchDisableNineZone`, `tvThreeZoneExplanation`) at the same position in the landscape variant. Landscape parity is mandatory - the variant exists. Keep the switch bounded-width (no full-screen stretch).

**Verification:**

- `Grep` - `switchDisableNineZone` and `tvThreeZoneExplanation` present in `res/layout-land/fragment_settings_playback.xml`.

**Status:** `[ ]` not done

---

### Step 04.4 - Bind the toggle and visibility logic in the fragment + VM

**Files:** `ui/settings/fragments/PlaybackSettingsFragment.kt`, `ui/settings/SettingsViewModel.kt`
**Depends on:** Steps 04.2, 04.3

**Prompt for developer:**

> In `PlaybackSettingsFragment`, mirror the `alwaysShowTouchZonesOverlay` wiring for the new switch: render `switchDisableNineZone.isChecked = !settings.nineZoneGridEnabled`; on change, persist `nineZoneGridEnabled = !isChecked` through the same VM/settings path the other playback toggles use (add a setter in `SettingsViewModel` mirroring the existing ones). When disabled (`isChecked == true`): set the 9-zone-specific rows (`always_show_touch_zones_overlay` row + the `show_player_hint` row and any 9-zone legend view in this block) to `gone` and `tvThreeZoneExplanation` to `visible`; invert when enabled. Use the binding view ids, no business logic beyond view state - keep it in the fragment's existing settings-render helper, not new logic in a layer below.

**Verification:**

- `Grep` - `nineZoneGridEnabled` present in `PlaybackSettingsFragment.kt` and `SettingsViewModel.kt`.
- `Grep` - `tvThreeZoneExplanation` visibility toggled (`isVisible`/`visibility`) in `PlaybackSettingsFragment.kt`.

**Status:** `[ ]` not done

---

### Step 04.5 - Build gate + string locale audit

**Files:** (none - validation only)
**Depends on:** Steps 04.1-04.4

**Prompt for developer:**

> Run `/build` -> `standard debug`, then `scripts/check_strings_localized.ps1 -KeyPrefix "disable_nine_zone"` and `-KeyPrefix "three_zone_layout"`.

**Verification:**

- `/build` standard debug PASS.
- `check_strings_localized.ps1` exits 0 for both prefixes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Settings docs sync (CLAUDE.md Rule 22): regenerate `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` + annotation for the new toggle (covered in Phase 05 if not done here).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

User-facing feature complete. Phase 05 runs catalog/doc sync, the settings-manifest regen, the ALL_FEATURES record, and the debug-tag insertion for the on-device gate.

---

## Rollback Plan

Revert phase commit(s) - removes the toggle + strings; the storage field and resolver default keep the grid on, so the player is unaffected.
