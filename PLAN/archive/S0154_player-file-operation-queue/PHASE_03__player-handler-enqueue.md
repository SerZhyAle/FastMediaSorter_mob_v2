# Phase 03 — Player Handler Enqueue + Optimistic UI

**Strategic spec:** [`../S0154_player-file-operation-queue.md`](../S0154_player-file-operation-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-05-11
**Completed:** 2026-05-11

---

## Objective

Replace the "single in-progress flag, drop the rest" model in `FileOperationsHandler` with: build a `PlayerFileOperation` snapshot → `enqueue` it → immediately remove the source file from the player list by path and run optimistic navigation → return. The queue (Phase 02) does the actual work. `moveInProgress` / `deleteInProgress` are removed.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] S0152 confirmed `Verified` (the guard logic this phase deletes is the one S0152 patched).
- [ ] `FileOperationsHandler.kt` (737 LOC) and `PlayerManagerInitializer.kt` (902 LOC) are both >500 LOC → timestamped backups required before edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` | Modified | ≤ 650 (target: shrink) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 920 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ +20 vs current |

> Backup step 03.0 is mandatory: copy both >500-LOC files to `temp/S0154_phase03_<timestamp>/` before any edit.

---

## Steps

### Step 03.0 — Backups

**Files:** `temp/S0154_phase03_<timestamp>/`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `FileOperationsHandler.kt` and `PlayerManagerInitializer.kt` to a timestamped folder under `temp/` before editing (project rule: file >500 LOC → backup first).

**Verification:**

- `Glob` — `temp/S0154_phase03_*/FileOperationsHandler.kt` exists.
- `Glob` — `temp/S0154_phase03_*/PlayerManagerInitializer.kt` exists.

**Status:** `[x]` done

---

### Step 03.1 — Own the queue at PlayerActivity session scope

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 03.0

**Prompt for developer:**

> Add a `lateinit var playerFileOperationQueue: PlayerFileOperationQueue` to `PlayerActivity` (mirroring how `fileOperationsHandler` is held). Construct it in `PlayerManagerInitializer.initFileOps()` using `activity.fileOpsAppScope`, `activity.viewModel.fileOperationUseCase`, `activity.settingsRepository`. Call `playerFileOperationQueue.shutdown()` from the same place `fileOpsAppScope` is torn down (find the existing teardown in `PlayerActivity`/`PlayerLifecycleManager`).

**Verification:**

- `Grep` — `playerFileOperationQueue` matches in both `PlayerActivity.kt` and `PlayerManagerInitializer.kt`.
- `Grep` — `PlayerFileOperationQueue(` matches once in `PlayerManagerInitializer.kt`.
- `Grep` — `playerFileOperationQueue.shutdown()` matches at least once.

**Status:** `[x]` done

---

### Step 03.2 — Rewrite `performMove` / `performMoveToPath` / `performDelete` to enqueue

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Give `FileOperationsHandler` a reference to the queue (constructor param or setter). Rewrite the three methods so each: reads `callback.getCurrentFile()` / `getCurrentResource()` (still the "current" file at click time), builds the matching `PlayerFileOperation` via the Phase 01 factory, calls `callback.onBeforeMove(path)` / `onBeforeDelete(path)` (which already does stop-playback + remove-by-path + optimistic navigate), then `queue.enqueue(op)`, then returns — synchronously, no `appScope.launch` for the operation itself, no `moveInProgress`/`deleteInProgress` check or set. Delete the `moveInProgress` / `deleteInProgress` fields, `resetMoveInProgress()`, `resetDeleteInProgress()`, and the `Timber.d("S0152: ...")` lines. The "started" toast (`msg_move_started` etc.) moves to a queue-event handler (Phase 06) — for this phase a temporary toast on enqueue is acceptable but mark it `TODO(phase-06)` so Phase 06 cleans it. `performCopy` / `performCopyToPath` are **not** queued (copies leave the source in the list — no list mutation race) — leave them on `appScope` as today, but they may also route through the queue if it is trivial; if not, leave untouched and note it.

**Verification:**

- `Grep` — `moveInProgress` returns zero hits in `FileOperationsHandler.kt`.
- `Grep` — `deleteInProgress` returns zero hits in `FileOperationsHandler.kt`.
- `Grep` — `Timber.d("S0152:` returns zero hits anywhere under `app_v2/`.
- `Grep` — `queue.enqueue(` (or `playerFileOperationQueue.enqueue(`) matches at least 3 times across `FileOperationsHandler.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `FileOperationsHandler.kt`.

**Status:** `[x]` done

---

### Step 03.3 — Move the "list empty → finish" check off the optimistic path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In the `FileOperationCallback` object: `onBeforeMove` / `onBeforeDelete` still remove the file from the list and navigate, **but must not** call `activity.finish()` when the list becomes empty — leave the player open while the queue still has work. Remove the `onMoveSuccess` / `onMoveToPathSuccess` / `onDeleteSuccess` bodies that currently do `resetMoveInProgress()` + `removeMovedFile()` + `finish()` — those callbacks are no longer invoked by the handler (the queue owns results now). Decide one of: (a) keep the callback interface methods but make them no-ops with a KDoc explaining the queue replaced them, or (b) remove them from the interface and all call sites. Pick (b) if it does not ripple beyond `FileOperationsHandler` + `PlayerManagerInitializer` + `StandaloneFileOperationsHandler` interface conformance; otherwise (a). The actual "list empty after a real operation finished → finish()" decision is wired in Phase 05 from the queue's `Drained` / `Succeeded` events. `lifecycleManager.trackModifiedFile(...)` must still run for every actually-completed operation — move that call into the Phase 05 success handler, not lose it.

**Verification:**

- `Grep` — `resetMoveInProgress` and `resetDeleteInProgress` return zero hits across `app_v2/`.
- `Grep` — within `PlayerManagerInitializer.kt` the `onBeforeMove` body still contains `removeFile` and `navigateNextAfterOperation` but no `activity.finish()`.
- Project compiles — run `/build`.

**Status:** `[x]` done

---

### Step 03.4 — Route enqueue results to a thin listener (placeholder)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Subscribe to `playerFileOperationQueue.events` (or set its listener) from `PlayerManagerInitializer` after construction, on `activity.lifecycleScope`. For this phase the listener may just `Timber.i` each event and show the success/error toasts inline — full projection (per-file error strings, progress, empty-list-finish, permission) is filled by Phases 05–06. Tag the placeholder bodies `TODO(phase-05)` / `TODO(phase-06)` accordingly. Ensure the subscription is cancelled with the activity (use a lifecycle-aware launch / `repeatOnLifecycle` if using a `Flow`).

**Verification:**

- `Grep` — `playerFileOperationQueue.events` (or `setListener`) matches at least once in `PlayerManagerInitializer.kt`.
- `Grep` — `TODO(phase-05)` and `TODO(phase-06)` each match at least once in `PlayerManagerInitializer.kt`.
- Project compiles — run `/build`.
- Manual smoke (not automated): queueing two moves on a slow network leaves both buttons responsive and never drops the second action.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `Timber.d("S0152:` returns zero hits anywhere under `app_v2/`.
- [x] `Grep` for `moveInProgress` / `deleteInProgress` / `resetMoveInProgress` / `resetDeleteInProgress` returns zero hits under `app_v2/`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 03 the move/delete flow is queue-backed and buttons never stick, but result projection is placeholder (`TODO(phase-05/06)`): no per-file error copy, no progress reuse, no "finish after queue drained", no permission pause/resume wiring on the UI side, rename still runs synchronously in `RenameDialog`. Phases 04–06 close those.

---

## Rollback Plan

Revert phase commit(s) and restore `FileOperationsHandler.kt` / `PlayerManagerInitializer.kt` from `temp/S0154_phase03_<timestamp>/`. No data migration. The Phase 01–02 files become dead code again but compile.
