# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S0802_handled-network-failure-log-noise.md`](../S0802_handled-network-failure-log-noise.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-29
**Completed:** 2026-06-29

---

## Objective

Close the ticket with spec/status sync, catalog refresh, and verification evidence for the logging normalization change.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Working tree is on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0802_handled-network-failure-log-noise.md` | Modified | ≤ 260 |
| `PLAN/S0802_handled-network-failure-log-noise/INDEX.md` | Modified | ≤ 260 |
| `PLAN/S0802_handled-network-failure-log-noise/PHASE_01__log-policy-foundations.md` | Modified | ≤ 260 |
| `PLAN/S0802_handled-network-failure-log-noise/PHASE_02__sync-path-normalization.md` | Modified | ≤ 260 |
| `PLAN/S0802_handled-network-failure-log-noise/PHASE_03__docs-catalog-cleanup.md` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Sync spec progress metadata

**Files:** `PLAN/S0802_handled-network-failure-log-noise.md`, `PLAN/S0802_handled-network-failure-log-noise/INDEX.md`, `PLAN/S0802_handled-network-failure-log-noise/PHASE_01__log-policy-foundations.md`, `PLAN/S0802_handled-network-failure-log-noise/PHASE_02__sync-path-normalization.md`, `PLAN/S0802_handled-network-failure-log-noise/PHASE_03__docs-catalog-cleanup.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Bring the strategic/tactical documents in sync with the implementation run: tactical link in the strategic spec, phase counters, done markers, and the final status transition notes needed before `/spec-check`. Do not add a FEATURES entry because strategic §8 says this ticket is not a user-facing feature.

**Verification:**

- `Grep` - `\\*\\*Tactical plan:\\*\\*` present in `PLAN/S0802_handled-network-failure-log-noise.md`.
- `Grep` - `\\*\\*Phases:\\*\\*` present in `PLAN/S0802_handled-network-failure-log-noise/INDEX.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-29 - Verification PASS. Files: strategic + tactical S0802 markdown synced to implemented state.

---

### Step 03.2 - Refresh catalog and record validation

**Files:** `PLAN/S0802_handled-network-failure-log-noise.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run the required mechanical closure for the Kotlin changes: post-change per touched Kotlin file, one `catalog_sync.ps1 -Module app_v2`, and the cheapest compile proof that matches the logging-only code delta. Leave the ticket ready for `/spec-check`.

**Verification:**

- `Grep` - `NetworkFilesSyncWorker.kt` present in `dev/CHANGELOG.md`.
- `Grep` - `SyncNetworkResourcesUseCase.kt` present in `dev/CHANGELOG.md`.
- `Grep` - `HandledNetworkOutcomeLogger.kt` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-29 - Verification PASS. `.\a.ps1 fk` green; `catalog_sync.ps1 -Module app_v2` green; `assert-no-ticket-logs.ps1` green after stale probe cleanup.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` PASS on 2026-06-29.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
