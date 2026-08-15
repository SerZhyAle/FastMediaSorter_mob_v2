# Phase 03 - Playback: resolve ShareTarget labels off the main thread

**Strategic spec:** [`../S0474_settings-activity-perf-research.md`](../S0474_settings-activity-perf-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01/02/04
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Move the per-target `PackageManager` label lookup in `PlaybackSettingsFragment.setupSendCommandsGroup()` off the main thread, so building the "Send file to.." group does not do N synchronous Binder calls during `onViewCreated`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 470 |

> `PlaybackSettingsFragment.kt` is 439 LOC (<500) - no backup step needed.

---

## Steps

### Step 03.1 - Build rows immediately with the declared title, no PackageManager on the open path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `setupSendCommandsGroup()` (currently line ~272) each `SettingsToggleRow` is built with `setTitle(resolveShareTargetLabel(target))`; `resolveShareTargetLabel()` (line ~332) calls `pm.getApplicationLabel(pm.getApplicationInfoCompat(pkg))` synchronously for every package-backed target. Change row construction to set the title to the fast fallback `getString(target.titleRes)` up front (no PackageManager), keeping all other row wiring (availability, subtitle, help, checked state, listener) unchanged. The `sendCommandRows[target.id] = row` map population stays.

**Verification:**

- `Grep` - `setTitle(getString(target.titleRes))` present in `setupSendCommandsGroup` area.
- `Grep` - `resolveShareTargetLabel` no longer called synchronously inside the `targets.forEach { .. }` row-builder block.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Row title now `setTitle(getString(target.titleRes))` at L288; no synchronous `resolveShareTargetLabel` in row-builder. `PlaybackSettingsFragment.kt`.

---

### Step 03.2 - Resolve installed-app labels in background and apply to existing rows

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> After the rows are added, launch a `lifecycleScope.launch` that computes the labels on `kotlinx.coroutines.Dispatchers.IO` (`withContext(Dispatchers.IO) { targets.associate { it.id to resolveShareTargetLabel(it) } }`) and then, back on the main thread, applies each resolved label via `sendCommandRows[id]?.setTitle(label)`. Use the view-bound `lifecycleScope` so the job is cancelled if the fragment view is destroyed; guard the apply with the existing `_binding`/lifecycle pattern used elsewhere in the file. Add imports for `androidx.lifecycle.lifecycleScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`, `kotlinx.coroutines.launch` as needed. Do not use `GlobalScope`.

**Verification:**

- `Grep` - `withContext(Dispatchers.IO)` present in `PlaybackSettingsFragment.kt`.
- `Grep` - `sendCommandRows[` followed by a `.setTitle(` apply appears in the background-apply block.
- `Grep` - `GlobalScope` returns zero hits in `PlaybackSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. `withContext(Dispatchers.IO)` resolves labels (L329), applied via `sendCommandRows[id]?.setTitle` (L332), `viewLifecycleOwner.lifecycleScope`; no `GlobalScope`. `PlaybackSettingsFragment.kt`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (17s, joint with Phase 02).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in `PlaybackSettingsFragment.kt` (no Log.d introduced).
- [x] Dev log entry added for `PlaybackSettingsFragment.kt`.

---

## Handoff Notes to Next Phase

Send-commands group renders instantly with declared titles; installed-app labels swap in asynchronously. No user-visible string changes. Independent of other phases.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
