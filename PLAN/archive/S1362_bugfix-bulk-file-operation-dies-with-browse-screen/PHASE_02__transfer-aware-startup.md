# Phase 02 - Transfer-aware startup

**Strategic spec:** [`../S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md`](../S1362_bugfix-bulk-file-operation-dies-with-browse-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 1 / 1

## Objective

Prevent a recreated Browse screen from clearing cache files required by the active transfer worker.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt` | Modified | ≤ 250 |

## Steps

### Step 02.1 - Guard initialization cache cleanup with transfer state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Pass the same named suspend predicate into the lifecycle setup manager. On its existing IO launch, skip `UnifiedFileCache.clearAll()` while an interactive transfer is active and retain the cleanup on idle; use Timber for the skip.

**Why:** Reopening Browse triggers the same shared-cache deletion after the worker has outlived the previous Activity.

**Verification:**

- `rg -n 'hasActiveTransfer|activeTransfer' app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt` returns the startup guard.
- `rg -n 'unifiedCache.clearAll' app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt` shows the idle-only cleanup.

**Status:** `[x]` done - startup cache guard added; `a.ps1 fk` passed on 2026-08-03.

## Phase Done Criteria

- [x] Every step is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File .\a.ps1 fk` exited 0 on 2026-08-03.
- [x] Phase-boundary audit has no unresolved P0/P1 findings.
