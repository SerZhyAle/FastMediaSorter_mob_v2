# Phase 01 — Bounded-read helper

**Strategic spec:** [`../S0206_ftp-true-partial-read.md`](../S0206_ftp-true-partial-read.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Add a package-internal helper in the FTP wrapper subpackage that reads at most N bytes from an `InputStream` opened via `FTPClient.retrieveFileStream`, then invokes ABOR + `completePendingCommand` to close the data channel cleanly. The helper encapsulates the buffer-size decision, the abort policy, and the success-with-data fallback for non-positive ABOR responses. No call sites are migrated in this phase — that is Phase 02.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none for this phase).
- [ ] Strategic §6 research items are resolved-by-design as recorded in INDEX.md Pre-Implementation Blockers.
- [ ] Working tree is clean or on a feature branch.
- [ ] `FtpCommandUtils.kt` already exists at `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpCommandUtils.kt` (created by the prior NPE-defensive fix).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpCommandUtils.kt` | Modified | ≤ 120 |

> File is currently 30 lines, projected ~80–110 after this phase — under the 500-line backup threshold.

---

## Steps

### Step 01.1 — Add buffer-size constant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpCommandUtils.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a package-internal constant `FTP_BOUNDED_READ_BUFFER_BYTES = 8 * 1024` at the top of the file (after the package + imports, before the existing `safeCompletePendingCommand` function). Add a one-line KDoc summarising the rationale: «Read-loop buffer size for bounded FTP reads. Picked as the smallest power-of-two that keeps syscall count low while limiting overrun past the requested cap to at most one buffer.» This constant is referenced by Step 01.3.

**Verification:**

- `Grep` — `internal const val FTP_BOUNDED_READ_BUFFER_BYTES = 8 \* 1024` matches exactly once in `FtpCommandUtils.kt`.
- `Grep` — the constant declaration is preceded by a `/**`-style KDoc block (multiline KDoc immediately above the `internal const val` line).

**Status:** `[x]` done — 2026-05-15

---

### Step 01.2 — Define bounded-read result type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpCommandUtils.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add an `internal` data class `FtpBoundedReadResult(val bytes: ByteArray, val abortInvoked: Boolean, val completeOk: Boolean)` in the same file. `abortInvoked` records whether the cap was reached and ABOR was sent; `completeOk` records whether the subsequent `completePendingCommand` returned positive. Both flags are diagnostic only — callers treat any return value as success at the byte-array level. Add KDoc explaining each field in one sentence each.

**Verification:**

- `Grep` — `internal data class FtpBoundedReadResult\(` matches exactly once in `FtpCommandUtils.kt`.
- `Grep` — `val bytes: ByteArray`, `val abortInvoked: Boolean`, `val completeOk: Boolean` each match exactly once in `FtpCommandUtils.kt`.

**Status:** `[x]` done — 2026-05-15

---

### Step 01.3 — Implement `readBoundedAndAbort` helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpCommandUtils.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Add an `internal` function `readBoundedAndAbort(client: FTPClient, stream: InputStream, maxBytes: Int, label: String): FtpBoundedReadResult`. Behaviour:
>
> 1. Read from `stream` into a `ByteArrayOutputStream` using a `FTP_BOUNDED_READ_BUFFER_BYTES`-sized read buffer.
> 2. Stop reading as soon as the cumulative count reaches `maxBytes` (truncate the last buffer write to exactly `maxBytes`) **or** when `stream.read(..) == -1` (natural EOF before cap).
> 3. After exiting the loop, if the cap was reached (cumulative >= `maxBytes` and stream not at EOF), call `client.abort()` and set `abortInvoked = true`. If natural EOF happened first, do not invoke ABOR. Set `abortInvoked = false`.
> 4. Then call `safeCompletePendingCommand(client, label)` (the existing helper in this file). On `IOException` thrown by it: catch the exception, set `completeOk = false`, log a single-line `Timber.w` mentioning `label` and that the channel was already closed by peer. On normal return: `completeOk = <returned-Boolean>`.
> 5. Return `FtpBoundedReadResult(bytes = baos.toByteArray(), abortInvoked = …, completeOk = …)`. **Never** throw out of this function on the ABOR-or-complete legs — bytes already collected always reach the caller. Read-loop `IOException` propagates normally (caller handles).
> 6. Add KDoc explaining: (a) why ABOR-then-complete is the canonical sequence per the protocol, (b) why we tolerate non-positive completion as success-with-data when ABOR was invoked deliberately.
>
> Required imports: `org.apache.commons.net.ftp.FTPClient`, `java.io.ByteArrayOutputStream`, `java.io.InputStream`, `timber.log.Timber`. Add any missing imports — do not remove the existing `IOException` import.

**Verification:**

- `Grep` — `internal fun readBoundedAndAbort\(` matches exactly once in `FtpCommandUtils.kt`.
- `Grep` — `client.abort\(\)` matches exactly once in `FtpCommandUtils.kt`.
- `Grep` — `safeCompletePendingCommand\(client, label\)` matches at least once inside the new helper.
- `Grep` — `ByteArrayOutputStream` import or fully qualified usage is present in `FtpCommandUtils.kt`.
- Module compiles: run `/build standardDebug compileKotlin` (or equivalent) and confirm exit code 0.

**Status:** `[x]` done — 2026-05-15

---

### Step 01.4 — Smoke-build the new helper in isolation

**Files:** —
**Depends on:** Step 01.3

**Prompt for developer:**

> No source edits — verify that the new helper compiles under both `standardDebug` and one of the legacy/lite variants, and that no other source file references it yet (Phase 02 wiring is intentionally absent). Run `/build` to compile the `standardDebug` Kotlin source set and grep the project for callers.

**Verification:**

- `/build` succeeds for `:app_v2:compileStandardDebugKotlin` (exit 0).
- `Grep` — `readBoundedAndAbort\(` matches exactly once in `app_v2/src/main/java/` (declaration only; no callers yet). Expected: 1, Actual: must equal 1.
- `Grep` — `FtpBoundedReadResult` matches exactly inside `FtpCommandUtils.kt` (declaration + return-type usage in helper) — confirm no leakage outside the file. Expected: declarations only, Actual: only inside `FtpCommandUtils.kt`.

**Status:** `[x]` done — 2026-05-15

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `standardDebug` BUILD SUCCESSFUL (50s, 2026-05-15).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `FtpCommandUtils.kt` (2026-05-15).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1056 records, 2026-05-15 02:25).

---

## Handoff Notes to Next Phase

- Phase 02 may rely on `readBoundedAndAbort` being a no-throw helper for the ABOR + complete legs (read-loop `IOException` still propagates).
- Buffer size and abort policy are fixed via the constant and helper — Phase 02 callers do not re-decide either.
- `FtpBoundedReadResult.abortInvoked` and `completeOk` are diagnostic-only — Phase 02 does not branch on them; bytes are returned as success regardless.

---

## Rollback Plan

Revert the single commit that adds the helper + constant + result type in `FtpCommandUtils.kt`. No call sites depend on the new helper at the end of Phase 01, so removal is mechanically safe.
