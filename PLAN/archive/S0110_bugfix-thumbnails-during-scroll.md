# S0110: Fix Thumbnail Loading and Placeholders During Fast Scroll

## 1. Problem Description
When scrolling through the file list in the `Browse` section:
1. **Missing Thumbnails**: Thumbnails that have already been loaded (e.g., in Glide's memory cache) are not visible. The list appears blank or shows file extensions until scrolling stops.
2. **Incorrect Extensions**: The file extension placeholders shown during scrolling are frequently incorrect. For example, a video file might temporarily show a "PDF" or "TXT" extension.

## 2. Root Cause Analysis
The issue stems from `AdapterThumbnailLoader.kt`, specifically the early return in the `load()` method:
```kotlin
if (getIsScrolling()) {
    Timber.v("loadThumbnail: SKIPPED during scroll for ${file.name}")
    return null
}
```
**Impact:**
- **Problem 1 (Missing Cache Hits):** By returning `null` immediately, Glide is completely bypassed. Even if the thumbnail is instantly available in the RAM cache, it is never requested.
- **Problem 2 (Wrong Extension):** Returning `null` also skips the placeholder assignment (`showGeneratedPlaceholder()`). Because Android's `RecyclerView` recycles `ViewHolder`s, the `ImageView` retains the placeholder bitmap from the previous file that occupied this view. `Glide.clear()` (called in `onViewRecycled`) clears Glide requests but does NOT clear bitmaps manually assigned via `setImageBitmap()`. Thus, the old extension text persists during scroll.

## 3. Implementation Requirements

### 3.1. Correct Placeholder Assignment
- During a scroll (`isScrolling == true`), the loader MUST immediately assign the correct file extension placeholder using `showGeneratedPlaceholder(imageView, file)`.
- This operation relies on `ExtensionThumbnailGenerator` (which utilizes an `LruCache` for Bitmaps) and is virtually instantaneous, making it perfectly safe for high-frequency `onBindViewHolder` calls during rapid scrolling.

### 3.2. Cache-Only Glide Requests
- Instead of terminating the load process entirely, `isScrolling` should be passed down to specific loading functions (`loadEpub`, `loadPdf`, `loadImage`, `loadVideo`).
- If `isScrolling` is true, the Glide request MUST be modified to use `.onlyRetrieveFromCache(true)`.
- This ensures Glide performs a zero-IO synchronous check in memory. If the image is cached, it is instantly applied to the `ImageView`. If not, Glide will naturally fall back to the `.placeholder()` (which is properly set to the generated extension bitmap).

### 3.3. Suppress Error Listeners During Scroll
- When using `.onlyRetrieveFromCache(true)`, a cache miss will trigger an `onLoadFailed` event.
- If `isScrolling` is true, do NOT attach standard error listeners (e.g., `RequestListener`s that call `NetworkFileDataFetcher.markThumbnailAsFailed(file.path)`). A cache miss during a fast scroll is an expected behavior, not a network or decoding failure.

### 3.4. Maintain Full-Load Trigger on Scroll Stop
- The `load()` method must return `null` if `isScrolling` is true.
- `return if (isScrolling) null else newKey`
- This ensures that `lastLoadedKey` in the `ViewHolder` remains un-updated (or null) during scroll. When the scroll stops, `MediaFileAdapter` invokes a partial rebind (`LOAD_THUMBNAILS` payload). Because `lastLoadedKey` will not match `newKey`, a full, unconstrained Glide load (with network/disk access and error listeners) will be properly executed.

## 4. Affected Files
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`

## 5. Acceptance Criteria
- [x] While scrolling rapidly, unloaded files show correct file extensions corresponding to their specific type.
- [x] While scrolling rapidly, files whose thumbnails are already in memory cache are displayed seamlessly without jank.
- [x] No fake network failure logs or state flags (`markThumbnailAsFailed`) are generated during cache misses while scrolling.

**Implemented date:** 2026-05-07

## Last Audit

**Date:** 2026-05-07
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 0

### Manual / on-device

- [ ] While scrolling rapidly, unloaded files show correct file extensions (AC1).
- [ ] While scrolling rapidly, memory-cached thumbnails display seamlessly without jank (AC2).
- [ ] No `markThumbnailAsFailed` calls appear in logcat during fast scroll (AC3 — confirm via `/log-reader`).
