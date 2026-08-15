# Phase 02 — Scanner Stat Elimination

**Strategic spec:** [`../S0085_enh-sftp-scan-performance.md`](../S0085_enh-sftp-scan-performance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Update all three `stat()` call sites in `SftpMediaScanner` to use `SftpFileListing` attrs from Phase 01, wire `onProgress` into `scanFolder()`, and resolve strategic §6 open items in the spec file.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Project compiles without errors in `SftpClient.kt`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt` | Modified | ≤ 520 |
| `PLAN/S0085_enh-sftp-scan-performance.md` | Modified | — |

> `SftpMediaScanner.kt` is currently 497 lines; edits may push it past 500 — backup before editing (Step 02.1).

---

## Steps

### Step 02.1 — Backup SftpMediaScanner.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `SftpMediaScanner.kt` to `temp/SftpMediaScanner_backup_<YYYYMMDD_HHMMSS>.kt`.

**Verification:**

- `Glob` — `temp/SftpMediaScanner_backup_*.kt` returns at least one match.

**Status:** `[ ]` not done

---

### Step 02.2 — Fix scanFolder() — replace stat() with listing attrs + add onProgress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `SftpMediaScanner.scanFolder()`, the `filesResult.getOrNull()?.mapNotNull { filePath -> ... }` block currently calls `sftpClient.stat(clientInfo, filePath)` for each file (around line 103).
>
> 1. Change the `mapNotNull` lambda parameter from `filePath: String` to `listing: SftpFileListing`. Replace all uses of `filePath` with `listing.path`.
>
> 2. Remove the `stat()` call block entirely. Replace it with:
>    ```kotlin
>    // Use attrs from listFiles() — no extra round-trip needed
>    val fileSize = listing.size
>    val fileDate = listing.modifiedDate
>    // Skip directories (already excluded by listFiles, but guard for safety)
>    if (listing.isDirectory) return@mapNotNull null
>    ```
>    Where `attrs.size`, `attrs.modifiedDate`, and `attrs.isDirectory` were previously used, substitute `fileSize`, `fileDate`, and `listing.isDirectory` respectively.
>    Fallback: if `fileSize <= 0L` and the size filter requires a minimum, call `sftpClient.stat(clientInfo, listing.path)` to get the real size — wrap with the existing null/failure check pattern.
>
> 3. Add progress reporting: after the `mapNotNull` transforms the listing to a `MediaFile`, increment a local counter (`var processedCount = 0`); after every 10 files call:
>    ```kotlin
>    if (processedCount % 10 == 0) onProgress?.onProgress(processedCount)
>    ```
>    Declare `processedCount` before the `mapNotNull` block and increment it inside.
>
> Note: `mapNotNull` is not a suspend lambda in Kotlin, so `onProgress` calls must be moved to a `forEach` loop if `onProgress?.onProgress` requires `suspend`. Restructure the mapping as a `mutableListOf` + `for` loop if needed.

**Verification:**

- `Grep` — `sftpClient.stat(clientInfo, filePath)` inside the `scanFolder` method returns **zero** hits (the unconditional stat call is gone).
- `Grep` — `listing.isDirectory` returns at least **two** hits in `SftpMediaScanner.kt` (scanFolder + listDirectoryContents).
- `Grep` — `onProgress?.onProgress` returns at least **one** hit in `SftpMediaScanner.kt`.
- `Grep` — `Log\.d\(` returns **zero** hits in `SftpMediaScanner.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 — Fix scanFolderPaged() — replace stat() with listing attrs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `SftpMediaScanner.scanFolderPaged()`, apply the same stat-elimination as Step 02.2:
>
> - Change `mapNotNull { filePath ->` to `mapNotNull { listing: SftpFileListing ->`.
> - Remove the `sftpClient.stat(clientInfo, filePath)` block (around line 214).
> - Use `listing.size`, `listing.modifiedDate`, `listing.isDirectory` directly.
> - Same fallback: call `stat()` only if `listing.size <= 0L` and a size minimum is required.
>
> `scanFolderPaged` does not call `onProgress` — leave it without progress reporting (it is used for paginated background counting, not for interactive scans).

**Verification:**

- `Grep` — the string `// Get file attributes via stat()` returns **zero** hits in `SftpMediaScanner.kt` (both comment blocks removed).
- `Grep` — `sftpClient.stat(clientInfo, filePath)` returns **zero** unconditional hits inside both `scanFolder` and `scanFolderPaged` method bodies.

**Status:** `[ ]` not done

---

### Step 02.4 — Fix listDirectoryContents() — use includeDirectories flag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `SftpMediaScanner.listDirectoryContents()` (around line 326):
>
> 1. Change the `listFiles(clientInfo, connectionInfo.remotePath, recursive = false)` call to `listFiles(clientInfo, connectionInfo.remotePath, recursive = false, includeDirectories = true)` — this enables directory entries to appear in the result.
>
> 2. Change `mapNotNull { filePath ->` to `mapNotNull { listing: SftpFileListing ->`.
>
> 3. Remove the `sftpClient.stat(clientInfo, filePath)` block (around line 345). Replace it with:
>    ```kotlin
>    // attrs already available from listing; no stat() call needed
>    if (listing.isDirectory) {
>        // directory branch — same logic as before but use listing.modifiedDate for createdDate
>    } else {
>        // file branch — use listing.size, listing.modifiedDate, listing.path
>    }
>    ```
>    The `childCountResult` block (calls `listFiles` again to count children) must remain intact — that is a separate count call, not a stat call.
>
> 4. Replace `filePath` usages in path-construction (`"sftp://${connectionInfo.host}:${connectionInfo.port}$filePath"`) with `listing.path` (which already contains the full remote path from `SftpFileListing`).

**Verification:**

- `Grep` — `sftpClient.stat(clientInfo, filePath)` returns **zero** hits anywhere in `SftpMediaScanner.kt`.
- `Grep` — `includeDirectories = true` returns exactly **one** hit in `SftpMediaScanner.kt`.
- `Grep` — `listing.isDirectory` returns at least **three** hits (scanFolder, listDirectoryContents `if` branch, and `isDirectory = true` field assignment).

**Status:** `[ ]` not done

---

### Step 02.5 — Resolve strategic §6 items in spec file

**Files:** `PLAN/S0085_enh-sftp-scan-performance.md`
**Depends on:** Step 02.1 *(spec-file update only)*

**Prompt for developer:**

> In `PLAN/S0085_enh-sftp-scan-performance.md` §6, set both research items to `**Статус:** Resolved` and append findings:
>
> §6.1: "Resolved — JSch `channel.ls()` uses SFTP `readdir` (protocol v3); `SftpATTRS` object is always non-null. Guard added: stat() fallback triggered if `listing.size <= 0L` when a size minimum filter is active."
>
> §6.2: "Resolved — `sftpClient.stat()` is called in `SftpOperationStrategy` (7 sites) and `SftpDataSource` (1 site) for transfer/playback — both unchanged. Only 3 call sites in `SftpMediaScanner` were targeted."

**Verification:**

- `Grep` — `**Статус:** Open` returns **zero** hits in `PLAN/S0085_enh-sftp-scan-performance.md`.
- `Grep` — `**Статус:** Resolved` returns **two** hits in `PLAN/S0085_enh-sftp-scan-performance.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- All unconditional `stat()` calls removed from `SftpMediaScanner`; file attributes come from the `ls` response.
- `onProgress` wired in `scanFolder()` — fires every 10 processed files.
- `listDirectoryContents` correctly receives directory entries via `includeDirectories = true`.
- Phase 03 (docs-catalog-cleanup) may start.

---

## Rollback Plan

Revert phase commit(s). `SftpOperationStrategy` and `SftpDataSource` unchanged — no transfer or playback regression.
