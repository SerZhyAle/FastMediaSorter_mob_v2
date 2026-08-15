# Phase 01 - Add Grid View elements to Interface section

**Strategic spec:** [`../S0254_settings-grid-to-interface.md`](../S0254_settings-grid-to-interface.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 7 / 7
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Append the three control rows from `containerGridView` (Icon Size + Grid Mode, Hide Grid Action Buttons, File Ops Overflow Menu) into the end of `containerInterface` in General settings - both portrait and landscape - and wire up their Kotlin binding inside `GeneralSettingsFragment` so they read/write `SettingsViewModel` exactly as they do today in `PlaybackSettingsFragment`. After this phase the controls work from both screens simultaneously; the Playback copy is removed in Phase 02.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] No outstanding edits in `fragment_settings_general.xml` (portrait/landscape) or `GeneralSettingsFragment.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 360 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | ≤ 600 |

> **Landscape parity:** the landscape variant of `fragment_settings_general.xml` exists. Both portrait and landscape are edited in matching steps below.
> **Flavor placement:** all touched files are in `src/main/` - this feature is flavor-agnostic (all flavors share the same settings UI).

---

## Steps

### Step 01.1 - Append Icon Size + Grid Mode row to containerInterface (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Locate `<LinearLayout android:id="@+id/containerInterface"` (around line 46). Insert a new child `LinearLayout` row as the **last child** of `containerInterface`, immediately before its closing `</LinearLayout>` tag (around line 162-163). The row content must be a verbatim copy of the "Icon Size for Grid" row from `app_v2/src/main/res/layout/fragment_settings_playback.xml` (lines 113-127): a horizontal `LinearLayout` containing `tilIconSize` (`TextInputLayout` + nested `AutoCompleteTextView etIconSize`), `iconHelpGridSize` (`ImageButton`), `switchGridMode` (`SwitchMaterial`), and the trailing label `TextView` with `android:text="@string/grid_mode"`. Preserve every attribute (id, width/height, margins, hints, content descriptions, dimens references, style) byte-for-byte. Do NOT add a section header above; do NOT add a divider or extra padding. The row must be a flat sibling of the existing Interface elements.

**Verification:**

- `Grep` - `android:id="@+id/etIconSize"` matches exactly once in `app_v2/src/main/res/layout/fragment_settings_general.xml` (expected: 1 | actual: must equal 1).
- `Grep` - `android:id="@+id/switchGridMode"` matches exactly once in the same file.
- `Grep` - `android:id="@+id/iconHelpGridSize"` matches exactly once in the same file.
- `Grep` - `android:id="@+id/tilIconSize"` matches exactly once in the same file.
- `Grep` - `android:id="@+id/headerGridView"` does **not** match in the same file (expected: 0).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 5/5 PASS (etIconSize=1, switchGridMode=1, iconHelpGridSize=1, tilIconSize=1, headerGridView=0). File: `app_v2/src/main/res/layout/fragment_settings_general.xml` (+16 LOC). Dev log recorded.

---

### Step 01.2 - Append Hide Grid Action Buttons row to containerInterface (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Immediately after the row inserted in Step 01.1 and still inside `containerInterface`, append a verbatim copy of the "Hide Grid Action Buttons" row from `app_v2/src/main/res/layout/fragment_settings_playback.xml` (lines 129-138): a horizontal `LinearLayout` with `switchHideGridActionButtons` (`SwitchMaterial`) and a nested vertical `LinearLayout` holding the title `TextView` (`@string/hide_grid_action_buttons`) and the secondary description `TextView` (`@string/setting_hide_grid_action_buttons_desc`). Preserve all attributes byte-for-byte. No extra wrappers.

**Verification:**

- `Grep` - `android:id="@+id/switchHideGridActionButtons"` matches exactly once in `app_v2/src/main/res/layout/fragment_settings_general.xml`.
- `Grep` - `@string/hide_grid_action_buttons` matches at least once in the same file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS (switchHideGridActionButtons=1, @string/hide_grid_action_buttons=1). File: `app_v2/src/main/res/layout/fragment_settings_general.xml` (+10 LOC). Dev log recorded.

---

### Step 01.3 - Append File Ops Overflow Menu row to containerInterface (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Immediately after the row inserted in Step 01.2 and still inside `containerInterface`, append a verbatim copy of the "File Ops Overflow Menu" row from `app_v2/src/main/res/layout/fragment_settings_playback.xml` (lines 140-160): a horizontal `LinearLayout` with `switchFileOpsOverflowMenu` (`SwitchMaterial`) and a nested vertical `LinearLayout` holding the title `TextView` (`@string/pref_file_ops_overflow_menu_title`) and the secondary description `TextView` (`@string/pref_file_ops_overflow_menu_desc`). Preserve all attributes byte-for-byte.

**Verification:**

- `Grep` - `android:id="@+id/switchFileOpsOverflowMenu"` matches exactly once in `app_v2/src/main/res/layout/fragment_settings_general.xml`.
- `Grep` - `@string/pref_file_ops_overflow_menu_title` matches at least once in the same file.
- `Grep` - the closing `</LinearLayout>` for `containerInterface` is positioned **after** the three newly inserted rows (manual visual check; predicate: `containerInterface` and the three new switch ids appear in this order in the file).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS (switchFileOpsOverflowMenu=1, @string/pref_file_ops_overflow_menu_title=1, order: containerInterface@46 < switchGridMode@172 < switchHideGridActionButtons@181 < switchFileOpsOverflowMenu@195). File: `app_v2/src/main/res/layout/fragment_settings_general.xml` (+22 LOC). Dev log recorded.

---

### Step 01.4 - Mirror Steps 01.1–01.3 in landscape layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Repeat Steps 01.1, 01.2, 01.3 against the landscape variant. Locate `<LinearLayout android:id="@+id/containerInterface"` in `app_v2/src/main/res/layout-land/fragment_settings_general.xml` and append the same three rows (Icon Size + Grid Mode, Hide Grid Action Buttons, File Ops Overflow Menu) as the last children of that container, copying verbatim from `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` lines 113-160. Preserve every attribute byte-for-byte; do NOT change widths/margins relative to portrait. No header, no divider.

**Verification:**

- `Grep` - `android:id="@+id/etIconSize"` matches exactly once in `app_v2/src/main/res/layout-land/fragment_settings_general.xml`.
- `Grep` - `android:id="@+id/switchGridMode"` matches exactly once.
- `Grep` - `android:id="@+id/switchHideGridActionButtons"` matches exactly once.
- `Grep` - `android:id="@+id/switchFileOpsOverflowMenu"` matches exactly once.
- `Grep` - `android:id="@+id/iconHelpGridSize"` matches exactly once.
- `Grep` - `android:id="@+id/tilIconSize"` matches exactly once.
- `Grep` - `android:id="@+id/headerGridView"` does **not** match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS (etIconSize=1, switchGridMode=1, switchHideGridActionButtons=1, switchFileOpsOverflowMenu=1, iconHelpGridSize=1, tilIconSize=1, headerGridView=0). File: `app_v2/src/main/res/layout-land/fragment_settings_general.xml` (+48 LOC). Dev log recorded.

---

### Step 01.5 - Wire Grid Mode + Hide Grid Action Buttons + File Ops Overflow Menu listeners in GeneralSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` (plus appropriate helper if delegation is preferred: `GeneralSettingsViewSetupHelper.kt`)
**Depends on:** Step 01.4

**Prompt for developer:**

> Add listener wiring for the three new switches and the help icon, equivalent to lines 167-176, 179-189, 342-348 of `PlaybackSettingsFragment.kt`. `SettingsViewModel` is the same `by activityViewModels()` instance across all settings fragments, so calls of the form `viewModel.updateSettings(viewModel.settings.value.copy(defaultGridMode = isChecked))` write to the same `AppSettings`. Reuse the existing `isUpdatingFromSettings` re-entrancy guard pattern from `PlaybackSettingsFragment` (introduce an equivalent flag in `GeneralSettingsFragment` if absent, or delegate via the existing helper used by other Interface controls; do not duplicate state). Wire:
>
> - `binding.switchGridMode.setOnCheckedChangeListener` → `viewModel.updateSettings(current.copy(defaultGridMode = isChecked))`.
> - `binding.switchHideGridActionButtons.setOnCheckedChangeListener` → `viewModel.updateSettings(current.copy(hideGridActionButtons = isChecked))`.
> - `binding.switchFileOpsOverflowMenu.setOnCheckedChangeListener` → preserve the exact behaviour from `PlaybackSettingsFragment.kt` lines 179-191 including the `fileOpsOverflowMenuHintShown` first-time hint logic. Do NOT simplify - bug for bug copy.
> - `binding.iconHelpGridSize.setOnClickListener` → open the same tooltip dialog the playback fragment opens (lines 342-348). The tooltip implementation MUST be reused, not re-implemented; if it lives in a shared helper, call that helper; if it is inline in `PlaybackSettingsFragment`, extract to a shared utility (place under `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/` if extracted) **before** wiring General to it.
>
> Use `Timber.d`, never `Log.d`. Do NOT add a `Timber.d("S0254: …")` tag yet - tags are inserted at the boundary into `BlockNeedUserTest` (see Phase 03 close-out).

**Verification:**

- `Grep` - `binding.switchGridMode.setOnCheckedChangeListener` matches exactly once in `GeneralSettingsFragment.kt` (or in the helper it delegates to).
- `Grep` - `binding.switchHideGridActionButtons.setOnCheckedChangeListener` matches exactly once.
- `Grep` - `binding.switchFileOpsOverflowMenu.setOnCheckedChangeListener` matches exactly once.
- `Grep` - `binding.iconHelpGridSize.setOnClickListener` matches exactly once.
- `Grep` - `Log\.d\(` returns zero hits across all files modified in this step (expected: 0 | actual: must be 0).
- Build: `.\a.ps1 dq` (or `/build` debug-standard) returns exit 0 - project compiles with the new bindings.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 6/6 PASS (switchGridMode/HideGridActionButtons/FileOpsOverflowMenu listeners=1 each, iconHelpGridSize click=1, Log.d=0, assembleStandardDebug BUILD SUCCESSFUL in 30s). File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` (+29 LOC). Dev log recorded.

---

### Step 01.6 - Wire Icon Size autocomplete + observer in GeneralSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` (plus `GeneralSettingsViewSetupHelper.kt` if used for similar controls)
**Depends on:** Step 01.5

**Prompt for developer:**

> Port the icon-size adapter, initial text, item-click handler, and focus-change clamp logic from `PlaybackSettingsFragment.kt` lines 333-340 and 391-405. The data source for the dropdown values must remain the same `ArrayAdapter` content as in Playback - copy the resource(s) it reads from, not re-derive. Re-use the same `viewModel.updateSettings(current.copy(defaultIconSize = clampedSize))` write path. Add the matching observer reading `settings.defaultIconSize` and updating `binding.etIconSize` only when the value differs, equivalent to `PlaybackSettingsFragment.kt` lines 477-481. Wire the observer through the existing `GeneralSettingsObserversHelper` if other Interface controls already use it; otherwise inline using `collectOnLifecycle` consistent with the rest of `GeneralSettingsFragment`.

**Verification:**

- `Grep` - `binding.etIconSize.setAdapter` matches exactly once across `GeneralSettingsFragment.kt` and its helpers.
- `Grep` - `binding.etIconSize.setOnItemClickListener` matches exactly once.
- `Grep` - `binding.etIconSize.setOnFocusChangeListener` matches exactly once.
- `Grep` - `defaultIconSize` appears at least twice in `GeneralSettingsFragment.kt` (once in listener write path, once in observer read path) - or split across the fragment + helper; total count must be ≥ 2.
- Build: `.\a.ps1 dq` returns exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 5/5 PASS (etIconSize.setAdapter/setOnItemClickListener/setOnFocusChangeListener each present once in GeneralSettingsViewSetupHelper.kt, defaultIconSize=6 occurrences in helper, assembleStandardDebug BUILD SUCCESSFUL in 31s). Files: `GeneralSettingsViewSetupHelper.kt` (+28 LOC: setupIconSizeInput + setup() call), `GeneralSettingsObserversHelper.kt` (+10 LOC: 4 reconciliation blocks - 3 switches + 1 icon size). Note: switch reconciliation added alongside icon size as natural symmetric counterpart to listeners from 01.5. Dev log recorded.

---

### Step 01.7 - Update SettingsSearchIndex destinations and re-route grid search records

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`
**Depends on:** Step 01.6

**Prompt for developer:**

> Locate every `SettingsSearchIndex(..)` record whose `destination = SettingsSearchDestination.PLAYBACK` and whose `viewId` references one of: `R.id.switchGridMode`, `R.id.switchHideGridActionButtons`, `R.id.switchFileOpsOverflowMenu`, `R.id.etIconSize`, `R.id.iconHelpGridSize`, `R.id.tilIconSize`. For each such record, change `destination` to `SettingsSearchDestination.GENERAL`. Do NOT change the `key`, `title`, or `keywords` fields - they remain searchable as-is. If a `SettingsSearchDestination.GENERAL` enum value does not yet exist, add it; place it next to existing destination values in the enum block. The `key` prefix `playback.` MAY remain unchanged to avoid a search-history mismatch; do not rename keys.

**Verification:**

- `Grep` - `R.id.switchGridMode` appears in `SettingsSearchIndex.kt` AND the nearest `destination = ` line above it (within 8 lines) matches `SettingsSearchDestination.GENERAL` (manual diff check; predicate: zero `PLAYBACK` strings within 8 lines above any of the five `R.id.*` references listed in this step).
- `Grep` - `R.id.switchFileOpsOverflowMenu` reference also has `destination = SettingsSearchDestination.GENERAL` within 8 lines above.
- `Grep` - `R.id.etIconSize` reference also has `destination = SettingsSearchDestination.GENERAL` within 8 lines above.
- Build: `.\a.ps1 dq` returns exit 0.
- Smoke: launch the Settings search (Phase 03 device test); searching for "grid mode" routes the result to General tab. **Deferred to Phase 03 device test - not blocking phase close.**

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Two records (playback.grid_mode → R.id.switchGridMode, playback.icon_size → R.id.etIconSize) re-routed PLAYBACK → GENERAL. Other PLAYBACK entries (sort mode, slideshow interval, play to end, allow delete, camera capture/filename) untouched - unrelated to S0254 scope. assembleStandardDebug BUILD SUCCESSFUL in 46s. File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt` (+2 LOC comments + 4 line edits). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] All three new rows appear at the end of `containerInterface` in BOTH portrait and landscape (visual confirmation via XML grep).
- [ ] No portrait-only edits: every `Grep -l` for the five new view ids that returns `fragment_settings_general.xml` (portrait) also returns the landscape variant.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 03; no API changes in this phase.

---

## Handoff Notes to Next Phase

After Phase 01, the controls work from both screens (Playback "Сетка" block and the bottom of General "Интерфейс" block). Interim state is intentional - Phase 02 removes the Playback copy atomically. `SettingsSearchIndex` already routes to General, so search behavior is consistent with the final state from this phase onwards.

---

## Rollback Plan

Revert phase commit(s) - no data migration, no user-facing API change, no Room version bump. SharedPreferences values are untouched; rolling back simply hides the new controls in General and leaves the Playback originals working as before.
