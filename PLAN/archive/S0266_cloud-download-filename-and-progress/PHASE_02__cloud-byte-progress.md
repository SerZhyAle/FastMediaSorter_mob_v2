# Phase 02 — Cloud Byte Progress

**Strategic spec:** [`../S0266_cloud-download-filename-and-progress.md`](../S0266_cloud-download-filename-and-progress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Wire `ByteProgressCallback` through `CloudFileOperationHandler.downloadFromCloudTo` to the cloud client's `downloadFile(..., progressCallback: (TransferProgress) -> Unit)` channel. The dialog already accepts byte-level events from SMB/FTP; this phase makes cloud sources emit the same kind of events so the progress dialog updates during a download instead of sitting on idle text.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree compiles on `./a.ps1 dq`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/CloudProgressAdapter.kt` | New | ≤ 70 |

---

## Steps

### Step 02.1 — Create `CloudProgressAdapter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/CloudProgressAdapter.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file with a small helper that converts `(TransferProgress) -> Unit` into a closure that also computes `speedBytesPerSecond` locally and forwards into a `ByteProgressCallback`. Signature: `fun adaptCloudProgress(callback: ByteProgressCallback?, scope: CoroutineScope): ((TransferProgress) -> Unit)?`. Returns `null` when `callback` is null. Otherwise returns a lambda that captures `startTimeMs = System.currentTimeMillis()` and on each call launches `scope.launch { callback.onProgress(progress.bytesTransferred, progress.totalBytes, computedSpeed) }` where `computedSpeed = if (elapsedMs > 0) bytesTransferred * 1000 / elapsedMs else 0`. Throttle: only emit when `bytesTransferred - lastEmittedBytes >= ByteProgressCallback.PROGRESS_REPORT_INTERVAL`. Use a thread-safe `AtomicLong` for `lastEmittedBytes`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/CloudProgressAdapter.kt` exists.
- `Grep` — `fun adaptCloudProgress(` matches exactly once.
- `Grep` — `ByteProgressCallback.PROGRESS_REPORT_INTERVAL` present.
- `Grep` — `AtomicLong` present.
- `Grep -n "Log\\.d\\("` — zero hits in the new file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 5/5 PASS. Files: `CloudProgressAdapter.kt` (+37 LOC, new). Dev log recorded.

---

### Step 02.2 — Wire adapter into `downloadFromCloudTo`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `downloadFromCloudTo(cloudPath, destPath, fileName, progressCallback)`, remove `@Suppress("UNUSED_PARAMETER")` from `progressCallback`. Build a local `CoroutineScope` (use `CoroutineScope(Dispatchers.IO)` from the existing `withContext(Dispatchers.IO)` boundary — pass `this` if already inside a coroutine block, or `CoroutineScope(coroutineContext)`). Replace `client.downloadFile(pathInfo.fileId, outputStream, null)` with `client.downloadFile(pathInfo.fileId, outputStream, adaptCloudProgress(progressCallback, scope))`. Add `import com.sza.fastmediasorter.data.transfer.adaptCloudProgress`.

**Verification:**

- `Grep -B2 -A2 "client.downloadFile"` in `CloudFileOperationHandler.kt` — third argument is `adaptCloudProgress(progressCallback, ...)`, not `null`.
- `Grep` — `@Suppress("UNUSED_PARAMETER")` no longer present on `progressCallback` parameter.
- `Grep` — `import com.sza.fastmediasorter.data.transfer.adaptCloudProgress` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 3/3 PASS. Files: `CloudFileOperationHandler.kt` (@Suppress removed on downloadFromCloudTo.progressCallback; downloadFile call passes cloudProgressEmitter; +4 LOC scope setup + import). Note: dead-code `downloadFromCloud` (no caller) intentionally left out of scope. Dev log recorded.

---

### Step 02.3 — Verify `onFileStarted` is emitted before download

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `executeCopy` cloud-source branch (around the existing `Cloud executeCopy: [${index + 1}/${operation.sources.size}] Downloading $fileName` log), insert a call to `progressCallback?.onFileStarted(index + 1, fileName, operation.sources.size)` BEFORE the `downloadFromCloudTo` invocation. Repeat for `executeMove` cloud-source branch (same pattern). Use the *resolved* fileName (display-name) — pass via the existing `fileName` local that's already computed by `extractFileName`.

**Verification:**

- `Grep` — `progressCallback?.onFileStarted(index + 1, fileName, operation.sources.size)` matches at least twice in `CloudFileOperationHandler.kt` (executeCopy + executeMove).
- `Grep -n "Log\\.d\\("` — zero hits in `CloudFileOperationHandler.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 2/2 PASS (note: Log.d zero-hit check N/A — existing `Timber.d` lines are correct; `Log.d` truly zero). Files: `CloudFileOperationHandler.kt` (+4 LOC across executeCopy + executeMove cloud branches). Dev log recorded.

---

### Step 02.4 — Compile gate

**Files:** —
**Depends on:** Step 02.1 .. Step 02.3

**Prompt for developer:**

> Run `./a.ps1 dq`. Treat BUILD SUCCESSFUL as the gate.

**Verification:**

- `Bash` — `./a.ps1 dq` exits 0.
- `Grep` — `BUILD SUCCESSFUL` in build output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 2/2 PASS. BUILD SUCCESSFUL in 55s, version 2.60.5201.222.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `./a.ps1 dq` exits 0.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Cloud downloads now emit `Processing` events through the same `ByteProgressCallback` contract as SMB/SFTP/FTP.
- The progress dialog will receive non-zero `bytesTransferred` and `speedBytesPerSecond` during a real download. Phase 03 ensures the dialog's *initial* visual state isn't a placeholder string.

---

## Rollback Plan

Revert Phase 02 commit(s). No persistent state changed. Cloud downloads return to "silent" mode but filename fix from Phase 01 remains.
