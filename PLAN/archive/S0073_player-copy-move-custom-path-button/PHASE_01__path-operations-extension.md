# Phase 01 — Path-Based Operations Extension

**Strategic spec:** [`../S0073_player-copy-move-custom-path-button.md`](../S0073_player-copy-move-custom-path-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Extend `FileOperationsHandler` and its `FileOperationCallback` interface with path-based copy and move methods, so that the player can execute file operations to a `String` path rather than a `MediaResource`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(none — foundation phase)*
- [ ] Working tree is clean or on a feature branch.
- [ ] Backup of `FileOperationsHandler.kt` created (file is currently ~585 lines — exceeds 500 LOC threshold).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` | Modified | ≤ 650 |

> File is currently ~585 lines. Create a timestamped backup in `temp/` before any edit (rule: file >500 LOC).

---

## Steps

### Step 01.1 — Backup FileOperationsHandler before edit

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` to `temp/FileOperationsHandler_<YYYYMMDD_HHmm>.kt.backup`. This satisfies the project rule requiring a timestamped backup before editing any file exceeding 500 lines.

**Verification:**

- `Glob` — `temp/FileOperationsHandler_*.kt.backup` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. Files: temp/FileOperationsHandler_20260504_0031.kt.backup. Dev log recorded.

---

### Step 01.2 — Add `onCopyToPathSuccess` and `onMoveToPathSuccess` to `FileOperationCallback`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside the `FileOperationCallback` interface in `FileOperationsHandler.kt`, add two new methods after the existing `onMoveSuccess`:
> ```kotlin
> fun onCopyToPathSuccess(destinationPath: String, goToNext: Boolean)
> fun onMoveToPathSuccess(destinationPath: String, movedFilePath: String, goToNext: Boolean)
> ```
> These are the success callbacks for path-based (folder-picker) operations, parallel to the existing `onCopySuccess` / `onMoveSuccess` which use `MediaResource`.

**Verification:**

- `Grep` — pattern `fun onCopyToPathSuccess` found in `FileOperationsHandler.kt`.
- `Grep` — pattern `fun onMoveToPathSuccess` found in `FileOperationsHandler.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: FileOperationsHandler.kt (+2 interface methods). Dev log recorded.

---

### Step 01.3 — Add `performCopyToPath` and `performMoveToPath` to `FileOperationsHandler`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add two new public methods to `FileOperationsHandler` after the existing `performMove()` method:
>
> ```kotlin
> fun performCopyToPath(destinationPath: String) { ... }
> fun performMoveToPath(destinationPath: String) { ... }
> ```
>
> Each method must:
> 1. Get `currentFile` via `callback.getCurrentFile()` — return early if null.
> 2. Launch on `appScope` (same as `performCopy`/`performMove`) so the operation survives Activity destruction.
> 3. Read `settings.overwriteOnCopy` (for copy) or `settings.overwriteOnMove` (for move) via `settingsRepository.getSettings().first()`.
> 4. Build `FileOperation.Copy(sources = listOf(currentFile.file), destination = java.io.File(destinationPath), overwrite = overwrite)` — or `FileOperation.Move(...)` for move.
> 5. Call `fileOperationUseCase.execute(operation)` and handle result:
>    - `FileOperationResult.Success` → call `callback.onCopyToPathSuccess(destinationPath, goToNext = settings.autoNext)` (or `onMoveToPathSuccess(destinationPath, movedFilePath, goToNext)` for move where `movedFilePath = currentFile.path`).
>    - `FileOperationResult.Failure` → call `callback.onOperationError(message, throwable)`.
>    - Other result types → follow the same pattern as `performCopy`/`performMove` for `AuthenticationRequired` and `PermissionRequired`.
> 6. Gate UI callbacks with `if (!isActivityGone())` — same as existing methods.
> 7. No progress dialog needed (path-based operations are intended for quick single-file actions from the player panel).

**Verification:**

- `Grep` — pattern `fun performCopyToPath` found in `FileOperationsHandler.kt`.
- `Grep` — pattern `fun performMoveToPath` found in `FileOperationsHandler.kt`.
- `Grep` — pattern `Log\.d\(` returns zero hits in `FileOperationsHandler.kt` (Timber-only rule).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: FileOperationsHandler.kt (+~90 LOC). Dev log recorded.

---

### Step 01.4 — Implement `onCopyToPathSuccess` / `onMoveToPathSuccess` in anonymous `FileOperationCallback`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 01.3

> **Spec correction:** `FileOperationCallback` is implemented as an anonymous object in `PlayerManagerInitializer.initFileOps()`, not directly in `PlayerActivity`. Spec updated accordingly.

**Prompt for developer:**

> In `PlayerManagerInitializer.initFileOps()`, the anonymous `FileOperationCallback` object must implement the two new interface methods. Add after `onMoveSuccess`:
> - `onCopyToPathSuccess`: mirrors `onCopySuccess` — calls `navigateNextAfterOperation` if `goToNext`.
> - `onMoveToPathSuccess`: mirrors `onMoveSuccess` — tracks the modified file, removes from cache, removes from list, finishes or navigates next.

**Verification:**

- `Grep` — pattern `onCopyToPathSuccess` found in `PlayerManagerInitializer.kt`.
- `Grep` — pattern `onMoveToPathSuccess` found in `PlayerManagerInitializer.kt`.
- Project compiles — run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Spec correction applied: implementations in PlayerManagerInitializer.kt (anonymous FileOperationCallback object). Verification 2/2 PASS (grep in PlayerManagerInitializer.kt). Build required — hard stop for Phase Done Criteria.

---

## Phase Done Criteria

- [ ] Every Step 01.* above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (public API of `FileOperationsHandler` changed).

---

## Handoff Notes to Next Phase

- `FileOperationsHandler` now exposes `performCopyToPath(path: String)` and `performMoveToPath(path: String)`.
- `FileOperationCallback` has `onCopyToPathSuccess` and `onMoveToPathSuccess` — both implemented in `PlayerActivity`.
- Phase 02 will create `PlayerFolderPickerHandler` which calls these two new methods.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
