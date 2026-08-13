# Phase 02 - Operations: take section-state disk read off the main thread

**Strategic spec:** [`../S0474_settings-activity-perf-research.md`](../S0474_settings-activity-perf-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01/03/04
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Wrap the synchronous `SharedPreferences` read in `OperationsSettingsFragment.setupExpandableSections()` in `StrictModeHelper.allowDiskReads` (and the listener write in `allowDiskWrites`), matching the pattern already used by `MediaSettingsFragment`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 1180 |

> `OperationsSettingsFragment.kt` is 1165 LOC (>500) - backup step required (Step 02.1). It is under the 1500 hard limit; do not add net new bulk - this phase is a wrap, not a feature. Structural decomposition of this file is S0479, not here.

---

## Steps

### Step 02.1 - Back up OperationsSettingsFragment before edit

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `OperationsSettingsFragment.kt` to `temp/OperationsSettingsFragment_<yyyyMMdd-HHmmss>.kt.bak` before editing (CLAUDE.md §10.5).

**Verification:**

- `Glob` - `temp/OperationsSettingsFragment_*.kt.bak` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 1/1 PASS. Backup `temp/OperationsSettingsFragment_20260617-122852.kt.bak` created.

---

### Step 02.2 - Wrap section-state prefs access in StrictMode helpers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `setupExpandableSections()` (currently line ~711) the body opens `requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)` and calls `prefs.getBoolean(..)` per section directly on the main thread during `onViewCreated`, with no StrictMode guard - unlike `MediaSettingsFragment.getSavedSectionStates()` which wraps the same access in `StrictModeHelper.allowDiskReads`. Build the `sections` list first (no I/O), then read the saved states inside a single `StrictModeHelper.allowDiskReads { .. }` into a `Map<String, Boolean>` keyed by `prefKey`, and use that map in the `forEach`. Wrap the per-section listener write (currently lines ~735-739) in `StrictModeHelper.allowDiskWrites { .. }`. Add the import `com.sza.fastmediasorter.core.debug.StrictModeHelper` if absent. Do not change the persisted keys, defaults, or the `ENABLE_SCHEDULED_OPERATIONS` gating.

**Verification:**

- `Grep` - `StrictModeHelper.allowDiskReads` present in `OperationsSettingsFragment.kt`.
- `Grep` - `StrictModeHelper.allowDiskWrites` present in `OperationsSettingsFragment.kt`.
- `Grep` - `import com.sza.fastmediasorter.core.debug.StrictModeHelper` present exactly once.
- `Grep` - `getBoolean(section.prefKey` no longer appears outside an `allowDiskReads` block (state read goes through the map).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS. `allowDiskReads` (state read → map), `allowDiskWrites` (listener write), import added; `getBoolean(section.prefKey` 0 hits. `OperationsSettingsFragment.kt`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (17s, joint with Phase 03).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in `OperationsSettingsFragment.kt` (no Log.d introduced).
- [x] Dev log entry added for `OperationsSettingsFragment.kt`.

---

## Handoff Notes to Next Phase

Operations-tab section-state I/O no longer hits the main thread unguarded. File LOC essentially unchanged (wrap only). Independent of other phases.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. Restore from `temp/OperationsSettingsFragment_*.kt.bak` if needed.
