# Phase 02 - Browse Progress Reattach UI

**Strategic spec:** [`../S0818_browse-file-operations-background-mode.md`](../S0818_browse-file-operations-background-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-29
**Completed:** 2026-06-29

---

## Objective

Turn the browse progress dialog into an attach/detach surface that can move to the background and later reattach to the same active transfer from BrowseActivity.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] The worker-backed browse transfer from Phase 01 is enqueued and observable by WorkInfo.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/dialog_file_operation_progress.xml` | Modified | ≤ 250 |
| `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml` | Modified | ≤ 250 |

---

## Steps

### Step 02.1 - Add a background action to the progress dialog in both orientations

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`, `app_v2/src/main/res/layout/dialog_file_operation_progress.xml`, `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the file-operation progress dialog with a dedicated background action alongside cancel, keeping the existing dialog button taxonomy and landscape parity. The dialog API must support attach-only rendering of external progress updates, user cancel, and a non-destructive "send to background" action that dismisses only the modal surface.

**Verification:**

- `Grep` - `btnBackground` present in `app_v2/src/main/res/layout/dialog_file_operation_progress.xml`.
- `Grep` - `btnBackground` present in `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`.
- `Grep` - `onBackground` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`.
- `Grep` - `Widget.FastMediaSorter.Button.DialogConfirm` present on the new background action in both layouts.

**Status:** `[ ]` not done

---

### Step 02.2 - Start browse copy/move through the coordinator instead of the dialog-owned coroutine

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the current dialog-owned `executeWithProgress` launch path with coordinator-backed enqueue/observe logic. The destination dialog should hand off the request, show the attachable progress surface, and refuse to launch a second interactive copy/move while the current browse transfer is still active.

**Verification:**

- `Grep` - `BrowseFileTransferCoordinator` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`.
- `Grep` - `executeWithProgress(` absent from `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt`.
- `Grep` - `startBackgroundTransfer` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`.

**Status:** `[x] done`

---

### Step 02.3 - Reattach BrowseActivity to the active transfer on return

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Wire BrowseActivity and its initializer to observe the coordinator's active WorkInfo flow. When the app returns from the notification or the user reopens the same resource while the transfer is running, the browse screen must navigate back to the stored folder path and reattach the progress surface instead of starting a new stack.

**Verification:**

- `Grep` - `collectOnLifecycle` present near the browse-transfer observer in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`.
- `Grep` - `initialFolderPath` present in the browse-transfer return path handling.
- `Grep` - `requestTransferDialogReattach` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` PASS.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Phase 02 establishes UI attach/detach behavior only. Completion/error/auth/result semantics still need centralized handling in Phase 03.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing persistence changed.
