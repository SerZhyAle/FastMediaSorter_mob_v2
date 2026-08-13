# Phase 02 - Browse delete handoff

**Strategic spec:** [`../S1369_bulk-delete-dies-with-hosting-screen.md`](../S1369_bulk-delete-dies-with-hosting-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3

## Objective

Route Browse multi-delete into the worker and reconcile its terminal events in the existing Browse transfer owner.

## Files Touched

| File | Change | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt` | Modified | <= 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | <= 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | <= 1500 |

## Steps

### Step 02.1 - Enqueue Browse deletion

**Files:** `BrowseDeleteManager.kt`, `BrowseViewModel.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Build a DELETE `BrowseFileTransferRequest` from the exact selection, preserving directory markers, path strings, current resource identity, overflow override behavior and the resolved soft-delete policy. Enqueue it through the injected coordinator; do not execute the batch in `viewModelScope`.

**Why:**

The observed defect is a ViewModel-owned batch, and the strategic goal is survival of Browse destruction without changing delete policy.

**Verification:**

- `BrowseDeleteManager.deleteSelectedFiles` does not call `fileOperationUseCase.execute` for the durable batch.
- Active work rejects a second interactive transfer using coordinator semantics.
- No activity or view reference enters a singleton or worker request.

**Status:** `[ ]` not done

### Step 02.2 - Reconcile DELETE terminal events

**Files:** `BrowseFileOperationsManager.kt`, `BrowseViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend the shared terminal-event handler for DELETE: update the file list, selection, undo only when soft delete permits it, partial/failure details, cloud auth, Android permission retry and cancellation message. Use the existing background progress dialog and notification reattach path.

**Why:**

A worker terminal event has no visible value unless returning Browse can reconcile the completed or partial deletion safely.

**Verification:**

- DELETE maps to deleting/deleted/failed/cancelled resources without falling through COPY text.
- Permission required reaches the existing activity-owned permission callback only after a Browse UI host is available.
- Explicit Cancel calls coordinator cancellation only.

**Status:** `[ ]` not done

### Step 02.3 - Remove lifecycle-bound duplicate ownership

**Files:** `BrowseDeleteManager.kt`, `BrowseViewModel.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Delete or narrow obsolete direct-batch state and callbacks so one owner handles durable delete completion. Keep delete-by-size on its independently scoped path unless it is explicitly converted and verified in this ticket.

**Why:**

Two completion owners can double-remove files, show contradictory messages or reintroduce lifecycle cancellation.

**Verification:**

- Production Browse bulk-delete has one terminal-event owner.
- Delete-by-size behavior is unchanged or explicitly covered by tests.
- `Log.d(` has zero matches in modified Kotlin files.

**Status:** `[ ]` not done

## Phase Done Criteria

- [ ] Every step is done.
- [ ] `pwsh -NoProfile -File a.ps1 fk` passes.
- [ ] Phase-boundary audit covers WorkManager ownership, lifecycle collection, cancellation, terminal-event consumption and UI references.
