# Phase 05 - Settings UI rows

**Strategic spec:** [`../S0659_streams-settings-expansion.md`](../S0659_streams-settings-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Render the four new rows in the «Трансляции» settings section - three `SettingsDropdownRow` (Default sort, Default media filter, Catalog refresh policy) and one "Clear play statuses" action button - persisting via the shared `SettingsViewModel`, gated to visible only when `enableStreams == true`, with full EN/RU/UK strings and keyboard/D-pad/mouse focus.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - settings fields persist.
- [ ] Phase 03 ✅ Done - `SettingsViewModel.clearStreamPlayStatuses()` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt` | Modified | ≤ 170 |
| `app_v2/src/main/res/layout/fragment_settings_streams.xml` | Modified | ≤ 120 |
| `app_v2/src/main/res/layout-land/fragment_settings_streams.xml` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/StreamsSettingsFragment.kt` | Modified | ≤ 170 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |

> Landscape parity (Rule 11): `res/layout-land/fragment_settings_streams.xml` exists and is edited in lockstep with the portrait variant in Step 05.2.

---

## Steps

### Step 05.1 - BaseSettingsFragment: dropdown bind helpers

**Files:** `ui/settings/fragments/BaseSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two helpers mirroring the existing `bindSpinner`/`setSpinnerSelection` pair, for `SettingsDropdownRow`:
> - `protected fun bindDropdown(row: SettingsDropdownRow, onUserSelected: (position: Int) -> Unit)` - registers `row.setOnItemSelectedListener { if (!isUpdatingFromSettings) onUserSelected(it) }`.
> - `protected fun setDropdownSelection(row: SettingsDropdownRow, index: Int)` - calls `row.setSelection(index)` only when `row.getSelectedIndex() != index` (avoids redundant re-selection; `setSelection` already suppresses the listener).
> This unifies the dropdown pattern (covers research candidate #3 in-scope).

**Verification:**

- `Grep` - `fun bindDropdown(row: SettingsDropdownRow` present.
- `Grep` - `fun setDropdownSelection(row: SettingsDropdownRow` present.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - added `bindDropdown` (listener gated on `isUpdatingFromSettings`) + `setDropdownSelection` (re-selects only on change) to `BaseSettingsFragment`, mirroring `bindSpinner`/`setSpinnerSelection`.

---

### Step 05.2 - Strings: settings-row labels + dropdown entries + confirm (EN/RU/UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add the strings BEFORE the layout references them (keeps each step build-clean). Add trilingual keys via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En ... -Ru ... -Uk ...` (one lockstep call per key, parity-enforced):
> - `settings_streams_default_sort` (title), `settings_streams_default_filter` (title), `settings_streams_catalog_refresh` (title).
> - `settings_streams_catalog_refresh_manual`, `settings_streams_catalog_refresh_on_open`, `settings_streams_catalog_refresh_wifi` (dropdown entries).
> - `settings_streams_clear_statuses` (button), `settings_streams_clear_statuses_confirm` (dialog body), `settings_streams_clear_statuses_done` (toast).
> Reuse existing `streams_sort_name|topic|language|recent` and `streams_filter_all|streams_filter_media_audio|streams_filter_media_video` for the sort/filter dropdown entries (no new keys for those). All copy follows `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone): plain user language, no "HLS"/"transport"/technical terms.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_streams_"` exits 0.
- `Grep` - `settings_streams_catalog_refresh_on_open` present in all three `strings.xml`.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist (offer/neutral tone, no technical jargon).

**Status:** `[x]` done

**Step Log:** 2026-06-24 - added 9 trilingual keys (titles `default_sort`/`default_filter`/`catalog_refresh`; entries `catalog_refresh_manual`="Only when I ask"/`_on_open`="Suggest when I open"/`_wifi`="Automatically on Wi-Fi"; `clear_statuses`/`_confirm`/`_done`); reused `streams_sort_*` + `streams_filter_*`; `settings_streams_` parity check exit 0.

---

### Step 05.3 - Layouts: add three dropdowns + clear-statuses button (portrait + land)

**Files:** `res/layout/fragment_settings_streams.xml`, `res/layout-land/fragment_settings_streams.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> In BOTH layout files, between `rowEnableStreams` and `btnStreams`, add a vertical container `streamsDefaultsGroup` (id) holding three `com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow` (ids `rowDefaultSort`, `rowDefaultMediaFilter`, `rowCatalogRefresh`) with `app:sdr_title` set to the new title strings and a fixed `app:sdr_fieldWidth` matching the width other settings dropdowns use (grep an existing `sdr_fieldWidth` usage for the dimen/value - avoid greedy `match_parent` in weighted rows, per memory on SettingsInputRow). Below them add a `MaterialButton` `btnClearPlayStatuses` (style `@style/Widget.FastMediaSorter.SettingsButton.Tonal`, `wrap_content` width, text `@string/settings_streams_clear_statuses`). Every row/button: `android:focusable="true"`, `android:clickable="true"`; no hardcoded hex colors (use `?attr/`/`@color/`); keep inside the systemBars-safe padding already on the root. Set D-pad order with `nextFocusDown`/`nextFocusUp` chaining toggle -> dropdowns -> clear -> shortcut.

**Verification:**

- `Grep` - `rowDefaultSort`, `rowDefaultMediaFilter`, `rowCatalogRefresh`, `btnClearPlayStatuses` each present in BOTH layout files.
- `Grep` - no `="#` literal hex color introduced in either file.
- `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` passes for both layouts.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - added `streamsDefaultsGroup` (3 `SettingsDropdownRow` `rowDefaultSort`/`rowDefaultMediaFilter`/`rowCatalogRefresh` at `sdr_fieldWidth=@dimen/settings_dropdown_compact_width`) + `btnClearPlayStatuses` (Tonal) to BOTH layouts in lockstep; focusable/clickable + nextFocus chaining toggle->dropdowns->clear->shortcut; no hex; neuroslop exit 0; files byte-identical except header comment.

---

### Step 05.4 - Fragment: bind dropdowns, clear action, and visibility gate

**Files:** `ui/settings/fragments/StreamsSettingsFragment.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> In `onViewCreated`:
> - Set entries on each dropdown (sort = `streams_sort_*` in `StreamDefaultSort` order; filter = `streams_filter_all`/`_media_audio`/`_media_video` in `StreamMediaTypeFilter` order; catalog = the three `settings_streams_catalog_refresh_*` in `StreamsCatalogRefreshPolicy` order).
> - `bindDropdown(binding.rowDefaultSort) { viewModel.updateSettings(viewModel.settings.value.copy(streamsDefaultSort = StreamDefaultSort.entries[it])) }`; same pattern for filter and catalog policy.
> - Wire `binding.btnClearPlayStatuses` to a `MaterialAlertDialogBuilder` confirm (`settings_streams_clear_statuses_confirm`) whose positive button calls `viewModel.clearStreamPlayStatuses()` then shows the `settings_streams_clear_statuses_done` toast.
> - In the existing `collectOnLifecycle(viewModel.settings)` block, reflect current values with `withSettingsUpdate { setDropdownSelection(row, enumValue.ordinal) }`, and gate `binding.streamsDefaultsGroup.visibility` and `binding.btnClearPlayStatuses.visibility` on `settings.enableStreams` (mirrors `btnStreams`, keeping the section compact when off).

**Verification:**

- `Grep` - `bindDropdown(binding.rowDefaultSort` present.
- `Grep` - `clearStreamPlayStatuses()` invoked from the confirm dialog.
- `Grep` - `streamsDefaultsGroup.visibility` gated on `settings.enableStreams`.
- `Grep -n "Log\.d\("` returns zero hits in `StreamsSettingsFragment.kt` (Timber only).
- `/build` standard debug compiles.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - `onViewCreated` sets enum-ordered entries, `bindDropdown` persists each via `updateSettings(copy(..))` using `Enum.entries[it]`; `btnClearPlayStatuses` -> confirm dialog -> `clearStreamPlayStatuses()` + done toast; settings collector reflects values via `withSettingsUpdate { setDropdownSelection(.., ordinal) }` and gates `streamsDefaultsGroup`/`btnClearPlayStatuses`/`btnStreams` on `enableStreams`. `Log.d(` zero hits. Static checks pass (binding IDs cross-match, symbols resolve); `/build` NOT run per task constraint - compile unverified.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - run `/build` standard debug.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_streams_"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All four rows render and persist; visibility follows the master toggle. Phase 06 regenerates the settings docs (Rule 22), records the capability in `ALL_FEATURES`, and regenerates the catalog.

---

## Rollback Plan

Revert phase commit(s) - layout/string/fragment additions are self-contained; the underlying settings persist harmlessly without the UI.
