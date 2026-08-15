# Phase 02 - Rewire Title/Breadcrumb Consumers

**Strategic spec:** [`../S0906_cloud-title-internal-id.md`](../S0906_cloud-title-internal-id.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Rewire every place that displays the browse-screen title/breadcrumb (the interactive breadcrumb getters, the info-string builder, and the resource-root path display) to read the real names tracked in Phase 01 instead of re-deriving a name from the path string - closing both manifestations of the bug (root-level path leak and subfolder-level id leak).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt` | Modified | ≤ 550 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 - Rewrite the 4 breadcrumb-getter methods to use the tracked name stack

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt`
**Depends on:** Phase 01 Step 01.2

**Prompt for developer:**

> Rewrite `getCurrentBreadcrumb()`, `getCurrentFolderName()`, `getBreadcrumbPath()`, `getBreadcrumbParts()` to derive their output from `stateFlow.value.folderNameStack` + `stateFlow.value.currentFolderName` instead of parsing `currentPath`/`resource.path` strings:
>
> - `getCurrentFolderName(): String?` -> `return stateFlow.value.currentFolderName` (drop the old `File(currentPath).name` derivation entirely).
> - `getCurrentBreadcrumb(): String` -> `val s = stateFlow.value; return (s.folderNameStack + listOfNotNull(s.currentFolderName)).joinToString("/")` (empty string at root, matching the old contract).
> - `getBreadcrumbPath(): String` -> `val s = stateFlow.value; val resource = s.resource ?: return ""; return (listOf(resource.name) + s.folderNameStack + listOfNotNull(s.currentFolderName)).joinToString(" / ")`.
> - `getBreadcrumbParts(): Pair<String, List<String>>` -> `val s = stateFlow.value; val resource = s.resource ?: return Pair("", emptyList()); return Pair(resource.name, s.folderNameStack + listOfNotNull(s.currentFolderName))`.
>
> Remove the `import java.io.File` line only if no other method in the file still uses `File` (check `computeDirectoryHash`/`loadDirectoryContents` - if `File` is used nowhere else, drop the import; otherwise keep it).

**Verification:**

- `Grep -c "File\(currentPath\)"` in `BrowseNavigationManager.kt` returns 0 (old buggy pattern fully removed from these 4 methods).
- `Grep` - `fun getCurrentBreadcrumb\(\)`, `fun getCurrentFolderName\(\)`, `fun getBreadcrumbPath\(\)`, `fun getBreadcrumbParts\(\)` each still declared exactly once.
- `Grep -n "Log\.d\("` in `BrowseNavigationManager.kt` - zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS (File(currentPath): 0 hits, all 4 methods declared once, zero Log.d). Build PASS. Files: BrowseNavigationManager.kt (net -25 LOC). Dev log recorded.

---

### Step 02.2 - Rewire BrowseUtilityManager.buildBreadcrumb to consume BrowseState directly

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt`
**Depends on:** Phase 01 Step 01.1

**Prompt for developer:**

> Change `private fun buildBreadcrumb(rootPath: String, currentPath: String): String` to `private fun buildBreadcrumb(state: BrowseState): String`. New body:
>
> ```kotlin
> private fun buildBreadcrumb(state: BrowseState): String {
>     val parts = state.folderNameStack + listOfNotNull(state.currentFolderName)
>     if (parts.isEmpty()) return "📁 Root"
>     return "📁 " + listOf("Root").plus(parts).joinToString(" > ")
> }
> ```
>
> Update its call site in `buildResourceInfo(state: BrowseState)` (the `val pathDisplay = state.currentPath?.takeIf { state.isSubfolderMode }?.let { ... }` block) to call `buildBreadcrumb(state)` instead of `buildBreadcrumb(resource.path, currentPath)`.

**Verification:**

- `Grep` - `private fun buildBreadcrumb(state: BrowseState): String` present.
- `Grep -c "currentPath.startsWith(rootPath)"` in `BrowseUtilityManager.kt` returns 0.
- `Grep -n "Log\.d\("` in `BrowseUtilityManager.kt` - zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Build PASS. Files: BrowseUtilityManager.kt. Dev log recorded.

---

### Step 02.3 - Fix resource-root path leak for cloud resources

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> `buildRootPathDisplay(resourcePath: String, resourceName: String): String` (called from `buildResourceInfo` when not in subfolder mode) falls through to returning the raw `resourcePath` unchanged whenever the path's last segment doesn't match `resourceName` - always true for cloud resources, since their path's last segment is an opaque provider id, never the real name (strategic §1 / research Finding 5). Change the call site in `buildResourceInfo` to pass `state.isCloudResource` through, and short-circuit `buildRootPathDisplay` for cloud resources before the segment-matching logic: add an `isCloudResource: Boolean` parameter; if true, return an empty string (there is no meaningful hierarchical "parent path" to show for a flat cloud id). Keep the existing segment-matching logic unchanged for the non-cloud branch (local/SMB/FTP/SFTP resources - no behavior change there). Since `pathDisplay` can now be blank (a case the original string-join format never had to handle), also change `buildResourceInfo`'s final `return` line so the `pathDisplay • ` segment is only appended when non-blank - otherwise the format produces a dangling "• •" with nothing between.

**Verification:**

- `Grep` - `buildRootPathDisplay` signature includes an `isCloudResource: Boolean` parameter (3 params total).
- `Grep` - call site in `buildResourceInfo` passes `state.isCloudResource`.
- `Grep -n "Log\.d\("` in `BrowseUtilityManager.kt` - zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS (isCloudResource param + call site present, zero Log.d). Also adjusted buildResourceInfo's final format string so the pathDisplay bullet is only appended when non-blank (avoids dangling "• •" for cloud root) - covered by strategic §5 self-correction, in scope of this step's fix. Build PASS. Files: BrowseUtilityManager.kt. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Both manifestations of the bug (resource-root path leak, subfolder breadcrumb/title id leak) are fixed; local/network resource display is unchanged (verified by design - the same name-tracking mechanism naturally produces identical output for hierarchical paths where segment == name). Final phase is catalog/dev-log cleanup only.

---

## Rollback Plan

Revert phase commit(s) - display-only change, no data migration or persisted state affected. Reverting restores the pre-S0906 (buggy for cloud, correct for local) display behavior.
