# Phase 01 - Browse Transfer Contract

**Strategic spec:** [`../S0818_browse-file-operations-background-mode.md`](../S0818_browse-file-operations-background-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-29
**Completed:** 2026-06-29

---

## Objective

Introduce a worker-backed contract for one active browse copy/move transfer that can survive dialog dismissal and expose progress/cancel/return metadata outside the dialog.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] WorkManager remains the only execution engine for the backgroundable browse transfer.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferRequestStore.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferProgressCodec.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferCoordinator.kt` | New | ≤ 350 |

---

## Steps

### Step 01.1 - Persist the transfer request outside WorkManager Data

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferRequestStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a small internal-store component that writes and reads one active browse transfer request from app-private storage. The payload must preserve operation type, source paths, optional cloud display names and sizes, destination path, source credentials id, source resource id, and the browse return folder path so large selections do not overflow WorkManager input limits.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferRequestStore.kt` exists.
- `Grep` - `class BrowseFileTransferRequestStore` matches exactly once in that file.
- `Grep` - `fun writeActiveRequest` present.
- `Grep` - `fun readActiveRequest` present.

**Status:** `[x] done`

---

### Step 01.2 - Encode worker-visible progress and operation metadata

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferProgressCodec.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a stable codec for browse transfer progress and worker output metadata. It must encode enough information for the dialog and notification to show current file, overall progress, speed, ETA, operation type, and the resource/folder return path without reparsing raw log text.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferProgressCodec.kt` exists.
- `Grep` - `object BrowseFileTransferProgressCodec` matches exactly once in that file.
- `Grep` - `fun encode` present.
- `Grep` - `fun decode` present.

**Status:** `[x] done`

---

### Step 01.3 - Execute the active browse transfer through a foreground worker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferCoordinator.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a Hilt worker that restores the stored request, runs `FileOperationUseCase.executeWithProgress`, keeps one foreground notification alive, updates WorkManager progress/output with the codec, exposes a cancel action, and posts a content intent that returns to BrowseActivity. Add a coordinator that enqueues exactly one unique interactive browse transfer, resolves the active WorkInfo flow, and prevents a second modal copy/move stack from being launched while one transfer is already active.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt` exists.
- `Grep` - `class BrowseFileTransferWorker` matches exactly once in that file.
- `Grep` - `enqueueUniqueWork` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferCoordinator.kt`.
- `Grep` - `createCancelPendingIntent` present in `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` PASS.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Phase 01 owns the unique browse-transfer execution contract. Later phases must attach UI to the coordinator/WorkInfo surface instead of starting new transfer coroutines directly from dialogs.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed yet.
