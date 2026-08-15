# Phase 03 - Result Return And Copy

**Strategic spec:** [`../S0818_browse-file-operations-background-mode.md`](../S0818_browse-file-operations-background-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-29
**Completed:** 2026-06-29

---

## Objective

Close the loop for completion, failure, notification return, undo, and localized user-facing copy so the background browse transfer behaves as one coherent feature.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] BrowseActivity can already reattach to the active transfer.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 250 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 250 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 250 |

---

## Steps

### Step 03.1 - Handle completion and cancel without losing Browse state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Consume the worker's terminal output in browse so success reloads the resource, saves undo metadata when available, clears the active request store, and shows one user-facing completion/cancel/failure message only once. A cancellation from the dialog or notification must stop the worker and remove the modal surface without leaving a stale active-operation lock.

**Verification:**

- `Grep` - `clearActiveRequest` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`.
- `Grep` - `FileOperationResult.Success` present in the browse transfer terminal handling path.
- `Grep` - `cancelUniqueWork` present in the browse transfer cancel path.

**Status:** `[x] done`

---

### Step 03.2 - Route notification taps back into the correct Browse context

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a dedicated browse-transfer notification return path. Tapping the ongoing notification must reopen BrowseActivity on the right resource and folder, preserving the existing task when possible and restoring the attachable progress UI instead of creating a new modal stack.

**Verification:**

- `Grep` - `EXTRA_REATTACH_TRANSFER` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`.
- `Grep` - `PendingIntent.getActivity` present in `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`.

**Status:** `[x] done`

---

### Step 03.3 - Add the new user-visible strings with communication-policy checks

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add the new progress-dialog, notification, and duplicate-owner copy for background browse transfers in EN/RU/UK. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 and §6 so the strings stay direct, actionable, and non-technical.

**Verification:**

- `Grep` - `browse_transfer_progress_background` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `browse_transfer_progress_background` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `browse_transfer_progress_background` present in `app_v2/src/main/res/values-uk/strings.xml`.
- `Grep` - `browse_transfer_notif_` present in all three files.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` PASS.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Phase 03 should leave the feature ready for compile proof and final docs/catalog closure; only release-facing documentation and ticket bookkeeping remain.

---

## Rollback Plan

Revert phase commit(s) - no data migration or irreversible storage changes.
