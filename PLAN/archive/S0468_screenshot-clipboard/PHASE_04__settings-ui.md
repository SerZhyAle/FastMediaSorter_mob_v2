# Phase 04 - Settings UI

**Strategic spec:** [`../S0468_screenshot-clipboard.md`](../S0468_screenshot-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Expose a "Save screenshots to clipboard" toggle inside the existing gesture-capture settings group, with trilingual strings, bound to `copyScreenshotToClipboard`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`AppSettings.copyScreenshotToClipboard`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 1100 |

> Landscape parity: both `layout/` and `layout-land/fragment_settings_destinations.xml` exist and hold the gesture rows - the new row goes into BOTH.
>
> `OperationsSettingsFragment.kt` is 1224 LOC (>500, <1500) - take a timestamped backup into `temp/` before editing (CLAUDE.md Rule 5); no split required.

---

## Steps

### Step 04.1 - Add trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two keys across EN/RU/UK in lockstep with one call each (the capture confirmation string is authored in Phase 03):
> - `setting_copy_screenshot_to_clipboard_title` - toggle label.
> - `setting_copy_screenshot_to_clipboard_summary` - one-line description ("Also copy each captured screenshot to the clipboard, ready to paste").
>
> Use `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"` (parity-enforced). RU/UK must use `ё`/`Ё` where correct. Each string must pass `docs/COMMUNICATION_POLICY.md` §2 message formula and the §6 tone checklist before integration.

**Verification:**

- `Grep` - each of the two keys matches in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_copy_screenshot_to_clipboard"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification PASS: both keys present EN/RU/UK, parity check OK. Files: values/values-ru/values-uk strings.xml (+2 keys each). Cyrillic verified (no mojibake).

---

### Step 04.2 - Add the toggle row to both layouts

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> In both portrait and landscape variants, add a toggle row `@+id/row_copy_screenshot_to_clipboard` inside the gesture-capture settings group, mirroring the existing `row_gesture_overlay_enabled` row widget (same custom row type / style). Bind title to `@string/setting_copy_screenshot_to_clipboard_title` and summary to `@string/setting_copy_screenshot_to_clipboard_summary`. Use `?attr/`/`@color/` tokens only - no hardcoded hex. Ensure the row is keyboard/D-pad focusable and clickable, consistent with sibling rows.

**Verification:**

- `Grep` - `row_copy_screenshot_to_clipboard` matches in `layout/fragment_settings_destinations.xml`.
- `Grep` - `row_copy_screenshot_to_clipboard` matches in `layout-land/fragment_settings_destinations.xml`.
- `Grep -n "#[0-9a-fA-F]\{6\}"` on both files returns no new hardcoded color in the added row.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification PASS: `rowCopyScreenshotToClipboard` present in BOTH layout/ and layout-land/ (×1 each), no new hex. Used `SettingsToggleRow` mirroring `rowGestureOverlayEnabled`. Note: layout id convention here is camelCase (`@+id/rowCopyScreenshotToClipboard`), not snake_case as the plan's grep suggested - matched the existing sibling rows.

---

### Step 04.3 - Bind the toggle in the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Inside the existing `if (screenGestureControllers.isNotEmpty()) { .. }` settings-render block, sync `binding.rowCopyScreenshotToClipboard` from `settings.copyScreenshotToClipboard` via `setCheckedSilently(..)` (mirror the `rowGestureOverlayEnabled` pattern). Add a change listener that calls `viewModel.updateSettings(viewModel.settings.value.copy(copyScreenshotToClipboard = isChecked))`. The row is part of the gesture group, so it stays hidden in flavors where `screenGestureControllers` is empty - no extra gating.

**Verification:**

- `Grep` - `rowCopyScreenshotToClipboard` matches at least twice in `OperationsSettingsFragment.kt` (sync + listener).
- `Grep` - `copyScreenshotToClipboard = ` matches once in `OperationsSettingsFragment.kt` (the `copy(..)` update).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification PASS: `rowCopyScreenshotToClipboard` ×3 (sync if-check, sync setCheckedSilently, listener), `copyScreenshotToClipboard = isChecked` ×1. Files: OperationsSettingsFragment.kt (+6 LOC, backed up to temp/backups/). Bound inside the existing `screenGestureControllers.isNotEmpty()` group - stays hidden in non-noLegal flavors.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles for the noLegal flavor - run `/build` (noLegal debug; do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_copy_screenshot_to_clipboard"` - exit 0.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

User-facing surface is complete on the noLegal flavor. Phase 05 documents the feature and regenerates catalog/logs.

---

## Rollback Plan

Revert phase commit(s) - UI rows and strings are additive; removing them returns the settings screen to its prior layout.
