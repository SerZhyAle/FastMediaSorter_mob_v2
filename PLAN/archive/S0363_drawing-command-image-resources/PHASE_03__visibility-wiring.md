# Phase 03 - Visibility Wiring

**Strategic spec:** [`../S0363_drawing-command-image-resources.md`](../S0363_drawing-command-image-resources.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Expose "Create drawing" for the allow-listed virtual image resources by routing every visibility/guard site through `DrawingTargetPolicy.canCreateDrawing`, replacing the inline `!VirtualPathUtils.isVirtualPath(...) && supportsImages()` checks. The toolbar overflow behaviour is unchanged - the menu item keeps its existing `isControlVisibleOnScreen` collapse rule.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt` | Modified | ≤ 440 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt` | Modified | ≤ 210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDrawingCreateManager.kt` | Modified | ≤ 80 |

> No layout XML touched - `menu_resource_ops.xml` already declares `action_create_drawing`; visibility is decided in code. No landscape counterpart involved.

---

## Steps

### Step 03.1 - Route overflow menu + dialog guard through the policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `showMenu`, replace the inline `canCreateDrawing` computation (`resource != null && !resource.isReadOnly && !VirtualPathUtils.isVirtualPath(resource.path) && resource.supportsImages()`) with `DrawingTargetPolicy.canCreateDrawing(resource)`, keeping the `&& !isControlVisibleOnScreen(anchor, R.id.btnCreateDrawing)` overflow-collapse condition. In `showCreateDrawingDialog`, replace the four-condition early-return guard with `if (!DrawingTargetPolicy.canCreateDrawing(resource)) return`. Add the `DrawingTargetPolicy` import.

**Verification:**

- `Grep` - `DrawingTargetPolicy.canCreateDrawing(` matches at least twice in `ResourceOpsMenuManager.kt`.
- `Grep` - inside `ResourceOpsMenuManager.kt`, the drawing visibility no longer reads `VirtualPathUtils.isVirtualPath(resource.path)\n.*supportsImages()` for the drawing item (manual read confirms the inline check is gone).
- Project compiles - run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification PASS (canCreateDrawing used 2x: showMenu + showCreateDrawingDialog; inline virtual/supportsImages guard removed). Compile confirmed at phase build. Files: ui/browse/managers/ResourceOpsMenuManager.kt. Dev log recorded.

---

### Step 03.2 - Route toolbar button visibility through the policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `updateCreateFolderButtonVisibility`, replace the inline `canCreateDrawing` computation feeding `binding.btnCreateDrawing?.isVisible` with `DrawingTargetPolicy.canCreateDrawing(resource)`. Add the `DrawingTargetPolicy` import. Leave the Create-folder and Create-text-note lines untouched.

**Verification:**

- `Grep` - `binding.btnCreateDrawing?.isVisible = DrawingTargetPolicy.canCreateDrawing(resource)` present in `BrowseStateUiUpdater.kt`.
- Project compiles - run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification PASS (toolbar btn visibility routed through policy; inline guard removed). Compile confirmed at phase build. Files: ui/browse/managers/BrowseStateUiUpdater.kt. Dev log recorded.

---

### Step 03.3 - Align the create-command predicate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDrawingCreateManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Change `isAvailableFor` from `resource.supportsImages()` to `DrawingTargetPolicy.canCreateDrawing(resource)` so the `BrowseCreateEntityCommand` predicate matches the toolbar and overflow gates. Add the `DrawingTargetPolicy` import.

**Verification:**

- `Grep` - `override fun isAvailableFor(resource: MediaResource): Boolean = DrawingTargetPolicy.canCreateDrawing(resource)` present in `BrowseDrawingCreateManager.kt`.
- Project compiles - run `/build`.
- Regression run passes: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.ui.browse.managers.ResourceOpsMenuManagerTest"` - expected: BUILD SUCCESSFUL (per-class XML report).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification PASS (isAvailableFor routed through policy; module compiled; ResourceOpsMenuManagerTest exit 0). Files: ui/browse/managers/BrowseDrawingCreateManager.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\build-debug.PS1` BUILD SUCCESSFUL, standardDebug APK produced (v2.60.6051.227).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

"Create drawing" is now visible and functional for "all images", "camera", and "downloads" (and unchanged for normal image folders). Phase 04 updates docs, catalog, and dev log.

---

## Rollback Plan

Revert phase commit - three call-site edits, no schema change; the command simply stops appearing for the virtual resources.
