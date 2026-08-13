# S1021 - Browse background Move transfer silently stalls before reaching any transfer handler

**Status:** Archived

## 0. Raw report (owner, 2026-07-12)

Owner moved 369 files (SFTP source -> SMB destination) via the Browse toolbar Move action.
Files did not appear to move. Log analysis of `logs/fastmediasorter_20260712_221021.log`
requested to confirm before filing.

## 1. Symptom

Move triggered from Browse toolbar for a large (369-file) cross-protocol transfer
(sftp://193.178.50.43:64048/Downloads -> smb://192.168.1.112/down/_iN) enqueues correctly via
WorkManager, the use case starts, and then produces zero further observable activity - no
protocol-classification log, no handler-entry log, no progress, no notification update, no
exception - for the remainder of the captured session.

## 2. Evidence from log analysis

Source: `logs/fastmediasorter_20260712_221021.log`, 2026-07-12 22:22:38-22:23:00.

- 22:22:39.901 `BrowseFileTransferCoordinator: enqueued workId=e25ac8a8-d308-4ef5-9fee-377344fa84ec`
  - WorkManager unique work `browse_file_transfer_interactive` accepted the request (not the
    `ActiveAlreadyRunning` short-circuit path).
- 22:22:39.978 `SLog: [e6a0a008|file-operation] START executeWithProgress`
- 22:22:39.980 `FileOperation: Starting operation: Move` - first line of
  `FileOperationUseCase.executeInternal`.
- No further line for correlation id `e6a0a008` appears anywhere in the rest of the log. Every
  other correlation-tracked operation in the same session (`get-media-files` x4,
  `update-resource` x2, `add-multiple-resources` x2) reaches a terminal `SUCCESS`/`COMPLETE`
  `SLog` line within milliseconds to a few seconds - this is the only one that does not.
- The very next lines of code after `Starting operation: Move` are pure in-memory string-prefix
  checks over the 369 source paths (`FileOperation.isNetworkPath`, `FileOperation.Move: sources=..`
  debug logs) with no I/O and no suspension point. None of these lines appear in the log either -
  zero occurrences of `FileOperation.` (with a following dot) anywhere in the file.
- `SmbFileOperationHandler.executeMove` logs an unconditional `Timber.i` ENTRY line
  (`SmbFileOperationHandler.executeMove: ENTRY - sources=..`). This line - and every other line
  from that handler (`SMB executeMove: ..`, `moveBridgeToSmb`, etc.) - is entirely absent from the
  log. The handler this operation should dispatch to (mixed SMB<->SFTP, destination is SMB) was
  never entered.
- No exception was logged. `executeInternal` wraps its whole body in `try/catch(Exception)` with
  `StructuredLogger.e(e, "EXCEPTION in executeInternal")` on any throw; that line never appears.
  `BrowseFileTransferWorker.doWork()`'s `catch (cancelled: CancellationException)` path does not
  call Timber at all today, so a silent cancellation there would also be invisible.
- Concurrent app activity in the same window (thumbnail loads, folder navigation) kept logging
  normally, ruling out a global logging/process freeze - the stall is specific to this one
  coroutine.
- User backed out of BrowseActivity (`onDestroy` 22:22:57.340) about 17s after the Move started;
  `BrowseShutdownCoordinator.onShutdown` cancelled `ConnectionThrottleManager` state for
  `sftp://193.178.50.43:64048` at that point. `BrowseFileTransferWorker` runs as a foreground-service
  `CoroutineWorker` under a WorkManager unique-work name, architecturally independent of
  `BrowseViewModel`/`BrowseActivity` lifecycle, so this should not be the cause - but it is a
  candidate worth ruling out with direct evidence rather than assumption.
- Log capture ends at 22:23:00, ~20s after the Move started - too short a window to know whether a
  369-file, multi-GB cross-protocol transfer would legitimately still be running; the anomaly is not
  "it did not finish in 20s", it is "not one further line of a purely synchronous, no-I/O code path
  was ever logged".

## 3. Current hypothesis

The `Move` coroutine (launched via `launch(Dispatchers.IO)` inside
`FileOperationUseCase.executeWithProgress`) stalls or is silently cancelled somewhere between
logging `FileOperation: Starting operation: Move` and dispatching to
`SmbFileOperationHandler.executeMove`/`SftpFileOperationHandler.executeMove` - i.e. during the
protocol-classification `when` blocks or the handler-dispatch `when` block in `executeInternal`
(`domain/usecase/FileOperationUseCase.kt`, roughly lines 215-372). Not confirmed:

- genuine hang inside the classification loop (no known blocking call there today);
- silent `CancellationException` swallowed before the outer catch logs it;
- `ConnectionThrottleManager` semaphore contention for `sftp://193.178.50.43:64048` shared with
  concurrent thumbnail loads on the same resource.

Existing code already has generous non-ticket `Timber.d` logging across this exact path that
*should* have fired and did not - adding more of the same logging would not add information. The
probes below bisect the path at coroutine-builder and branch boundaries the current logging does
not cover, so the next repro pinpoints the last checkpoint reached.

## 4. Debug instrumentation added (S1021 probes, BlockNeedUserTest only)

- `FileOperationUseCase.kt` - `executeWithProgress`, immediately before
  `launch(Dispatchers.IO) { .. }`: confirms the parent flow body ran to the coroutine-builder call.
- `FileOperationUseCase.kt` - `executeInternal`, immediately after entering the `try` block, before
  protocol classification starts: confirms the IO-dispatched child coroutine resumed past its first
  (already-logged) line into the classification code.
- `FileOperationUseCase.kt` - `executeInternal`, immediately before the handler-dispatch `when`
  (after `hasSmbPath`/`hasSftpPath`/`hasFtpPath`/`hasCloudPath` are all computed): confirms
  classification completed, with the four flag values.
- `FileOperationUseCase.kt` - `executeInternal`, inside the `hasSmbPath && hasSftpPath` branch,
  immediately before each of the SMB-handler and SFTP-handler `executeMove` calls: confirms which
  handler was chosen and that dispatch was reached.
- `BrowseFileTransferWorker.kt` - `doWork()`, inside `catch (cancelled: CancellationException)`,
  at entry: makes an otherwise-silent cancellation path visible.

## 5. Reproduction steps for the user

- Install the debug build with this instrumentation.
- Repeat the same kind of transfer that failed (large multi-file Move, ideally cross-protocol
  SFTP/SMB/FTP source-destination like the original).
- Let the device sit long enough to observe whether a progress notification ever appears, or
  capture logcat/log continuously through the transfer instead of stopping shortly after start.
- Send back the resulting log file (or `logs/current.log`) - the last `S1021:` line that fired
  pinpoints exactly where execution stopped.

## 6. Second repro (device, `logs/fastmediasorter_20260712_230207.log`, 2026-07-12 23:02-23:03)

Owner retried the Move twice in this session (369 files at 23:02:48, then 390 files at 23:03:11,
~25s later - a manual retry, consistent with the first attempt appearing to do nothing).

- Both attempts: probe `S1021: launch transfer coroutine ..` and
  `S1021: executeInternal entered try block ..` fire. Probe
  `S1021: classified smb=.. sftp=.. ftp=.. cloud=..` (the very next debug line, right after
  computing all four protocol flags) does **not** fire in either attempt.
- This narrows the stall to inside computing `hasSmbPath` specifically (the first of the four
  `when` blocks) - before even the first per-source `FileOperation.isNetworkPath` debug line of
  the existing (non-ticket) logging fires. Two independent reproductions land in the exact same
  narrow window.
- `executeInternal`'s surrounding `try` only has `catch (e: Exception)` - this does **not** catch
  `Error` (e.g. `OutOfMemoryError`, `StackOverflowError`); such a `Throwable` would escape both
  the use case's catch and the Worker's `catch (cancelled: CancellationException)`, propagate out
  of `doWork()` uncaught, and be reported by WorkManager's own internal (non-Timber) logger - which
  this app's Timber file logger cannot see. Heap looked low at the moment of both attempts
  (`MEM_ENDURANCE .. heapUsed=33MB heapMax=512MB`), which argues against `OutOfMemoryError`
  specifically, but does not rule out some other `Error`.
- Alternative hypothesis worth ruling out before assuming a real hang: the classification loop
  calls up to 2 `Timber.d` per source (~740-780 calls for 369-390 files) in a tight synchronous
  loop with no delay between them - if the on-device burst-rate log throttling silently drops
  messages in a dense burst, both the loop's own debug lines AND the `S1021: classified ..` probe
  immediately following it could be dropped even if the code actually ran to completion and
  proceeded into the handler. This would make the symptom purely a logging-visibility gap, not a
  functional break - falsifiable by whether a "Moving files.." progress notification was ever
  observed on-device and whether any files actually landed at the destination, independent of what
  the log shows.

## 7. Owner confirmation (2026-07-12, chat)

No progress notification appeared during either attempt, and no files landed in `down_in`.
Thumbnails of the source files still load and open fine (read path unaffected). This rules out the
logging-throttling explanation - the stall is a genuine functional break, not a log-visibility gap.
This is a core workflow for the owner ("this is literally what I'm here for").

## 8. Fix applied (widen catch to Throwable)

`catch (e: Exception)` cannot see an `Error` (`OutOfMemoryError`, `StackOverflowError`, ..) - such a
`Throwable` would escape `executeInternal` silently, then the Worker's `CancellationException`-only
catch, then `doWork()` itself uncaught, and be swallowed by WorkManager's own internal (non-Timber)
failure logging - invisible to this app's log file. Added:

- `FileOperationUseCase.executeInternal` - a second catch clause, `catch (t: Throwable)`, after the
  existing `catch (e: Exception)`, logging via `Timber.d(t, "S1021: Throwable escaped
  executeInternal (not an Exception)")` (S1021 probes are `Timber.d`-only by convention) and
  returning a `Failure` result instead of disappearing.
- `BrowseFileTransferWorker.doWork()` - a backstop `catch (t: Throwable)` after the
  `CancellationException` catch, logging via `Timber.d(t, "S1021: BrowseFileTransferWorker.doWork
  caught unexpected Throwable")` and returning `Result.failure()`, in case something escapes even
  the use case's own new catch.

Next repro should either reach the `S1021: classified ..` probe (proving the earlier freeze is
gone) or produce one of these two new `Timber.e` lines with a real stack trace - either result
narrows the cause further.

## 9. ROOT CAUSE FOUND (`logs/fastmediasorter_20260712_232150.log`, 2026-07-12 23:35)

Third repro (two Copy attempts, n=4 and n=10 files) produced the decisive line the widened catch
now surfaces:

```
E/App: Throwable escaped FileOperationUseCase.executeInternal
java.lang.StackOverflowError: stack size 4108KB
    at ..BrowseFileTransferWorker$toFile$1.getPath(BrowseFileTransferWorker.kt:298)
    at ..BrowseFileTransferWorker$toFile$1.getPath(BrowseFileTransferWorker.kt:298)
    ... (repeated to stack exhaustion)
```

Infinite self-recursion in the anonymous `File` subclass built by
`BrowseFileTransferWorker.toFile()` for a network source:

```kotlin
private fun BrowseFileTransferSource.toFile(): File = when {
    ..
    path.startsWith("smb://") || .. -> object : File(path) {
        override fun getPath(): String = path        // <-- BUG
        override fun getAbsolutePath(): String = path // <-- BUG
        ..
    }
}
```

Inside the `object : File(..)` body the bare name `path` binds to the `File.getPath()` member being
overridden (the object's own implicit receiver), NOT to the extension receiver
`BrowseFileTransferSource.path`. So `getPath()` returns `getPath()` - unbounded recursion. Same for
`getAbsolutePath()`. `getName()`/`length()` were safe because `displayName`/`size` are not `File`
members and resolve to the receiver.

Why it was invisible until now: `StackOverflowError` is an `Error`, not an `Exception`, so
`executeInternal`'s `catch (e: Exception)` never caught it; it escaped silently into WorkManager's
own logging (§2, §8). The widened `catch (Throwable)` (§8) is exactly what made it visible.

Why the classification probe never fired (§6): `executeInternal`'s first act after the try-block
probe is `source.isNetworkPath("smb")`, which reads `this.path` - triggering the recursive
`getPath()` before the `S1021: classified ..` probe. Two repros landing in that exact window
(§6) match perfectly.

## 10. FIX

`BrowseFileTransferWorker.toFile()` - capture the receiver's `path`/`displayName`/`size` into
locals (`sourcePath`/`sourceName`/`sourceSize`) before the `object : File(..)` so the overrides
return the locals instead of the colliding `File.getPath()` member. The destination-side anonymous
`File` in `toFileOperation()` was already safe (it returns a local `destinationPath`, no collision).

Kept as permanent (non-probe) hardening from §8: the `catch (Throwable)` clauses in
`executeInternal` and `doWork()` plus their `Timber.e(t, ..)` lines - a future `Error` on this path
will now be logged instead of vanishing.

## 12. FIX CONFIRMED on device (`logs/fastmediasorter_20260713_001108.log`, 2026-07-13 00:11)

Copy of 15 files (SFTP source -> `smb://192.168.1.112/down/_iN`) with the fixed build:

- `S1021: classified smb=true sftp=true ftp=false cloud=false` - classification now COMPLETES
  (the exact probe that never fired before).
- `S1021: dispatching Mixed SMB<->SFTP move to SMB handler` - dispatch reached.
- Real transfer runs: `SmbFileOperationHandler: Bridging copy ..`, `AtomicFileOperationStrategy`
  downloads file 1 from SFTP into a temp file (`Bridge copy: Downloaded 76882 bytes ..`), progress
  dialog advances `Processing 1/15 .. 4/15`.
- NO `StackOverflowError` anywhere in the log. The S1021 recursion bug is fixed.

The transfer then failed for a DIFFERENT, non-code reason: the destination SMB host
`192.168.1.112:445` was unreachable this session - `Fast connectivity check failed to
192.168.1.112:445 after 3000ms` / `SMB connection failed: Server unreachable` on every file. After
4 files stuck retrying the dead host, the owner pressed Back (`CLICK: Back` 00:11:48), cancelling
the worker (the two `S1021: .. caught CancellationException` lines are this user cancel, not a code
fault).

Conclusion: the StackOverflow fix is proven. A full end-to-end success (files actually landing at
the destination) was NOT observed only because the target SMB server was down. Ticket stays
BlockNeedUserTest pending one clean run against a reachable destination; then -> Verified and remove
the S1021 probes.

## 13. Follow-up parked (not part of this fix)

On a fully-unreachable destination server the operation retries the TCP precheck per-file
(~15 x ~9s here) instead of aborting the whole batch after the first `Server unreachable`. A
fail-fast-on-dead-destination improvement is a separate UX ticket - parked as its own draft.

## 11. Same-pattern findings elsewhere (to park, not fixed here)

`rg "override fun getPath\(\): String = path"` finds 8 more `object : File(path)` sites with the
identical override shape (BrowseFileOperationsManager x3, BrowseDeleteManager, BrowseRenameDialogManager,
BrowseShareOperationsHelper, DeleteDialog, DeleteByFileSizeUseCase, PlayerFileOperation). There the
`path` is a local/lambda-param rather than an extension-receiver property, but the same
inner-implicit-receiver shadowing rule applies, so each is a recursion suspect that needs its
name-resolution and live-path status verified. Out of scope for this ticket - parked separately.
