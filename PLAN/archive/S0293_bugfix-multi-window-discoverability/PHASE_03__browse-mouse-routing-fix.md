# Phase 03 - Browse Mouse Routing Fix (Bug A)

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - parallel-safe with Phase 01/02/04
**Blocks:** -
**Steps done:** 1 / 1
**Started:** 2026-05-22
**Completed:** 2026-05-23

---

## Objective

Fix the regression in the Browse media-file adapter context-menu callback. Mouse right-click on a file row currently opens the resource-level operations menu without the per-file callbacks - `allowSeparateWindow` / `openBrowseInNewWindow` are unbound, hiding the multi-window action and also showing options unrelated to the clicked file. Route the callback through the same per-file overflow menu that the row's `⋮` button uses, with the full callback set.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 1200 |

> File is currently around 1100 LOC. Backup required if it crosses 1500 LOC after edit. Current delta is < 40 LOC, no backup needed.

---

## Steps

### Step 03.1 - Route mouse context menu to per-file overflow with full callback set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Locate the `onContextMenuRequest` lambda passed to `MediaFileAdapter` (around line 172-175). Replace its body with a call to `browseFileOverflowMenuManager.showFor(...)` using the same callback set already used by `onOverflowMenuClick` (lines 209-243). To avoid duplicating the long argument block, extract the shared `showFor` invocation into a `private fun showPerFileOverflowMenu(anchor: View, file: MediaFile)` inside `BrowseManagerInitializer` that closes over the existing `viewModel`, `fileOperationsManager`, etc., and have BOTH `onContextMenuRequest` and `onOverflowMenuClick` delegate to it. Preserve the existing `UserActionLogger.logItemLongClick(file.name, context = "Mouse context menu")` call before the delegation.
>
> The single declaration of all callbacks (`onCopy`, `onMove`, `onRename`, `onDelete`, `onMoveUp`, `onMoveDown`, `onFavorite`, `onShare`, `onInfo`, `onGoogleLens`, `onDrawOverlay`, `onSearchYoutubeMusic`, `onOpenInPlayer`, `onOpenInNewWindow`) must live in the new private function, with `isGridMode = mediaFileAdapter.isInGridMode` and `appSettings = settingsRepository.getSettings().first()` resolved inside.

**Verification:**

- `Grep` - `private fun showPerFileOverflowMenu\(anchor: android.view.View, file: .*MediaFile\)` matches exactly once in the file.
- `Grep` - `onContextMenuRequest = \{ anchor, file ->` followed within 4 lines by `showPerFileOverflowMenu\(anchor, file\)` (delegation call exists).
- `Grep` - `onOverflowMenuClick = overflowClick@\{ file, anchor ->` body delegates to `showPerFileOverflowMenu` (same indirection).
- `Grep` - inside the file body, `browseFileOverflowMenuManager.showFor\(` appears exactly once (no duplicated invocation block).
- `Grep` - the substring `resourceOpsMenuManager.showMenu\(anchor = anchor, viewModel = viewModel\)` (without further arguments) is **absent** from the file - the old buggy call site is gone.
- `Grep -n "Log\.d\("` on the file returns zero hits.
- Compile check via `/build` (target: `assembleStandardDebug`) - PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-23 - Verification 6/6 PASS. Extracted `showPerFileOverflowMenu(anchor: View, file: MediaFile)`. Both `onContextMenuRequest` and `onOverflowMenuClick` delegate. Old buggy call site gone. Build PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for `BrowseManagerInitializer.kt` via post-change.ps1.

---

## Handoff Notes to Next Phase

Mouse right-click on a file row now opens the same per-file overflow menu as the row's ⋮ button, with all callbacks bound (including `onOpenInNewWindow`). Phase 04 fixes the symmetric issue in the player. No data flow change beyond UI routing.

---

## Rollback Plan

Revert the phase commit. The previous behavior (mouse right-click → resource-level menu without per-file callbacks) was already a regression, but restoring it is a single git revert - no schema or persistent-state implications.
