# Phase 01 - Transfer-aware shutdown

**Strategic spec:** [`../S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md`](../S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2

## Objective

Guard Browse shutdown cleanup with the active WorkManager transfer state.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt` | Modified | ≤ 250 |

## Steps

### Step 01.1 - Supply active-transfer predicate to shutdown coordinator

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up the file under `temp/S1362/` before editing because it exceeds 500 lines. Pass the existing Browse transfer coordinator's suspend active-state method to the shutdown coordinator as a named predicate; do not add a new Hilt binding or alter explicit cancellation.

**Why:** The worker is process-level and must survive Browse lifecycle destruction; the existing coordinator is the authoritative owner of its WorkManager state.

**Verification:**

- `rg -n 'hasActiveTransfer' app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` returns the new predicate wiring.
- `rg -n 'cancelActiveTransfer' app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` remains the explicit dialog-Cancel path.

**Status:** `[x]` done - predicate wired; `a.ps1 fk` passed on 2026-08-03.

### Step 01.2 - Skip shared shutdown cleanup during active transfer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Accept the suspend active-transfer predicate. On the IO/non-cancellable cleanup path, check it before resetting connection throttling and before clearing the unified cache; log the deliberate skip with Timber. Preserve the existing cleanup when no transfer is active.

**Why:** Closing Browse currently resets shared throttle state and deletes shared cache files while the background worker is still using them.

**Verification:**

- `rg -n 'hasActiveTransfer|activeTransfer' app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt` returns guards for shutdown cleanup.
- `rg -n 'cancelAllForResource|unifiedCache.clearAll' app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt` shows each shared cleanup behind the guard.

**Status:** `[x]` done - shutdown throttle/cache guards added; phase audit found no P0/P1.

## Phase Done Criteria

- [x] Every step is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File .\a.ps1 fk` exited 0 on 2026-08-03.
- [x] Phase-boundary audit has no unresolved P0/P1 findings.
