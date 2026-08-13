# Phase 01 — Domain: Total-Bytes Propagation

**Strategic spec:** [`../S0074_copy-move-dialog-progress.md`](../S0074_copy-move-dialog-progress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Extend the `FileOperationProgress` sealed class so that both the total size of the whole operation and the running count of already-transferred bytes are propagated to the dialog; compute these values in `executeWithProgress` without blocking operation start.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt` | Modified | ≤ 530 |

> File is currently 512 LOC — stays under 1500; no backup required (under 500-LOC backup threshold). **Note:** actual current LOC may be higher after reading the full file; create a timestamped backup in `temp/` if the file exceeds 500 LOC before editing.

---

## Steps

### Step 1.1 — Add `totalOperationBytes` to `FileOperationProgress.Starting`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `FileOperationUseCase.kt`, locate the `FileOperationProgress.Starting` data class (around line 67). Add a new field `totalOperationBytes: Long = 0L` as the third parameter. Default value ensures binary compatibility with any other call sites that construct this class directly.

**Verification:**

- `Grep` — pattern `data class Starting` in `domain/usecase/FileOperationUseCase.kt` returns a line containing `totalOperationBytes`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. Files: domain/usecase/FileOperationUseCase.kt. Dev log deferred to phase end.

---

### Step 1.2 — Add `completedOperationBytes` to `FileOperationProgress.Processing`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `FileOperationUseCase.kt`, locate the `FileOperationProgress.Processing` data class (around line 68). Add a new field `completedOperationBytes: Long = 0L` as the last parameter. This represents total bytes from all files that have already finished plus `bytesTransferred` from the file currently in progress.

**Verification:**

- `Grep` — pattern `data class Processing` in `domain/usecase/FileOperationUseCase.kt` returns a line containing `completedOperationBytes`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. Files: domain/usecase/FileOperationUseCase.kt.

---

### Step 1.3 — Compute and propagate total/completed bytes in `executeWithProgress`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt`
**Depends on:** Step 1.2

**Prompt for developer:**

> In `FileOperationUseCase.executeWithProgress`, make the following changes:
>
> 1. Before `send(FileOperationProgress.Starting(...))`, compute `totalOperationBytes` by summing `file.length()` over all source files in Copy/Move operations. For Delete and Rename operations set it to 0L. Network-path files (SFTP/SMB/FTP) return 0 from `file.length()` — this is acceptable; the dialog will show `≈` when total is zero or partial.
>
> 2. Pass `totalOperationBytes` to the `Starting` event.
>
> 3. Inside the `progressCallback` object, add a `var completedFileBytes = 0L` accumulator before the lambdas. In `onFileStarted`, when `index > 1`, add the previous file's size to `completedFileBytes` (i.e., `completedFileBytes += previousFileSize`). Track `previousFileSize` by storing `totalBytes` from the last `onProgress` call, or by pre-looking up the size of the file at `index - 2` in the sources list.
>    A simpler approach: maintain a `val fileSizes: List<Long>` computed from sources before the callback is created; in `onFileStarted(index, ...)` do `completedFileBytes = fileSizes.take(index - 1).sum()`.
>
> 4. In `onProgress`, emit `completedOperationBytes = completedFileBytes + bytesTransferred`.

**Verification:**

- `Grep` — pattern `totalOperationBytes` in `domain/usecase/FileOperationUseCase.kt` has ≥ 3 matches (declaration in Starting, computation, and pass-through to send).
- `Grep` — pattern `completedOperationBytes` in `domain/usecase/FileOperationUseCase.kt` has ≥ 2 matches (declaration in Processing and assignment in onProgress).
- `Grep` — pattern `fileSizes` in `domain/usecase/FileOperationUseCase.kt` returns at least 1 match (the pre-computed list).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. totalOperationBytes ≥3 hits, completedOperationBytes ≥2 hits, fileSizes ≥1 hit.

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `FileOperationUseCase.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase, `FileOperationProgress.Starting` carries the total byte count for the entire operation, and every `FileOperationProgress.Processing` event carries the running `completedOperationBytes`. Phase 02 consumes both to compute overall % and ETA.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
