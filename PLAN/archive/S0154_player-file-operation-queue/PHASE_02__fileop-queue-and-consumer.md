# Phase 02 — File Operation Queue and Consumer

**Strategic spec:** [`../S0154_player-file-operation-queue.md`](../S0154_player-file-operation-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-11
**Completed:** 2026-05-11

---

## Objective

Introduce `PlayerFileOperationQueue` — a FIFO queue with a single sequential background consumer that executes one `PlayerFileOperation` at a time via `FileOperationUseCase`, emits per-operation result events, and can pause/resume around a permission request. No player wiring yet.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Research §6.1 default confirmed: queue is owned at PlayerActivity session scope (constructed by `PlayerManagerInitializer`, runs on a scope that outlives the visible window but dies with the activity — reuse the existing `PlayerActivity.fileOpsAppScope`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperationQueue.kt` | New | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperationEvent.kt` | New | ≤ 90 |

---

## Steps

### Step 02.1 — Define the result/event model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperationEvent.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a sealed class `PlayerFileOperationEvent` with: `Enqueued(op)`, `Started(op)`, `Succeeded(op, processedCount: Int)`, `Failed(op, message: String, retryable: Boolean)`, `PermissionRequired(op, pendingIntent: PendingIntent)`, `AuthRequired(op, provider: String, message: String)`, `Drained` (queue became empty after the last op finished). All payloads carry the originating `PlayerFileOperation`. No Android UI imports beyond `PendingIntent`.

**Verification:**

- `Glob` — `PlayerFileOperationEvent.kt` exists.
- `Grep` — `sealed class PlayerFileOperationEvent` matches once.
- `Grep` — `object Drained` and `class PermissionRequired` and `class Failed` each match once.

**Status:** `[x]` done

---

### Step 02.2 — Implement the FIFO queue + sequential consumer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperationQueue.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class PlayerFileOperationQueue(scope: CoroutineScope, fileOperationUseCase: FileOperationUseCase, settingsRepository: SettingsRepository)`. Internally hold an ordered structure (a `Channel<PlayerFileOperation>` with unlimited capacity, or a mutex-guarded `ArrayDeque` + a `Mutex`-serialized worker loop). Expose:
> - `fun enqueue(op: PlayerFileOperation)` — synchronous, returns immediately; emits `Enqueued`; starts the consumer loop if idle.
> - `val events: SharedFlow<PlayerFileOperationEvent>` (replay 0) — or a callback interface set via `setListener(...)`. Pick one; document the choice in a KDoc line.
> - `val pendingCount: StateFlow<Int>` — number of operations queued or in flight (for the optional indicator in Phase 06).
> The consumer loop: take head → emit `Started` → `fileOperationUseCase.execute(op.toDomainFileOperation(settings))` on `Dispatchers.IO` → map `FileOperationResult` to a `PlayerFileOperationEvent` (`Success`/`PartialSuccess`→`Succeeded`, `Failure`→`Failed(retryable = true)`, `AuthenticationRequired`→`AuthRequired`, `PermissionRequired`→`PermissionRequired` then **suspend the loop** until resumed) → loop. When the queue is empty after a completion, emit `Drained`. Catch exceptions per-operation: log via `Timber.e`, emit `Failed(retryable = true)`, continue with the next. **Never** let one operation's failure cancel the loop. Exactly one operation executes at a time (single worker). `Log.d()` is forbidden — Timber only.

**Verification:**

- `Glob` — `PlayerFileOperationQueue.kt` exists.
- `Grep` — `class PlayerFileOperationQueue` matches once.
- `Grep` — `fun enqueue(` matches once; `pendingCount` matches at least once.
- `Grep -n "Log\.d\("` returns zero hits in `PlayerFileOperationQueue.kt`.

**Status:** `[x]` done

---

### Step 02.3 — Permission pause/resume API

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperationQueue.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `fun resumeAfterPermission(granted: Boolean, op: PlayerFileOperation)`. On `granted = true`: re-run the same operation (it must complete now that the system permission was granted) — emit `Started` then proceed as a normal execution. On `granted = false`: emit `Failed(op, <not-completed message key resolved by the caller — pass a generic retryable=true here>, retryable = true)` and move on. Either way the consumer loop must continue with the rest of the queue afterwards. The loop stays suspended between `PermissionRequired` and the matching `resumeAfterPermission` call.

**Verification:**

- `Grep` — `fun resumeAfterPermission(` matches once in `PlayerFileOperationQueue.kt`.

**Status:** `[x]` done

---

### Step 02.4 — Cancellation / shutdown hook

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperationQueue.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `fun snapshotPending(): List<PlayerFileOperation>` (for the exit-warning in Phase 05) and `fun shutdown()` (stops accepting new ops; lets the in-flight one finish or cancels the worker per the owning scope's lifecycle). Document that the queue is non-persistent — pending ops are lost on process death by design (strategic non-goal). Add `TODO(phase-02)` nowhere — leave the file clean.

**Verification:**

- `Grep` — `fun snapshotPending(` and `fun shutdown(` each match once.
- `Grep` for `TODO(phase-02)` returns zero hits.
- Project compiles — run `/build`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `PlayerFileOperationQueue.kt` and `PlayerFileOperationEvent.kt`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated; both new classes have `role` + `status` set.

---

## Handoff Notes to Next Phase

The queue is fully self-contained and untested in the running app — Phase 03 wires it into `FileOperationsHandler` and `PlayerManagerInitializer`, replacing the `moveInProgress`/`deleteInProgress` guards and making `onBefore*` removal-by-path fire on **enqueue**, not on success.

---

## Rollback Plan

Revert phase commit(s) — new files only, nothing references them yet.
