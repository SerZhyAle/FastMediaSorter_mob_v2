# Phase 03 — Result Surfaces

**Strategic spec:** [../S0149_enh-sftp-permission-denied-message.md](../S0149_enh-sftp-permission-denied-message.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Wire classified SFTP failures into file-operation results and show the right localized copy in browse and player surfaces.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Resolver string keys from Phase 02 are available before handler wiring starts.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` | Modified | ≤ 560 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 — Backup the large player operations file

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup in `temp/` before editing `FileOperationsHandler.kt`. The file is already above 500 lines and the move/delete result handling stays on a user-visible path.

**Verification:**

- `Glob` — `temp/S0149_FileOperationsHandler_*.backup` exists.

**Status:** `[ ]` not done

---

### Step 03.2 — Map classified SFTP failures to `FileOperationResult`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Update `SftpFileOperationHandler` copy, move, delete, and rename paths to classify write-side failures via the Phase 01 model and resolve them via the Phase 02 resolver. Return `Failure(errorRes, formatArgs)` for direct SFTP failures, keep Android scoped-storage `PermissionRequired` intact, and when move already uploaded the destination but failed to delete the source emit the dedicated copied/source-remains message plus one structured Timber line with `operation`, `category`, `statusCode`, and `copyCompleted`.

**Verification:**

- `Grep` — `SftpOperationMessageResolver` present in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt`.
- `Grep` — `copyCompleted` present in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt`.
- `Grep` — `errorRes = R.string.error_sftp_` present in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 — Stop treating SFTP partial failures as success-only in browse and player

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Detect `PartialSuccess` results that carry SFTP access-denied or copied/source-remains details and surface the error/details channel instead of a success-only toast. Keep existing success toasts for fully successful results and preserve undo or navigation behavior for already-copied destinations.

**Verification:**

- `Grep` — `result.errors` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`.
- `Grep` — `result.errors` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`.
- `Grep` — `FileOperationResult.PartialSuccess` present in both files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Direct SFTP failures are now resource-backed, and browse/player no longer hide access-denied partial failures behind success-only toasts.

---

## Rollback Plan

Revert phase commit(s) and restore the `FileOperationsHandler.kt` backup from `temp/` if the player surface regresses.