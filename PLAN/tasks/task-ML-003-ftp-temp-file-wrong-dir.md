# TASK ML-003: FTP Cross-Protocol Copy Uses System Temp Dir

**Priority**: HIGH  
**Area**: Disk / Temp File Leak  
**Component**: `FtpFileOperationHandler`  
**Effort**: 30min  

---

## Problem

`FtpFileOperationHandler.kt:603` calls `File.createTempFile()` **without specifying a directory**, which defaults to the system `/tmp` directory (or equivalent). All other cross-protocol copy operations in the same class correctly use `context.cacheDir`:

```kotlin
// WRONG — FtpFileOperationHandler.kt:603 (FTP → FTP copy)
val tempFile = File.createTempFile("ftp_copy_", ".tmp")  // ← system /tmp, app has no control

// CORRECT — same file, line 809 (FTP ← SFTP bridge)
val tempFile = File.createTempFile("ftp_sftp_copy_", ".tmp", context.cacheDir)

// CORRECT — same file, line 852 (FTP ← SMB bridge)
val tempFile = File.createTempFile("ftp_smb_copy_", ".tmp", context.cacheDir)
```

**Consequences**:
1. Files written to system temp are **outside the app's sandbox** — Android cannot clean them up under storage pressure
2. If the app crashes or is force-killed after download but before cleanup at line 668, the file **persists indefinitely**
3. Inconsistent behavior vs. the other 2 temp file usages in the same class

**File**:
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt` — L603

---

## Fix

Add `context.cacheDir` as the third argument:

```kotlin
// Before (line 603):
val tempFile = File.createTempFile("ftp_copy_", ".tmp")

// After:
val tempFile = File.createTempFile("ftp_copy_", ".tmp", context.cacheDir)
```

Verify that `context` is available in scope at line 603 (it should be — the class is `FtpFileOperationHandler(private val context: Context, ...)`).

---

## Test Plan

1. Perform FTP → FTP copy operation (two different FTP servers)
2. Check that temp file is created in `files-path` storage: `adb shell ls /data/data/com.sza.fastmediasorter/cache/`
3. Interrupt the copy mid-way (kill WiFi)
4. Verify temp file is cleaned up on next app launch (via `TempFileManager.cleanupOldTempFiles()` once ML-008 is fixed)
5. `adb shell find /tmp -name "ftp_copy_*"` should be empty

---

## Acceptance Criteria

- [x] Line 603 uses `context.cacheDir` as third argument
- [x] Temp file is created in `context.cacheDir` (not system `/tmp`)
- [x] Behavior is consistent with lines 809 and 852

---

## Implementation Status

**Completed**: 2026-04-13 16:41:02  
**Changes**:
- Line 603: Changed `File.createTempFile("ftp_copy_", ".tmp")` to `File.createTempFile("ftp_copy_", ".tmp", context.cacheDir)`
- Updated comment to note consistency with copyFtpToSftp and copyFtpToSmb
- Logged to dev/CHANGELOG.md

**Next**: Validation testing (FTP→FTP copy with network interruption, verify cleanup on next launch)
