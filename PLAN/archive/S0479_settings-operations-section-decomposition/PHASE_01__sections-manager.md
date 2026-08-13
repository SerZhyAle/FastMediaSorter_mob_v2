# Phase 01 - Sections Manager

**Strategic spec:** [`../S0479_settings-operations-section-decomposition.md`](../S0479_settings-operations-section-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Extract the expandable-section + section-state-prefs machinery out of `OperationsSettingsFragment` into `OperationsSectionsManager`; fragment delegates section setup.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Backup `OperationsSettingsFragment.kt` (>500 LOC) to `temp/` before the first edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsSectionsManager.kt` | New | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 1180 |

> No XML touched (managers bind the existing `FragmentSettingsDestinationsBinding`). Layout/landscape parity not triggered.

---

## Steps

### Step 01.1 - Create `OperationsSectionsManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsSectionsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `OperationsSectionsManager(binding: FragmentSettingsDestinationsBinding, fragment: Fragment)`. Move into it the private `ExpandableSection` data class, the `PREFS_NAME` + `KEY_*_EXPANDED` constants, and the body of `setupExpandableSections()` (verbatim, including the `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` gate that hides the scheduled header/container and the `StrictModeHelper.allowDiskReads/allowDiskWrites` wrapping). Expose a single `fun setup()`. Preserve the existing per-section pref read/write behaviour exactly - this is a move, not a redesign. Use `fragment.requireContext()` for context. Timber only; no new logging.

**Verification:**

- `Glob` - `OperationsSectionsManager.kt` exists.
- `Grep` - `class OperationsSectionsManager` matches exactly once.
- `Grep` - `fun setup()` present in the new file.
- `Grep` - `StrictModeHelper.allowDiskReads` present in the new file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. Files: helpers/OperationsSectionsManager.kt (New, 86 LOC).

---

### Step 01.2 - Delegate from the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a lazy field `private val sectionsManager by lazy { OperationsSectionsManager(binding, this) }`. Replace the `setupExpandableSections()` call in `onViewCreated` with `sectionsManager.setup()`. Delete the now-migrated `setupExpandableSections()` method, the `ExpandableSection` data class, and the `PREFS_NAME` / `KEY_*_EXPANDED` constants from the fragment. Keep `checkAndExpandScheduledSection()` in the fragment for now (it is scheduled-flow logic moved in Phase 03).

**Verification:**

- `Grep` - `sectionsManager.setup()` present in the fragment.
- `Grep` - `private fun setupExpandableSections` returns zero hits in the fragment.
- `Grep` - `data class ExpandableSection` returns zero hits in the fragment.
- `Grep` - `KEY_SAFETY_EXPANDED` returns zero hits in the fragment.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. Fragment delegates to sectionsManager.setup(); removed setupExpandableSections, ExpandableSection, KEY_* constants + unused StrictModeHelper/CollapsibleSectionHeader imports.

---

### Step 01.3 - Compile

**Files:** -
**Depends on:** Step 01.2

**Prompt for developer:**

> Compile the touched area. Resolve any unresolved-reference fallout (imports for the moved constants/classes now live in the manager).

**Verification:**

- `/build` (or `.\a.ps1 fk`) exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - `.\a.ps1 fk` BUILD SUCCESSFUL (compileStandardDebugKotlin). Neuroslop gate PASS (no new violations).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 06 (batch).

---

## Handoff Notes to Next Phase

Section expand/collapse + prefs now owned by `OperationsSectionsManager`. Section headers/containers are untouched in the binding; later managers wire their own row listeners and rely on these sections already being set up.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
