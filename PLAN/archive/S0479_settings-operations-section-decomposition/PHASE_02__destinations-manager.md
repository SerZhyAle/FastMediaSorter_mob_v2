# Phase 02 - Destinations Manager

**Strategic spec:** [`../S0479_settings-operations-section-decomposition.md`](../S0479_settings-operations-section-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Extract the destinations list (adapter, reorder/delete/color, add dialog, adaptive layout, list observation) out of `OperationsSettingsFragment` into `OperationsDestinationsManager`; destinations stay embedded in the Operations tab.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Backup `OperationsSettingsFragment.kt` (>500 LOC) to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsDestinationsManager.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 1000 |

> No XML touched. The adaptive 1-col/grid layout uses the existing `R.integer.destinations_column_count`; preserve it.

---

## Steps

### Step 02.1 - Create `OperationsDestinationsManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsDestinationsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `OperationsDestinationsManager(binding: FragmentSettingsDestinationsBinding, viewModel: SettingsViewModel, fragment: Fragment)`. Move into it: the `DestinationsAdapter` inner class (make it a nested class of the manager) and the `DestinationDiffCallback` object; `setupDestinationsLayoutManager()`, `moveDestination()`, `deleteDestination()`, `showAddDestinationDialog()`, `showColorPicker()`, `updateAddDestinationVisibility()`; the destinations RecyclerView wiring and `btnAddDestination` listener currently in `setupViews()`; the `iconHelpDestinations` tooltip listener. Expose `fun setup()` (adapter + layout manager + listeners), `fun observe()` (the `viewModel.destinations` collect that calls `submitList`, updates add-destination visibility, and toggles `tvNoScheduledOps` empty-hint), and `fun onConfigurationChanged()` (re-applies the layout manager). Expose `val currentDestinations: List<MediaResource>` so other sections can read the latest destination targets. Use `fragment.viewLifecycleOwner.lifecycleScope` / `collectOnLifecycle` for the collect - never a bare `lifecycleScope.launch { collect }`.

**Verification:**

- `Glob` - `OperationsDestinationsManager.kt` exists.
- `Grep` - `class OperationsDestinationsManager` matches exactly once.
- `Grep` - `fun setup()` and `fun observe()` and `fun onConfigurationChanged()` present in the new file.
- `Grep` - `class DestinationsAdapter` matches exactly once in the new file.
- `Grep -n "lifecycleScope.launch {[^}]*\.collect"` - zero hits in the new file (lifecycle-safe collection only).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 5/5 PASS. Files: helpers/OperationsDestinationsManager.kt (New, 232 LOC). Uses collectOnLifecycle; adapter moved as nested class.

---

### Step 02.2 - Delegate from the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `private val destinationsManager by lazy { OperationsDestinationsManager(binding, viewModel, this) }`. Call `destinationsManager.setup()` from `setupViews()` (where the adapter was wired) and `destinationsManager.observe()` inside `observeData()`. Route `onConfigurationChanged()` to `destinationsManager.onConfigurationChanged()`. Replace the in-fragment `destinationTargets` usage in `showDestinationPicker()` with `destinationsManager.currentDestinations`. Delete the migrated members from the fragment: `adapter` field, `DestinationsAdapter`, `DestinationDiffCallback`, `setupDestinationsLayoutManager`, `moveDestination`, `deleteDestination`, `showAddDestinationDialog`, `showColorPicker`, `updateAddDestinationVisibility`, `destinationTargets`, and the destinations `viewModel.destinations.collect` block in `observeData`.

**Verification:**

- `Grep` - `destinationsManager.setup()` and `destinationsManager.observe()` present in the fragment.
- `Grep` - `inner class DestinationsAdapter` returns zero hits in the fragment.
- `Grep` - `private fun showAddDestinationDialog` returns zero hits in the fragment.
- `Grep` - `private fun moveDestination` returns zero hits in the fragment.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. Fragment delegates to destinationsManager.setup()/observe()/onConfigurationChanged(); picker reads currentDestinations; removed adapter+8 methods+destinationTargets and 5 unused imports.

---

### Step 02.3 - Compile

**Files:** -
**Depends on:** Step 02.2

**Prompt for developer:**

> Compile the touched area. Resolve unresolved references (the picker now reads `destinationsManager.currentDestinations`).

**Verification:**

- `/build` (or `.\a.ps1 fk`) exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - `.\a.ps1 fk` BUILD SUCCESSFUL. Neuroslop gate PASS.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog regeneration deferred to Phase 06.

---

## Handoff Notes to Next Phase

`destinationsManager.currentDestinations` is the single source of latest destination targets for the destination pickers used by Capture (Phase 04) and Gestures (Phase 05).

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
