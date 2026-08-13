# Phase 01 — Listfiles Attr Contract

**Strategic spec:** [`../S0085_enh-sftp-scan-performance.md`](../S0085_enh-sftp-scan-performance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Add `SftpFileListing` data class to `SftpClient`, change `listFiles()` return type to `Result<List<SftpFileListing>>`, and include file attributes (size, modifiedDate, isDirectory) populated from `LsEntry.attrs` — no extra network requests.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(foundation phase — N/A)*
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 700 |

> `SftpClient.kt` is currently 637 lines — backup required before editing (Step 01.1).

---

## Steps

### Step 01.1 — Backup SftpClient.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `SftpClient.kt` to `temp/SftpClient_backup_<YYYYMMDD_HHMMSS>.kt`. Confirm the backup exists before editing.

**Verification:**

- `Glob` — `temp/SftpClient_backup_*.kt` returns at least one match.

**Status:** `[ ]` not done

---

### Step 01.2 — Add SftpFileListing data class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `SftpClient.kt`, immediately after the existing `SftpFileAttributes` data class declaration (around line 32), add:
>
> ```kotlin
> /**
>  * Lightweight listing entry returned by [listFiles].
>  * Attributes are populated from [ChannelSftp.LsEntry.attrs] — no extra stat() call needed.
>  */
> data class SftpFileListing(
>     val path: String,
>     val size: Long,
>     val isDirectory: Boolean,
>     val modifiedDate: Long  // Unix timestamp in milliseconds (mtime * 1000)
> )
> ```

**Verification:**

- `Grep` — `data class SftpFileListing` returns exactly **one** hit in `SftpClient.kt`.
- `Grep` — `val modifiedDate: Long` returns at least **one** hit in `SftpClient.kt` (the new class).

**Status:** `[ ]` not done

---

### Step 01.3 — Update listFilesSingleLevel to collect SftpFileListing

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `SftpClient.kt`, change `listFilesSingleLevel` signature and body:
>
> Old signature: `private fun listFilesSingleLevel(channel: ChannelSftp, remotePath: String, results: MutableList<String>)`
>
> New signature: `private fun listFilesSingleLevel(channel: ChannelSftp, remotePath: String, results: MutableList<SftpFileListing>, includeDirectories: Boolean = false)`
>
> Replace the body so that for every `LsEntry` (excluding `.` and `..`):
> - Compute `fullPath` as before.
> - If `entry.attrs.isDir && !includeDirectories` → skip (existing behaviour for scan mode).
> - Otherwise: add `SftpFileListing(path = fullPath, size = entry.attrs.size, isDirectory = entry.attrs.isDir, modifiedDate = entry.attrs.mTime.toLong() * 1000L)` to `results`.
>
> `entry.attrs.size` is a `Long`; `entry.attrs.mTime` is an `Int` (seconds since epoch) — multiply by 1000L for milliseconds.

**Verification:**

- `Grep` — `MutableList<SftpFileListing>` returns at least **two** hits in `SftpClient.kt` (both private methods updated).
- `Grep` — `MutableList<String>` returns **zero** hits inside `listFilesSingleLevel` function body in `SftpClient.kt`.
- `Grep` — `includeDirectories` returns at least **one** hit in `SftpClient.kt`.

**Status:** `[ ]` not done

---

### Step 01.4 — Update listFilesRecursive and listFiles() return type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> 1. Change `listFilesRecursive` signature to `private fun listFilesRecursive(channel: ChannelSftp, remotePath: String, results: MutableList<SftpFileListing>)`. In the body, replace `results.add(fullPath)` with `results.add(SftpFileListing(path = fullPath, size = entry.attrs.size, isDirectory = false, modifiedDate = entry.attrs.mTime.toLong() * 1000L))`. Directory entries are still recursed into but not themselves added to results (unchanged logical behaviour).
>
> 2. Change `listFiles()` signature to:
>    ```kotlin
>    suspend fun listFiles(
>        connectionInfo: SftpConnectionInfo,
>        remotePath: String = "/",
>        recursive: Boolean = true,
>        includeDirectories: Boolean = false
>    ): Result<List<SftpFileListing>>
>    ```
>    Inside, change `val allFiles = mutableListOf<String>()` to `val allFiles = mutableListOf<SftpFileListing>()`. Pass `includeDirectories` to `listFilesSingleLevel`. Return type unchanged structurally — just `List<SftpFileListing>` instead of `List<String>`.

**Verification:**

- `Grep` — `Result<List<SftpFileListing>>` returns exactly **one** hit in `SftpClient.kt` (the `listFiles` signature).
- `Grep` — `Result<List<String>>` returns **zero** hits in `SftpClient.kt`.
- `Grep` — `mutableListOf<SftpFileListing>()` returns exactly **one** hit in `SftpClient.kt` (inside `listFiles`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly). Compile errors expected in `SftpMediaScanner.kt` until Phase 02 is applied — confirm they are only in that file and nowhere else.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `SftpClient.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (public API changed).

---

## Handoff Notes to Next Phase

- `SftpClient.listFiles()` now returns `Result<List<SftpFileListing>>` with `size`, `isDirectory`, `modifiedDate` fields populated from the SFTP `ls` response.
- `includeDirectories = false` preserves old scan behaviour; set `true` to get directories too (used by `listDirectoryContents` in Phase 02).
- `SftpMediaScanner.kt` will fail to compile until Phase 02 updates all three call sites. Do not merge Phase 01 alone to a shared branch.

---

## Rollback Plan

Revert phase commit(s). `SftpOperationStrategy` and `SftpDataSource` call `stat()` directly, not `listFiles()` — they are unaffected.
