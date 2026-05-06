# Phase 02 — retry-with-backoff

**Strategic spec:** [`../S0099_sftp-concurrent-access-fix.md`](../S0099_sftp-concurrent-access-fix.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Replace the single-attempt retry in `SftpClient.downloadFile()` with a 3-attempt loop with exponential backoff (1 s / 2 s / 4 s); introduce `SftpDownloadExhaustedException` to signal exhaustion as a typed exception that Phase 03 can map to user-friendly copy errors.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpDownloadExhaustedException.kt` | New | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 700 |

> `SftpClient.kt` is 668 lines — backup required before edit (Step 02.1).

---

## Steps

### Step 02.1 — Backup `SftpClient.kt`

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
> to `temp/SftpClient_S0099_backup.kt`.

**Verification:**

- `Glob` — `temp/SftpClient_S0099_backup.kt` exists.

**Status:** `[ ]` not done

---

### Step 02.2 — Create `SftpDownloadExhaustedException`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpDownloadExhaustedException.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create a new file at `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpDownloadExhaustedException.kt` with content:
>
> ```kotlin
> package com.sza.fastmediasorter.data.remote.sftp
>
> class SftpDownloadExhaustedException(
>     remotePath: String,
>     cause: Throwable? = null
> ) : Exception("SFTP download exhausted all retries: $remotePath", cause)
> ```

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpDownloadExhaustedException.kt` exists.
- `Grep` — `class SftpDownloadExhaustedException` present in that file.

**Status:** `[ ]` not done

---

### Step 02.3 — Replace `downloadFile()` with 3-attempt backoff loop

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `import kotlinx.coroutines.delay` to the import block of `SftpClient.kt` (after the existing `kotlinx.coroutines` imports).
>
> Replace the entire `downloadFile()` function body (from `// First attempt` through the closing `}` of the function) with a 3-attempt backoff loop. The new function is:
>
> ```kotlin
>     // Download file from SFTP server to OutputStream
>     suspend fun downloadFile(
>         connectionInfo: SftpConnectionInfo,
>         remotePath: String,
>         outputStream: OutputStream,
>         fileSize: Long = 0,
>         progressCallback: ByteProgressCallback? = null
>     ): Result<Unit> {
>         val retryDelaysMs = longArrayOf(1_000, 2_000, 4_000)
>         var lastException: Exception? = null
>
>         for (attempt in 0..retryDelaysMs.size) {
>             if (attempt > 0) {
>                 Timber.d("SFTP [FILE_OPS] download retry $attempt/${retryDelaysMs.size} for $remotePath")
>                 pool.invalidate(connectionInfo)
>                 if (outputStream is java.io.ByteArrayOutputStream) outputStream.reset()
>                 delay(retryDelaysMs[attempt - 1])
>             }
>
>             val result = withConnection(connectionInfo) { channel ->
>                 try {
>                     channel.get(remotePath).use { inputStream ->
>                         if (progressCallback != null && fileSize > 0) {
>                             inputStream.copyToWithProgress(outputStream, fileSize, progressCallback)
>                         } else {
>                             inputStream.copyTo(outputStream)
>                         }
>                     }
>                     Result.success(Unit)
>                 } catch (e: IndexOutOfBoundsException) {
>                     Timber.w("SFTP [FILE_OPS] IndexOutOfBoundsException attempt $attempt: $remotePath")
>                     Result.failure(e)
>                 } catch (e: SftpException) {
>                     if (e.id == ChannelSftp.SSH_FX_FAILURE || e.id == ChannelSftp.SSH_FX_BAD_MESSAGE) {
>                         Timber.w("SFTP [FILE_OPS] SftpException ${e.id} attempt $attempt: $remotePath")
>                         Result.failure(e)
>                     } else {
>                         Timber.e(e, "SFTP [FILE_OPS] download failed: $remotePath")
>                         Result.failure(e)
>                     }
>                 } catch (e: IOException) {
>                     Timber.w("SFTP [FILE_OPS] IOException attempt $attempt: $remotePath — ${e.message}")
>                     Result.failure(e)
>                 } catch (e: Exception) {
>                     Timber.e(e, "SFTP [FILE_OPS] download failed: $remotePath")
>                     Result.failure(e)
>                 }
>             }
>
>             if (result.isSuccess) return result
>
>             val ex = result.exceptionOrNull()
>             val retriable = ex is IndexOutOfBoundsException ||
>                 (ex is SftpException && (ex.id == ChannelSftp.SSH_FX_FAILURE || ex.id == ChannelSftp.SSH_FX_BAD_MESSAGE)) ||
>                 ex is IOException
>             if (!retriable) return result
>             lastException = ex as? Exception ?: Exception(ex?.message)
>         }
>
>         Timber.e("SFTP [FILE_OPS] download exhausted all retries: $remotePath")
>         return Result.failure(SftpDownloadExhaustedException(remotePath, lastException))
>     }
> ```
>
> The old function body between `// First attempt` and the final closing brace of `downloadFile()` must be fully replaced — do not leave any trace of the old single-retry logic.

**Verification:**

- `Grep` — `import kotlinx.coroutines.delay` present in `SftpClient.kt`.
- `Grep` — `retryDelaysMs` present in `SftpClient.kt`.
- `Grep` — `SftpDownloadExhaustedException` present in `SftpClient.kt`.
- `Grep` — `// First attempt` — 0 matches in `SftpClient.kt` (old marker removed).
- `Grep` — `SFTP \[FILE_OPS\] download exhausted` present in `SftpClient.kt`.

**Status:** `[ ]` not done

---

### Step 02.4 — Build

**Files:** —
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `/build` (debug, any flavor). Build must succeed with zero errors.

**Verification:**

- Build exits with code 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Project compiles — `/build` exits 0.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for both new/modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`SftpClient.downloadFile()` now retries up to 3 times with backoff and throws `SftpDownloadExhaustedException` on exhaustion. Phase 03 maps that exception to user-facing string resources.

---

## Rollback Plan

Revert phase commits. Restore `SftpClient.kt` from `temp/SftpClient_S0099_backup.kt`. Delete `SftpDownloadExhaustedException.kt`.
