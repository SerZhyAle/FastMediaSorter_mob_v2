# Phase 01 - Reorder to Edge

**Strategic spec:** [`../S0377_resource-menu-open-launch-reorder.md`](../S0377_resource-menu-open-launch-reorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Add "move resource to very top" and "move resource to very bottom" operations to the reorder layer and view model, reusing the existing full-list reindex transaction. No UI or menu changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceOrderManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt` | Modified | ≤ 60 (delta only) |

> Neither file exceeds 500 lines after change - no backup step required.

---

## Steps

### Step 01.1 - Add move-to-top / move-to-bottom to ResourceOrderManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceOrderManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two suspend functions `moveResourceToTop(resource: MediaResource, currentList: List<MediaResource>): OrderResult` and `moveResourceToBottom(resource: MediaResource, currentList: List<MediaResource>): OrderResult`. Each finds the resource index in `currentList`; if not found, or already at the target edge (index 0 for top, `lastIndex` for bottom), return `OrderResult.CannotMove`. Otherwise build a new list with the resource moved to the edge (preserve the relative order of the others), then persist via `resourceRepository.updateResourcesDisplayOrder(newOrder)` in a single call, wrapped in try/catch returning `OrderResult.Error(e)` on failure and `OrderResult.Success` on success. Use plain-English `Timber.d` describing the operation (no ticket id). Reuse the existing `OrderResult` sealed class - do not introduce a new result type.

**Verification:**

- `Grep` - `fun moveResourceToTop(` matches exactly once.
- `Grep` - `fun moveResourceToBottom(` matches exactly once.
- `Grep` - `updateResourcesDisplayOrder(` present inside the file.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 4/4 PASS. Files: ResourceOrderManager.kt (+~62 LOC). Dev log recorded.

---

### Step 01.2 - Expose move-to-edge in MainViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fun moveResourceToTop(resource: MediaResource)` and `fun moveResourceToBottom(resource: MediaResource)` mirroring the existing `moveResourceUp` / `moveResourceDown` shape: launch on `ioDispatcher + exceptionHandler`, read `state.value.resources` as the current list, call the matching `orderManager` function, and on `OrderResult.Success` switch to `orderManager.getRecommendedSortMode()` and call `loadResources()`. `CannotMove` is silently ignored; `Error` falls through to the exception handler (same as the up/down methods).

**Verification:**

- `Grep` - `fun moveResourceToTop(resource: MediaResource)` matches exactly once.
- `Grep` - `fun moveResourceToBottom(resource: MediaResource)` matches exactly once.
- `Grep` - `orderManager.moveResourceToTop` and `orderManager.moveResourceToBottom` both present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. Files: MainViewModel.kt (+~42 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - validated in the consolidated build at Phase 03 end (foundation methods are unused until Phase 02, so a standalone build adds nothing).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - deferred to the single catalog regen in Phase 04.2 (avoids redundant scans across the shared module).

---

## Handoff Notes to Next Phase

`MainViewModel.moveResourceToTop` / `moveResourceToBottom` are the entry points Phase 02 wires the new menu items to.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed (the new methods are unused until Phase 02).
