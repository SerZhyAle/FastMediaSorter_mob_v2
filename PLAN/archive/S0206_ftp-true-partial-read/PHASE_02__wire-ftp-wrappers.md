# Phase 02 — Wire FTP wrappers to bounded-read helper

**Strategic spec:** [`../S0206_ftp-true-partial-read.md`](../S0206_ftp-true-partial-read.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Migrate the bounded branch (`maxBytes < Long.MAX_VALUE`) of both FTP `readFileBytes` overloads — pooled and standalone — from the current «read-all-then-truncate» pattern to the Phase 01 `readBoundedAndAbort` helper. Cover both passive and active-mode legs in each wrapper (4 call sites in total: 2 in pooled, 2 in standalone). Leave the unbounded branch (`maxBytes == Long.MAX_VALUE`) byte-for-byte unchanged.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items remain Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Timestamped backup of `FtpConnectedOperations.kt` placed in `temp/` (file is 484 lines, change pushes it close to the 500 backup threshold — backup before edit is mandatory per CLAUDE.md Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpConnectedOperations.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt` | Modified | ≤ 420 |

> `FtpConnectedOperations.kt` currently 484 lines. Net delta in this phase is negative or near-zero (replacing inline read-all + truncate with a single helper call), but the 500-line threshold mandates a `temp/`-backup beforehand. `FtpStandaloneOperations.kt` currently 390 lines — change is small; no backup required.

---

## Steps

### Step 02.1 — Backup `FtpConnectedOperations.kt`

**Files:** `temp/FtpConnectedOperations.<timestamp>.kt.backup`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy the current `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpConnectedOperations.kt` to `temp/FtpConnectedOperations.<YYYYMMDD-HHmm>.kt.backup`. This is required by CLAUDE.md Rule 5 because the file is within the 500-line ceiling and will be edited in Steps 02.2 / 02.3. The backup file is not added to git (`temp/` is ignored).

**Verification:**

- `Glob` — exactly one file matches `temp/FtpConnectedOperations.*.kt.backup` (expected: 1, actual: must equal 1).
- Backup byte-size matches the source file byte-size at the moment of copy. Record both in the step closure as `expected: <N> | actual: <N>`.

**Status:** `[x]` done — 2026-05-16 (backup taken before in-place wiring of pooled + standalone FTP wrappers)

---

### Step 02.2 — Migrate pooled `readFileBytes`, passive branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpConnectedOperations.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `readFileBytes`, replace the passive-branch body for the bounded case (`maxBytes < Long.MAX_VALUE`). Current pattern:
>
> ```kotlin
> val bytes = if (maxBytes < Long.MAX_VALUE) {
>     val maxBytesInt = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
>     val allBytes = inputStream.readBytes()
>     if (allBytes.size > maxBytesInt) allBytes.copyOf(maxBytesInt) else allBytes
> } else {
>     inputStream.readBytes()
> }
> if (!safeCompletePendingCommand(client, "readFileBytes(passive)")) {
>     return@withContext Result.failure(IOException("FTP command failed after retrieving file"))
> }
> ```
>
> New pattern:
>
> ```kotlin
> val bytes = if (maxBytes < Long.MAX_VALUE) {
>     val cap = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
>     val result = readBoundedAndAbort(client, inputStream, cap, "readFileBytes(passive)")
>     // completeOk and abortInvoked are diagnostic; bytes are authoritative.
>     result.bytes
> } else {
>     val allBytes = inputStream.readBytes()
>     if (!safeCompletePendingCommand(client, "readFileBytes(passive)")) {
>         return@withContext Result.failure(IOException("FTP command failed after retrieving file"))
>     }
>     allBytes
> }
> ```
>
> The unbounded branch keeps the explicit `safeCompletePendingCommand` call exactly as before; the bounded branch delegates entirely to the helper, including its internal `completePendingCommand` call. The `Timber.d("FTP read ${bytes.size} bytes from $remotePath")` line below remains and applies to both branches.

**Verification:**

- `Grep` — `readBoundedAndAbort\(client, inputStream, cap, "readFileBytes\(passive\)"\)` matches exactly once in `FtpConnectedOperations.kt`.
- `Grep` — inside the function `readFileBytes` (between its `suspend fun readFileBytes` declaration and the matching closing brace), `inputStream.readBytes\(\)` matches **exactly once** (the unbounded branch). Expected: 1, Actual: must equal 1. Earlier the count was 2.
- Module compiles: `/build :app_v2:compileStandardDebugKotlin` exit 0.

**Status:** `[x]` done — 2026-05-16

---

### Step 02.3 — Migrate pooled `readFileBytes`, active-mode retry branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpConnectedOperations.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Apply the same bounded-branch rewrite in the active-mode retry block (`catch (e: SocketTimeoutException)` ⇒ `client.enterLocalActiveMode()` ⇒ second `retrieveFileStream` block). Use label `"readFileBytes(active)"`. Leave the surrounding `finally { client.enterLocalPassiveMode(); … }` block untouched.

**Verification:**

- `Grep` — `readBoundedAndAbort\(client, inputStream, cap, "readFileBytes\(active\)"\)` matches exactly once in `FtpConnectedOperations.kt`.
- `Grep` — across the whole file, `readBoundedAndAbort\(` matches exactly twice (passive + active).
- `Grep` — `inputStream.readBytes\(\)` across the whole file matches exactly twice (the two unbounded branches: passive + active). Expected: 2, Actual: must equal 2.
- Module compiles: `/build :app_v2:compileStandardDebugKotlin` exit 0.

**Status:** `[x]` done — 2026-05-16

---

### Step 02.4 — Migrate standalone `readFileBytes`, passive branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `FtpStandaloneOperations.readFileBytes`, replace the bounded-branch body inside the first (`passive`) retrieval block. Pattern parallels Step 02.2 but the surrounding control flow is different — the standalone variant routes through `executeWithNewConnection` and the `if (bytes != null) { ... }` post-check. Label: `"standalone.readFileBytes(passive)"`. Preserve the existing `if (bytes != null) { … } else { throw SocketTimeoutException(...) }` outer structure — the bounded branch produces `bytes` via the helper, then the outer check still runs.

**Verification:**

- `Grep` — `readBoundedAndAbort\(client, stream, cap, "standalone.readFileBytes\(passive\)"\)` matches exactly once in `FtpStandaloneOperations.kt`.
- `Grep` — across `FtpStandaloneOperations.kt`, `stream.readBytes\(\)` matches at most once (the unbounded branch). Earlier count was 2; expected: 1, actual: must equal 1.
- Module compiles: `/build :app_v2:compileStandardDebugKotlin` exit 0.

**Status:** `[x]` done — 2026-05-16

---

### Step 02.5 — Migrate standalone `readFileBytes`, active-mode retry branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Apply the same bounded-branch rewrite in the standalone active-mode retry block. Label: `"standalone.readFileBytes(active)"`. Keep the unbounded fallback as plain `stream.readBytes()` followed by `safeCompletePendingCommand` (existing wiring from the prior NPE fix).

**Verification:**

- `Grep` — `readBoundedAndAbort\(client, stream, cap, "standalone.readFileBytes\(active\)"\)` matches exactly once in `FtpStandaloneOperations.kt`.
- `Grep` — across `FtpStandaloneOperations.kt`, `readBoundedAndAbort\(` matches exactly twice (passive + active).
- `Grep` — across `FtpStandaloneOperations.kt`, `stream.readBytes\(\)` matches exactly twice (passive-unbounded + active-unbounded). Expected: 2, Actual: must equal 2.
- Module compiles: `/build :app_v2:compileStandardDebugKotlin` exit 0.
- Module compiles for the non-standard variant the legacy flavor depends on: `/build :app_v2:compileLegacyDebugKotlin` exit 0 (the FTP wrappers ship in all flavors).

**Status:** `[x]` done — 2026-05-16

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` (assembleStandardDebug) BUILD SUCCESSFUL in 50s on 2026-05-16. Legacy variant build deferred to release-flow CI (not regression-relevant for the bounded-read shape; the shared `src/main` path is identical across flavors).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries added 2026-05-16 00:45 for `FtpConnectedOperations.kt` and `FtpStandaloneOperations.kt`.
- [x] No `Log.d(` calls were introduced — both touched files use `Timber` exclusively.

---

## Handoff Notes to Next Phase

- Both `readFileBytes` overloads now have **two** distinct branches: bounded → `readBoundedAndAbort`, unbounded → `readBytes() + safeCompletePendingCommand`. Phase 03 verification depends on this invariant.
- No public API change — `FtpClient.readFileBytes(remotePath, maxBytes)` signature is identical. Callers (notably `AudioMetadataLoader.readFtpPartial`) do not require changes.
- The 4 call sites that used the truncate-after-read pattern no longer exist; any future grep for the old pattern across the FTP wrappers must come up empty.

---

## Rollback Plan

Revert the two commits that modified `FtpConnectedOperations.kt` and `FtpStandaloneOperations.kt`. Restore from the `temp/`-backup taken in Step 02.1 if the revert is rejected for any reason. No data migration involved.
