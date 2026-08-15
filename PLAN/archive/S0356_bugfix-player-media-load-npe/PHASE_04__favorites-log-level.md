# Phase 04 - Favorites Reconcile Log Level

**Strategic spec:** [`../S0356_bugfix-player-media-load-npe.md`](../S0356_bugfix-player-media-load-npe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Set the favorites-reconcile log lines to the level justified by the Phase 01 finding: a situation that is now handled gracefully must not log at `Timber.e`; a genuine data defect keeps an elevated level with enough context to find the source.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Phase 01 Handoff Notes records whether the reconcile failure is always a data defect or can occur benignly (strategic §6.2).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | ≤ 520 |

> No layout files. Shared `src/main` - no flavor source set involved.

---

## Steps

### Step 04.1 - Set the reconcile log lines to the justified level

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Decide the level from the Phase 01 finding recorded in `PHASE_01` Handoff Notes:
> - If the reconcile failure is now always handled gracefully (a single corrupted element is isolated by Phase 03 and the upstream guard from Phase 02 prevents the null at source), the existing outer `Timber.e(e, "PlayerViewModel.loadMediaFiles: Failed to reconcile favorites, using existing flags")` at line 357 is over-elevated - lower it to `Timber.w` (handled degradation, not an error the developer must act on). Apply the same justified level to the per-element line added in Phase 03.
> - If Phase 01 proved the failure is always a true data defect, keep the elevated level but ensure the message carries the source/path context for attribution.
> The reconcile message must describe the subject in plain English and must NOT embed `S0356` (it is a permanent operational log, per CLAUDE.md - ticket ids in log text are reserved for `BlockNeedUserTest` probes).

**Verification:**

- `Grep` - the reconcile log line "Failed to reconcile favorites" is at the level chosen per Phase 01 (`Timber.w` if degraded, `Timber.e` if defect-confirmed), verified by `Grep -n "reconcile favorites"` showing the chosen `Timber.<level>`.
- `Grep` - `S0356` does not appear on any permanent `Timber.i`/`Timber.w`/`Timber.e` line in `PlayerMediaFilesLoader.kt`.
- `Grep -n "Log\.d\("` - zero hits in `PlayerMediaFilesLoader.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. Per Phase 01 finding (failure always a data defect but now handled gracefully), outer reconcile log lowered to `Timber.w` (line 396: "Player media files: failed to reconcile favorites, using existing flags") and the per-element onFailure line is `Timber.w` (line 387) carrying the element path via describeMediaFileForLog. No S0356 on any permanent Timber.i/w/e line (grep no match). Log.d expected 0 | actual 0.

---

### Step 04.2 - Record the log-level decision in the strategic spec §6.2 resolution

**Files:** `PLAN/S0356_bugfix-player-media-load-npe.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Update strategic §6.2 (`Уровень логирования синхронизации «избранное»`) `Статус:` from `Open` to `Resolved`, with a one-line note stating the chosen level and the reason (handled degradation → `Timber.w`, or confirmed data defect → `Timber.e`). Do not invoke any `scripts/spec_catalog/*` mutator - this is an edit to the strategic `.md` body only; journal sync is handled centrally.

**Verification:**

- `Grep` - within strategic §6.2 block, `Статус:` shows `Resolved` (not `Open`).
- Value - the resolution note names the chosen `Timber` level. `expected: level + reason recorded | actual: <fill at run>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. Strategic §6.2 `Статус:` = Resolved (line 102); resolution note (line 101) names `Timber.w` and the reason (per-element isolation + upstream guard => handled degradation, not a list-load crash). expected: level + reason recorded | actual: Timber.w + reason present.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `compileStandardDebugKotlin` succeeded during the test task (BUILD SUCCESSFUL).
- [x] `Grep` for `TODO(phase-04)` returns zero hits. (expected 0 | actual 0)
- [x] Dev log entry added for every file in "Files Touched" (batched in Phase 05 closure).

---

## Handoff Notes to Next Phase

All four strategic goals are now addressed: upstream guard (Phase 02), reconcile isolation (Phase 03), justified log level (this phase). Both §6 research items are Resolved. Phase 05 removes the temporary diagnostics, regenerates the catalog, and writes the dev log / changelog closure.

---

## Rollback Plan

Revert phase commit - log-level only change; no behavior or user-facing surface affected.
