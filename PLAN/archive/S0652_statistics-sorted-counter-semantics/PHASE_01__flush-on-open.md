# Phase 01 - Flush on screen open

**Strategic spec:** [`../S0652_statistics-sorted-counter-semantics.md`](../S0652_statistics-sorted-counter-semantics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-06-23
**Completed:** 2026-06-24

---

## Objective

Force a pending-stats flush before the dashboard/report read the snapshot, so copy/move operations completed within the ~2.5s debounce window are visible immediately instead of showing 0.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `StatsSink.flushNow()` exists (verified: `domain/stats/StatsSink.kt:18`).
- [ ] `StatsSink` is `@Singleton`-bound and injectable (verified: `core/di/StatsModule.kt:16-18`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetStatisticsUseCase.kt` | Modified | ≤ 40 |

> No layout, no DI module change (StatsSink already bound). Both the dashboard card and the TXT report read through `GetStatisticsUseCase`, so flushing here covers both read paths and preserves card/report parity.

---

## Steps

### Step 01.1 - Flush pending stats before reading the snapshot

**Files:** `domain/usecase/GetStatisticsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink` into `GetStatisticsUseCase` (constructor `@Inject` param, no new Hilt `@Provides` - it is already bound in `StatsModule`). At the start of `invoke()`, before `repository.getSnapshot()`, call `statsSink.flushNow()` so any operations buffered in the in-memory batch are persisted to DataStore first. Keep `invoke()` suspending. Do not add a swallowing `try/catch`; `flushNow()` already returns cleanly when the sink is disabled/empty. Add a one-line KDoc/comment explaining WHY the flush is here (close the debounce gap so just-completed copy/move ops are not read as 0).

**Verification:**

- `Grep` - `private val statsSink: StatsSink` matches once in `GetStatisticsUseCase.kt`.
- `Grep` - `statsSink.flushNow()` matches once, on a line above `repository.getSnapshot()`.
- `Grep -n "Log\.d\("` over `GetStatisticsUseCase.kt` returns zero hits.
- `/build` standard debug compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 3/3 PASS (statsSink decl ×1, flushNow above getSnapshot, 0 Log.d). Files: GetStatisticsUseCase.kt (+StatsSink dep, flush in invoke()).

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File .\a.ps1 fc` PASS on 2026-06-24.
- [x] Dev log entry added for `GetStatisticsUseCase.kt`.
- [x] `dev/CATALOG/app_v2.jsonl` regen completed in Phase 03.

---

## Handoff Notes to Next Phase

`GetStatisticsUseCase.invoke()` now flushes before reading. Phase 02 (wording) is independent and may run in parallel. Device test (BlockNeedUserTest) must verify: with statistics collection ON, perform copy/move, open Statistics immediately - the "Sorted" card shows the new non-zero count without re-opening.

---

## Rollback Plan

Revert the phase commit - no data migration, no schema change, no user-facing surface added (behavior-only: flush timing).
