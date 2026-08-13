# Phase 01 - New Section Scaffold

**Strategic spec:** [`../S0911_main-window-interface-settings-group.md`](../S0911_main-window-interface-settings-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Add an empty, registered "Main window interface" collapsible section to the General settings tab (portrait + landscape) and the new group-title string, so later phases have a container to move rows into.

---

## Prerequisites

- [ ] Strategic spec `Status: Approved` or later.
- [ ] Research artifact `research/01__main-window-settings-candidates.md` read in full.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 500 (currently well under) |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 400 (currently 349) |

---

## Steps

### Step 01.1 - Add the new group-title string and rename the programs-panel toggle title

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new trilingual string key `settings_category_main_window_interface` via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key settings_category_main_window_interface -En "Main window interface" -Ru "Интерфейс главного окна" -Uk "Інтерфейс головного вікна"` (one lockstep call, parity-enforced). Then shorten the existing `setting_show_programs_panel_title` value in each locale via three `-Action set` calls (guard each with `-ExpectedOldValue` to the current text) - EN: "Show programs panel in main window" -> "Programs panel"; RU: "Показывать панель программ в основном окне" -> "Панель программ"; UK: "Показувати панель програм у головному вікні" -> "Панель програм". Do not touch `setting_show_programs_panel_summary`, `setting_show_streams_panel_title/_summary`, or `resource_ops_in_overflow_menu` / `setting_resource_ops_in_overflow_menu_desc` - only the one title changes wording (strategic §3.1).

**Verification:**

- `Grep` - `name="settings_category_main_window_interface"` present in all three `strings.xml` files.
- `Grep` - `name="setting_show_programs_panel_title">Programs panel<` in `values/strings.xml`.
- `Grep` - `name="setting_show_programs_panel_title">Панель программ<` in `values-ru/strings.xml`.
- `Grep` - `name="setting_show_programs_panel_title">Панель програм<` in `values-uk/strings.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 4/4 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

### Step 01.2 - Add the empty section markup to both General layouts

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 01.1 (string key must exist before the layout references it)

**Prompt for developer:**

> In both layout files, immediately after the existing "INTERFACE SECTION" `MaterialCardView` block (the one containing `headerInterface`/`containerInterface`) and before the "FILE BROWSER INTERFACE SECTION" block, insert a new `MaterialCardView` block of the identical shape: a `CollapsibleSectionHeader` with `android:id="@+id/headerMainWindowInterface"` and `app:csh_title="@string/settings_category_main_window_interface"` (no icon, matching `headerInterface`'s minimal style), followed by an empty `LinearLayout` `android:id="@+id/containerMainWindowInterface"` (vertical orientation, same padding attributes as `containerInterface` in that file) with no child rows yet - later phases append rows into it. Copy the exact `MaterialCardView` wrapper attributes (`layout_marginHorizontal`, `cardCornerRadius`, `cardElevation`, `contentPadding`) from the adjacent Interface block in the same file so the new section is visually identical.

**Verification:**

- `Grep` - `headerMainWindowInterface` present in both layout files.
- `Grep` - `containerMainWindowInterface` present in both layout files.
- `Grep` - `settings_category_main_window_interface` present in both layout files (referenced by the new header).

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: layout/fragment_settings_general.xml (+13 LOC), layout-land/fragment_settings_general.xml (+13 LOC). Dev log recorded.

---

### Step 01.3 - Register the new section in GeneralSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 01.2 (view ids must exist in the inflated binding)

**Prompt for developer:**

> In `setupCollapsibleSections()`, add one more `register(...)` call for the new section, following the exact pattern of the existing calls: `register(binding.headerMainWindowInterface, binding.containerMainWindowInterface, "general__main_window_interface")`. Place it directly after the `register(binding.headerInterface, ...)` line, matching the new section's position in the layout.

**Verification:**

- `Grep` - `headerMainWindowInterface`, `containerMainWindowInterface`, and `general__main_window_interface` each present exactly once in the file (the single-line form exceeds the 120-char detekt limit, so the actual call is wrapped across 3 args - one per line - matching this file's existing wrap style for long calls).
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS (3 wrapped-arg call, each identifier confirmed once; 0 `Log.d(` hits). Files: ui/settings/fragments/GeneralSettingsFragment.kt (+5 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL, 2026-07-03; packaged resources confirmed to contain `headerMainWindowInterface` in both portrait and landscape.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`containerMainWindowInterface` (both portrait and landscape General layouts) is the target container Phases 02-04 append their moved rows into. The section registers and expands/collapses correctly even while empty - Phase 02 is the first to prove it holds a row.

**Known deferred gate:** `post-change.ps1`'s `settings-doc-sync-gate` (a ~3 min `testStandardDebugUnitTest` run) correctly FAILs from this step onward - `docs/settings/settings-manifest.json` is now stale relative to the new section, by design, until Phase 05 Step 05.2 regenerates it. Every other gate (dev-log, neuroslop, fgs-notification, focus-highlight) passed. Phases 02-04 skip re-running the full `post-change.ps1` for settings-surface files for this reason (record dev-log manually + run `assert-neuroslop.ps1 -Gate -ChangedFiles` directly instead) - the composite gate is meant to, and does, run to a real PASS only once, at Phase 05.

---

## Rollback Plan

Low-risk: revert this phase's commit(s). No Room schema, no Hilt scope, no data migration - purely a new empty UI section plus two string edits.
