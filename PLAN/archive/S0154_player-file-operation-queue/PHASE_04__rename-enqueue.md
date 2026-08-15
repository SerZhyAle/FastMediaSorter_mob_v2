# Phase 04 — Rename Through the Queue

**Strategic spec:** [`../S0154_player-file-operation-queue.md`](../S0154_player-file-operation-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-11
**Completed:** 2026-05-11

---

## Objective

Route player rename of the current file through `PlayerFileOperationQueue` instead of executing it inline in `RenameDialog`: the dialog collects the new name, the rename is enqueued, the player stays on the current file (path updated optimistically), and the queue serialises it behind any in-flight move/delete.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.
- [ ] Confirm whether `RenameDialog` is also used outside the player (Browse rename). If yes, do **not** change `RenameDialog` itself — add a player-only enqueue path and keep `RenameDialog`'s inline execution for non-player callers.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/RenameDialog.kt` | Modified (only if player-safe) | ≤ +30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 930 |

---

## Steps

### Step 04.1 — Add a "collect new name only" mode to the player rename path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt` (+ `RenameDialog.kt` if it must expose a name-only callback)
**Depends on:** — start of phase

**Prompt for developer:**

> In `PlayerDialogHelper.showRenameDialog`, instead of letting `RenameDialog` run `fileOperationUseCase`, obtain just the chosen new file name and surface it via a new callback `dialogCallback.onRenameRequested(oldPath: String, newName: String)`. Prefer adding an optional `onNameChosen` callback to `RenameDialog` (when set, the dialog validates the name and calls back without executing) over forking the dialog. Keep `onBeforeRenameDialog(oldPath)` firing as today (it stops video + drops the cache entry). The existing `onRenameComplete(oldPath, newPath)` callback stays for the Browse path / non-queued callers.

**Verification:**

- `Grep` — `onRenameRequested(` matches once in `PlayerDialogHelper.kt`'s callback interface.
- `Grep` — `RenameDialog(` call in `PlayerDialogHelper.kt` no longer passes `fileOperationUseCase` for the player path, or passes a flag/`onNameChosen` that suppresses execution.

**Status:** `[x]` done

---

### Step 04.2 — Enqueue the rename and update the player path optimistically

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Implement `onRenameRequested(oldPath, newName)` in the dialog callback object: if `oldPath != viewModel.state.value.currentFile?.path` return; build `PlayerFileOperation.Rename` via the Phase 01 factory; compute the new path (same directory, new name); call `viewModel.updateRenamedFilePath(oldPath, newPath)` (point update — keeps playlist position, per S0094) and on `false` fall back to `viewModel.reloadAfterRename()`; then `playerFileOperationQueue.enqueue(op)`. Player stays on the (now renamed) current file — do **not** navigate.

**Verification:**

- `Grep` — `onRenameRequested` matches in `PlayerManagerInitializer.kt`.
- `Grep` — within that callback body: `updateRenamedFilePath` and `playerFileOperationQueue.enqueue(` both present.

**Status:** `[x]` done

---

### Step 04.3 — Reconcile rename result from the queue

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In the queue event listener (the placeholder from Step 03.4): on `Succeeded` for a `Rename` op — nothing extra (path was already updated optimistically); on `Failed` for a `Rename` op — show the per-file error (Phase 06 supplies the string) and revert the optimistic path via `viewModel.updateRenamedFilePath(newPath, oldPath)` **only if** the file is still the current one and still present; otherwise just show the error (do not chase the path). Mark the string lookup `TODO(phase-06)`.

**Verification:**

- `Grep` — the queue event listener in `PlayerManagerInitializer.kt` references `Rename` (e.g. `is PlayerFileOperation.Rename` or `op is ...Rename`).
- Project compiles — run `/build`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if any public signature changed.

---

## Handoff Notes to Next Phase

Rename now flows through the same queue; player stays put on the current (renamed) file. Failure handling for rename, like move/delete, still uses placeholder copy until Phase 06.

---

## Rollback Plan

Revert phase commit(s) — rename falls back to inline execution in `RenameDialog`. No data migration.
