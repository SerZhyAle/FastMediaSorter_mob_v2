# Phase 02 - Share-receive handoff

**Strategic spec:** [`../S1370_share-receive-copy-dies-with-activity.md`](../S1370_share-receive-copy-dies-with-activity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** -
**Completed:** 2026-08-03

---

## Objective

Move both copy branches of the share-receive screen off screen-bound scopes and onto the background transfer work, handing staged-source ownership with them.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §3.3 "UI placement contract" read - this phase adds no new screen, dialog or layout element.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 800 |

> Layout parity: this phase touches no `res/layout*` file - the screen's visible UI is unchanged.

---

## Steps

### Step 02.1 - Inject the transfer coordinator into the receive screen

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `@Inject lateinit var browseTransferCoordinator: BrowseFileTransferCoordinator` to `ReceiveShareActivity`. The class is already `@Singleton @Inject constructor` and needs no new Hilt module or binding.

**Why:**

Strategic §5 makes the screen a caller that orders the operation rather than one that runs it, and the coordinator is the only component that owns the unique work and its terminal state.

**Verification:**

- `Grep` - `browseTransferCoordinator: BrowseFileTransferCoordinator` matches exactly once in `ReceiveShareActivity.kt`.
- `Grep` - no new `@Module` file added under `di/` for this binding.

**Status:** `[x]` done

---

### Step 02.2 - Add a single enqueue helper for both branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a private suspend helper that builds a `BrowseFileTransferRequest` for the cached files and enqueues it through `browseTransferCoordinator.enqueueIfIdle`. Fill `operationType = FileOperationType.COPY`, `sourceResourceId = -1L`, `sourceResourceName` from `R.string.receive_share_source_name`, `sourceCredentialsId = null`, `currentBrowsePath = null`, `overwriteFiles = false`, sources from `cachedFiles`, `sourcesOwnedByOperation = true` and `stagingDirectoryPath` from the share temp directory. Take the destination path and display name as parameters so both call sites reuse the helper. On `Enqueued` mark the staged files as handed off and finish the screen without deleting them; on `ActiveAlreadyRunning` show `R.string.browse_transfer_already_running` as a Toast and leave the screen open so the user can retry.

**Why:**

Strategic §5.1 requires both destination branches to reduce to one order, and §6 item 4 records that the unique work is shared with the Browse screen, so a rejected order must be reported rather than swallowed - otherwise the accepted files are lost with no trace.

**Verification:**

- `Grep` - `enqueueIfIdle` matches exactly once in `ReceiveShareActivity.kt`.
- `Grep` - `sourcesOwnedByOperation = true` matches exactly once in that file.
- `Grep` - `browse_transfer_already_running` matches in that file.
- `Grep` - `ActiveAlreadyRunning` matches in that file.

**Status:** `[x]` done

---

### Step 02.3 - Route the destination dialog through the helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `showDestinationDialog()`, pass an `onOperationRequested` lambda to `FileOperationDestinationDialog` that calls the Step 02.2 helper with the picked destination's path and name. Keep every existing argument, including `onSelectFolderClicked` and the dismiss listener. The dialog dismisses itself on this branch, so the dismiss listener must not run the temp-file cleanup once the order was accepted.

**Why:**

Strategic §1 identifies the dialog's built-in branch as the code that dies with the window; passing the handler is what selects the long-lived branch the dialog already supports.

**Verification:**

- `Grep` - `onOperationRequested` matches in `ReceiveShareActivity.kt`.
- `Grep` - `FileOperationDestinationDialog(` still matches exactly once in that file.
- `Grep` - `onSelectFolderClicked` still present in the same construction.

**Status:** `[x]` done

---

### Step 02.4 - Route the picked-folder branch through the helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Replace the body of `copyToSafFolder(treeUri)`: keep `takePersistableUriPermission`, then call the Step 02.2 helper with the tree URI string as the destination path and the tree's display name as the destination name, instead of copying through `lifecycleScope` and `DocumentFile`. Keep the existing failure Toast for the case where the permission cannot be taken.

**Why:**

Strategic §2 records this branch as the second point of interruption, and §11 criterion 2 makes it a release criterion; leaving it on `lifecycleScope` would close only half the defect.

**Verification:**

- `Grep -n "lifecycleScope" app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` returns no hit inside `copyToSafFolder`.
- `Grep` - `takePersistableUriPermission` still matches in that file.
- `Grep` - `DocumentFile.fromTreeUri` no longer matches in `copyToSafFolder`.

**Status:** `[x]` done

---

### Step 02.5 - Stop deleting staged files once ownership is handed off

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.3, Step 02.4

**Prompt for developer:**

> Add a private flag set when Step 02.2 reports `Enqueued`, and make `deleteCachedFilesAsync` return immediately while it is set. Leave every other cleanup path untouched, including `onDestroy` and the cancel paths, so a screen that never enqueued still empties the share temp directory as it does today.

**Why:**

Strategic §6 item 3 records that the screen's finish deletes the staged sources asynchronously, which would remove them from under a running background copy; ADR-2 assigns that deletion to the executor only after ownership is handed over.

**Verification:**

- `Grep` - `deleteCachedFilesAsync` still matches in `ReceiveShareActivity.kt`.
- `Grep` - the new flag name matches at least twice (set site and guard).
- `Grep` - `applicationScope.launch` still present for the non-handed-off path.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The share-receive screen no longer executes file operations. Every copy it starts lives in the background work, and staged sources are deleted by the executor.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no user-facing surface changed; the Phase 01 request fields simply stop being set.
