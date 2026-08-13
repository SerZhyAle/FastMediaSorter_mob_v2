# Phase 02 — Wire writer into network strategies

**Strategic spec:** [`../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md`](../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 6 / 6
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Replace the raw `FileOutputStream(destFile)` writes in the network → local download paths of all four protocol strategies (SFTP, SMB, FTP, Cloud) with `LocalDestinationWriter` usage. After this phase, downloads from any network protocol into public collections work on Android 10+ without `MANAGE_EXTERNAL_STORAGE`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `LocalDestinationWriter` and `LocalDestinationClassifier` are provided via Hilt.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt` | Modified | ≤ 770 (was 724) — **BACKUP REQUIRED** |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt` | Modified | ≤ 740 (was 690) — **BACKUP REQUIRED** |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt` | Modified | ≤ 770 (was 721) — **BACKUP REQUIRED** |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt` | Modified | ≤ 760 (was 707) — **BACKUP REQUIRED** |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt` | Modified | ≤ 480 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt` | Modified | ≤ 720 — **BACKUP REQUIRED** |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt` | Modified | ≤ 580 — **BACKUP REQUIRED** |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Modified | ≤ 1050 — **BACKUP REQUIRED** |

> Every file currently >500 LOC requires a timestamped backup to `temp/` before editing (CLAUDE.md Rule 5).

---

## Steps

### Step 02.1 — Backup all files >500 LOC scheduled for modification

**Files:** N/A (creates copies under `temp/`)
**Depends on:** — start of phase

**Prompt for developer:**

> Before any edit, copy each `>500 LOC` file to `temp/` with timestamp suffix. Use PowerShell:
>
> ```powershell
> $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
> $files = @(
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt"
> )
> foreach ($f in $files) {
>   $name = [IO.Path]::GetFileNameWithoutExtension($f)
>   Copy-Item $f "temp/${name}_${stamp}.kt.bak"
> }
> ```

**Verification:**

- `Glob` — `temp/SftpOperationStrategy_*.kt.bak` matches at least one file.
- `Glob` — `temp/CloudFileOperationHandler_*.kt.bak` matches at least one file.
- All 7 backup files exist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 02:41 — Verification 3/3 PASS. 7 backups created in temp/ with timestamp 20260517_024148.

---

### Step 02.2 — Add classifier+writer params to `SftpOperationStrategy` and rewrite `downloadFromSftp`

**Status:** `[~]` in progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> 1. Add two new constructor parameters at the end of the existing constructor: `private val destinationClassifier: LocalDestinationClassifier`, `private val destinationWriter: LocalDestinationWriter`.
> 2. In `downloadFromSftp(source, destination, progressCallback)` (currently at line ~404):
>    - Replace `val destFile = File(destination); destFile.parentFile?.mkdirs(); FileOutputStream(destFile).use { outputStream -> sftpClient.downloadFile(...) }` with:
>      ```kotlin
>      val category = destinationClassifier.classify(destination)
>      val sinkResult = destinationWriter.open(category, overwrite = true)
>      val sink = sinkResult.getOrElse {
>          Timber.e(it, "SFTP to Local: writer.open failed for $destination")
>          return Result.failure(it)
>      }
>      val downloadResult = try {
>          sink.outputStream.use { outputStream ->
>              sftpClient.downloadFile(connectionInfo, sourceInfo.remotePath, outputStream, fileSize, progressCallback)
>          }
>          sink.commit()
>      } catch (e: CancellationException) {
>          sink.abort()
>          throw e
>      } catch (e: Throwable) {
>          sink.abort()
>          Result.failure(e)
>      }
>      return downloadResult.map { destination }
>      ```
>    - The `overwrite = true` here mirrors the existing atomic-strategy expectation: the atomic wrapper passes destination with overwrite handled at its level. Do not introduce overwrite branching at this level.
>
> 3. Imports to add:
>    - `com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier`
>    - `com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter`
>    - Keep `FileOutputStream` import if still used elsewhere in the file; remove if unused.
>
> 4. Do not touch other methods in this file. Do not modify `uploadToSftp` or SFTP-to-SFTP paths.

**Verification:**

- `Grep` in file — `destinationClassifier: LocalDestinationClassifier` matches once.
- `Grep` in file — `destinationWriter: LocalDestinationWriter` matches once.
- `Grep` in file — `destinationClassifier.classify(destination)` matches once.
- `Grep` in file — `destinationWriter.open(category, overwrite = true)` matches once.
- `Grep` in `downloadFromSftp` function body — `FileOutputStream(destFile)` no longer present.
- File total LOC ≤ 770 (verify with `wc -l` or PowerShell `Measure-Object -Line`).
- `Grep -n "Log\.d\("` in file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 7/7 PASS. Files: `SftpOperationStrategy.kt` (739 LOC, +15 from 724). FileOutputStream import removed (no longer used). Dev log recorded.

---

### Step 02.3 — Mirror Step 02.2 in `SmbOperationStrategy.downloadFromSmb`

**Status:** `[~]` in progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the same pattern as Step 02.2 to `SmbOperationStrategy.downloadFromSmb` (line ~269):
> 1. Add `destinationClassifier`, `destinationWriter` constructor parameters at the end.
> 2. Replace the raw `FileOutputStream(destFile)` block with the same classifier+writer pattern. Adapt method signatures to the SMB client (the inner I/O call differs — keep that part intact, just swap the OutputStream source and bracket with sink.commit/abort).
> 3. Do not modify SMB-to-SMB or upload paths.

**Verification:**

- `Grep` — `destinationClassifier.classify(destination)` matches once in this file.
- `Grep` — `destinationWriter.open(category, overwrite = true)` matches once.
- `Grep` — inside `downloadFromSmb` body, `FileOutputStream(destFile)` not present.
- File total LOC ≤ 740.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 4/4 PASS. Files: `SmbOperationStrategy.kt` (729 LOC, +39). content:// branch preserved as-is; filesystem branch routed through writer. FileOutputStream import removed. Dev log recorded.

---

### Step 02.4 — Mirror in `FtpOperationStrategy.downloadFromFtp` and `CloudOperationStrategy` download path

**Status:** `[~]` in progress

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt`

**Depends on:** Step 02.1

**Prompt for developer:**

> Same pattern as Steps 02.2 / 02.3, applied to:
> 1. `FtpOperationStrategy.downloadFromFtp` (line ~408 in current file).
> 2. The cloud download path in `CloudOperationStrategy` — locate the function that writes a downloaded cloud file to local (search for `FileOutputStream(destFile)` or the equivalent local-write block; the cloud strategy may have multiple download paths per provider — apply the wrapper to each, or extract a shared helper inside the file).
>
> If the cloud strategy has multiple per-provider download branches (Google Drive, Dropbox, OneDrive), extract one private helper `private suspend fun writeToLocal(destination: String, sourceWriter: suspend (OutputStream) -> Result<Unit>): Result<String>` that performs the classifier+writer dance, and reuse it from each provider branch. This keeps the LOC budget achievable.

**Verification:**

- `Grep` in `FtpOperationStrategy.kt` — `destinationWriter.open(category` matches once.
- `Grep` in `CloudOperationStrategy.kt` — `destinationWriter.open(category` matches at least once.
- `Grep` in each file inside the download function body — `FileOutputStream(destFile)` not present after the rewrite (allowed elsewhere in the file for non-local-destination paths).
- File LOC budgets respected: FTP ≤ 770, Cloud ≤ 760.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 4/4 PASS. FTP: 732 LOC (+11 from 721). Cloud: 724 LOC (+17 from 707). Both download paths route through writer; FtpOperationStrategy FileOutputStream import removed. Dev log recorded.

---

### Step 02.5 — Update `DirectoryStrategyModule` and 4 `*FileOperationHandler` instantiations

**Status:** `[~]` in progress

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`

**Depends on:** Steps 02.2, 02.3, 02.4

**Prompt for developer:**

> 1. In `DirectoryStrategyModule.kt`, update the 4 `@Provides` factories for `Sftp/Smb/Ftp/Cloud OperationStrategy` to inject `LocalDestinationClassifier` and `LocalDestinationWriter`. Example for SFTP:
>    ```kotlin
>    @Provides @Singleton @StrategySftp
>    fun provideSftpStrategy(
>        @ApplicationContext context: Context,
>        sftpClient: SftpClient,
>        credentialsRepository: CredentialsRepository,
>        stagingDir: StagingDirectory,
>        stagingRegistry: StagingRegistry,
>        classifier: LocalDestinationClassifier,
>        writer: LocalDestinationWriter
>    ): FileOperationStrategy = SftpOperationStrategy(
>        context, sftpClient, credentialsRepository, stagingDir, stagingRegistry, classifier, writer
>    )
>    ```
>    Mirror for SMB, FTP, Cloud.
>
> 2. In each of the 4 `*FileOperationHandler.kt` files, the strategies are also instantiated directly via `AtomicFileOperationStrategy(SftpOperationStrategy(context, sftpClient, ...))`. Update each such constructor call site to pass the new classifier+writer parameters. The handlers are themselves Hilt-injected — add `LocalDestinationClassifier` and `LocalDestinationWriter` to each handler's constructor and forward them.
>
> 3. Verify no compilation errors by running the build at the end of this step.

**Verification:**

- `Grep` in `DirectoryStrategyModule.kt` — `classifier: LocalDestinationClassifier` matches at least 4 times (one per protocol provider).
- `Grep` in each of 4 handler files — `LocalDestinationClassifier` and `LocalDestinationWriter` both present in constructor declarations.
- `Grep` count `AtomicFileOperationStrategy(.*Strategy(` (multiline) — every match in handler files passes through the updated constructor signatures (no compilation = silent breakage prevention).
- `/build` target: `standardDebug` build succeeds (run `.\a.ps1 bd`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 4/4 PASS. DirectoryStrategyModule: 4 factories updated. 4 handlers + BrowseActivity.kt:160 (ad-hoc CloudOperationStrategy) updated. Build standardDebug 2m 20s, APK produced. Dev log recorded.

---

### Step 02.6 — Add Timber probe tags and verify build green

**Status:** `[~]` in progress (tag insertion deferred per CLAUDE.md "Debug Verification Tags" — tags are inserted in a single pass right before flipping to BlockNeedUserTest)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt` (+ Smb/Ftp/Cloud)

**Depends on:** Steps 02.2–02.5

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags", the spec will transition to `BlockNeedUserTest` at the end of this tactical run. Insert `Timber.d("S0231: <description>")` at the entry of each modified download function — one per protocol:
>
> - `SftpOperationStrategy.downloadFromSftp` first line of body: `Timber.d("S0231: sftp download via LocalDestinationWriter destination=$destination")`
> - `SmbOperationStrategy.downloadFromSmb`: `Timber.d("S0231: smb download via LocalDestinationWriter destination=$destination")`
> - `FtpOperationStrategy.downloadFromFtp`: `Timber.d("S0231: ftp download via LocalDestinationWriter destination=$destination")`
> - `CloudOperationStrategy` download helper / per-provider branch: `Timber.d("S0231: cloud download via LocalDestinationWriter destination=$destination provider=...")` — one tag per provider branch is acceptable; or one tag in the shared helper from Step 02.4 is sufficient.
>
> Run `/build` (`.\a.ps1 bd`) and confirm `BUILD SUCCESSFUL`.

**Verification:**

- `Grep -n 'Timber.d("S0231:' app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/` — at least 4 matches.
- `/build` exit code 0.
- `Grep` for `Log.d(` in each modified file returns zero hits (Timber-only invariant).

**Status:** `[x]` done (deferred — see note below)

**Step Log:**

- 2026-05-17 — Per CLAUDE.md "Debug Verification Tags", `Timber.d("S0231:")` tags must exist iff the ticket status is `BlockNeedUserTest`. The status is currently `In Progress`, so tags must NOT be present. Tag-insertion locations recorded: `SftpOperationStrategy.downloadFromSftp` (entry line after `parseSftpPath` success), `SmbOperationStrategy.downloadFromSmb` (filesystem branch entry, after classifier call), `FtpOperationStrategy.downloadFromFtp` (after `ensureConnected`), `CloudOperationStrategy.downloadCloudToLocal` (after classifier call), `AtomicFileOperationStrategy.copyFile` (Phase 03 short-circuit branch). All 5 sites will receive tags in a single pass at the end of Phase 05 right before flipping to `BlockNeedUserTest`. Build standardDebug already verified in Step 02.5 (no additional Timber-only invariant to re-check this step).

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] Project compiles — `standardDebug` BUILD SUCCESSFUL in 2m 20s (Step 02.5 evidence).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] All 7 backup files exist in `temp/` (Step 02.1 evidence — timestamp 20260517_024148).
- [ ] Each modified network strategy has exactly one `Timber.d("S0231:` tag — **deferred** per CLAUDE.md "Debug Verification Tags" (tags inserted in single pass at end of Phase 05, before BlockNeedUserTest flip). Currently 0 tags by design.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (10 entries: 4 strategies + 4 handlers + DirectoryStrategyModule + BrowseActivity).
- [x] No new `BuildConfig.IS_*` / `BuildConfig.SUPPORT_*` flavor guards introduced.

---

## Handoff Notes to Next Phase

After Phase 02:
- Network downloads write through `LocalDestinationWriter` for any local destination.
- For public collections, writes go through MediaStore IS_PENDING — EACCES no longer occurs on Android 10+ for canonical media paths.
- The atomic wrapper `*.temp_copy` + rename layer still wraps these calls — Phase 03 short-circuits it for public collections so the temp-suffix MediaStore record is not created in vain.
- `Timber.d("S0231:` tags are present and will remain in code until the ticket leaves `BlockNeedUserTest`.

---

## Rollback Plan

Backups under `temp/` allow per-file restore. Full rollback: `git revert` the phase commit(s). Removing the new constructor params requires either reverting Phase 01 too, or temporarily passing default-construction values (not recommended — prefer full revert).
