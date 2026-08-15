# Phase 01 - Navigation Name Tracking

**Strategic spec:** [`../S0906_cloud-title-internal-id.md`](../S0906_cloud-title-internal-id.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Add a parallel "real display name" stack to `BrowseState`, kept in sync with the existing path stack at every navigation mutation point in `BrowseNavigationManager` - no consumer wiring yet, this phase only produces the data.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseState.kt` and `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt` exist as read during research (confirmed).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseState.kt` | Modified | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt` | Modified | ≤ 500 |

---

## Steps

### Step 01.1 - Add name-stack fields to BrowseState

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseState.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Subfolder navigation state" field group of `BrowseState` (next to `currentPath`/`pathStack`/`isSubfolderMode`), add two new fields: `val folderNameStack: List<String> = emptyList()` (real display names of visited parent folders, parallel to `pathStack`) and `val currentFolderName: String? = null` (real display name of the current folder; `null` at resource root). One-line comment on each explaining they mirror `pathStack`/`currentPath` but carry the human-readable name instead of the path string - this is the fix for S0906 (cloud resources have opaque id-based paths where a path segment is not a folder name).

**Verification:**

- `Grep` - `val folderNameStack: List<String> = emptyList()` present in `BrowseState.kt`.
- `Grep` - `val currentFolderName: String? = null` present in `BrowseState.kt`.
- `Grep -n "Log\.d\("` in `BrowseState.kt` - zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: BrowseState.kt (+5 LOC). Dev log recorded.

---

### Step 01.2 - Keep folderNameStack/currentFolderName in sync across every navigation mutation point

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Update every method in `BrowseNavigationManager` that currently mutates `pathStack`/`currentPath` to mutate `folderNameStack`/`currentFolderName` the same way, in the same `updateState { .. }` call:
>
> - `navigateToFolder(folder: MediaFile)` (the click-driven overload, the one with a real `MediaFile` and therefore a real `folder.name`): mirror the existing `currentPath`/`pathStack` logic exactly - `val currentName = stateFlow.value.currentFolderName`; `val newNameStack = if (currentName != null) stateFlow.value.folderNameStack + currentName else emptyList()`; set `currentFolderName = folder.name` in the same `updateState` block that sets `currentPath = folder.path`.
> - `navigateToFolder(folderPath: String)` (the path-only overload used by resume/reattach callers with no `MediaFile` available): resolve `val resolvedName = stateFlow.value.mediaFiles.firstOrNull { it.path == folderPath }?.name ?: File(folderPath).name` (best-effort lookup in the already-loaded listing; falls back to the pre-existing path-derived behavior only when the path isn't found there - this preserves current behavior for this narrow reattach edge case, not a regression). Push `stateFlow.value.currentFolderName` (no fallback to `resource.name` here, unlike the path field's `?: resource?.path` fallback) - the path field's fallback is invisible (only ever consumed internally by `navigateBack()`'s reload target), but the name stack is now read directly for display (Phase 02), so falling back to `resource.name` here would inject a duplicate resource-name segment into the visible breadcrumb the first time this overload fires from true root. Set `currentFolderName = resolvedName`.
> - `navigateBack()`: pop `folderNameStack.lastOrNull()` as `parentName` and `folderNameStack.dropLast(1)` as the new stack, exactly mirroring how `pathStack.last()`/`pathStack.dropLast(1)` are used; set `currentFolderName = if (newStack.isEmpty()) null else parentName` in the same `updateState` block.
> - `navigateUp()`: same pop pattern as `navigateBack()`. `currentPath`'s `finalPath = newPath ?: resource?.path` fallback exists because `loadDirectoryContents` needs a real path to reload; the name field has no such requirement, so set `currentFolderName = newName` directly (no `?: resource?.name` fallback) - `null` at empty stack is the correct "at root" sentinel every other method uses, and falling back to `resource.name` here would make it indistinguishable from "one level deep, folder named after the resource".
> - `navigateToDepth(depth: Int)`: in the branch that currently succeeds (`currentPath.startsWith(resourcePath)` true - local/network hierarchical paths), rebuild `folderNameStack` by slicing `pathParts` (already real folder names for that branch, since local path segments are names) exactly parallel to how `newStack`/`targetPath` are built; set `currentFolderName` to `pathParts.getOrNull(depth - 1)` or `resource.name` when `depth == 0`. Do **not** touch the early-return branch for cloud-path mismatch (`Timber.w("BrowseNavigationManager.navigateToDepth: path mismatch")` at line ~242) - that pre-existing limitation (arbitrary-depth breadcrumb click doesn't navigate at all for cloud resources) is tracked separately as S0917 and is out of scope here; leaving it untouched is correct, not a gap in this step.
> - `resetToRoot()`, `enableSubfolderMode()`, `disableSubfolderMode()`: reset `folderNameStack = emptyList()`, `currentFolderName = null` in the same `updateState` blocks that already reset `pathStack`/`currentPath`.
>
> Do not touch the four breadcrumb-getter methods (`getCurrentBreadcrumb`, `getCurrentFolderName`, `getBreadcrumbPath`, `getBreadcrumbParts`) in this step - they are rewired in Phase 02.

**Verification:**

- `Grep -c "folderNameStack"` in `BrowseNavigationManager.kt` returns at least 10 (field reads/writes across the 7 methods listed above).
- `Grep -c "currentFolderName"` in `BrowseNavigationManager.kt` returns at least 7.
- `Grep -n "Log\.d\("` in `BrowseNavigationManager.kt` - zero hits.
- File line count after edit ≤ 600 (current 490 + this step's delta; well under the 1500 hard-refuse threshold, no backup required since the pre-edit file was under 500 LOC).

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 4/4 PASS (folderNameStack: 12 occurrences, currentFolderName: 10, zero Log.d, 556 LOC). Build PASS. Files: BrowseNavigationManager.kt (+66 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`BrowseState.folderNameStack` + `BrowseState.currentFolderName` are now correctly populated at every navigation depth, for both local/network and cloud resources, kept in sync with the pre-existing `pathStack`/`currentPath`. Nothing reads them yet - Phase 02 rewires the display consumers to use them instead of path-string parsing.

---

## Rollback Plan

Revert phase commit(s) - purely additive new state fields plus their producers; no consumer reads them yet, so a revert is a clean no-op with zero visible behavior change.
