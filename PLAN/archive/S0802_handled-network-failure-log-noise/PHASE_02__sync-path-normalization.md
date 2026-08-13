# Phase 02 - Sync Path Normalization

**Strategic spec:** [`../S0802_handled-network-failure-log-noise.md`](../S0802_handled-network-failure-log-noise.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-29
**Completed:** 2026-06-29

---

## Objective

Apply the shared handled-outcome logging policy to background and manual network sync paths without changing sync business behavior.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCase.kt` | Modified | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Normalize background sync logging

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the per-resource and top-level catch-path logging in `NetworkFilesSyncWorker` with the shared handled-outcome helper. Wi-Fi-gated SMB skips and similarly handled network outcomes must no longer produce throwable/E-level noise, while genuinely unexpected worker failures must still keep the throwable signal.

**Verification:**

- `Grep` - `HandledNetworkOutcomeLogger` present in `app_v2/src/main/java/com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt`.
- `Grep` - `Timber\\.e\\(e, \"NetworkFilesSyncWorker: Failed to sync` returns zero hits in that file.
- `Grep` - `Timber\\.e\\(e, \"NetworkFilesSyncWorker: Background sync failed` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-29 - Verification PASS. Files: `NetworkFilesSyncWorker.kt` (+4 LOC, -2 LOC). Background sync now routes handled outcomes through the shared logger.

---

### Step 02.2 - Normalize manual sync logging

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the same handled-outcome helper to `SyncNetworkResourcesUseCase` for both `syncAll()` and `syncSingle()`. Preserve current success/failure return semantics and progress callbacks; this step is logging-only semantics, not a behavior rewrite.

**Verification:**

- `Grep` - `HandledNetworkOutcomeLogger` present in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCase.kt`.
- `Grep` - `Timber\\.e\\(e, \"SyncNetworkResourcesUseCase: Failed to sync` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-29 - Verification PASS. Files: `SyncNetworkResourcesUseCase.kt` (+12 LOC, -3 LOC). Manual sync now routes handled outcomes through the shared logger.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` PASS on 2026-06-29.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

The remaining validation focus is proof: compile, catalog sync, spec status, and final audit that real defects still keep a throwable path.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
