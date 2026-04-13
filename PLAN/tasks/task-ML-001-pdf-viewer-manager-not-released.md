# TASK ML-001: PdfViewerManager Not Released on Activity Destroy

**Priority**: CRITICAL  
**Area**: Memory / Resource Leak  
**Component**: `PlayerLifecycleManager` + `PdfViewerManager`  
**Effort**: 1h  

---

## Problem

`PdfViewerManager.close()` is **never called** from `PlayerLifecycleManager.onDestroy()`. A misleading comment on line 221 incorrectly states that `PdfViewerManager` does not require explicit cleanup:

```kotlin
// PlayerLifecycleManager.kt:221
// Note: PdfViewerManager and TextViewerManager don't require explicit cleanup  ← WRONG
```

`TextViewerManager` IS already released (line 166), but `PdfViewerManager` is skipped. The `close()` method at `PdfViewerManager.kt:1060` performs critical cleanup:
- Cancels `pageRenderJob` (in-flight render coroutine)
- Calls `closePdfRenderer()` → closes `PdfRenderer` (Android native) + `ParcelFileDescriptor`
- Clears `PdfBitmapCache` and `PdfPageAdapter`
- Recycles `currentPageBitmap`
- Saves page position to Room DB

Every time a user opens and closes a PDF:
- 1 `PdfRenderer` (native memory) is leaked
- 1 `ParcelFileDescriptor` (file handle) is leaked
- 1 render job may remain running
- Bitmaps are not recycled → heap growth

**Files**:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` — L221–224
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` — L1060–1087

---

## Fix

In `PlayerLifecycleManager.kt`, add `PdfViewerManager.close()` call in `onDestroy()`, following the same pattern used for `EpubViewerManager` (lines 207–212):

```kotlin
// Release EpubViewerManager (already exists, lines 207-212)
try {
    activity.epubViewerManager.release()
} catch (e: UninitializedPropertyAccessException) {
    // Not initialized, skip
}

// ADD: Release PdfViewerManager  ← INSERT THIS
try {
    activity.pdfViewerManager.close()
} catch (e: UninitializedPropertyAccessException) {
    // Not initialized, skip
}
```

Also remove or correct the misleading comment on line 221:
```kotlin
// REMOVE: "Note: PdfViewerManager and TextViewerManager don't require explicit cleanup"
// TextViewerManager IS already released (line 166). PdfViewerManager now released above.
```

---

## Test Plan

1. Open a PDF file (any size)
2. Navigate a few pages
3. Press Back to exit `PlayerActivity`
4. Repeat 5 times
5. Check with Android Profiler:
   - Heap: no growing `PdfRenderer` instances
   - File handles: `lsof -p <PID> | grep pdf` — should be 0 after exit
6. Run with LeakCanary enabled: zero leaked `PdfRenderer` or `ParcelFileDescriptor` instances

---

## Acceptance Criteria

- [x] `PdfViewerManager.close()` called in `PlayerLifecycleManager.onDestroy()`
- [x] Misleading comment removed and corrected
- [ ] No `PdfRenderer`/`ParcelFileDescriptor` leak detected by LeakCanary after 5 open/close cycles
- [ ] Heap stable after 3+ PDF open/close sessions

---

## Implementation Status

**Completed**: 2026-04-13 16:35:27  
**Changes**: 
- Added `activity.pdfViewerManager.close()` call in `onDestroy()` (PlayerLifecycleManager.kt:210-213)
- Corrected misleading comment: now accurately states TextViewerManager IS released (line 166)
- Logged to dev/CHANGELOG.md

**Next**: Validation testing (LeakCanary, heap monitoring)
