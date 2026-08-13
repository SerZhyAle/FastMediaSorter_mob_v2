# Phase 03 — Settings Toggle

**Strategic spec:** [`../S0158_player-large-buttons.md`](../S0158_player-large-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add the "Big Buttons Mode" toggle row to the Player UI settings section (`containerPlayerUI`) in both portrait and landscape layout XML files, and wire the toggle in `PlaybackSettingsFragment` to `PlayerLayoutModePrefs`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (string keys `big_buttons_mode_title`, `big_buttons_mode_summary`, `tooltip_big_buttons_mode_title`, `tooltip_big_buttons_mode_message` exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | — |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 680 |

> `PlaybackSettingsFragment.kt` is 631 LOC — create timestamped backup in `temp/` before editing:
> `Copy-Item "app_v2/src/main/java/.../PlaybackSettingsFragment.kt" "temp/PlaybackSettingsFragment_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt.backup"`

---

## Steps

### Step 03.1 — Add toggle row to portrait layout XML

**Files:** `res/layout/fragment_settings_playback.xml`
**Depends on:** Phase 01 done (string keys exist)

**Prompt for developer:**

> Inside `containerPlayerUI` (id `@+id/containerPlayerUI`) in `fragment_settings_playback.xml`, add a new toggle row immediately after the last existing row in that section.
>
> Use the same pattern as neighboring toggle rows in that file (horizontal `LinearLayout` containing a vertical title/summary `LinearLayout` + a `SwitchMaterial`). Also add a help icon (`ImageView` id `iconHelpBigButtonsMode`) matching the pattern of `iconHelpCompactControls`.
>
> IDs to use:
> - Row switch: `switchBigButtonsMode`
> - Help icon: `iconHelpBigButtonsMode`
>
> Label: `@string/big_buttons_mode_title`
> Summary: `@string/big_buttons_mode_summary`

**Verification:**

- `Grep` — `switchBigButtonsMode` present in `res/layout/fragment_settings_playback.xml`.
- `Grep` — `iconHelpBigButtonsMode` present in `res/layout/fragment_settings_playback.xml`.
- `Grep` — `big_buttons_mode_title` referenced in `res/layout/fragment_settings_playback.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. switchBigButtonsMode, iconHelpBigButtonsMode, big_buttons_mode_title all present. Dev log recorded.

---

### Step 03.2 — Mirror change in landscape layout XML

**Files:** `res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Apply the identical toggle row addition (Step 03.1) to `res/layout-land/fragment_settings_playback.xml`. The landscape counterpart exists and must stay in sync with the portrait layout for the `containerPlayerUI` section.

**Verification:**

- `Grep` — `switchBigButtonsMode` present in `res/layout-land/fragment_settings_playback.xml`.
- `Grep` — `big_buttons_mode_title` referenced in `res/layout-land/fragment_settings_playback.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. switchBigButtonsMode and big_buttons_mode_title present in landscape. Dev log recorded.

---

### Step 03.3 — Wire toggle in `PlaybackSettingsFragment`

**Files:** `PlaybackSettingsFragment.kt`
**Depends on:** Steps 03.1 and 03.2

**Prompt for developer:**

> In `PlaybackSettingsFragment`, after creating the view, add:
>
> 1. **Init** (in `setupViews()` or `onViewCreated`): read `PlayerLayoutModePrefs.isBigButtonsMode(requireContext())` and set `binding.switchBigButtonsMode.isChecked` accordingly. Guard with an `isUpdatingFromSettings` flag to prevent recursive change events (same pattern used for `spinnerSortMode`).
>
> 2. **Toggle listener**: `binding.switchBigButtonsMode.setOnCheckedChangeListener { _, isChecked -> ... }`. When triggered (and not `isUpdatingFromSettings`): call `PlayerLayoutModePrefs.setBigButtonsMode(requireContext(), isChecked)` immediately. No app restart required (ADR-2: change takes effect next time player opens).
>
> 3. **Help icon**: `binding.iconHelpBigButtonsMode?.setOnClickListener { TooltipDialog.show(requireContext(), R.string.tooltip_big_buttons_mode_title, R.string.tooltip_big_buttons_mode_message) }`. Use the same `TooltipDialog` pattern as `iconHelpCompactControls`.
>
> Strings must pass `COMMUNICATION_POLICY.md` §6 tone checklist (verified in Phase 01 Step 01.3).

**Verification:**

- `Grep` — `switchBigButtonsMode` referenced in `PlaybackSettingsFragment.kt`.
- `Grep` — `setBigButtonsMode` called in `PlaybackSettingsFragment.kt`.
- `Grep` — `isBigButtonsMode` called in `PlaybackSettingsFragment.kt`.
- `Grep` — `iconHelpBigButtonsMode` referenced in `PlaybackSettingsFragment.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlaybackSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 5/5 PASS. isBigButtonsMode, setBigButtonsMode, switchBigButtonsMode, iconHelpBigButtonsMode all wired. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for each modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "big_buttons_mode"` exits 0.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "tooltip_big_buttons"` exits 0.

---

## Handoff Notes to Next Phase

Phase 03 delivers a working settings toggle that persists to `player_layout_mode` SharedPreferences. Phase 04 reads the preference and applies `PlayerBigButtonsModeManager` in the player.

---

## Rollback Plan

Revert phase commit(s) — no data migration; only layout XML and fragment code changed.
