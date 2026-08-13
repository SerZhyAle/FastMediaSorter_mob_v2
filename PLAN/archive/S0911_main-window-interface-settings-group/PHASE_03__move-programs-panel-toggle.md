# Phase 03 - Move Programs-Panel Toggle

**Strategic spec:** [`../S0911_main-window-interface-settings-group.md`](../S0911_main-window-interface-settings-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Move the "Programs panel" toggle (owner's named example, strategic §3.1) from Operations > Additional Programs into General > Main window interface, including its Kotlin read/write wiring - the first cross-fragment relocation in this ticket.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 650 (currently 668) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | ≤ 500 |

---

## Steps

### Step 03.1 - Remove the row from Operations (layout + Kotlin)

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** - start of phase (Phase 01 already done)

**Prompt for developer:**

> Delete the `rowShowProgramsPanel` `SettingsToggleRow` block (with its "S0755: surface the programs menu.." comment) from `containerAdditionalPrograms` in both `fragment_settings_destinations.xml` (portrait) and `fragment_settings_destinations.xml` (landscape - there it sits inside a two-column left-side `LinearLayout`; remove only this row, keep the surrounding column structure and its other children intact). In `OperationsSettingsFragment.kt`, delete the `binding.rowShowProgramsPanel.setOnCheckedChangeListener { .. }` block (the "S0755: main-window programs panel toggle." comment) and the `if (binding.rowShowProgramsPanel.isChecked != settings.showProgramsPanelInMainWindow) { .. }` render block. Do not touch any other row in this fragment or its layouts.

**Verification:**

- `Grep` - `rowShowProgramsPanel` returns zero hits in both `fragment_settings_destinations.xml` layout files.
- `Grep` - `rowShowProgramsPanel` returns zero hits in `OperationsSettingsFragment.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `OperationsSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: layout/fragment_settings_destinations.xml, layout-land/fragment_settings_destinations.xml, ui/settings/fragments/OperationsSettingsFragment.kt (-8 LOC). Dev log recorded.

---

### Step 03.2 - Add the row to General (layout + Kotlin)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt`
**Depends on:** Step 03.1 (row must be fully removed from its old home first, so the id is not duplicated while both exist mid-edit)

**Prompt for developer:**

> Add a `SettingsToggleRow` with `android:id="@+id/rowShowProgramsPanel"`, `app:str_title="@string/setting_show_programs_panel_title"` (now the shortened "Programs panel" text from Phase 01 Step 01.1), `app:str_subtitle="@string/setting_show_programs_panel_summary"` as the first child of `containerMainWindowInterface` in both General layout files (portrait and landscape), matching the row shape/margins already used by `rowResourceOpsInOverflowMenu` there (Phase 02). In `GeneralSettingsViewSetupHelper.kt`'s `setupSwitches()`, add: `binding.rowShowProgramsPanel.setOnCheckedChangeListener { isChecked -> if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener; val current = viewModel.settings.value; if (current.showProgramsPanelInMainWindow == isChecked) return@setOnCheckedChangeListener; viewModel.updateSettings(current.copy(showProgramsPanelInMainWindow = isChecked)) }`. In `GeneralSettingsObserversHelper.kt`'s `observeData()`, add: `if (binding.rowShowProgramsPanel.isChecked != settings.showProgramsPanelInMainWindow) binding.rowShowProgramsPanel.setCheckedSilently(settings.showProgramsPanelInMainWindow)`, placed next to the existing `rowResourceOpsInOverflowMenu` observation line. This toggle has no visibility gate (unlike streams-panel in Phase 04) - it is always shown, matching its behavior in its old location.

**Verification:**

- `Grep` - `rowShowProgramsPanel` present inside `containerMainWindowInterface`'s body in both General layout files.
- `Grep` - `binding.rowShowProgramsPanel.setOnCheckedChangeListener` present exactly once in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `showProgramsPanelInMainWindow` present in `GeneralSettingsObserversHelper.kt`.
- `Grep -n "Log\.d\("` returns zero hits in both modified `.kt` files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 4/4 PASS. **Ordering correction**: placed as the true first child (ahead of `rowResourceOpsInOverflowMenu`, not after it as originally drafted) - the owner's named example reads as the group's headline row; Phase 04's streams-panel row goes second, resource-ops now third. Files: layout/fragment_settings_general.xml, layout-land/fragment_settings_general.xml, ui/settings/helpers/GeneralSettingsViewSetupHelper.kt (+6 LOC), ui/settings/helpers/GeneralSettingsObserversHelper.kt (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL, 2026-07-03; cross-checked generated `FragmentSettingsGeneralBinding`/`FragmentSettingsDestinationsBinding` classes directly to confirm the view-id migration landed correctly (whole-project resource-link build still blocked by unrelated S0774 WIP - see Phase 02 note).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`rowShowProgramsPanel` is fully relocated and functions identically (same `AppSettings` field, same read/write semantics) from its new home. Phase 04 repeats this cross-fragment pattern for the streams-panel toggle, which additionally needs its existing visibility gate replicated explicitly (it currently inherits visibility from a wrapper it is about to leave).

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - no Room schema, no Hilt scope, no data migration. The `AppSettings.showProgramsPanelInMainWindow` field itself is untouched; only its settings-row UI location moves.
