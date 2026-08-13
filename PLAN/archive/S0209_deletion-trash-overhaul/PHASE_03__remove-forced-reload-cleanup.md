# Phase 03 — Remove forced reload cleanup

**Strategic spec:** [`../S0209_deletion-trash-overhaul.md`](../S0209_deletion-trash-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Implement ADR-1: drop the `maxAgeMs = 0L` forced trash cleanup from refresh/reload/shutdown pathways for LOCAL resources. Periodic `TrashCleanupWorker` becomes the sole automatic finaliser. Network resources keep their existing behaviour for now (no scope change for SMB/SFTP/FTP/Cloud in this phase).

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRefreshManager.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | (caller change only) |

> `BrowseViewModel.kt` size unknown — Grep before edit to confirm ≤ 1500 lines. If above 1500, abort and re-plan via Manager extraction; this phase does not address oversized ViewModel.

---

## Steps

### Step 03.1 — Drop forced cleanup from `launchReload`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRefreshManager.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> In `launchReload`, remove the block that calls `cleanupTrashFoldersUseCase.cleanup(rootDir, maxAgeMs = 0L)` for LOCAL resources (lines ~84–94 in current code). Keep the `MediaStore` sync block below it intact. The `CleanupTrashFoldersUseCase` dependency is retained for `cleanupTrashOnBackground` until Phase 03.2.

**Verification:**

- `Grep -n` — `cleanupTrashFoldersUseCase\.cleanup\(rootDir, maxAgeMs = 0L\)` returns zero matches inside `launchReload`.
- `Grep -n` — `cleanupTrashFoldersUseCase` still referenced exactly once in this file (only inside `cleanupTrashOnBackground`).
- Target variant compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Removed the forced `cleanupTrashFoldersUseCase.cleanup(rootDir, maxAgeMs = 0L)` call from `launchReload` for LOCAL resources while leaving the MediaStore refresh block intact.

---

### Step 03.2 — Make `cleanupTrashOnBackground` LOCAL branch a no-op (or remove)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRefreshManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> The shutdown coordinator currently binds `cleanupTrash = { resource -> refreshManager.cleanupTrashOnBackground(resource, maxAgeMs = 0L) }` (`BrowseViewModel.kt` near line 439). For LOCAL resources we no longer want forced cleanup. Two acceptable approaches:
> (a) Remove the LOCAL branch of `cleanupTrashOnBackground`: log a debug line "LOCAL trash cleanup skipped — periodic worker handles this" and return early. Keep the network branch.
> (b) Keep the LOCAL branch but route only when `maxAgeMs > 0L`. Caller in `BrowseViewModel` then passes the worker's `DEFAULT_AGE_MS` (5 minutes) instead of 0L. Network branch unchanged.
> Choose (a) — simpler, no risk of accidentally pruning recent items on shutdown. Document the choice in commit message.
> Adjust `BrowseViewModel.kt` accordingly: the lambda still exists for network resources, so it remains; only the resulting behaviour for LOCAL flips from "purge all" to "skip".

**Verification:**

- `Grep -n` — inside `cleanupTrashOnBackground` for `ResourceType.LOCAL` no longer calls `cleanupTrashFoldersUseCase.cleanup`.
- `Grep -n` — `cleanupTrashFoldersUseCase` no longer present in `BrowseRefreshManager.kt` constructor params (remove the Hilt-provided dependency if zero remaining usages). If it remains for any reason, document why.
- Target variant compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Took path (a): LOCAL shutdown cleanup now short-circuits and leaves the periodic worker as the sole automatic finaliser. `BrowseRefreshManager` no longer keeps the cleanup use-case dependency; `BrowseViewModel` now routes shutdown cleanup through `refreshManager.cleanupTrashOnBackground(resource)` for the remaining network branch.

---

### Step 03.3 — Verify `TrashCleanupWorker` schedule is the sole automatic finaliser

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/worker/TrashCleanupWorker.kt` (no edit, verify only)
**Depends on:** Step 03.2

**Prompt for developer:**

> Read both files. Confirm `WorkManagerScheduler.scheduleTrashCleanup` registers the periodic worker (15 min) at app startup and that `TrashCleanupWorker.doWork` calls `cleanupTrashFoldersUseCase.cleanup(dir)` without overriding `maxAgeMs` (so it defaults to 5 minutes). No code change expected here — this step is a structural audit.

**Verification:**

- `Grep -n` — `PeriodicWorkRequestBuilder<TrashCleanupWorker>` exists in `WorkManagerScheduler.kt` with interval ≥ 15 minutes.
- `Grep -n` — inside `TrashCleanupWorker.doWork`, `cleanupTrashFoldersUseCase.cleanup(directory)` is invoked without `maxAgeMs = 0L`.
- `expected: both grep counts == 1 | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Structural audit PASS. `WorkManagerScheduler.scheduleTrashCleanup` still registers `PeriodicWorkRequestBuilder<TrashCleanupWorker>(15, TimeUnit.MINUTES)`, and `TrashCleanupWorker.doWork` still calls `cleanupTrashFoldersUseCase.cleanup(directory)` without `maxAgeMs = 0L`.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep -rn "cleanupTrashFoldersUseCase\.cleanup\(.*maxAgeMs = 0L"` across `app_v2/src/main` returns hits only inside `Browse*` files used for the network branch (or zero, if Step 03.2 took path (a) and network kept its own variable name).
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- After this phase, soft-deleted LOCAL files persist on disk for at least the next periodic worker run (up to 15 minutes after the 5-min TTL expires). UX-message in Phase 04 reflects the new contract.

---

## Rollback Plan

- Revert the phase commit. The previous code path is fully restored.
