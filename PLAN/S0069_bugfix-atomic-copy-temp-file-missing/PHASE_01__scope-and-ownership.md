# Phase 01 — Scope and Ownership

**Strategic spec:** [`../S0069_bugfix-atomic-copy-temp-file-missing.md`](../S0069_bugfix-atomic-copy-temp-file-missing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Freeze the fixed-release scope to the SMB reproducer, identify the active temp-file owner(s), and decide whether S0069 continues as SMB-only or blocks into a follow-up ticket. No behavior change yet.

---

## Prerequisites

- [ ] Working tree is clean or on a branch dedicated to S0069.
- [ ] `logs/fastmediasorter_20260503_180505.log` is available locally.
- [ ] Backups for `SmbOperationStrategy.kt` and `SmbFileOperations.kt` are created before any edit to those files (>500 LOC rule).

---

## Files Read / Potential Touches

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/SmbOperationStrategy.kt.<YYYYMMDD_HHmmss>.backup` | New | n/a |
| `temp/SmbFileOperations.kt.<YYYYMMDD_HHmmss>.backup` | New | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt` | Audit only | 311 current LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt` | Audit only | >500 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt` | Audit only | >500 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt` | Audit only | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Audit only | >500 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Audit only | >500 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupOrphanedTempFilesUseCase.kt` | Audit only | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Audit only | >500 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` | Audit only | n/a |

---

## Steps

### Step 01.1 — Backup large SMB files

**Files:** `SmbOperationStrategy.kt`, `SmbFileOperations.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy the current `SmbOperationStrategy.kt` and `SmbFileOperations.kt` to `temp/` with timestamped `.backup` suffixes before making any edits in later phases. Both files are above the 500-LOC backup threshold.

**Verification:**

- `Glob` — `temp/SmbOperationStrategy.kt.*.backup` returns at least one match.
- `Glob` — `temp/SmbFileOperations.kt.*.backup` returns at least one match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Files: temp/SmbOperationStrategy.kt.20260503_191302.backup, temp/SmbFileOperations.kt.20260503_191302.backup. Journal moved to In Progress.

---

### Step 01.2 — Audit every `AtomicFileOperationStrategy` wrapper

**Files:** `SmbFileOperationHandler.kt`, `SftpFileOperationHandler.kt`, `FtpFileOperationHandler.kt`, `CloudFileOperationHandler.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Run a code audit over every `AtomicFileOperationStrategy(` instantiation. Confirm whether the same atomic wrapper is applied to SMB, SFTP, FTP, Cloud, and Local strategies. Do not change behavior in this step. The output of this audit decides whether S0069 remains SMB-only or must stop into a broader follow-up.

**Verification:**

- `Grep -n "AtomicFileOperationStrategy\(" "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt"` returns at least one hit.
- `Grep -n "AtomicFileOperationStrategy\(" "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt"` returns at least one hit.
- `Grep -n "AtomicFileOperationStrategy\(" "app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt"` returns at least one hit.
- `Grep -n "AtomicFileOperationStrategy\(" "app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt"` returns at least one hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt, app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt, app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt, app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt. Atomic wrapper confirmed across SMB/SFTP/FTP/Cloud/Local.

---

### Step 01.3 — Audit active temp owners and deleters

**Files:** `AtomicFileOperationStrategy.kt`, `SmbOperationStrategy.kt`, `SmbFileOperations.kt`, `CleanupOrphanedTempFilesUseCase.kt`, `UnifiedFileOperationHandler.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Audit the exact temp-file owner chain. Confirm where `TempFileNamingStrategy.getTempPath(destination)` is created, where `cleanupTempFile(tempPath)` runs, whether SMB delegate code deletes any destination-like path, whether orphan cleanup touches `*.temp_copy`, and whether bridge-copy cleanup via `TempFileManager` is on the active S0069 reproducer path. Treat `UnifiedFileOperationHandler` and `CleanupOrphanedTempFilesUseCase` as potential alternative owners until disproved.

**Verification:**

- `Grep -n "TempFileNamingStrategy.getTempPath" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` matches once.
- `Grep -n "cleanupTempFile\(" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` returns at least two hits (call site + method definition).
- `Grep -n "CleanupOrphanedTempFilesUseCase|temp_copy" "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupOrphanedTempFilesUseCase.kt"` returns hits.
- `Grep -n "tempFileManager.cleanupTempFile|createTempFile" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt"` returns hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt, app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupOrphanedTempFilesUseCase.kt, app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt. Active temp path originates in AtomicFileOperationStrategy; orphan cleanup and bridge temp cleanup are adjacent but separate.

---

### Step 01.4 — Resolve stop / continue decision from reproducer boundary

**Files:** `logs/fastmediasorter_20260503_180505.log`
**Depends on:** Step 01.3

**Prompt for developer:**

> Inspect the reproducer cluster around `Temp file doesn't exist after copy!`. Continue only if the adjacent failure chain stays SMB-specific (`Failed to download file from SMB`, SMB cancel path, SMB strategy classes) and the active temp owner remains inside the SMB/local path. If the same cluster proves to be shared with non-SMB delegates or bridge-copy cleanup, stop S0069 here and open a follow-up ticket instead of broadening the fixed-release branch.

**Verification:**

- `Grep -n "Temp file doesn't exist after copy!|Unexpected error during atomic copy|Failed to download file from SMB" "logs/fastmediasorter_20260503_180505.log"` returns at least one hit for each phrase.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 1/1 PASS. Files: logs/fastmediasorter_20260503_180505.log. Continue-path selected: reproducer cluster stays on SMB cancel flow and AtomicFileOperationStrategy cleanup, with no non-SMB evidence in the incident log.

---

## Stop Condition

Stop the spec after Phase 01 if either of the following is true:

- the same `temp-missing-invariant` is proven on SFTP / FTP / Cloud delegates;
- the active deleter is a shared cross-protocol owner (`UnifiedFileOperationHandler`, `TempFileManager`, or a non-SMB wrapper) rather than the SMB/local path.

If stopped: mark INDEX row `⛔ Blocked`, do not start Phase 02, and create a follow-up strategic spec for the broader invariant.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Timestamped backups exist for both >500-LOC SMB files.
- [x] The active temp-owner chain is understood.
- [x] The spec is explicitly classified either `continue SMB-only` or `blocked for broader scope`.

---

## Handoff Notes to Next Phase

If the continue-path is selected, Phase 02 preserves cancellation semantics through the SMB transfer stack first. Do not change the atomic orchestrator until the SMB delegate stops converting cancellation into generic failure.

---

## Rollback Plan

Delete the `temp/*.backup` files if the phase is abandoned. No source behavior changes are made in this phase.
