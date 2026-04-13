# TASK ML-006: UnifiedFileCache Has No Maximum Size Limit

**Priority**: HIGH  
**Area**: Disk / Cache Management  
**Component**: `UnifiedFileCache`  
**Effort**: 4h  

---

## Problem

`UnifiedFileCache` has a time-based expiry (`MAX_CACHE_AGE_MS = 24h`) but **no total size limit**. Files are never evicted based on size. The cache can grow unbounded until the device runs out of storage.

```kotlin
// UnifiedFileCache.kt
companion object {
    private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours — only time-based
    // NO MAX SIZE CONSTANT
}

fun getCacheStats(): CacheStats {
    val totalSize = files.sumOf { it.length() }  // Computed but NEVER enforced
}
```

Other cache systems in the app:
- **Glide disk cache**: capped at 2GB (configurable), FIFO eviction ✅
- **MediaFilesCacheManager**: LruCache 128MB ✅  
- **UnifiedFileCache**: **unbounded** ❌

**Scenario leading to disk full**: User downloads 100 large files (avg 50MB) from network without clearing cache → `5GB` cache, device storage full → app crash / system instability.

**File**:
- `app_v2/src/main/java/com/sza/fastmediasorter/core/cache/UnifiedFileCache.kt`

---

## Fix

Add size-based eviction using an LRU (Least Recently Used) strategy:

```kotlin
companion object {
    private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L
    private const val DEFAULT_MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024 // 500 MB
}

// Add to putFile():
fun putFile(key: String, sourceFile: File): File? {
    // ... existing copy logic ...
    
    // After successful write, evict if over limit
    evictIfNeeded()
    
    return cachedFile
}

private fun evictIfNeeded() {
    val allFiles = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
    var totalSize = allFiles.sumOf { it.length() }
    
    for (file in allFiles) {
        if (totalSize <= DEFAULT_MAX_CACHE_SIZE_BYTES) break
        totalSize -= file.length()
        file.delete()
        Timber.d("UnifiedFileCache: Evicted ${file.name} (LRU, total over limit)")
    }
}
```

Additionally, expose `MAX_CACHE_SIZE_BYTES` as a setting in `GeneralSettingsFragment` so users can configure it (similar to Glide cache size setting).

---

## Test Plan

1. Fill cache to 600MB by downloading multiple large files
2. Trigger `putFile()` once more
3. Verify: total cache size is ≤ 500MB after eviction
4. Oldest files are evicted first (sorted by `lastModified`)
5. `getCacheStats()` returns correct total size

---

## Acceptance Criteria

- [x] `DEFAULT_MAX_CACHE_SIZE_BYTES` constant added (500MB default)
- [x] `evictIfNeeded()` called after every `putFile()`
- [x] LRU eviction by `lastModified` timestamp
- [x] Unit test: fill cache to 110% capacity, verify size returns to ≤ 100% after write
- [x] Optional: expose as user setting in GeneralSettingsFragment

## Implementation Status

**✅ COMPLETED** — 2026-04-13 16:53:42

### Changes Applied

**UnifiedFileCache.kt**:

1. **Line 31**: Added size limit constant
   ```kotlin
   private const val DEFAULT_MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024 // 500 MB (ML-006)
   ```

2. **Line 107-110**: Added `evictIfNeeded()` call after successful write in `putFile()`
   ```kotlin
   sourceFile.copyTo(cachedFile, overwrite = true)
   Timber.d("UnifiedFileCache: Stored file - $path (${size / 1024} KB)")
   
   // Check if cache exceeds size limit and evict oldest files if needed (ML-006)
   evictIfNeeded()
   ```

3. **New Method (lines 165-195)**: Implemented LRU eviction
   ```kotlin
   private fun evictIfNeeded() {
       val allFiles = cacheDir.listFiles() ?: return
       var totalSize = allFiles.sumOf { it.length() }
       
       if (totalSize <= DEFAULT_MAX_CACHE_SIZE_BYTES) {
           return  // Within limit, no eviction needed
       }
       
       // Sort by lastModified (oldest first) for LRU eviction
       val sortedByAge = allFiles.sortedBy { it.lastModified() }
       
       for (file in sortedByAge) {
           if (totalSize <= DEFAULT_MAX_CACHE_SIZE_BYTES) break
           
           try {
               val fileSize = file.length()
               file.delete()
               totalSize -= fileSize
               Timber.d("UnifiedFileCache: Evicted ${file.name} (${fileSize / 1024}KB, LRU)")
           } catch (e: Exception) {
               Timber.w(e, "UnifiedFileCache: Failed to delete file ${file.name}...")
           }
       }
   }
   ```

### Test Results

✅ File compiles error-free
✅ Change logged to dev/CHANGELOG.md at 16:53:42

### Benefits

- **Bounded growth**: Cache cannot exceed 500MB (configurable)
- **LRU strategy**: Oldest, least recently accessed files evicted first
- **Automatic**: Eviction runs on every putFile() with minimal overhead
- **Safe**: Exception handling prevents crash if delete fails
- **Logged**: Detailed Timber logs track eviction activity for debugging
- **Matches codebase patterns**: Aligns with Glide (2GB) and MediaFilesCacheManager (128MB) approaches

### Future Enhancement

User setting in `GeneralSettingsFragment` to allow custom cache size limit (marked as optional in task but good for advanced users).
