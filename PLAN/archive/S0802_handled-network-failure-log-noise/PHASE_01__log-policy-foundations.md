# Phase 01 - Log Policy Foundations

**Strategic spec:** [`../S0802_handled-network-failure-log-noise.md`](../S0802_handled-network-failure-log-noise.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-29
**Completed:** 2026-06-29

---

## Objective

Introduce a reusable, silent classification + severity policy helper for handled network outcomes so background/manual sync paths can stop treating policy/fallback control-flow as throwable-level defects.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/HandledNetworkOutcomeLogger.kt` | New | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Add silent network classification entrypoint

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Refactor `NetworkErrorClassifier` so callers can classify a throwable without emitting the current "unclassified exception" warning as a side effect. Keep the existing `classify(...)` behavior intact for legacy callers, but add a second entrypoint for silent use by log-normalization code. Do not change the actual subtype mapping rules.

**Verification:**

- `Grep` - `fun classifySilently\\(` present in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt`.
- `Grep` - `private fun classifyInternal\\(` present in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-29 - Verification PASS. Files: `NetworkErrorClassifier.kt` (+84 LOC). Silent classification path added.

---

### Step 01.2 - Add handled outcome severity logger

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/HandledNetworkOutcomeLogger.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a small helper that accepts a scope label, resource label, and throwable, then emits the normalized log line for handled network outcomes. Map Wi-Fi/no-network/policy-style typed outcomes to `Timber.i`, handled transport/auth/not-found/timeout style outcomes to `Timber.w` without a throwable, and keep `Timber.e(throwable, ..)` only for truly unexpected/unclassified cases. Keep the output compact and stable enough for grep (`scope`, `resource`, `failureClass` or equivalent tags).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/HandledNetworkOutcomeLogger.kt` exists.
- `Grep` - `object HandledNetworkOutcomeLogger` matches exactly once in that file.
- `Grep` - `fun logHandledSyncFailure\\(` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-29 - Verification PASS. Files: `HandledNetworkOutcomeLogger.kt` (+46 LOC). Shared handled-outcome logger added.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` PASS on 2026-06-29.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Background/manual sync call sites must use the shared helper instead of direct `Timber.e(e, ..)` for handled network exceptions.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
