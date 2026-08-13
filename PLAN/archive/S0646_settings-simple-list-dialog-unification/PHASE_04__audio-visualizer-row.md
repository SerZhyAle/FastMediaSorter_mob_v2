# Phase 04 - Migrate audio visualizer selector to trigger row (K)

**Strategic spec:** [`../S0646_settings-simple-list-dialog-unification.md`](../S0646_settings-simple-list-dialog-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

> Step Log (batched): audio empty-state dropdown replaced by `rowAudioEmptyStateMode` (`SettingsSelectionRow`) + `SimpleValueChoiceDialog` in both portrait + landscape `fragment_settings_audio.xml`; delivery gate (`requireInstalled(AUDIO_VISUALIZATIONS)`) preserved in `onSelected`, MODE_GIF_LOOP->MODE_VISUALIZATION normalization kept. Compiled in the consolidated clean build.

---

## Objective

Convert the "audio empty-state animation mode" (visualizer-when-cover-art-absent) `AutoCompleteTextView` exposed-dropdown into a `SettingsSelectionRow` trigger row that opens `SimpleValueChoiceDialog`, while preserving the on-demand delivery gate that downloads the audio-visualizations set when `VISUALIZATION` is picked and keeps the prior mode on refusal (strategic §6.4).

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`SimpleValueChoiceDialog` available).
- [ ] `res/layout/fragment_settings_audio.xml` and `res/layout-land/fragment_settings_audio.xml` both present (verified).
- [ ] `DeliveryEnableInterceptor` injection in `AudioSettingsFragment` present (verified).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_audio.xml` | Modified | ≤ 160 |
| `app_v2/src/main/res/layout-land/fragment_settings_audio.xml` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt` | Modified | ≤ 320 |

> **Landscape parity (mandatory):** the audio empty-state block exists in both `layout/` and `layout-land/` variants; both are edited in Step 04.1.

---

## Steps

### Step 04.1 - Replace empty-state dropdown with trigger row (XML, portrait + landscape)

**Files:** `res/layout/fragment_settings_audio.xml`, `res/layout-land/fragment_settings_audio.xml`
**Depends on:** start of phase

**Prompt for developer:**

> In BOTH layout files, inside the `layoutAudioEmptyState` container, replace the label `TextView` + the `TextInputLayout`/`AutoCompleteTextView` (`tilAudioEmptyStateMode` / `actvAudioEmptyStateMode`) with a single `com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow` named `rowAudioEmptyStateMode`.
> Carry over `android:layout_width="match_parent"`, `android:layout_height="wrap_content"`, and set `app:ssr_title="@string/audio_empty_state_label"`. The chevron shows by default; value is set from the fragment. No hardcoded hex colors.

**Verification:**

- `Grep -n "actvAudioEmptyStateMode"` in both `fragment_settings_audio.xml` files returns zero hits.
- `Grep` - `rowAudioEmptyStateMode` present in both portrait and landscape files.
- `Grep -n "AutoCompleteTextView"` in both files (within the empty-state block) returns zero hits.

**Status:** `[x] done`

---

### Step 04.2 - Rewire row to dialog, preserve delivery gate (K)

**Files:** `AudioSettingsFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Remove the `ArrayAdapter` + `actvAudioEmptyStateMode.setAdapter` / `setOnItemClickListener` wiring and the `actvAudioEmptyStateMode.setText` call in `revertEmptyStateModeSelection()`.
> Build the ordered option list of `SimpleValueChoiceDialog.Option(key, label)` from `emptyStateModeKeys` paired with the existing `audio_empty_state_none/avd_pulse/canvas_bars/canvas_waves/visualization` labels.
> Set `rowAudioEmptyStateMode.setOnRowClickListener` to open `SimpleValueChoiceDialog(requireContext(), viewLifecycleOwner, title = getString(R.string.audio_empty_state_label), options, currentKey = <persisted mode, normalizing MODE_GIF_LOOP -> MODE_VISUALIZATION as today>, onSelected = { key -> ... })`.
> In `onSelected`, reproduce the current branch: when `key == MODE_VISUALIZATION`, call `deliveryEnableInterceptor.requireInstalled(this, DeliverableSet.AUDIO_VISUALIZATIONS, onReady = { viewModel.updateSettings(current.copy(audioEmptyStateMode = MODE_VISUALIZATION)) }, onUnavailable = { /* no settings write; row value stays on persisted mode */ })`; otherwise `viewModel.updateSettings(current.copy(audioEmptyStateMode = key))`.
> Keep `revertEmptyStateModeSelection()` only if still referenced elsewhere; otherwise remove it. Refresh the row value text from persisted settings in `observeData()` (same normalization), so a refused download leaves the row showing the prior mode.

**Verification:**

- `Grep -n "setOnItemClickListener"` for `actvAudioEmptyStateMode` returns zero hits in the file.
- `Grep` - `rowAudioEmptyStateMode` referenced with `setOnRowClickListener`.
- `Grep` - `deliveryEnableInterceptor.requireInstalled` still present with `AUDIO_VISUALIZATIONS` (delivery gate preserved).
- `Grep` - `SimpleValueChoiceDialog` present.

**Status:** `[x] done`

---

### Step 04.3 - Compile and confirm audio section

**Files:** (build only)
**Depends on:** Steps 04.1-04.2

**Prompt for developer:**

> Build standard debug. Confirm `FragmentSettingsAudioBinding` no longer exposes `actvAudioEmptyStateMode` / `tilAudioEmptyStateMode` and the fragment compiles against `rowAudioEmptyStateMode`.

**Verification:**

- `/build` -> `standard debug` (`a.ps1 dq`) exits 0.
- `Grep -n "actvAudioEmptyStateMode"` across `AudioSettingsFragment.kt` returns zero hits.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` -> `standard debug`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Both portrait and landscape `fragment_settings_audio.xml` edited (no portrait-only change).
- [ ] Dev log entry added (batched) via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All 11 inventory sites (A-K) now render through the unified tap-row + `ListSelectionDialog` / `SimpleValueChoiceDialog` pattern. The visualizer delivery gate is preserved through the dialog's `onSelected` wrapper; a refused download writes nothing, so the observed settings flow keeps the row on the prior mode.

---

## Rollback Plan

Revert phase commit(s): XML reverts to the exposed-dropdown, fragment reverts to `ArrayAdapter` + `setOnItemClickListener`. No data migration; `audioEmptyStateMode` keys unchanged.
