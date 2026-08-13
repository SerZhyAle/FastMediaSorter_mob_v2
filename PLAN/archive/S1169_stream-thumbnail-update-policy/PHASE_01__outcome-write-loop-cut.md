# Phase 01 - Cut the outcome-write feedback loop

**Strategic spec:** [`../S1169_stream-thumbnail-update-policy.md`](../S1169_stream-thumbnail-update-policy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Make `markPlayOutcome` a conditional UPDATE that touches zero rows when the outcome value is unchanged, so a repeated capture/probe outcome no longer invalidates the Room `observeAll()` Flow and no longer re-emits the whole catalog list.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `StreamSourceDao.markPlayOutcome` / `StreamSourceRepository.recordPlayOutcome` unchanged from current main.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/local/db/StreamSourceDaoOutcomeTest.kt` | New | ≤ 160 |

> No `@Database` version bump: the SET columns are unchanged, only the WHERE clause changes - Room schema is identical, so no migration.

---

## Steps

### Step 01.1 - Make `markPlayOutcome` a no-op when the outcome is unchanged

**Files:** `data/local/db/StreamSourceDao.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the `@Query` on `markPlayOutcome(id, outcome, atMillis)` to only update when the stored outcome differs from `:outcome`: add `AND (lastPlayOutcome IS NULL OR lastPlayOutcome <> :outcome)` to the existing `WHERE id = :id`. This makes a repeated same-value probe (e.g. a chronically dead channel already `UNKNOWN`) affect zero rows, so SQLite's update hook does not fire and Room does not re-emit `observeAll()`. Do not bump the `@Database` version - columns are unchanged. Keep the KDoc, add one line noting the conditional-write intent (WHY: cut the capture->DB->list re-emit loop, S1169).

**Verification:**

- `Grep` - `lastPlayOutcome <> :outcome` present exactly once in `StreamSourceDao.kt`.
- `Grep` - `SET lastPlayOutcome = :outcome, lastPlayOutcomeAt = :atMillis` still present (SET clause unchanged).
- `Grep -n "version = "` in `data/local/db/AppDatabase.kt` - value unchanged from pre-phase (no migration introduced).

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Step 01.1: Verification 3/3 PASS (conditional WHERE present, SET clause intact, DB version 44 unchanged). File: StreamSourceDao.kt.

### Step 01.2 - Test the conditional write

**Files:** `data/local/db/StreamSourceDaoOutcomeTest.kt` (new)
**Depends on:** Step 01.1

**Prompt for developer:**

> Add an in-memory Room test (`Room.inMemoryDatabaseBuilder`, `allowMainThreadQueries` is banned - use `runTest` + suspend DAO calls) that: inserts one row; calls `markPlayOutcome(id, "UNKNOWN", t1)`; asserts the row's `lastPlayOutcome == "UNKNOWN"` and `lastPlayOutcomeAt == t1`; calls `markPlayOutcome(id, "UNKNOWN", t2)` with `t2 != t1`; asserts `lastPlayOutcomeAt` is STILL `t1` (zero-row update - value unchanged); then `markPlayOutcome(id, "OK", t3)` and asserts outcome flips to `OK` and `lastPlayOutcomeAt == t3`. Follow the existing Room-test setup in `StreamFramePersistentStoreTest.kt` for builder/teardown style.

**Verification:**

- `Glob` - `StreamSourceDaoOutcomeTest.kt` exists.
- `Grep` - `markPlayOutcome` matches >= 3 times in the test (three writes).
- `.\a.ps1 fu` targeted: `--tests *StreamSourceDaoOutcomeTest` green.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Step 01.2: Verification 3/3 PASS (file exists, markPlayOutcome x3 in test, `testStandardDebugUnitTest --tests *StreamSourceDaoOutcomeTest` tests=1 failures=0 errors=0). Main compilation proven by the test compile. File: StreamSourceDaoOutcomeTest.kt.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `--tests *StreamSourceDaoOutcomeTest` green.
- [ ] Dev log entry added for both files.
- [ ] Phase-boundary audit: no P0/P1 (Room main-safety - DAO stays `suspend`; no `allowMainThreadQueries`).

---

## Handoff Notes to Next Phase

A repeated same-value outcome no longer re-emits the catalog Flow. Genuine OK<->UNKNOWN transitions still re-emit (expected); Phase 03 makes that re-emit a partial (status-bullet-only) rebind instead of a full one.

---

## Rollback Plan

Revert the phase commit(s) - query-only change, no data migration or user-facing surface changed.
