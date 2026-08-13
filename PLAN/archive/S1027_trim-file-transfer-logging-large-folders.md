# S1027 - Trim file-transfer logging noise, especially on large folders

**Status:** Archived

## 0. Owner request (2026-07-13)

"Need to cut down logging, especially when working with large folders."

## 1. Problem

A single large multi-file transfer floods the log with per-file / per-progress / per-call debug
lines. Measured on one Move of 369 files (`logs/fastmediasorter_20260713_001108.log`, after
00:30:32), the repeating transfer-path tags alone:

- `FileOperation.isNetworkPath: ..` - 1481 lines (logged for every source x every protocol probe)
- `AtomicFileOperationStrategy: ..` per-file step lines - 1476 lines
- `FileOperationProgressDialog: Processing ..` - 1225 lines (re-logged on every byte-progress
  callback for the SAME file, not just on file change)
- `SFTP [FILE_OPS] operation failed` full stack traces - 36 x ~18 lines (~650) for an expected
  `permission denied` on the read-only source
- plus `SmbErrorClassifier` connectivity failures that swallow the real cause behind a generic
  "after 3000ms" message

~5000 lines for one operation. This bloats the on-device log file, hurts I/O in the transfer hot
loop, and buries the signal (actual successes/failures) in noise.

## 2. Scope of edits

Trim happy-path / repetitive debug in the transfer hot path; KEEP genuine errors and key events.

- `FileOperationUseCase.executeInternal` - drop the per-call `isNetworkPath` debug line; collapse
  the four near-identical protocol-classification `when`-blocks (each of which also logged one line
  per branch - 16 lines/operation) into a single `hasProtocol` helper plus one per-operation
  classification summary line.
- `FileOperationProgressDialog.applyProgressToUI` - log `Processing <file>` only when the file
  index changes, not on every byte-progress tick.
- `AtomicFileOperationStrategy.copyFile` - remove the per-file step chatter (Source/Destination/
  Temp/Starting/Copy-completed/Renaming/Destination-exists); keep failure/abort logs.
- `SftpConnectionPool.withConnection` - log the concise cause (`e.message`) instead of a full
  `Timber.e(e, ..)` stack trace for every failed op; the caller already classifies + surfaces it.
- `SmbErrorClassifier.checkConnectivity` - log the real exception class+message instead of the
  misleading fixed "after 3000ms" text (also a diagnosis win: distinguishes refused / no-route /
  timeout).

## 3. Non-goals

- Not touching browse/thumbnail logging (separate hot path).
- Not changing transfer behaviour - logging only.

## 4. Related findings (separate tickets)

- [[S1025]] - fail-fast when the destination server is fully unreachable (observed earlier this
  session).
- Move on a delete-forbidden SFTP source surfaces raw `3: permission denied` per file (copy
  succeeds on SMB, source delete is rejected). That is a write-capability / UX concern tied to
  [[S1019]] semantics - the logging trim here stops the stack-trace spam, but a clean
  "copied, originals not removed (no delete permission)" terminal message is a separate follow-up.

## Last Audit

**Date:** 2026-07-13
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

All five §2 edits confirmed in code:
1. `FileOperationUseCase` - `hasProtocol` helper + single per-operation classification summary (16 per-branch lines gone).
2. `FileOperationProgressDialog.applyProgressToUI` - `Processing` logged only on `currentIndex != lastLoggedIndex`.
3. `AtomicFileOperationStrategy.copyFile` - per-file step chatter removed; failure/abort logs kept.
4. `SftpConnectionPool.withConnection` - concise `${e.message}` (`Timber.w`), no per-file full stack.
5. `SmbErrorClassifier.checkConnectivity` - real `${e.javaClass.simpleName}: ${e.message}` instead of fixed "after 3000ms".

No stray `Timber.d("S1027:` probe tags. Logging-only change - EXEMPT from FEATURES.

### Manual / on-device

- [ ] Run a large multi-file Move over SMB/SFTP; confirm log volume dropped and successes/failures still visible.
