# Phase 05 — Permission Pause/Resume + Empty-List Close + Exit Warning

**Strategic spec:** [`../S0154_player-file-operation-queue.md`](../S0154_player-file-operation-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-11
**Completed:** 2026-05-11

---

## Objective

Wire the queue's `PermissionRequired` / `Drained` / `Succeeded` events into the player: pause the queue around the Android batch-delete permission dialog and resume after the answer; close the player only after the queue is actually drained and the list is empty; warn the user if they leave the player while operations are still queued.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (queue owned by `PlayerActivity`, event listener placeholder in place).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 940 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ +25 vs current |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 05.1 — Permission pause/resume wiring

**Files:** `PlayerManagerInitializer.kt`, `PlayerLifecycleManager.kt`, `PlayerActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> On a `PlayerFileOperationEvent.PermissionRequired(op, pendingIntent)`: store `op` (the queue is suspended), call `lifecycleManager.storePendingBatchDeleteFilePath(op.sourcePath)` and launch `activity.batchDeletePermissionLauncher` exactly as the current `onBatchDeletePermissionRequired` does. In the launcher's result callback (find it in `PlayerActivity`): call `playerFileOperationQueue.resumeAfterPermission(granted = <result ok>, op = <stored op>)` instead of the old direct-delete completion path. Remove/repurpose the old `FileOperationCallback.onBatchDeletePermissionRequired` plumbing if it is now unused by the queued flow (keep it only if `performCopy` or non-queued paths still need it). Keep `storePendingBatchDeleteFilePath` — it still attributes the deletion to the right file.

**Verification:**

- `Grep` — `resumeAfterPermission(` matches in `PlayerActivity.kt` or `PlayerManagerInitializer.kt`.
- `Grep` — `PermissionRequired` is handled in the queue event listener in `PlayerManagerInitializer.kt`.
- `Grep` — `storePendingBatchDeleteFilePath` still present.

**Status:** `[x]` done

---

### Step 05.2 — Close the player only after the queue drains

**Files:** `PlayerManagerInitializer.kt`, `PlayerLifecycleManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> On `PlayerFileOperationEvent.Succeeded(op, _)`: call `lifecycleManager.trackModifiedFile(op.sourcePath)` (the call moved here from the old `onMoveSuccess`/`onDeleteSuccess`). On `PlayerFileOperationEvent.Drained`: if the player's media list is now empty (`viewModel` reports no remaining files) → `activity.finish()` returning to Browse; otherwise stay. The optimistic `onBeforeMove/onBeforeDelete` path must **not** call `finish()` (already ensured in Phase 03) — closing is exclusively a `Drained` decision. Edge case: if the list is empty *and* the queue is also already empty at the moment of the last optimistic removal (e.g. a synchronous local delete that the queue completes instantly) — the `Drained` event still fires, so the single code path covers it; do not add a second close trigger.

**Verification:**

- `Grep` — `Drained` is handled in the queue event listener in `PlayerManagerInitializer.kt`.
- `Grep` — `activity.finish()` appears inside the `Drained` branch.
- `Grep` — `trackModifiedFile` appears inside the `Succeeded` branch.

**Status:** `[x]` done

---

### Step 05.3 — Warn on player exit with a non-empty queue

**Files:** `PlayerLifecycleManager.kt` (or wherever player back/exit is handled), `PlayerManagerInitializer.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> When the user leaves the player (back press / up) and `playerFileOperationQueue.snapshotPending()` is non-empty: show a confirm dialog — title/body explaining N operations are still finishing in the background and asking whether to wait or leave anyway. "Leave anyway" proceeds with the exit (the queue keeps running on `fileOpsAppScope` until the activity is destroyed, then is shut down — pending ops not yet started are lost; this is the documented first-iteration behaviour). "Stay" cancels the exit. Do not block normal exit when the queue is empty. New strings — see Step 05.4. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (confirmation formula) and §6 (tone checklist): calm, no alarm — "Несколько файлов ещё перемещаются" not "ВНИМАНИЕ! Операции не завершены!".

**Verification:**

- `Grep` — `snapshotPending()` is referenced from the exit/back handler.
- `Grep` — the new exit-warning string keys (Step 05.4) are referenced in code.

**Status:** `[x]` done

---

### Step 05.4 — Add exit-warning strings (EN/RU/UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add a string family for the exit-with-pending-queue dialog: a title, a body with a `%1$d` count placeholder, a "leave anyway" button label, a "stay / wait" button label. Use a shared prefix, e.g. `dialog_player_exit_pending_queue_*`. Add the same keys to all three `strings.xml` files. Apply Author Style (`..` not `...`, `ё`/`Ё`). Run the tone checklist (`COMMUNICATION_POLICY.md` §6) before committing.

**Verification:**

- `Grep` — each new key matches once in `values/strings.xml`, once in `values-ru/strings.xml`, once in `values-uk/strings.xml`.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "dialog_player_exit_pending_queue"` exits 0.
- Project compiles — run `/build`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] New strings pass `COMMUNICATION_POLICY.md` §6 checklist.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "dialog_player_exit_pending_queue"` exits 0.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if any public signature changed.

---

## Handoff Notes to Next Phase

Permission, empty-list-close, and exit-warning are wired. What's left for Phase 06: per-file error copy (replace the placeholder generic errors and the `TODO(phase-06)` tags), reuse of the S0074 progress mechanism for the in-flight queued op, the started/success toasts, and the optional "N in queue" indicator.

---

## Rollback Plan

Revert phase commit(s). The queued flow falls back to placeholder result handling from Phase 03 (still functional, just less polished). No data migration.
