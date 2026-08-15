# Phase 01 - Navigation Redesign

**Strategic spec:** [`../S0330_player-control-menu-redesign.md`](../S0330_player-control-menu-redesign.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-02
**Completed:** 2026-06-02

---

## Objective

Replace the Playback Control dialog section radio group with a Material segmented/menu navigation that works in portrait and landscape.

---

## Prerequisites

- [x] Strategic UI clarification is resolved.
- [x] Working branch is `DEBUG-v011`.
- [x] Landscape counterpart `res/layout-land/dialog_playback_control.xml` is included.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_playback_control.xml` | Modified | <= 650 |
| `app_v2/src/main/res/layout-land/dialog_playback_control.xml` | Modified | <= 650 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt` | Modified | <= 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Modified | <= 750 |

> `PlaybackControlDialogFragment.kt`, portrait XML and landscape XML are >500 lines before edit, so create timestamped backups in `temp/` before editing.

---

## Steps

### Step 01.1 - Replace section selector layouts

**Files:** `app_v2/src/main/res/layout/dialog_playback_control.xml`, `app_v2/src/main/res/layout-land/dialog_playback_control.xml`
**Depends on:** start of phase

**Prompt for developer:**

> Replace `radioGroupPlaybackSections` and `rbSection*` radio controls with `MaterialButtonToggleGroup` navigation. Portrait uses a top horizontal segmented menu. Landscape uses a left vertical rail/menu. Preserve existing section content IDs and the `Done` button.

**Verification:**

- `rg -n "MaterialRadioButton.*rbSection|radioGroupPlaybackSections" app_v2/src/main/res/layout/dialog_playback_control.xml app_v2/src/main/res/layout-land/dialog_playback_control.xml` returns zero hits.
- `rg -n "groupPlaybackSections|btnSectionVolume|btnSectionSpeed" app_v2/src/main/res/layout/dialog_playback_control.xml app_v2/src/main/res/layout-land/dialog_playback_control.xml` returns hits in both files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 2/2 PASS. Files: portrait and landscape dialog XML. Dev log recorded.

---

### Step 01.2 - Store last section semantically

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a stable `KEY_LAST_SECTION` preference. Keep `KEY_LAST_TAB` as a legacy fallback so old stored indexes do not break first open after update.

**Verification:**

- `rg -n "KEY_LAST_SECTION" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt` returns exactly one declaration.
- `rg -n "KEY_LAST_TAB" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt` still returns the legacy key.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 2/2 PASS. File: PlaybackControlPreferences.kt. Dev log recorded.

---

### Step 01.3 - Wire menu navigation in the dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Update `ControlSection` to use the new `btnSection*` IDs and stable section names. Replace radio-group checks with `MaterialButtonToggleGroup` checks. Restore the selected section by saved section name or `KEY_LAST_SECTION`, falling back to legacy `KEY_LAST_TAB`, and save the stable name on selection. Hide irrelevant section buttons and keep selected/focus state explicit.

**Verification:**

- `rg -n "RadioButton.*section|radioGroupPlaybackSections|checkedRadioButtonId|STATE_SELECTED_TAB" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` returns zero hits.
- `rg -n "KEY_LAST_SECTION|STATE_SELECTED_SECTION|groupPlaybackSections|btnSectionVolume" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` returns hits.
- `rg -n "Timber\\.d\\(\"S0330:" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` returns zero hits until the ticket enters `BlockNeedUserTest`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 3/3 PASS plus XML well-formed checks. File: PlaybackControlDialogFragment.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles through the selected debug build command. `.\a.ps1 dq` -> `BUILD SUCCESSFUL in 42s` (2026-06-02); the earlier unrelated `SettingsProfileDialogFragment.kt` compile error is resolved.
- [x] Dev log entry added for every modified file.
- [x] `scripts/catalog_sync.ps1 -Module app_v2` runs after Kotlin changes.

---

## Handoff Notes to Next Phase

Navigation uses section IDs instead of radio indexes. Phase 02 closes validation and spec status.

---

## Rollback Plan

Revert phase edits to the two dialog XML files and two Kotlin files; no data migration is involved.
