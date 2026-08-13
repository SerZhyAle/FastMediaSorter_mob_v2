# Phase 02 — Metadata Error Counter in SmbMediaScanner

**Status:** [x]

## Steps

### 2.1 ScanProgressCallback — add onMetadataErrors

File: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanProgressCallback.kt`

Add a new default method:
```kotlin
/**
 * Called after metadata extraction if any files failed.
 * Default no-op — only implemented where error count matters.
 */
suspend fun onMetadataErrors(errorCount: Int) = Unit
```

**Verification:** File compiles. All existing anonymous objects (`BrowseLoadingManager`, `AudioBackgroundPhotosManager`) still compile without changes.

### 2.2 SmbMediaScanner — add AtomicInteger counter

File: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt`

> File is 747 LOC — create a timestamped backup in `temp/` before editing:
> `Copy-Item SmbMediaScanner.kt temp/SmbMediaScanner_20260428_backup.kt`

**2.2.1** Add import at top:
```kotlin
import java.util.concurrent.atomic.AtomicInteger
```
(check if already imported — `CachedMediaMetadataExtractor` already imports it, but `SmbMediaScanner` may not)

**2.2.2** Add field inside the `SmbMediaScanner` class body (after the existing LruCache fields):
```kotlin
private val _metadataErrorCount = AtomicInteger(0)
```

**2.2.3** In `scanFolderWithProgress()`, at the start of the `is SmbResult.Success ->` branch (before `result.data.mapNotNull`), reset the counter:
```kotlin
_metadataErrorCount.set(0)
```

**2.2.4** In `extractVideoMetadata()`, in the `catch (e: Exception)` block, increment before returning:
```kotlin
_metadataErrorCount.incrementAndGet()
Timber.w(e, "SMB video metadata extraction failed for $remotePath")
null
```

**2.2.5** In `scanFolderWithProgress()`, after the `result.data.mapNotNull { ... }` block completes (i.e., after the `mapNotNull` result is assigned to the local variable or returned), call:
```kotlin
val errorCount = _metadataErrorCount.get()
if (errorCount > 0) {
    progressCallback?.onMetadataErrors(errorCount)
}
```

The call goes between the closing `}` of `mapNotNull` and the returned list. In the existing code structure, `result.data.mapNotNull { ... }` is the last expression of the `is SmbResult.Success ->` branch — wrap it:
```kotlin
is SmbResult.Success -> {
    // ...existing skipMetadataExtraction check...
    _metadataErrorCount.set(0)
    val files = result.data.mapNotNull { fileInfo ->
        // ...existing body unchanged...
    }
    val errCount = _metadataErrorCount.get()
    if (errCount > 0) progressCallback?.onMetadataErrors(errCount)
    files
}
```

**Verification:**
- `grep -n "_metadataErrorCount" SmbMediaScanner.kt` → lines: field decl, set(0), incrementAndGet, get().
- `grep -n "onMetadataErrors" ScanProgressCallback.kt SmbMediaScanner.kt` → declarations in both files.
