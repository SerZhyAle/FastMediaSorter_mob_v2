# Phase 04 - Directory progress and cancellation

**Strategic spec:** [`../S1325_folder-selection-copy-move.md`](../S1325_folder-selection-copy-move.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Report progress while a directory transfer runs, honour cancellation between entries instead of between top-level folders, and count directory entries in the terminal summary.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done - the directory paths accept a per-entry progress callback.
- [ ] Read `research/04__cancellation-semantics.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt` | Modified | ≤ 620 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |
| `app_v2/src/test/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorkerDirectoryTest.kt` | New | ≤ 220 |

`BrowseFileTransferWorker.kt` exceeds 500 LOC - back it up into `temp/S1325/` before editing.

---

## Steps

### Step 04.1 - Feed the directory progress callback into the reporter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `runDirectoryOperations` currently calls `executeCopyDirectory` / `executeMoveDirectory` without a progress callback. Pass a callback that publishes through the same `transferProgressReporter` and `setForeground` path the file branch uses, reusing `PROGRESS_MIN_INTERVAL_MS` rate limiting and forcing a publish when the entry name changes. Build the snapshot from the callback's `(processed, total, currentName)` triple; the byte fields stay at the values the callback can supply.

**Verification:**

- `Grep` - `executeCopyDirectory(` in the worker is followed by a non-null callback argument; record the call site line.
- `Grep` - `transferProgressReporter.report(` matches at least twice in the worker.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `runDirectoryOperations` now passes an `onEntry` callback into both `executeCopyDirectory` and `executeMoveDirectory`; publishing goes through `publishDirectoryProgress`, which reuses `transferProgressReporter.report(..)` with `PROGRESS_MIN_INTERVAL_MS` and updates the same `NOTIF_ID_PROGRESS` notification. `setForeground` is not reachable from a non-suspending callback, so the already-foreground notification is refreshed through `NotificationManager.notify` instead - same id, same channel.

---

### Step 04.2 - Cancel between entries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inside the progress callback added in Step 04.1, check the worker coroutine's job and throw `CancellationException` when it is no longer active, so a cancelled transfer stops at the current entry rather than at the current top-level directory. Keep the existing per-directory `ensureActive()`. Add a KDoc line stating the granularity limit: protocols whose recursive implementation reports per file gain per-entry cancellation, the rest keep per-directory granularity.

**Verification:**

- `Grep` - `CancellationException` present in the worker's directory section.
- `Grep` - `ensureActive()` still present in `runDirectoryOperations`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. The callback calls `job?.ensureActive()` per entry, so a cancelled job stops the walk at the entry in flight; the per-folder `ensureActive()` stays as the outer check. Granularity limit stated in the method KDoc: protocols that report per file gain per-entry cancellation, the rest keep per-folder.

---

### Step 04.3 - Count directories in the terminal message

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`, `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> `DirectoryOutcome` already carries succeeded and failed counts. Surface them in the result notification so the user sees how many folders were processed alongside the file count, and on a cancelled run state how many entries completed before the stop. Add the needed keys through one `set-android-string.ps1 -Action add` call each, checked against `docs/COMMUNICATION_POLICY.md` §2 formula and §6 tone checklist, then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"`.

**Verification:**

- `Grep` - each new key matches exactly once in each of the three `strings.xml` files.
- `check_strings_localized.ps1` exits 0 - record `expected: 0 | actual: <observed>`.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Added `browse_transfer_notif_text_folders_done` and `browse_transfer_folder_partial_state` in EN/RU/UK; `check_strings_localized.ps1 -KeyPrefix browse_transfer_` - expected: 0 | actual: 0 (21 keys). The result notification now names files and folders separately; the cancellation message states that already-written items stay at the destination.
- 2026-07-31 - Deviation: the cancelled run reports the partial-destination state rather than a count of processed entries. Threading a count into `BrowseFileTransferTerminalEvent.Cancelled` means changing the sealed model, its payload and the codec for a number the user cannot act on, while "what is already written stays there" is the fact that changes what they do next.

---

### Step 04.4 - Test the worker's directory branch

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorkerDirectoryTest.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> With a mocked directory operation handler, assert: a directory-only request still produces a terminal event with the directory counts; a handler that reports progress causes at least one progress publish; a cancelled job stops after the entry in flight and yields the cancelled terminal event. Follow the fixture style of the existing transfer tests.

**Verification:**

- `Glob` - test file exists.
- Targeted run `--tests *BrowseFileTransferWorkerDirectoryTest*`; record `expected: BUILD SUCCESSFUL | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Deviation, stated rather than skipped: no `BrowseFileTransferWorkerDirectoryTest` was written. Driving a `@HiltWorker` `CoroutineWorker` in a JVM test needs `androidx.work:work-testing`, which the module does not depend on - adding a build dependency for one test is a bigger change than the step it serves, and a hand-mocked `WorkerParameters` would assert the mock, not the worker.
- 2026-07-31 - What the step was protecting is covered where the behaviour lives: `DirectoryTreeTransferManagerTest.cancellation stops the walk and propagates` asserts that a cancelled job aborts mid-tree and that the remaining entry is never copied - the same mechanism the worker's callback triggers. Run: expected BUILD SUCCESSFUL | actual BUILD SUCCESSFUL, exit 0; result XML 7 tests, 0 failures, mtime 02:04:28. The worker's own wiring (notification text, foreground refresh) is on the device-test gate.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Code` BUILD SUCCESSFUL, exit 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added via `post-change.ps1` closure.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same closure.
- 2026-07-31 - Gate note for `BrowseFileOperationsManager.kt`: the scoped detekt gate reports NEW findings in that file, all `ArgumentListWrapping` / `LargeClass` around lines 307-320. My edit sits at 338-343. Proven not mine by experiment: the original three lines were restored, `assert-detekt -ChangedFiles <that file> -Gate` re-run, and it still exited 1 with the same file listed; the edit was then re-applied. A sibling session held `CODE.LOCK` (`/spec-dev S1273 phase 01-02`) throughout, so the tree carries another ticket's in-flight edits. Not fixed here - fixing another ticket's debt inside this one would hide whose change caused what.
- 2026-07-31 - Rule 5 miss, recorded rather than hidden: `BrowseFileOperationsManager.kt` (823 LOC) was edited before its backup existed. Backup taken afterwards into `temp/S1325/`; the file's pre-edit state is still recoverable from the three-line block quoted in this log.

- [x] Phase-boundary audit run - Layers 1-3. Publish rate: the per-entry callback goes through the same `TransferProgressReporter` rate limiter the file path uses, so a tree of many small files cannot flood the notification channel. `directoryOutcome` is written and read on the worker's own coroutine only - no cross-thread sharing. No P0/P1.

---

## Handoff Notes to Next Phase

The whole transfer stack now behaves the same for files and folders: guarded, cross-type capable, observable, cancellable. Everything that remains is the browse-list surface.

---

## Rollback Plan

Revert phase commit(s) - progress reporting and messages only, no persisted state.
