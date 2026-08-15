# Phase 01 - Ordering Pin

**Strategic spec:** [`../S0488_pin-all-files-resource-first.md`](../S0488_pin-all-files-resource-first.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Hoist the predefined All-files resource to index 0 of the main-window resource list inside the single filtering-and-sorting stage, after tab and all filters, so list and grid both show it first under any sort mode; cover the behavior with unit tests.

---

## Prerequisites

- [ ] Strategic §6 research items 1 and 2 are Resolved (they are - see `research/`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceFilterManager.kt` | Modified | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/ResourceFilterManagerTest.kt` | New | ≤ 200 |

> All three files are below 500 LOC - no backup step required.

---

## Steps

### Step 01.1 - Add `isAllFilesPredefined` identity on `MediaResource`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a context-free identity predicate for the predefined All-files resource near the `MediaResource` declaration in `Models.kt`. Define `val MediaResource.isAllFilesPredefined: Boolean get() = profile == ResourceProfile.ALL_FILES && allFiles`. This is the single source of truth reused by the ordering stage and the adapter. Do not add a KDoc that merely restates the expression; a one-line note on *why* the profile uniquely tags the resource is enough only if non-obvious.

**Verification:**

- `Grep` - `isAllFilesPredefined` matches exactly once in `Models.kt` (the declaration).
- `Grep` - `profile == ResourceProfile.ALL_FILES` present in `Models.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Files: domain/model/Models.kt (+9 LOC, extension val `isAllFilesPredefined`). Dev log recorded.

---

### Step 01.2 - Pin All-files first after sorting in `ResourceFilterManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceFilterManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `applyFiltersAndSorting`, after the existing `filtered = applySorting(filtered, sortMode)` line, return the list through a new private helper `hoistAllFilesFirst(filtered)` instead of returning `filtered` directly. Implement `private fun hoistAllFilesFirst(resources: List<MediaResource>): List<MediaResource>` that finds the first element where `isAllFilesPredefined` is true; if its index is `<= 0` return the list unchanged; otherwise move that single element to index 0 preserving the relative order of all other elements. The hoist runs last, after the tab filter and every type/media/name filter, so when the active tab or filter removes the All-files resource there is nothing to hoist and it stays hidden (strategic ADR-2). The operation is in-memory only - never write `displayOrder` here.

**Verification:**

- `Grep` - `hoistAllFilesFirst` matches at least twice in `ResourceFilterManager.kt` (definition + call site).
- `Grep` - `isAllFilesPredefined` present in `ResourceFilterManager.kt`.
- `Grep` - `return hoistAllFilesFirst(` present (sort result routed through the hoist).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. Files: ui/main/helpers/ResourceFilterManager.kt (+15 LOC, `hoistAllFilesFirst` after sort + import). Dev log recorded.

---

### Step 01.3 - Unit-test the pin and the exceptions

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/ResourceFilterManagerTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `ResourceFilterManagerTest` exercising `applyFiltersAndSorting`. Build a resource list containing one All-files-predefined resource (`profile = ResourceProfile.ALL_FILES`, `allFiles = true`, `type = LOCAL`) plus several ordinary LOCAL resources and at least one non-LOCAL resource; reuse `DomainModelFactories` if it provides a `MediaResource` builder, otherwise construct instances directly. Cover: (1) under `SortMode.NAME_ASC`, `NAME_DESC`, `DATE_ASC`, `DATE_DESC`, `SIZE_ASC`, `SIZE_DESC` and `MANUAL`, the All-files resource is at index 0; (2) with `activeTab = ResourceTab.SMB` the All-files resource is absent from the result; (3) with `filterByType = setOf(ResourceType.SMB)` it is absent; (4) with `filterByName` not matching its name it is absent; (5) the relative order of the other resources after hoisting equals their order without the All-files resource present. Keep assertions on observable list positions, not implementation details.

**Verification:**

- `Glob` - `ResourceFilterManagerTest.kt` exists.
- `Grep` - `class ResourceFilterManagerTest` matches once.
- `Grep` - `@Test` matches at least 4 times in the file.
- `Grep` - `ResourceProfile.ALL_FILES` present in the test.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (file exists, class ×1, 5 @Test, ResourceProfile.ALL_FILES present). Files: ui/main/helpers/ResourceFilterManagerTest.kt (New, +120 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Unit -Tests "*ResourceFilterManagerTest"` → BUILD SUCCESSFUL.
- [x] `ResourceFilterManagerTest` passes - targeted `--tests "*ResourceFilterManagerTest"` ran green (exit 0).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public extension) - deferred to Phase 03.

---

## Handoff Notes to Next Phase

`MediaResource.isAllFilesPredefined` exists and uniquely identifies the pinned resource. The displayed list now always has the All-files resource at index 0 when present and surviving filters. Phase 02 consumes `isAllFilesPredefined` to make that row non-draggable and to block drops above index 0.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing string changed; ordering reverts to plain sort.
