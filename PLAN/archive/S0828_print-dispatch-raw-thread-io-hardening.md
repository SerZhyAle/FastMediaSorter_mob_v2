# S0828 - print-dispatch-raw-thread-io-hardening

**Status:** Archived
**Priority:** 35
**Tier:** 2
**Created:** 2026-06-30

## 0. Captured idea (raw)

Found during S0613 (standalone print) research. `PdfPrintDocumentAdapter.onWrite()` in
`PrintDispatchActivity.kt` copies the PDF on a raw `java.lang.Thread` (stored as `copyJob: Thread?`)
and cancels it via `copyJob?.interrupt()` in `onFinish()`. Two concerns:

1. `interrupt()` on a thread blocked in `FileInputStream.read()` only unblocks if the I/O is
   interruptible, which is not guaranteed on all OEMs - the copy thread may keep running after the
   print job is finished/cancelled (wasted work, potential file-handle hold).
2. `copyJob!!` (forced unwrap) at the write path is an NPE smell if `onWrite()` is ever reached before
   `copyJob` is assigned (low risk today since it is assigned before `start()`, but fragile).

Proposed: replace the raw `Thread` with a coroutine (cancellable, structured) or an `ExecutorService`
with proper cancellation, and remove the `!!`. Verify cancellation actually stops the copy.

Evidence: `app_v2/src/main/java/.../PrintDispatchActivity.kt` (onWrite / onFinish, ~L356-388).

## 1. Problem

The PDF-to-spooler copy ran on a raw `Thread`, cancelled only via `Thread.interrupt()` - unreliable
for a thread blocked in `FileInputStream.read()` (blocking-I/O interruptibility is OEM-dependent), so a
cancelled/finished print job could leave the copy running (wasted work, held file handle). The write
path also force-unwrapped `copyJob!!`.

## 2. Goals

1. Cancellation reliably stops the copy on all OEMs.
2. No forced unwrap on the write path.

**Non-goals:**

- Changing the print dispatch flow, share-for-print fallback, or the callback threading contract.

## 3. Notes

- Surfaced alongside S0613 (print core fix) but out of scope of that ticket (S0613 fixed the
  Activity-context IllegalStateException + the destination-panel race, not the copy-thread design).
- Low severity (no confirmed crash), but a real cancellation-correctness + resource concern.

## 4. Acceptance criteria

1. `PdfPrintDocumentAdapter` copies on a structured, cancellable coroutine scope (no raw `Thread`).
2. Cancellation (`onFinish()` / print `CancellationSignal`) closes the source stream, unblocking a
   stuck `read()`.
3. No `copyJob!!` / forced unwrap remains.
4. Project compiles (`compileStandardDebugKotlin`).
5. On-device: printing a large PDF and cancelling mid-copy actually stops the copy (no runaway thread).

## Implementation (2026-07-01, Simple path `/spec-all`)

- `PrintDispatchActivity.kt` `PdfPrintDocumentAdapter`: raw `Thread copyJob` replaced with an owned
  `CoroutineScope(Dispatchers.IO + SupervisorJob())`; the write runs via `adapterScope.launch`.
- `cancellationSignal.setOnCancelListener { cancelActiveCopy() }` + `onFinish()` both close the held
  `activeInput` stream (reliably unblocks a blocked `read()`) and cancel the scope.
- A mid-copy cancel surfaces the closed-stream `IOException` and is reported as `onWriteCancelled()`,
  not `onWriteFailed()`.
- `copyJob!!` removed; buffer size is now a companion `COPY_BUFFER_BYTES` const.
- Compilation: `compileStandardDebugKotlin` - BUILD SUCCESSFUL.
- Criterion 5 (device print-cancel) parked as `BlockNeedUserTest`.
