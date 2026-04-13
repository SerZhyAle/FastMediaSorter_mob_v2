# TASK ML-007: TempFileManager.cleanupOldTempFiles() Never Called on App Startup

**Priority**: HIGH  
**Area**: Disk / Temp File Accumulation  
**Component**: `TempFileManager`, `FastMediaSorterApp`  
**Effort**: 1h  

---

## Problem

`TempFileManager` provides two cleanup methods that are **never called**:

```kotlin
// TempFileManager.kt:88–105 — exists but not invoked anywhere
fun cleanupAllTempFiles(): Int { ... }    // Deletes all tracked temp files
fun cleanupOldTempFiles(maxAgeMs: Long = 24 * 60 * 60 * 1000): Int { ... }  // By age
```

`FastMediaSorterApp.onCreate()` already performs some cleanup on startup:
```kotlin
// FastMediaSorterApp.kt:125–129
applicationScope.launch(Dispatchers.IO) {
    NetworkFileDataFetcher.clearFailedVideoCache()  // ✅ clears failed video cache
}
TranslationCacheManager.clearAll()  // ✅ clears in-memory translation cache

// ❌ MISSING: tempFileManager.cleanupOldTempFiles()
```

**What accumulates**: If the app is force-killed during a cross-protocol file transfer, the `finally` block that deletes temp files **does not run**. These orphaned `.tmp` files in `context.cacheDir` have **no cleanup mechanism** and persist until the user manually clears app storage.

Scenario: User copies 50 large files (100MB each) from SMB → FTP over a week, app crashes on 10 of them → **1GB of orphaned temp files**.

**Files**:
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/TempFileManager.kt` — L88–105
- `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` — L125–129

---

## Fix

### 1. Call `cleanupOldTempFiles()` on app startup

In `FastMediaSorterApp.onCreate()`, add alongside existing cleanup:

```kotlin
applicationScope.launch(Dispatchers.IO) {
    NetworkFileDataFetcher.clearFailedVideoCache()
    tempFileManager.cleanupOldTempFiles(maxAgeMs = 24 * 60 * 60 * 1000L) // 24h max age
    Timber.i("App startup: cleaned up old temp files")
}
```

`TempFileManager` should be injectable via Hilt — confirm it's available in `FastMediaSorterApp`.

### 2. Add `onTrimMemory` hook for emergency cleanup

Override `onTrimMemory` in `FastMediaSorterApp` to release temp files under memory pressure:

```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
        applicationScope.launch(Dispatchers.IO) {
            tempFileManager.cleanupAllTempFiles()
            Timber.i("onTrimMemory(COMPLETE): All temp files cleaned up")
        }
    }
}
```

### 3. Verify cleanup also scans for untracked orphans

Ensure `cleanupOldTempFiles()` does not only delete files tracked in its own `tempFiles` map but also scans `context.cacheDir` for files matching the temp naming patterns (`*_copy_*.tmp`, `bridge_*`, `audio_meta_*.tmp`) older than `maxAgeMs`.

---

## Test Plan

1. Manually create orphaned temp files in `cacheDir`: `adb shell touch /data/data/com.sza.fastmediasorter/cache/ftp_copy_test.tmp`
2. Set file mtime to >24h ago: `adb shell touch -t 202401010000.00 ...`
3. Force-close and reopen app
4. Verify file is deleted: `adb shell ls /data/data/com.sza.fastmediasorter/cache/`
5. Logcat should show: "App startup: cleaned up old temp files"

---

## Acceptance Criteria

- [x] `tempFileManager.cleanupOldTempFiles()` called in `FastMediaSorterApp.onCreate()`
- [x] Cleanup runs on `Dispatchers.IO` (not blocking main thread)
- [x] `onTrimMemory(TRIM_MEMORY_COMPLETE)` triggers `cleanupAllTempFiles()`
- [x] `cleanupOldTempFiles()` scans actual `cacheDir` directory for all temp file patterns
- [x] Verified by code review: orphaned temp files older than 24h are removed on startup

## Implementation Status

**✅ COMPLETED** — 2026-04-13 16:57:46

### Changes Applied

**FastMediaSorterApp.kt**:

1. **Line 75-76**: Added TempFileManager injection
   ```kotlin
   @Inject
   lateinit var tempFileManager: com.sza.fastmediasorter.domain.transfer.TempFileManager
   ```

2. **Lines 128-132**: Added startup cleanup in onCreate() on Dispatchers.IO
   ```kotlin
   // Clean up orphaned temp files (ML-007) — handles crashes that prevented cleanup
   applicationScope.launch(Dispatchers.IO) {
       tempFileManager.cleanupOldTempFiles(24 * 60 * 60 * 1000L) // 24 hours max age
       Timber.d("App startup: cleaned up old orphaned temp files")
   }
   ```

3. **Lines 346-351**: Added emergency cleanup in onTrimMemory(COMPLETE)
   ```kotlin
   // Clean up all temp files under critical memory pressure (ML-007)
   applicationScope.launch(Dispatchers.IO) {
       tempFileManager.cleanupAllTempFiles()
       Timber.i("onTrimMemory(COMPLETE): All temp files cleaned up")
   }
   ```

**TempFileManager.kt**:

1. **Lines 111-144**: Enhanced cleanupOldTempFiles() to scan all temp file patterns used in app
   ```kotlin
   val tempPrefixes = listOf(
       "temp_",              // TempFileManager tracked files
       "ftp_copy_",          // FtpFileOperationHandler cross-protocol copies
       "ftp_sftp_copy_",     // FtpFileOperationHandler SFTP copies
       "bridge_",            // Bridge files (SMB/FTP/SFTP protocols)
       "audio_meta_"         // AudioMetadataLoader metadata extraction
   )
   
   // Check all files in cache directory
   context.cacheDir.listFiles()?.forEach { file ->
       if (tempPrefixes.any { file.name.startsWith(it) }) {
           val age = now - file.lastModified()
           if (age > maxAgeMs) { /* delete */ }
       }
   }
   ```

### Test Results

✅ Both files compile error-free
✅ All changes logged to dev/CHANGELOG.md at 16:57:46

### Coverage

**Temp File Patterns Now Scanned** (not just TempFileManager-tracked):
- `temp_*` — TempFileManager tracked files ✅
- `ftp_copy_*.tmp` — FTP→FTP cross-protocol ✅
- `ftp_sftp_copy_*.tmp` — FTP↔SFTP transfers ✅
- `bridge_*` — SMB/FTP/SFTP intermediate files ✅
- `audio_meta_*.tmp` — Audio metadata extraction ✅

### Benefits

- **Crash recovery**: Orphaned temp files from app crashes are cleaned up on startup
- **Memory pressure**: All temp files released when system needs memory (TRIM_MEMORY_COMPLETE)
- **Comprehensive**: Scans all temp file patterns across the app, not just TempFileManager tracked files
- **Safe**: Exceptions caught, doesn't crash if delete fails
- **Logged**: Detailed Timber output for debugging accumulation issues

### Scenarios Addressed

1. **Crash during 100MB SMB→FTP copy** → 5-10 orphaned `bridge_*` files cleaned on next startup
2. **Force-kill during SFTP streaming** → `ftp_sftp_copy_*.tmp` recovered  
3. **System memory pressure** → All temp files released immediately via TRIM_MEMORY_COMPLETE
4. **Audio metadata extraction interrupted** → `audio_meta_*.tmp` files scanned and cleaned
