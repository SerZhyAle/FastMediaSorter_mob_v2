# Phase 06 - Settings UI: per-direction action pickers

**Strategic spec:** [`../S0425_screenshot-gesture-actions.md`](../S0425_screenshot-gesture-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 0 / 4
**Started:** -
**Completed:** 2026-06-16

---

## Objective

Replace the dead `rowScreenshotGestureDown` switch with three action-picker rows (down / right / up) in the Screen Gestures section. Each row opens a single-choice dialog of the available actions and persists the selection. OCR-translate is hidden when the capability is absent. Picker logic lives in a helper manager to keep the fragment under the line budget.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`AppSettings` action fields + enums).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/ScreenshotGestureActionPickerManager.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 1500 |

> Landscape parity: both `layout/` and `layout-land/` variants of `fragment_settings_destinations.xml` exist and BOTH are edited in step 06.1. Picker logic is extracted to a Manager (step 06.3) to keep `OperationsSettingsFragment` under 1500 LOC.

---

## Steps

### Step 06.1 - Replace the down switch with three picker rows (both orientations)

**Files:** `res/layout/fragment_settings_destinations.xml`, `res/layout-land/fragment_settings_destinations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `containerScreenGestures`, remove the `SettingsToggleRow` `@id/rowScreenshotGestureDown`. Insert three clickable picker rows mirroring the existing `rowScreenshotDestination` pattern (vertical `LinearLayout`, `clickable="true"`, `focusable="true"`, title `TextView` + `ic_chevron_right`, value `TextView` below). Ids: `rowScreenshotGestureActionDown` / `Right` / `Up`, value text ids `tvScreenshotGestureActionDownValue` / `Right` / `Up`. Titles reference the new string keys from step 06.2. Apply the identical change to the `layout-land/` variant. Use only `?attr/`/`@color/`/`@dimen/` references - no hardcoded hex.

**Verification:**

- `Grep` - `rowScreenshotGestureDown"` returns zero hits in both layout files.
- `Grep` - `rowScreenshotGestureActionDown`, `..Right`, `..Up` present in both `layout/` and `layout-land/`.
- `Grep` - `tvScreenshotGestureActionDownValue` present in both.
- `.\a.ps1 fr` - resources/manifest compile.

**Status:** `[ ]` not done

---

### Step 06.2 - Add/remove strings in lockstep (EN/RU/UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Remove `setting_screenshot_gesture_down_title` and `setting_screenshot_gesture_down_summary` from all three locales (use `set-android-string.ps1 -Action remove`). Add via `set-android-string.ps1 -Action add` (one lockstep call per key, parity-enforced) the row titles `setting_screenshot_gesture_action_down_title`, `..._right_title`, `..._up_title`, the picker dialog title `setting_screenshot_gesture_action_dialog_title`, and the six action labels `screenshot_gesture_action_silent`, `..._open_player`, `..._open_draw`, `..._ocr_translate`, `..._share`, `..._none`. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (label formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `setting_screenshot_gesture_down_title` returns zero hits across all `strings*.xml`.
- `Grep` - each new key present in `values/`, `values-ru/`, `values-uk/`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screenshot_gesture_action"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 06.3 - Create the picker manager

**Files:** `ui/settings/helpers/ScreenshotGestureActionPickerManager.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Create `class ScreenshotGestureActionPickerManager`. Expose `fun labelFor(context: Context, action: ScreenshotGestureAction): String` (enum → string res) and `fun availableActions(): List<ScreenshotGestureAction>` filtering out `OCR_TRANSLATE` when `CapabilityAvailability.isTranslationAvailable()` is false. Expose `fun showPicker(context, direction, current, onPicked: (ScreenshotGestureAction) -> Unit)` building a `MaterialAlertDialogBuilder` single-choice list of `availableActions()` labels, pre-selecting `current`, invoking `onPicked` on confirm. No business logic beyond mapping/dialog; the fragment owns persistence.

**Verification:**

- `Glob` - `ui/settings/helpers/ScreenshotGestureActionPickerManager.kt` exists.
- `Grep` - `class ScreenshotGestureActionPickerManager` matches once.
- `Grep` - `fun labelFor`, `fun availableActions`, `fun showPicker` present.
- `Grep` - `isTranslationAvailable` present (OCR gating).

**Status:** `[ ]` not done

---

### Step 06.4 - Wire the rows in the fragment

**Files:** `ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 06.1, 06.3

**Prompt for developer:**

> Instantiate `ScreenshotGestureActionPickerManager`. In the settings-collect block (~L628 area) set each value `TextView` (`tvScreenshotGestureActionDownValue` etc.) to `manager.labelFor(action)` from `settings.screenshotGestureActionDown/Right/Up`. Set each row's click listener to `manager.showPicker(...)` with the current action, persisting via `viewModel.updateSettings(viewModel.settings.value.copy(screenshotGestureActionX = picked))`. Ensure rows are keyboard/D-pad focusable (layout already sets `focusable`; confirm `nextFocus*` continuity is not broken). Remove any residual references to the deleted toggle row.

**Verification:**

- `Grep` - `ScreenshotGestureActionPickerManager` referenced in the fragment.
- `Grep` - `screenshotGestureActionDown =`, `..Right =`, `..Up =` each present in a `copy(` call.
- `Grep` - `rowScreenshotGestureDown` returns zero hits in the fragment.
- `.\a.ps1 fc` - code + resources compile.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screenshot_gesture_action"` exits 0.
- [ ] `OperationsSettingsFragment.kt` ≤ 1500 LOC.
- [ ] No hardcoded hex in the edited layout rows (neuroslop gate).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The user can now set each of the three gesture directions to any available action (OCR hidden when unsupported). Selection persists and round-trips through `AppSettings`. Phase 07 handles FEATURES docs + catalog regen.

---

## Rollback Plan

Revert phase commit(s). Layout/strings/fragment only - no persisted-data change beyond the already-removed key.
