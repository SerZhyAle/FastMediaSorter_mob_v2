# Phase 01 — Unwrap SftpClient single-op wrappers (Pillar A)

**Strategic spec:** [`../S0219_bugfix-sftp-idle-retry-race.md`](../S0219_bugfix-sftp-idle-retry-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Stop swallowing exceptions inside the lambda body of single-op `SftpClient` wrappers (`listFiles`, `stat`, `mkdir`, `deleteFile`, `deleteDirectory`, `rename`, `uploadFile` (both overloads), `exists`). Let `SftpException` and `IOException` propagate to `SftpConnectionPool.withConnection`, which already implements the dead-transport retry (S0147). Preserve `CancellationException` re-throw (S0205). For `exists`, keep a point-catch only for `SSH_FX_NO_SUCH_FILE` translated to `Result.success(false)`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` is 744 LOC → timestamped backup in `temp/` required before edit (rule §5 of CLAUDE.md).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 760 |
| `temp/SftpClient.kt.2026-05-16__pre-S0219-phase01.bak` | New (backup) | n/a |

> File >500 LOC → backup step is mandatory (Step 01.0).

---

## Steps

### Step 01.0 — Snapshot the file

**Files:** `temp/SftpClient.kt.2026-05-16__pre-S0219-phase01.bak`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` to `temp/SftpClient.kt.2026-05-16__pre-S0219-phase01.bak` byte-for-byte. This is a mandatory pre-edit safeguard for any file >500 LOC.

**Verification:**

- `Glob` — `temp/SftpClient.kt.2026-05-16__pre-S0219-phase01.bak` exists.
- expected: SHA-256 of backup equals SHA-256 of current source | actual: 7F1B836FFB6BF3A5D480A851DD77F85C6AF28470673B45DC53202BFCD4CD47DF on both — match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. Files: temp/SftpClient.kt.2026-05-16__pre-S0219-phase01.bak (new, 744 LOC).

---

### Step 01.1 — Unwrap listFiles

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.0

**Prompt for developer:**

> In `listFiles` (around lines 150-173), remove the broad `catch (e: Exception)` that produces `Result.failure(e)`. Keep the `catch (e: CancellationException) { throw e }` guard. The body becomes: run `listFilesRecursive` or `listFilesSingleLevel`, then return `Result.success(allFiles)`. Any `SftpException`/`IOException` thrown from `channel.ls(...)` propagates out of the lambda — `SftpConnectionPool.withConnection` will catch and retry per S0147. Remove the `Timber.e(e, "SFTP list files failed")` log line as well: the pool already logs the failure at its layer, and double logging will be confusing. Add `Timber.d("S0219: SftpClient.listFiles unwrapped path entered for $remotePath")` at the very start of the lambda (after `withConnection(connectionInfo) { channel ->`) — this is the BlockNeedUserTest probe required by CLAUDE.md "Debug Verification Tags".

**Verification:**

- `Grep` — `path: app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt | pattern: 'Timber\.e\(e, "SFTP list files failed"\)' | -n true` → expected: 0 matches | actual: 0 — PASS.
- `Grep` — `pattern: 'S0219: SftpClient\.listFiles unwrapped path entered' | -n true` → expected: 1 match | actual: 1 — PASS.
- `Grep` — `pattern: 'catch \(e: CancellationException\)' -A 1 | -n true` → expected: still present once inside `listFiles` | actual: present at line 168 (listFiles), plus pre-existing lines 285 and 315 in readFileBytes — PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. Files: SftpClient.kt (≈ −6 LOC: removed broad catch + log line, added S0219 tag + WHY-comment). Dev log: pending end-of-phase (one dev log line per file in this phase).

---

### Step 01.2 — Unwrap stat, mkdir, deleteFile, deleteDirectory, rename

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Apply the same unwrap pattern to `stat` (≈543-560), `mkdir` (≈585-596), `deleteFile` (≈599-610), `deleteDirectory` (≈613-642), and `rename` (≈645-657): remove the broad `catch (e: Exception) { Timber.e(...); Result.failure(e) }`. The body becomes the channel-side call followed by `Result.success(...)`. For `deleteDirectory`, the inner `deleteRecursive(...)` helper itself is fine — it does not wrap exceptions; only the outer try/catch wrapping it needs to go. Add a single tag `Timber.d("S0219: SftpClient.<op> unwrapped path entered for $remotePath")` at the start of each wrapper's lambda — one tag per wrapper.

**Verification:**

- `Grep` — `pattern: 'Timber\.e\(e, "SFTP (stat|mkdir|delete file|delete directory|rename) failed' | -n true` → expected: 0 matches | actual: 0 — PASS.
- `Grep` — `pattern: 'S0219: SftpClient\.(stat\|mkdir\|deleteFile\|deleteDirectory\|rename) unwrapped' | -n true | -o true` → expected: 5 matches | actual: 5 — PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. Files: SftpClient.kt (≈ −15 LOC across five wrappers).

---

### Step 01.3 — Unwrap uploadFile (ByteArray)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `uploadFile(connectionInfo, remotePath, data: ByteArray)` (≈490-510), remove the broad `catch (e: Exception) { Timber.e(...); Result.failure(e) }`. Keep the `ensureDirectoryExists` call and the `data.inputStream().use { channel.put(...) }` block. Add `Timber.d("S0219: SftpClient.uploadFile(bytes) unwrapped path entered for $remotePath")` at the start of the lambda.

**Verification:**

- `Grep` — `pattern: 'Timber\.e\(e, "SFTP upload file failed' | -n true | -o true` → expected: 1 match remaining (in the InputStream overload, removed in next step) | actual: 1 — PASS.
- `Grep` — `pattern: 'S0219: SftpClient\.uploadFile\(bytes\) unwrapped' | -n true` → expected: 1 match | actual: 1 — PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. Files: SftpClient.kt (≈ −3 LOC).

---

### Step 01.4 — Unwrap uploadFile (InputStream)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `uploadFile(connectionInfo, remotePath, inputStream, fileSize, progressCallback)` (≈513-540), remove the broad `catch (e: Exception) { Timber.e(...); Result.failure(e) }`. Keep the `ensureDirectoryExists` + `channel.put(remotePath).use { ... copyTo(...) ... }` block. Add `Timber.d("S0219: SftpClient.uploadFile(stream) unwrapped path entered for $remotePath")` at the start of the lambda.

**Verification:**

- `Grep` — `pattern: 'Timber\.e\(e, "SFTP upload file failed' | -n true | -o true` → expected: 0 matches | actual: 0 — PASS.
- `Grep` — `pattern: 'S0219: SftpClient\.uploadFile\(stream\) unwrapped' | -n true` → expected: 1 match | actual: 1 — PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. Files: SftpClient.kt (≈ −3 LOC).

---

### Step 01.5 — Tighten exists to point-catch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `exists` (≈563-582), replace the outer broad `catch (e: Exception) { Timber.e(...); Result.failure(e) }` so that only `SftpException` with `id == ChannelSftp.SSH_FX_NO_SUCH_FILE` translates to `Result.success(false)`. Any other `SftpException`, any `IOException`, and any other `Exception` must propagate (do not log here, do not wrap). Drop the now-redundant inner try/catch. Resulting structure:
>
> ```kotlin
> suspend fun exists(connectionInfo, remotePath): Result<Boolean> = withConnection(connectionInfo) { channel ->
>     Timber.d("S0219: SftpClient.exists unwrapped path entered for $remotePath")
>     try {
>         channel.stat(remotePath)
>         Result.success(true)
>     } catch (e: SftpException) {
>         if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) Result.success(false) else throw e
>     }
> }
> ```
>
> No other `catch` clauses. CancellationException is not caught here — it propagates as required by S0205.

**Verification:**

- `Grep` — `pattern: 'Timber\.e\(e, "SFTP exists check failed' | -n true` → expected: 0 matches | actual: 0 — PASS.
- `Grep` — `pattern: 'S0219: SftpClient\.exists unwrapped' | -n true` → expected: 1 match | actual: 1 — PASS.
- `Grep` — `pattern: 'SSH_FX_NO_SUCH_FILE' | -n true` → expected: still present inside `exists` | actual: 2 (comment + branch at line 562) — PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. Files: SftpClient.kt (≈ −4 LOC: collapsed double try/catch).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `Grep` for `Timber\.e\(e, "SFTP (list files|stat|mkdir|delete file|delete directory|rename|upload file|exists check) failed` returns zero hits in `SftpClient.kt`.
- [ ] `Grep` for `S0219: SftpClient\.` returns at least 9 matches (one per unwrapped wrapper: listFiles, stat, mkdir, deleteFile, deleteDirectory, rename, uploadFile×2, exists).
- [ ] Dev log entry added for `SftpClient.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 04.

---

## Handoff Notes to Next Phase

After Phase 01, `SftpConnectionPool.withConnection` becomes the single owner of the "wrap-in-Result.failure" decision. The dead-transport retry path activated by `isDeadTransportException("inputstream is closed")` is now reachable from every single-op wrapper — Phase 02 builds on this by ensuring the underlying session is not torn down while a borrower is mid-block, which would still produce the same exception but from a preventable cause.

---

## Rollback Plan

Restore `temp/SftpClient.kt.2026-05-16__pre-S0219-phase01.bak` over `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`. No data migration; no public API change. Revert the dev log entry for the file.
