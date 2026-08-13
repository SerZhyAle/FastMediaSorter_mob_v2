# Phase 04 - Move Streams-Panel Toggle

**Strategic spec:** [`../S0911_main-window-interface-settings-group.md`](../S0911_main-window-interface-settings-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03 (Step 04.2 positions this row after `rowShowProgramsPanel`, which Phase 03 Step 03.2 inserts)
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Move the "Show streams panel in main window" toggle from Media > Streams into General > Main window interface, explicitly replicating its existing two-level visibility gate (flavor capability + `enableStreams` master toggle) that it currently inherits for free by living inside `streamsDefaultsGroup` - this is the phase strategic §7 risk row 1 is about.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_streams.xml` | Modified | n/a (small file) |
| `app_v2/src/main/res/layout-land/fragment_settings_streams.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/StreamsSettingsFragment.kt` | Modified | ≤ 150 (currently 147) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | ≤ 500 |

---

## Steps

### Step 04.1 - Remove the row from Streams (layout + Kotlin)

**Files:** `app_v2/src/main/res/layout/fragment_settings_streams.xml`, `app_v2/src/main/res/layout-land/fragment_settings_streams.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/StreamsSettingsFragment.kt`
**Depends on:** - start of phase (Phase 01 already done)

**Prompt for developer:**

> Delete the `rowShowStreamsPanel` `SettingsToggleRow` block (with its "S0756: show pinned channels.." comment) from inside `streamsDefaultsGroup` in both `fragment_settings_streams.xml` (portrait) and (landscape) - leave `streamsDefaultsGroup` itself and its remaining children (the dropdown rows) untouched. In `StreamsSettingsFragment.kt`, delete the `bindSwitch(binding.rowShowStreamsPanel) { .. }` call (with its "S0756: main-window streams panel toggle.." comment) and the `setSwitchChecked(binding.rowShowStreamsPanel, settings.showStreamsPanelInMainWindow)` line inside `collectOnLifecycle`. Do not touch `binding.streamsDefaultsGroup.visibility` or any other row - the group still gates its remaining dropdown rows exactly as before.

**Verification:**

- `Grep` - `rowShowStreamsPanel` returns zero hits in both `fragment_settings_streams.xml` layout files.
- `Grep` - `rowShowStreamsPanel` returns zero hits in `StreamsSettingsFragment.kt`.
- `Grep` - `streamsDefaultsGroup` still present in `StreamsSettingsFragment.kt` (confirms the group itself was not accidentally removed).
- `Grep -n "Log\.d\("` returns zero hits in `StreamsSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 4/4 PASS. Files: layout/fragment_settings_streams.xml, layout-land/fragment_settings_streams.xml, ui/settings/fragments/StreamsSettingsFragment.kt (-6 LOC). Dev log recorded.

---

### Step 04.2 - Add the row to General with its visibility gate replicated

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt`
**Depends on:** Step 04.1 (row removed from its old home first), Phase 03 Step 03.2 (`rowShowProgramsPanel` already the first child of `containerMainWindowInterface` - this row follows it)

**Prompt for developer:**

> Add a `SettingsToggleRow` with `android:id="@+id/rowShowStreamsPanel"`, `app:str_title="@string/setting_show_streams_panel_title"`, `app:str_subtitle="@string/setting_show_streams_panel_summary"` as the second child of `containerMainWindowInterface` (right after `rowShowProgramsPanel`) in both General layout files - titles/subtitles are unchanged (strategic §3.2). `GeneralSettingsObserversHelper` needs the streams capability gate to reproduce the two-level visibility this row previously got for free from `streamsDefaultsGroup`: add a constructor parameter `private val capabilityAvailability: com.sza.fastmediasorter.core.capability.CapabilityAvailability` (already injected in `GeneralSettingsFragment` for the Downloadable Extensions gate - pass the existing field through at the `GeneralSettingsObserversHelper(..)` construction site in `GeneralSettingsFragment.kt`). In `observeData()`, add: `if (binding.rowShowStreamsPanel.isChecked != settings.showStreamsPanelInMainWindow) binding.rowShowStreamsPanel.setCheckedSilently(settings.showStreamsPanelInMainWindow)` followed by `binding.rowShowStreamsPanel.visibility = if (capabilityAvailability.isStreamsAvailable() && settings.enableStreams) View.VISIBLE else View.GONE` (matches exactly the condition `StreamsSettingsFragment` used for `streamsDefaultsGroup` - flavor capability AND the master toggle). In `GeneralSettingsViewSetupHelper.kt`'s `setupSwitches()`, add: `binding.rowShowStreamsPanel.setOnCheckedChangeListener { isChecked -> if (getIsUpdatingSpinner()) return@setOnCheckedChangeListener; val current = viewModel.settings.value; if (current.showStreamsPanelInMainWindow == isChecked) return@setOnCheckedChangeListener; viewModel.updateSettings(current.copy(showStreamsPanelInMainWindow = isChecked)) }`.

**Verification:**

- `Grep` - `rowShowStreamsPanel` present inside `containerMainWindowInterface`'s body in both General layout files, after `rowShowProgramsPanel`.
- `Grep` - `capabilityAvailability: com.sza.fastmediasorter.core.capability.CapabilityAvailability` present in `GeneralSettingsObserversHelper.kt` constructor.
- `Grep` - `isStreamsAvailable() && settings.enableStreams` present in `GeneralSettingsObserversHelper.kt`.
- `Grep` - `binding.rowShowStreamsPanel.setOnCheckedChangeListener` present exactly once in `GeneralSettingsViewSetupHelper.kt`.
- `Grep -n "Log\.d\("` returns zero hits in all modified `.kt` files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 5/5 PASS. Final `containerMainWindowInterface` order confirmed: rowShowProgramsPanel, rowShowStreamsPanel, rowResourceOpsInOverflowMenu (matches Phase 03's ordering correction). Files: layout/fragment_settings_general.xml, layout-land/fragment_settings_general.xml, ui/settings/fragments/GeneralSettingsFragment.kt (+2 LOC), ui/settings/helpers/GeneralSettingsViewSetupHelper.kt (+6 LOC), ui/settings/helpers/GeneralSettingsObserversHelper.kt (+6 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL, 2026-07-03 (full recompile, 11/13 tasks executed, all Kotlin/data-binding wiring validated). Whole-project resource-link build still blocked by unrelated S0774 WIP - see Phase 02 note.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All three rows are now fully relocated into General > Main window interface with their original behavior (including the streams-panel's flavor+toggle gate) preserved. Phase 05 only needs docs/catalog regeneration - no further source changes.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - no Room schema, no Hilt scope, no data migration. `AppSettings.showStreamsPanelInMainWindow` is untouched; only its settings-row UI location and the code line that gates its visibility move.
