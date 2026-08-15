# Phase 01 - delete-flow

**Strategic spec:** [`../S0360_drawing-editor-delete-file.md`](../S0360_drawing-editor-delete-file.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Add an editor-initiated single-file delete path that reuses the existing trash/undo coordinator but returns the user to browse on success; no UI yet.

---

## Prerequisites

- [ ] Strategic §6 research item is Resolved (see INDEX Pre-Implementation Blockers).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 800 |

> `PlayerViewModel.kt` is >500 lines (780) - create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 01.1 - Add `finishOnSuccess` branch to the delete coordinator

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `PlayerDeleteUndoCoordinator.deleteCurrentFile()`, add a parameter `finishOnSuccess: Boolean = false` (default preserves all existing callers). Inside the `Success` / `PartialSuccess` branch, after `sendEvent(PlayerViewModel.PlayerEvent.FileModified(currentFile.path))` and `MediaFilesCacheManager.removeFile(resource.id, currentFile.path)`, when `finishOnSuccess` is `true` emit `sendEvent(PlayerViewModel.PlayerEvent.FinishActivity)` and `return@launch` - skipping the undo-snackbar bookkeeping and the index-advance logic. When `finishOnSuccess` is `false`, keep the current behavior (undo snackbar + advance/finish) exactly as-is. Do not change the failure / auth / permission branches. Keep `MediaFilesCacheManager.removeFile` reachable in both paths. Use `Timber` only.

**Verification:**

- `Grep` - `fun deleteCurrentFile(finishOnSuccess: Boolean = false)` matches once in `PlayerDeleteUndoCoordinator.kt`.
- `Grep` - `if (finishOnSuccess)` present in the success branch.
- `Grep` - `PlayerViewModel.PlayerEvent.FinishActivity` present in `PlayerDeleteUndoCoordinator.kt`.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. Files: PlayerDeleteUndoCoordinator.kt (+7 LOC). Dev log recorded.

---

### Step 01.2 - Expose `deleteCurrentFileAndFinish()` on the ViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Immediately after the existing `fun deleteCurrentFile(): Boolean? = deleteUndoCoordinator.deleteCurrentFile()` line, add `fun deleteCurrentFileAndFinish(): Boolean? = deleteUndoCoordinator.deleteCurrentFile(finishOnSuccess = true)`. Add a one-line KDoc stating it deletes the current file and finishes the activity (returns to browse) on success; on failure the activity stays. No other change.

**Verification:**

- `Grep` - `fun deleteCurrentFileAndFinish()` matches once in `PlayerViewModel.kt`.
- `Grep` - `deleteUndoCoordinator.deleteCurrentFile(finishOnSuccess = true)` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS. Files: PlayerViewModel.kt (+6 LOC). Backup in temp/. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL (after kapt-stall recovery; failure was stale stub cache, not this change).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2` (public API on VM/coordinator changed).

---

## Handoff Notes to Next Phase

- `PlayerViewModel.deleteCurrentFileAndFinish()` is the entry point Phase 02 wires to the overflow menu.
- Android 11+ local MediaStore batch-delete surfaces as `PermissionRequired` (handled by the activity, not a success) - in that path the activity owns completion and the "return to browse" finish does not fire. Acceptable edge case consistent with the existing player delete; note for device testing.

---

## Rollback Plan

Revert the phase commit(s) - the new parameter defaults to `false`, so reverting is non-breaking; no data migration or user-facing surface changed.
