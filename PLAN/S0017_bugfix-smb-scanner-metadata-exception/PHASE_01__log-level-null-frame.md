# Phase 01 — Log Level Fix + Null-Frame Handling

**Status:** [x]

## Steps

### 1.1 SmbMediaScanner — raise log level on metadata exception

File: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt`

In `extractVideoMetadata()`, the `catch (e: Exception)` block at the end:
```kotlin
// BEFORE:
Timber.v(e, "SMB video metadata extraction failed for $remotePath")
// AFTER:
Timber.w(e, "SMB video metadata extraction failed for $remotePath")
```

Leave the `SmbResult.Error` branch `Timber.v(...)` at line ~709 unchanged (that is a network-level skip, not an API exception).

**Verification:** `grep -n "Timber\.v.*extraction failed" SmbMediaScanner.kt` → 0 results.

### 1.2 NetworkVideoFrameDecoder — avoid redundant second getFrameAtTime call

File: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt`

In `extractVideoFrame()`, around line 224, replace:
```kotlin
retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    ?: retriever.getFrameAtTime(0) // Fallback to first frame
```
with:
```kotlin
val frame = retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
if (frame != null) return@submit frame
Timber.w("getFrameAtTime returned null for ${path.substringAfterLast('/')}, skipping fallback")
null
```

The `return@submit` label refers to the `submit<Bitmap?>` lambda. No second `getFrameAtTime` call.

**Verification:** `grep -n "getFrameAtTime(0)" NetworkVideoFrameDecoder.kt` → 0 results.
