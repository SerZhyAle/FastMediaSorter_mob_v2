# UI Features Specification

**Status:** Planned  
**Priority:** Medium  
**Related TODOs:** 5 open TODOs for UI improvements  
**Last Validated:** 2026-02-17 (codebase check)

---

## Overview

This specification covers several planned UI features that have TODO placeholders in the codebase. These are nice-to-have enhancements that improve user experience but are not critical for core functionality.

## Validation Snapshot (2026-02-17)

All items in this document are still актуально: all referenced TODO placeholders are present in source and no full implementation was found.

| Feature | Current Code State | Validation |
|---------|--------------------|------------|
| Multiple File Rename | Placeholder method + toast only | TODO present in `RenameDialog.kt` |
| Gesture Hint Overlay | Pref flag write + no overlay UI | TODO present in `PlayerGestureHelper.kt` |
| PDF Editing Dialog | Placeholder dialog only | TODO present in `PlayerDialogHelper.kt` |
| Manual Cloud Sync | Button click shows toast only | TODO present in `GeneralSettingsFragment.kt` |
| Surface Renderer Migration | Legacy `photoView` hardcoded path | TODO present in `PlayerGestureSetupManager.kt` |

---

## Feature 1: Multiple File Rename

**Status:** 🟡 **Planned (Not Implemented)**  
**Files:** [RenameDialog.kt:69, 160](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/RenameDialog.kt)  
**Priority:** Medium

### Problem

Currently, users can only rename one file at a time. Bulk rename would improve workflow for organizing multiple files.

### User Stories

1. As a user, I want to select multiple files and rename them with a pattern
2. As a user, I want to add prefix/suffix to multiple file names at once
3. As a user, I want to number files sequentially (e.g., photo_001.jpg, photo_002.jpg)

### Proposed UI

**Option 1: Pattern-Based Rename**

```
Selected files: 3 files
Pattern: [prefix]_[number:3]_[original]
Preview:
  vacation.jpg → summer_001_vacation.jpg
  beach.jpg → summer_002_beach.jpg
  sunset.jpg → summer_003_sunset.jpg
```

**Option 2: Find & Replace**

```
Selected files: 5 files
Find: "IMG"
Replace with: "Photo"
Preview:
  IMG_001.jpg → Photo_001.jpg
  IMG_002.jpg → Photo_002.jpg
```

### Implementation

```kotlin
// RenameDialog.kt
private fun setupMultipleRenameUI() {
    // TODO: Implement RecyclerView adapter for multiple file rename
    val adapter = MultiFileRenameAdapter(
        files = selectedFiles,
        onPatternChange = { pattern ->
            updatePreviews(pattern)
        }
    )
    
    binding.rvFileRenames.adapter = adapter
    binding.rvFileRenames.visibility = View.VISIBLE
}

private suspend fun renameMultipleFiles(pattern: String): FileOperationResult {
    // TODO: Implement multiple file rename
    selectedFiles.forEachIndexed { index, file ->
        val newName = applyPattern(pattern, file, index)
        val result = fileOperations.rename(file, newName)
        if (result is FileOperationResult.Error) {
            return result // Stop on first error
        }
    }
    return FileOperationResult.Success
}
```

### Testing

- [ ] Rename 2 files with pattern
- [ ] Rename 100+ files (performance)
- [ ] Handle name conflicts
- [ ] Undo bulk rename operation
- [ ] Cancel mid-operation

### Estimated Effort: 8-12 hours

---

## Feature 2: Gesture Hint Overlay

**Status:** 🟡 **Planned (Not Implemented)**  
**Files:** [PlayerGestureHelper.kt:185](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerGestureHelper.kt)  
**Priority:** Low

### Problem

New users don't know what gestures are available in the player (swipe for volume/brightness, pinch to zoom, etc.).

### User Stories

1. As a new user, I want to see available gestures on first launch
2. As a user, I want to dismiss the hint overlay
3. As a user, I want to re-enable hints from settings

### Proposed UI

**Overlay with gesture icons:**

```
┌──────────────────────────────┐
│  Swipe up/down (left)        │
│  📱→ Adjust Brightness       │
│                              │
│  Swipe up/down (right)       │
│  🔊→ Adjust Volume           │
│                              │
│  Pinch                       │
│  🔍→ Zoom                    │
│                              │
│  [Got it!]                   │
└──────────────────────────────┘
```

### Implementation

```kotlin
private fun showGestureHints() {
    // TODO: Implement hint overlay UI
    if (shouldShowHints()) {
        val overlay = GestureHintOverlay(context)
        overlay.show(
            hints = listOf(
                GestureHint.SwipeVertical("Left side", "Brightness"),
                GestureHint.SwipeVertical("Right side", "Volume"),
                GestureHint.Pinch("Zoom"),
                GestureHint.DoubleTap("Play/Pause")
            ),
            onDismiss = {
                PreferencesHelper.setGestureHintsShown()
            }
        )
    }
}
```

### Testing

- [ ] Show on first player launch
- [ ] Don't show after dismissal
- [ ] Re-enable from settings
- [ ] Overlay doesn't block UI interaction

### Estimated Effort: 4-6 hours

---

## Feature 3: PDF Editing Dialog

**Status:** ⚠️ **Planned (Complex, Not Implemented)**  
**Files:** [PlayerDialogHelper.kt:399](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt)  
**Priority:** Low

### Problem

Users cannot edit PDF files (merge, split, rotate, delete pages) within the app.

### User Stories

1. As a user, I want to rotate PDF pages
2. As a user, I want to delete unwanted pages
3. As a user, I want to extract specific pages
4. As a user, I want to merge multiple PDFs

### Proposed Features

**Phase 1: Simple Operations**

- Rotate page 90°/180°/270°
- Delete page
- Extract page to new PDF

**Phase 2: Advanced Operations**

- Reorder pages (drag & drop)
- Merge PDFs
- Split PDF at page number
- Add blank page

### Implementation Considerations

```kotlin
private fun showPdfEditDialog() {
    // TODO: Implement actual PDF editing dialog
    // Requires:
    // - PdfDocument API for manipulation
    // - Temporary file handling
    // - Progress UI for large PDFs
    // - Undo/redo support
}
```

**Libraries to consider:**

- Android PdfDocument API (built-in)
- Apache PDFBox (large dependency)
- iText (commercial license)

### Testing

- [ ] Rotate single page
- [ ] Delete multiple pages
- [ ] Extract pages to new PDF
- [ ] Merge 2+ PDFs
- [ ] Handle corrupted PDFs
- [ ] Large PDF performance (100+ pages)

### Estimated Effort: 20-30 hours (complex feature)

---

## Feature 4: Manual Cloud Sync

**Status:** 🟡 **Planned (Not Implemented)**  
**Files:** [GeneralSettingsFragment.kt:516](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt)  
**Priority:** Medium

### Problem

Users cannot manually trigger cloud sync. Sync only happens automatically.

### User Stories

1. As a user, I want to manually sync cloud files
2. As a user, I want to see sync progress
3. As a user, I want to cancel ongoing sync

### Proposed UI

**Settings option:**

```
Cloud Storage
  ├─ Google Drive: Connected
  ├─ Dropbox: Not connected
  └─ [Sync Now] ← NEW BUTTON
     Last synced: 5 minutes ago
     [Cancel] (shown during sync)
```

**Sync progress dialog:**

```
┌──────────────────────────────┐
│  Syncing Cloud Files...      │
│  ████████░░ 80%              │
│  Synced 80/100 files         │
│  [Cancel]                    │
└──────────────────────────────┘
```

### Implementation

```kotlin
private fun triggerManualSync() {
    // TODO: Trigger manual sync
    viewLifecycleOwner.lifecycleScope.launch {
        try {
            showSyncProgress()
            cloudSyncManager.syncAll { progress ->
                updateProgress(progress)
            }
            hideSyncProgress()
            showToast("Sync completed")
        } catch (e: Exception) {
            showError("Sync failed: ${e.message}")
        }
    }
}
```

### Implementation Details

1. Add `CloudSyncManager.syncAll()` method
2. Implement progress callback
3. Add cancellation support (CoroutineScope)
4. Update "Last synced" timestamp
5. Handle network errors gracefully

### Testing

- [ ] Manual sync works
- [ ] Progress updates correctly
- [ ] Cancel stops sync
- [ ] Network error handling
- [ ] Multiple cloud providers

### Estimated Effort: 6-8 hours

---

## Feature 5: Player Surface Renderer Migration

**Status:** 🏗️ **Architecture Change (Partial, Decision Required)**  
**Files:** [PlayerGestureSetupManager.kt:48](file:///c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt)  
**Priority:** High (if migration is planned)

### Problem

Currently uses `PhotoView` for image rendering. Migration to Surface renderer for better performance is in progress but incomplete.

### Implementation

```kotlin
private val targetView: View
    get() = binding.photoView // TODO: Switch based on renderer state when migration enabled
    // Future: return if (useSurfaceRenderer) binding.surfaceView else binding.photoView
```

### Decision Required

- ✅ **Complete migration** - Finish Surface renderer implementation
- ❌ **Cancel migration** - Remove TODO and stick with PhotoView

### If Completing Migration

**Requirements:**

- Implement gesture handling for SurfaceView
- Migrate zoom functionality
- Migrate scale type handling
- Test performance improvements

**Estimated Effort:** 15-20 hours

---

## Priority Ranking

| Feature | Priority | Effort | User Impact |
|---------|----------|--------|-------------|
| Manual Cloud Sync | High | 6-8h | High (many users) |
| Surface Renderer Migration | High | 15-20h | Medium (performance) |
| Multiple File Rename | Medium | 8-12h | Medium (power users) |
| Gesture Hints | Low | 4-6h | Low (onboarding) |
| PDF Editing | Low | 20-30h | Low (niche feature) |

---

## Recommended Implementation Order

1. **Manual Cloud Sync** - High impact, medium effort
2. **Multiple File Rename** - Useful for many users
3. **Gesture Hints** - Quick win for UX
4. **Surface Renderer Migration** - If performance issues exist
5. **PDF Editing** - Only if users request it

---

## Next Steps

1. ✅ Confirmed: document is актуально (5/5 TODO still open in code)
2. 🔍 **Get user decision** on Multiple File Rename scope (pattern only vs pattern + find/replace)
3. 🔍 **Get user decision** on Surface Renderer migration (complete or cancel)
4. ▶️ Implement Manual Cloud Sync (highest priority implementation candidate)
5. ▶️ Add Gesture Hints (quick win after sync)
6. ⌛ Defer PDF Editing unless explicit product demand
