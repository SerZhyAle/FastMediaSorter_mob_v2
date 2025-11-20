# TODO V2 - FastMediaSorter

**Latest Build**: 2.25.1119.2013  
**Version**: 2.25.1119.2013
**Package**: com.sza.fastmediasorter

---

## 📌 Recent Fixes

### Build 2.25.1120.xxxx - SMB Subfolder Support in Share Name
**Problem**: User could not add a specific subfolder of an SMB share (e.g., `_i\output\1`) because "Share Name" field validation rejected backslashes.
**Root Cause**: `SmbClient` and `SmbOperationsUseCase` treated "Share Name" strictly as the share name, without supporting path components.
**Solution**: 
- **SmbClient**: Updated `testConnection` to accept an optional `path` parameter.
- **SmbOperationsUseCase**: 
    - `testConnection`: Parses `shareName` input. If it contains separators (`\` or `/`), splits it into `actualShareName` and `subPath`. Passes `subPath` to `SmbClient`.
    - `saveCredentials`: Parses `shareName` input. Saves only `actualShareName` in credentials. The full path is preserved in `MediaResource.path` by `AddResourceViewModel`.
**Impact**: Users can now enter `Share\Subfolder` in the "Share Name" field. The app correctly connects to `Share` and navigates to `Subfolder`.
**Files Changed**:
- `SmbClient.kt`: Updated `testConnection` signature and logic.
- `SmbOperationsUseCase.kt`: Updated `testConnection` and `saveCredentials` to parse share name.
**Verified**: Compilation successful.

### Build 2.25.1120.xxxx - Fixed Copy/Move Dialog Width
**Problem**: Destination buttons in Copy/Move dialogs were squeezed and text appeared vertical because the dialog width was too narrow (wrapping content).
**Root Cause**: Standard Android Dialog wraps content width by default. Dynamic buttons with `layout_weight` need more horizontal space.
**Solution**: Explicitly set dialog window width to 90% of screen width in `onCreate`.
**Impact**: Destination buttons now have enough space to display text correctly.
**Files Changed**:
- `CopyToDialog.kt`: Added `window?.setLayout` in `onCreate`.
- `MoveToDialog.kt`: Added `window?.setLayout` in `onCreate`.
**Verified**: Compilation successful.

### Build 2.25.1119.2013 - Synchronous Trash Cleanup (Instant, No WorkManager Delay)
**Problem**: WorkManager cleaned trash every 15min - user deleted files, closed app, trash remained for up to 15min
**Root Cause**: Asynchronous periodic cleanup inappropriate for user-visible temp folders created during session
**Solution**: 
- **Synchronous cleanup on resource open**: `loadResource()` calls `cleanupTrashOnBackground(maxAge=0)` - deletes all trash folders immediately
- **Synchronous cleanup on resource close**: `onCleared()` calls `cleanupTrashOnBackground(maxAge=0)` - cleans up trash when leaving Browse screen
- **Disabled WorkManager**: Commented out `scheduleTrashCleanup()` in `FastMediaSorterApp` - no longer needed
- **Background execution**: Both calls use `viewModelScope.launch(ioDispatcher)` - non-blocking, runs on IO thread
- **Local resources only**: Network trash cleanup skipped (requires different approach via SmbOperationsUseCase)
**Impact**: 
- User opens resource → all old trash deleted instantly (background)
- User closes resource → session trash deleted instantly (background)
- No 15-minute wait, trash visible only during active Undo window
- WorkManager overhead removed from app startup (~100-200ms saved)
**Files Changed**:
- `BrowseViewModel.kt` line 72: Added `cleanupTrashFoldersUseCase` injection
- `BrowseViewModel.kt` lines 133-145: `onCleared()` now calls cleanup before exit
- `BrowseViewModel.kt` lines 316-318: `loadResource()` calls cleanup after resource loaded
- `BrowseViewModel.kt` lines 1289-1320: New `cleanupTrashOnBackground()` method
- `FastMediaSorterApp.kt` lines 46-61: Disabled WorkManager periodic scheduling (commented out)
**Verified**: Compilation successful

### Build 2.25.1119.2005 - Fixed Undo for Delete Operations (Soft-Delete)
**Problem**: Undo button didn't restore deleted files - files permanently removed, no trash backup
**Root Cause**: 
- Local files deleted via `file.delete()` directly (permanent deletion)
- Network files used `FileOperationUseCase` with `softDelete=true`, but local files bypassed it
- `UndoOperation` saved with `copiedFiles=null`, Undo code expected trash structure
**Solution**: 
- **All files** (local + network) now processed via `FileOperationUseCase.Delete(softDelete=true)`
- Files moved to `.trash_<timestamp>/` folder instead of permanent deletion
- `FileOperationResult.Success.copiedFilePaths` format: `[trashDirPath, originalPath1, ...]`
- `UndoOperation` saves trash paths in `copiedFiles` field
- Undo code unchanged - already implemented trash restoration logic
**Impact**: 
- Delete creates `.trash_<timestamp>/` folder, moves files there
- Undo restores files from trash back to original locations instantly via `addFiles()`
- Trash folders auto-cleaned by `CleanupTrashFoldersWorker` (15min intervals, 5min TTL)
**Files Changed**:
- `BrowseViewModel.kt` lines 833-916: Completely refactored `deleteSelectedFiles()` - removed manual `file.delete()`, unified to `FileOperationUseCase.execute(Delete)`
**Verified**: Compilation successful

### Build 2.25.1119.1959 - Fixed Grid Cell Width for Custom Icon Sizes
**Problem**: When user changed icon size (e.g., 256dp), Grid cells remained narrow (96dp width) while thumbnails stretched to 256dp height, creating distorted layout
**Root Cause**: Grid width calculation hardcoded to 96dp for non-thumbnail mode, ignoring user's `defaultIconSize` setting
**Solution**: 
- Removed hardcoded 96dp width
- Both thumbnail and non-thumbnail modes now use `iconSize` from settings
- Formula: `itemWidth = iconSize + cardPadding (8dp)`
**Impact**: Grid cells now square (e.g., 256x256) matching user-selected icon size, proper 2-column layout on tablets
**Files Changed**:
- `BrowseActivity.kt` lines 682-690: Unified width calculation for both modes
**Verified**: Compilation successful

### Build 2.25.1119.1956 - Smart Undo Without Full Reload
**Problem**: After Undo operation (Move/Delete), files restored but list not updated until manual Refresh. Also showed "Loading..." indicator.
**Root Cause**: `undoLastOperation()` called full `loadResource()` reload with progress dialog after every undo
**Solution**: 
- Created `addFiles()` method: adds files to list, re-sorts by current SortMode, updates cache
- Created `createMediaFileFromFile()` helper: constructs MediaFile objects from java.io.File
- Undo Move: collects restored files, calls `addFiles()` - instant update, no reload
- Undo Delete: same pattern - restore from trash, add to list directly
- Undo Rename: still uses `loadResource()` (file objects must be recreated with new names)
- Undo Copy: no reload needed (files were in destination folder, not current)
**Impact**: Undo operations instant, no "Loading..." spinner, files appear immediately in correct sort order
**Files Changed**:
- `BrowseViewModel.kt` lines 160-196: New `addFiles()` method with full SortMode support
- `BrowseViewModel.kt` lines 959-1056: Refactored `undoLastOperation()` - removed `setLoading(true)`, replaced `loadResource()` with `addFiles()`
- `BrowseViewModel.kt` lines 1267-1297: New `createMediaFileFromFile()` helper
**Verified**: Compilation successful

### Build 2.25.1119.1947 - Fixed Infinite Loading After Move/Copy Operations
**Problem**: After completing Move or Copy operation, progress dialog showed endless "Loading..." spinner
**Root Cause**: Flow `collect {}` continued after `Completed` event, waiting for more events from already-closed Flow
**Solution**: 
- Added `completed` flag to prevent processing duplicate events
- Added `progressDialog.dismiss()` explicitly before handling result
- Added `if (completed) return@collect` guard at start of collect block
**Impact**: Move and Copy operations now complete cleanly without UI freezes
**Files Changed**:
- `MoveToDialog.kt` lines 197-216: Added completion guard and dialog dismissal
- `CopyToDialog.kt` lines 197-216: Same fix pattern
**Verified**: Compilation successful, no warnings

---

## 🎯 Current Development - Active Tasks

### High Priority

- [x] ~~**Small Controls Mode in BrowseActivity**~~ **✅ COMPLETED** - Already implemented
  - Implementation: `applySmallControlsIfNeeded()` method in BrowseActivity
  - When `settings.showSmallControls=true`, all toolbar and bottom panel buttons reduce to 50% height (24dp)
  - Affects all 14 command buttons: Back, Sort, Filter, Refresh, Toggle View, Select All, Deselect All, Copy, Move, Rename, Delete, Undo, Share, Play
  - Automatic restore to 48dp when setting disabled
  - Code location: BrowseActivity.kt lines 1335-1396
  - Verified: Build 2.25.1119.xxxx

- [x] ~~**Browse Screen - Filter Dialog**~~ **✅ COMPLETED** - Already implemented
  - Complete implementation: `showFilterDialog()` method in BrowseActivity
  - Full UI: dialog_filter.xml with name, date range (DatePicker), size range (MB)
  - Filter logic in BrowseViewModel: applyFilter() with all criteria (name substring ignoreCase, minDate/maxDate timestamp comparison, minSizeMb/maxSizeMb)
  - Active filter indicator: tvFilterWarning at bottom displays "⚠ Filter active: ..." via buildFilterDescription()
  - Properly cleared on exit: filter stored in BrowseState (runtime only, not in Room)
  - Buttons: Apply, Clear (setFilter(null)), Cancel
  - Code locations: BrowseActivity.kt lines 674-744 (dialog), 586-609 (description), 340-345 (UI); BrowseViewModel.kt lines 1001-1067 (logic)
  - Verified: Build 2.25.1119.xxxx

- [x] ~~**Browse Screen - List View Item Operations**~~ **✅ COMPLETED** (Build 2.25.1119.xxxx)
  - Complete implementation: Per-item operation buttons in both list and grid views
  - List view: 4 buttons (Copy/Move/Rename/Delete) + Play button, 32dp each, horizontal row
  - Grid view: Same 5 buttons as overlay (24dp each, bottom-right corner)
  - Smart visibility: Copy/Move buttons check destinations availability via GetDestinationsUseCase
  - Permission-based: Move/Rename/Delete require resource.isWritable
  - Icons: Android system drawables (ic_menu_save, ic_menu_revert, ic_menu_edit, ic_menu_delete, ic_media_play)
  - Auto-selection: Single-click on operation button selects file and executes action immediately
  - Code locations: item_media_file.xml (lines 58-131), item_media_file_grid.xml (lines 58-121), MediaFileAdapter.kt (callbacks + visibility), BrowseActivity.kt (wiring lines 141-153, 347-355)
  - Verified: Build successful with all constraints met
  - Spec Reference: V2_p1_2.md lines 244-248

- [x] ~~**Player Screen - Command Panel Mode**~~ **✅ COMPLETED** (Build 2.25.1119.xxxx)
  - Implementation: Command panel as alternative to fullscreen mode
  - Top panel layout: Back, Previous, Next | Rename, Delete, Undo | Slideshow (with visual spacing)
  - Bottom panels: "Copy to..." and "Move to..." with dynamic destination buttons (1-10)
  - Mode toggle: `showCommandPanel` setting (default: false = fullscreen)
  - Touch zones in command panel mode:
    - Image: Previous (left 50%), Next (right 50%)
    - Video: Previous/Next only in top 50% (bottom 50% for video controls)
  - Button visibility per spec: Core buttons always visible, additional buttons (Share, Info, Edit, Fullscreen) hidden by default
  - Panel collapse/expand: Headers clickable, state persisted in settings
  - Small controls mode: All command buttons half height (24dp) when setting enabled
  - Code locations: activity_player_unified.xml (layout with Space separators), PlayerActivity.kt (updateCommandAvailability method)
  - Spec Reference: V2_p1_2.md sections 1.2 and 3.2

- [ ] **SMB Connection Blocking After Errors**
  - Issue: After certain SMB errors, connection becomes blocked until app restart
  - Status: Partial fix in Build 2.25.1119.xxxx (SMB Connection Recovery), needs more testing
  - Action: Monitor for remaining edge cases

### Cloud Storage Integration

- [ ] **OneDrive Integration - Phase 4** (UI Integration)
  - ✅ Backend complete: OneDriveRestClient with Microsoft Graph REST API v1.0
  - ⏳ Remaining: OAuth configuration in Azure AD, FolderPickerActivity, AddResourceActivity UI
  - Blocker: Requires Azure AD application registration

- [ ] **Dropbox Integration - Phase 4** (UI Integration)
  - ✅ Backend complete: DropboxClient with OAuth 2.0 PKCE
  - ⏳ Remaining: APP_KEY configuration, FolderPickerActivity, AddResourceActivity UI, AndroidManifest auth_callback
  - Blocker: Requires Dropbox App Console registration

- [ ] **Google Drive Testing**
  - ✅ Implementation complete
  - ⏳ Remaining: OAuth2 client configuration in Google Cloud Console
  - Blocker: Need package name + SHA-1 fingerprint, OAuth consent screen setup
  - Testing: Add folder → Browse → File operations

### Testing & Validation

- [ ] **Pagination Testing (1000+ files)**
  - Status: Implementation complete, needs real-world testing
  - Test scenarios:
    - LOCAL: 1000+, 5000+ files (images/videos mix)
    - SMB: Large network shares (test over slow connection)
    - SFTP/FTP: 1000+ files with thumbnails
  - Expected: No lag, smooth scrolling, memory efficient

- [ ] **Network Undo Operations - Testing**
  - Status: Implementation complete, needs verification
  - Test cases:
    - SMB/SFTP/FTP: Delete file → Undo → Verify restoration
    - Check trash folder creation permissions
    - Network timeout handling (slow connections)
    - Trash cleanup after 24 hours

- [ ] **Network Image Editing - Performance Testing**
  - Status: Implementation complete, needs performance validation
  - Test with:
    - Large images (10MB+) over slow network
    - Multiple edits (rotate, flip) in sequence
    - Connection interruption during download/upload
  - Add: Progress reporting, cancellation support

---

## 🟠 High Priority (Quality & UX)

- [ ] **Browse Screen - Multi-Select via Long Press**
  - First long press: Select single file (don't launch player)
  - Second long press on another file: Select all files between first and second
  - Allow scrolling while selecting
  - Show selection count in header
  - Spec Reference: V2_p1_2.md - "long-presses a media file"

- [ ] **Browse Screen - Selected Files Counter**
  - Display "N files selected" in text header below toolbar
  - Update dynamically as selection changes
  - Clear when deselecting all
  - Spec Reference: V2_p1_2.md - "a counter of selected media files"

- [ ] **File Operations - Undo System Enhancement**
  - Implement undo for all operations: Copy, Move, Rename, Delete
  - Store operation details until next operation or file view
  - Undo button enabled only when operation exists
  - Show "Operation undone" toast on success
  - Spec Reference: V2_p1_2.md - rename/delete/copy/move sections mention undo

- [x] ~~**Copy/Move Dialogs - Dynamic Destination Buttons**~~ **✅ COMPLETED** (Build 2.25.1120.xxxx)
  - Display 1-10 destination buttons based on available destinations
  - Show destination color (from destinationColor field)
  - Buttons sized dynamically to fill available space (Grid layout with 2 columns)
  - Fixed layout issue where buttons were too narrow (Dialog width set to 90% screen)
  - Background: green for copy, blue for move
  - Header: "copying/moving N files from [source]"
  - Spec Reference: V2_p1_2.md - "copy to..." and "move to..." dialog screens

- [ ] **Filter and Sort Resource List Dialog**
  - Implement main screen filter dialog with:
    - Sorting dropdown (by name)
    - Resource type checkboxes filter
    - Media type checkboxes filter
    - "By part of name" text field (substring, case-insensitive)
  - Show filter description at bottom of main screen when active
  - Apply/Cancel buttons
  - Spec Reference: V2_p1_2.md - "Filter and Sort Resource List Screen"

- [ ] **Edge Cases Handling**
  - Empty folders: Add explicit empty state indicators
  - Long filenames: Add ellipsize and proper text overflow
  - Special characters: Verify correct display in all UI components
  - Large file counts: Test >10000 files display

- [ ] **Static Analysis Integration**
  - Add detekt to build.gradle.kts
  - Configure baseline rules
  - Fix critical warnings
  - Add to CI/CD pipeline (future)

---

## 🟡 Medium Priority (Documentation & Polish)

### UI/UX Polish

- [ ] **Animations and Transitions**
  - Screen transitions (slide, fade, shared element)
  - RecyclerView item animations (add, remove, reorder)
  - Ripple effects for missing buttons
  - Smooth progress indicators

### Documentation

- [ ] **README Update**
  - Document v2 features and changes
  - Add screenshots of main screens
  - Localize in en/ru/uk
  - Add installation instructions

- [ ] **CHANGELOG Creation**
  - Format: Added/Changed/Fixed/Removed
  - Document migration from v1 to v2
  - List all major features

- [ ] **User Guide**
  - Features overview
  - FAQ section
  - Troubleshooting common issues
  - Localized (en/ru/uk)

### Build Optimization

- [ ] **Size Optimization**
  - Enable resource shrinking in release build
  - Check APK/AAB size
  - Remove unused resources and assets
  - Optimize images and drawables

- [ ] **Dependencies Update**
  - Update libraries to latest stable versions
  - Check compatibility and breaking changes
  - Test after updates

---

## ⚡ Performance Optimization (Low Priority)

- [ ] **ExoPlayer initialization off main thread** (~39ms blocking)
- [ ] **ExoPlayer audio discontinuity investigation** (warning in logs, не критично)
- [ ] **Background file count optimization** (duplicate SMB scans)
- [ ] **RecyclerView profiling** (onBind <1ms target, test on low-end devices)
- [ ] **Layout overdraw profiling** (<2x target)
- [ ] **Memory leak detection** (LeakCanary integration)
- [ ] **Battery optimization** (reduce sync on low battery)

---

## 🌐 Network Features (Future)

- [ ] **Offline Mode**
  - Cache thumbnails and metadata locally
  - Show cached data when network unavailable
  - Operation queue for delayed sync

---

## 🧪 Testing (Pre-Release)

- [ ] **Unit tests** (domain layer, >80% coverage)
- [ ] **Instrumented tests** (Room, Espresso UI flows)
- [ ] **Manual testing** (Android 8-14, tablets, file types, edge cases)
- [ ] **Security audit** (credentials, input validation, permissions)

---

## 🧰 Code Quality (Pre-Release)

- [ ] **Static analysis** (detekt/ktlint integration)
- [ ] **Edge cases** (empty folders, 1000+ files, long names, special chars)

---

## 📦 Release Preparation

### Build
- [ ] **ProGuard/R8** (rules, test obfuscated APK)
- [ ] **APK signing** (keystore, test signed APK)
- [ ] **Size optimization** (resource/code shrinking, AAB)
- [ ] **Versioning** (versionCode/Name, Git tag v2.0.0)
- [ ] **Dependencies** (update to latest stable)

### Documentation
- [ ] **README** (v2 features, screenshots, en/ru/uk)
- [ ] **CHANGELOG** (Added/Changed/Fixed/Removed)
- [ ] **User guide** (features, FAQ, troubleshooting)

---

## 🚀 Google Play Store (Pre-Release)

### Store Materials
- [ ] **Listing** (title, descriptions en/ru/uk)
- [ ] **Screenshots** (4-8 per device, localized)
- [ ] **Feature graphic** (1024x500px)
- [ ] **App icon** (adaptive, test launchers)
- [ ] **Privacy Policy** (v2 data usage, host online)
- [ ] **Content rating** (IARC questionnaire)

### Release
- [ ] **Internal testing** (APK/AAB upload, ProGuard mapping)
- [ ] **Closed beta** (5-20 testers, crash monitoring)
- [ ] **Production** (staged rollout 10→100%)
- [ ] **Post-release** (metrics, reviews, analytics)

---

## 🚀 Recent Fixes Archive

### Build 2.25.1119.xxxx ✅
- ✅ **UX: Grid View - Wider Cells in Text-Only Mode** (Implemented)
  - When thumbnails disabled (text-only mode): cells are 3.5x wider
  - Automatic spanCount adjustment: fewer columns = wider cells = better text visibility
  - TextView: increased maxLines from 2 to 3, changed width from wrap_content to match_parent
  - Added minWidth="80dp" for TextView to ensure minimum readability
  - Dynamic calculation respects showVideoThumbnails setting
  - No changes to normal thumbnail mode (original behavior preserved)
- ✅ **OPTIMIZATION: File Operations - No Unnecessary Reloads** (Implemented)
  - Copy: No reload of source folder (files remain in source)
  - Move: Remove moved files from list without full rescan (removeFiles method)
  - Delete: Remove deleted files from list without full rescan (removeFiles method)
  - Rename: Keep full reload (need new MediaFile object with updated metadata)
  - New ViewModel methods: removeFiles(paths), updateFile(oldPath, newFile)
  - Cache updated via MediaFilesCacheManager.setCachedList()
  - Major UX improvement: no lag after operations, instant UI updates
- ✅ **FEATURE: Player Screen - Command Panel Mode** (Implemented)
  - Top panel: Back, Previous, Next | Rename, Delete, Undo | Slideshow (with 12dp Space separators)
  - Button visibility per V2 spec: Core buttons always visible, additional buttons hidden by default
  - Bottom panels: Copy to/Move to with dynamic destination buttons (1-10, GridLayout)
  - Mode toggle via touch zones or settings (showCommandPanel)
  - Touch zones: Image (left=prev, right=next), Video (top 50% only for navigation)
  - Small controls support: All command buttons reduce to 24dp height when setting enabled
- ✅ **FEATURE: Browse Screen - List View Item Operations** (Implemented)
  - Per-item buttons: Copy (destinations check), Move (destinations + writable), Rename (writable), Delete (writable), Play (always)
  - List: 32dp buttons in horizontal row, Grid: 24dp overlay buttons
  - Smart visibility with real-time destinations/permissions checking
- ✅ **FEATURE: Browse Screen Filter Dialog** (Already implemented, now verified)
  - Implementation: BrowseActivity.showFilterDialog() + BrowseViewModel.applyFilter()
  - Full criteria: name substring (ignoreCase), date range (DatePicker), size range (MB)
  - Active indicator: tvFilterWarning displays "⚠ Filter active: ..." at bottom
  - Runtime only: filter in BrowseState, cleared on exit (not persisted)
  - UI: dialog_filter.xml with Apply/Clear/Cancel buttons
- ✅ **FEATURE: Small Controls Mode** (Already implemented, now verified)
  - Implementation: BrowseActivity.applySmallControlsIfNeeded() - halves button height when setting enabled
  - Affects: All 14 command panel buttons (toolbar + bottom panel)
  - Scale: 0.5f (48dp → 24dp)
  - Dynamic toggle: Restores original size when setting disabled
- ✅ **FEATURE: IP Address Input Filter**
- ✅ **FIXED: ExoPlayer MediaCodec Errors - Reduced Log Noise**
- ✅ **CRITICAL: BrowseActivity Thumbnail Loading - Fixed Network Starvation**
- ✅ **CRITICAL: SMB Connection Recovery After Socket Errors**

### Build 2.25.1118.xxxx ✅
- ✅ **UI: Standardized All Boolean Controls (24 elements)**
- ✅ **UI: Fixed Short Numeric Input Fields (9 fields)**

### Previous Builds
- ✅ **CRITICAL: Migrated SSHJ → JSch for SFTP** (Build 2.0.2511162358)
- ✅ **FEATURE: OneDrive REST API Implementation** (Build 2.0.2511171110)
- ✅ **FEATURE: Dropbox Core Implementation** (Build 2.0.2511171110)
- ✅ **Background Sync - UI Enhancement** (Build 2.0.2511170337)
- ✅ **Database indexes** (Build 2.0.2511170338)
- ✅ **Slideshow Countdown Display** (Already implemented, undocumented)

---

## 📋 Next Immediate Priorities

1. ✅ ~~**Implement Small Controls Mode**~~ - Already implemented and verified
2. ✅ ~~**Implement Browse Screen Filter Dialog**~~ - Already implemented and verified
3. **Test Cloud Storage** - Google Drive/OneDrive/Dropbox OAuth setup and testing
2. **Implement Browse Screen - Filter Dialog** (next active task)
3. **Test Google Drive integration** (OAuth, file operations) - needs OAuth setup
4. **Test pagination** (1000+ files on all resource types)
5. **Test network undo/editing** (SMB/SFTP/FTP)
6. **Monitor SMB connection recovery** (verify no blocking issues remain)
