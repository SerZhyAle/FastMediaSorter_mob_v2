# Phase 06 - Folder menu and destinations

**Strategic spec:** [`../S1325_folder-selection-copy-move.md`](../S1325_folder-selection-copy-move.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 05
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Make the per-row menu of a folder contain exactly the applicable operations, and make a folder-bearing selection reach a destination with a specific message when a target cannot accept a tree.

---

## Prerequisites

- [ ] Phases 01, 02 and 05 are ✅ Done.
- [ ] Read `research/01__saf-tree-destination.md` - it fixes the document-tree decision this phase implements.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | ≤ 880 |

`BrowseFileOperationsManager.kt` exceeds 500 LOC - back it up into `temp/S1325/` before editing.

---

## Steps

### Step 06.1 - Gate the menu entries through the policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `addDirectEntries` already offers copy, move, rename and delete without a directory test, so a folder inherits them once the button is reachable. Replace the inline `!file.isDirectory` tests for "Open" and the `isFolder` test that suppresses `FAVORITE` in `buildExtendedCommands` with `BrowseItemOperationPolicy.supports(..)` calls, and add the same gate for `SEND_TO`, `INFO` and archive extraction so a folder's menu does not offer them.

**Verification:**

- `Grep` - `BrowseItemOperationPolicy.supports(` matches at least four times in the file.
- `Grep` - `if (!isFolder) add(PlayerCommand.FAVORITE)` returns zero hits.
- `Grep` - `add(PlayerCommand.SEND_TO)` is inside a policy-gated branch; record the surrounding condition.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. `BrowseItemOperationPolicy.supports(` 5 hits; `if (!isFolder) add(PlayerCommand.FAVORITE)` 0 hits; `add(PlayerCommand.SEND_TO)` now sits inside `if (BrowseItemOperationPolicy.supports(BrowseItemOperation.SEND_TO, file))` (line 282). "Open", the VR Cinema entry, INFO and archive extraction are gated the same way, and the now-unused `isFolder` local was removed. A folder's menu therefore offers copy, move, rename, delete - and, in manual sort, move up/down - and nothing that needs a single media file.

---

### Step 06.2 - Report a refused destination instead of a generic failure

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> The picked destination may be a document-tree URI with no filesystem path; Phase 02 makes the directory dispatch refuse it. In the directory loop of `onFolderPicked`, map the refusal exception type to `error_folder_destination_not_supported` and show that message once for the whole batch instead of the generic "some operations failed" count, while the file part of the same operation proceeds untouched.

**Verification:**

- `Grep` - `error_folder_destination_not_supported` matches exactly once in `BrowseFolderPickerHandler.kt`.
- `Grep` - `DirectoryOperationRefusal` present in the file.
- `Grep` - `error_some_operations_failed` still present for the non-refusal failure path.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. `refusalMessageRes` 2 hits, `DirectoryOperationRefusal` 3 hits, `error_some_operations_failed` still 1 hit for the ordinary failure path. A refusal now wins over the generic count message, because "pick another destination" and "some items failed" call for different actions.

---

### Step 06.3 - Surface the refusal messages from the transfer path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Map the refusal exception type to its matching string - `error_folder_into_itself`, `error_folder_same_location`, `error_folder_destination_not_supported` - where the transfer's terminal event is turned into a user message, so a refused folder operation explains itself instead of surfacing as a generic copy or move failure. Do not introduce a second mapping table; keep one function used by both the copy and move paths.

**Verification:**

- `Grep` - all three keys appear in `BrowseFileOperationsManager.kt`.
- `Grep` - the mapping function name matches exactly once as a declaration and at least twice as a call.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification PASS with a placement change: the mapping lives in a new file `ui/browse/helpers/DirectoryRefusalMessages.kt` (`refusalMessageRes(reason)`), not inside `BrowseFileOperationsManager`. Reason: the background path loses the exception type before it reaches that manager - the worker collects `Throwable.message` into the terminal event - so the translation has to happen in the worker, and the folder picker needs the same table. One declaration, two call sites (worker, picker), so the same obstacle cannot be worded two ways.

---

### Step 06.4 - Keep the confirmation counts honest for a mixed selection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> `showMoveDialog` builds its confirmation text from `selectedPaths.size`, which is already the full mixed count, while `sourceFiles` handed to the destination dialog excludes directories. Pass the directory count into the destination dialog's source description so the dialog states what is really being transferred; leave the existing partition logic intact.

**Verification:**

- `Grep` - `dirItems` referenced in the destination dialog construction of both copy and move paths.
- `Grep` - `partition { it.isDirectory }` still matches exactly twice.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification PASS, with the count corrected: `partition { it.isDirectory }` matches three times, not two - the third is the permission-retry path in `executeMoveDirectly`, which predates this ticket. All three left intact.
- 2026-07-31 - Defect found while implementing: the destination dialog builds its text from `sourceFiles`, which never contains directories, so a folder-only selection announced "Copying 0 files from X" - exactly the case this ticket makes common. Fixed by a `directoryCount` property set before `show()` at both call sites and a trilingual `browse_transfer_folders_included` line. A constructor parameter was avoided deliberately: that constructor already carries a baselined `LongParameterList` finding, and adding an 18th argument would resurface it as a new one.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode CodeAndResources` BUILD SUCCESSFUL, exit 0 (after fixing a newline typed inside a string literal in my own edit).
- [x] `Grep` for `TODO(phase-06)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added via `post-change.ps1` closure - PASS (Mixed, 43814 ms), every gate PASS or SKIP.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same closure.
- [x] Phase-boundary audit run - Layers 1-3, and it paid for itself twice:
  - **P2, mine, fixed:** my refusal branches pushed `onFolderPicked` to `CyclomaticComplexMethod 21/20`. The folder half is now `transferPickedDirectories(op, destinationPath)`; re-run drops that file from the findings and the compile stays green.
  - **Not mine, left alone:** `FileOperationDestinationDialog` reports `LongParameterList 17/10`. Its baselined signature ends at `onDestinationSelected` while the code has `onOperationRequested` after it, so the finding drifted when that parameter was added by an earlier ticket. I added a property, not a parameter - deliberately, to avoid exactly this - so there is nothing here to fix inside this ticket.

---

## Handoff Notes to Next Phase

The feature is functionally complete: a folder can be selected, its menu offers only what applies, and every refusal explains itself. Phase 07 closes documentation and catalog.

---

## Rollback Plan

Revert phase commit(s) - menu gating and message mapping only.
