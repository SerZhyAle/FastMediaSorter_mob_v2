# TODO V2 - FastMediaSorter v2

## 🎯 Current Development Tasks

### 🔧 High Priority

- [ ] **Media processing optimization audit**
  - ✅ VERIFIED: Coil library used for image loading (v2.5.0 with video/GIF support)
  - ✅ VERIFIED: All file operations use IO dispatcher (viewModelScope.launch(ioDispatcher))
  - ✅ VERIFIED: Memory/disk cache enabled in CoilModule (memoryCachePolicy, diskCachePolicy)
  - ✅ VERIFIED: Custom NetworkFileFetcher for SMB/SFTP/FTP with 2MB buffer limit for thumbnails
  - ✅ VERIFIED: Async loading in all adapters (MediaFileAdapter, player activities)
  - ⚠️ TODO: Add bitmap downsampling configuration in CoilModule for large images
  - ⚠️ TODO: Implement EXIF metadata extraction (orientation, date, location) asynchronously
  - ⚠️ TODO: Add video metadata extraction (duration, resolution, codec) in background
  - ⚠️ TODO: Configure Coil memory cache size based on device capabilities
  - ⚠️ TODO: Add preloading strategy for next/previous images in PlayerActivity

- [ ] **State management and data consistency audit**
  - ✅ VERIFIED: MVVM architecture with StateFlow (BrowseViewModel, PlayerViewModel, MainViewModel)
  - ✅ VERIFIED: Reactive UI updates via repeatOnLifecycle(State.STARTED) + collect
  - ✅ VERIFIED: File existence check in MediaFileAdapter (localFile.exists()) before loading
  - ✅ VERIFIED: Delete operations update state immediately (remove from list, adjust index)
  - ✅ VERIFIED: Undo system for Copy/Move/Delete operations (UndoOperation in BrowseViewModel)
  - ✅ VERIFIED: Error placeholders shown for missing files (ic_image_error, ic_video_error)
  - ⚠️ PARTIALLY: File system watcher missing (no ContentObserver/FileObserver for external changes)
  - ⚠️ TODO: Add FileObserver to detect external file deletions/moves
  - ⚠️ TODO: Add automatic list refresh when returning from background (onResume)
  - ⚠️ TODO: Handle MediaStore changes with ContentObserver for local files
  - ⚠️ TODO: Add file integrity validation before file operations
  - ⚠️ TODO: Implement optimistic UI updates with rollback on operation failure
  - ⚠️ TODO: Add background sync to verify network file existence
  - ⚠️ TODO: Handle stale thumbnails when file content changes

- [ ] **Interactivity and feedback audit: Progress indicators & Undo functionality**
  
  **Progress indicators for long operations:**
  - ✅ VERIFIED: FileOperationProgressDialog exists and ready (dialog_file_operation_progress.xml)
  - ✅ VERIFIED: FileOperationUseCase.executeWithProgress() returns Flow<FileOperationProgress>
  - ✅ VERIFIED: Progress states: Starting (indeterminate) → Processing (file count) → Completed
  - ✅ VERIFIED: Shows current file name, progress counter (N/M), transfer speed (B/s, KB/s, MB/s)
  - ✅ VERIFIED: Dialog non-cancelable during operation (setCancelable(false))
  - ✅ VERIFIED: PlayerActivity shows loading indicator after 1 second (showLoadingIndicatorRunnable)
  - ✅ VERIFIED: Simple ProgressBar in CopyToDialog/MoveToDialog (visibility toggle)
  - ⚠️ MISSING: FileOperationProgressDialog NOT INTEGRATED with Copy/Move/Delete dialogs!
  - ⚠️ MISSING: No ScanProgressCallback in SmbMediaScanner (V1 has it, V2 doesn't)
  - ⚠️ MISSING: No progress for MainViewModel.scanAllResources() (large folder scan)
  - ⚠️ MISSING: No cancellation button in FileOperationProgressDialog
  - ⚠️ MISSING: Byte-level progress (bytesTransferred/totalBytes) not tracked in handlers
  - ⚠️ TODO: Integrate FileOperationProgressDialog in CopyToDialog.copyToDestination()
  - ⚠️ TODO: Integrate FileOperationProgressDialog in MoveToDialog.moveToDestination()
  - ⚠️ TODO: Integrate FileOperationProgressDialog in DeleteDialog.deleteFiles()
  - ⚠️ TODO: Add cancellation support with Job tracking in FileOperationUseCase
  - ⚠️ TODO: Add byte-level progress in SmbFileOperationHandler.copyFile()
  - ⚠️ TODO: Add ScanProgressCallback to SmbMediaScanner.scanFolder() like V1
  - ⚠️ TODO: Show progress dialog in MainActivity.scanAllResources() for >100 resources
  
  **Undo functionality (rollback last operation):**
  - ✅ VERIFIED: Full Undo system implemented (UndoOperation data class)
  - ✅ VERIFIED: BrowseViewModel.saveUndoOperation() stores last operation
  - ✅ VERIFIED: BrowseViewModel.undoLastOperation() reverses Copy/Move/Rename/Delete
  - ✅ VERIFIED: Undo button in BrowseActivity (btnUndo, visibility based on lastOperation)
  - ✅ VERIFIED: CopyToDialog creates UndoOperation with copiedFiles paths
  - ✅ VERIFIED: MoveToDialog creates UndoOperation with source/dest paths
  - ✅ VERIFIED: Rename creates UndoOperation with oldNames pairs (oldPath, newPath)
  - ✅ VERIFIED: Delete creates UndoOperation (but actual undo logic unclear)
  - ✅ VERIFIED: Settings toggle: AppSettings.enableUndo (default true)
  - ✅ VERIFIED: Undo logic:
    * COPY → delete copied files from destination
    * MOVE → move files back to source (renameTo)
    * RENAME → rename back to old name
    * DELETE → restore deleted files (message only, no implementation)
  - ⚠️ PARTIALLY: Delete undo doesn't restore files (only shows message)
  - ⚠️ PARTIALLY: No undo button in PlayerActivity (only BrowseActivity)
  - ⚠️ TODO: Implement DELETE undo with deferred deletion (trash/temp folder pattern)
  - ⚠️ TODO: Add undo button in PlayerActivity for single-file operations
  - ⚠️ TODO: Clear undo operation on next operation (copy → undo → copy should clear first undo)
  - ⚠️ TODO: Add undo operation expiry (clear after 5 minutes or app background)
  - ⚠️ TODO: Add "Undo" toast notification after successful operation
  - ⚠️ TODO: Test undo for network files (SMB/SFTP operations)
  
  **Critical issues:**
  1. **BLOCKER**: FileOperationProgressDialog exists but NOT USED anywhere! (Ready infrastructure not integrated)
  2. **BLOCKER**: Delete undo doesn't work (only shows message, doesn't restore files)
  3. **HIGH**: No progress for SMB scanning (V1 has ScanProgressCallback, V2 doesn't)
  4. **HIGH**: No cancellation support for long operations (100+ files stuck without cancel)
  5. **MEDIUM**: No byte-level progress (users can't see transfer speed for large files)
  6. **MEDIUM**: No undo in PlayerActivity (only batch operations in BrowseActivity have undo)

- [ ] **UI: Accessibility improvements**
  - Add content descriptions for all images/icons
  - Test with TalkBack screen reader
  - Verify minimum touch target size (48dp) for all buttons
  - Test high contrast mode compatibility
  - Add accessibility labels for all interactive elements

- [ ] **UI: Animations and transitions**
  - Add screen transitions (slide, fade animations)
  - Implement list animations (add, remove, reorder items)
  - Add button ripple effects where missing
  - Improve progress indicators animations

- [ ] **Performance: Memory optimization**
  - Profile with Android Profiler
  - Fix memory leaks using LeakCanary
  - Optimize bitmap loading (add downsampling for thumbnails)
  - Implement pagination for large file lists (1000+ files)

### 🐛 Bug Fixes

- [ ] **SMB Authentication test after password migration**
  - Test SMB connection with migrated passwords
  - Verify plaintext fallback works correctly
  - Document password encryption migration process

### 🌐 Network Features

- [ ] **Cloud: Google Drive API Integration**
  - Implement Google Sign-In and Drive API
  - Add folder browsing and file operations
  - Handle OAuth2 flow and token storage
  - Adapt copy/move operations for cloud files

- [ ] **Cloud: Dropbox API Integration**
  - Integrate Dropbox SDK
  - Implement authentication and file access
  - Add folder sync and operations
  - Ensure compatibility with existing file operations

- [ ] **Background synchronization**
  - Use WorkManager for periodic sync
  - Check for new/deleted files in network/cloud resources
  - Update fileCount and thumbnail cache
  - Add synchronization status indicator in UI

- [ ] **Offline mode**
  - Cache thumbnails and metadata locally
  - Show cached data when network unavailable
  - Add offline status indication in UI
  - Implement operation queue for later sync

### 🧪 Testing

- [ ] **Unit tests**
  - Write tests for all UseCase classes with JUnit
  - Test ViewModels with kotlinx-coroutines-test
  - Mock Repository dependencies
  - Goal: >80% code coverage for domain layer

- [ ] **Instrumented tests**
  - Test database operations with Room testing library
  - UI flow tests with Espresso
  - Navigation between screens testing
  - File operations with temporary test folders

- [ ] **Manual testing checklist**
  - Test on Android versions 8.0 - 14.0
  - Test on different screen sizes (phone, tablet)
  - Test different file types and sizes
  - Test connection scenarios (slow network, no internet, connection drops)

- [ ] **Security audit**
  - Check for hardcoded credentials
  - Validate input sanitization
  - Test file path traversal prevention
  - Review permission usage

### ⚡ Performance Optimization

- [ ] **Performance audit: FastMediaSorter FAST promise verification**
  
  **File operations and scanning:**
  - ✅ VERIFIED: LocalMediaScanner uses MediaStore API (efficient for local files)
  - ✅ VERIFIED: SmbMediaScanner.scanFolderChunked() - lazy loading first 100 files (maxFiles parameter)
  - ✅ VERIFIED: useChunkedLoading flag in BrowseViewModel/PlayerViewModel for SMB resources
  - ✅ VERIFIED: File operations on IO dispatcher (viewModelScope.launch(ioDispatcher))
  - ✅ VERIFIED: Structured concurrency with proper error handling (exceptionHandler)
  - ⚠️ PARTIALLY: No progress callback for SMB scanning (unlike V1's ScanProgressCallback)
  - ⚠️ TODO: Add ScanProgressCallback to SmbMediaScanner for long operations (>2 seconds)
  - ⚠️ TODO: Implement batch processing with UI updates for large folders (1000+ files)
  - ⚠️ TODO: Add cancellation support for long-running scans (Job cancellation)
  
  **RecyclerView optimization:**
  - ✅ VERIFIED: MediaFileAdapter extends ListAdapter with DiffUtil.ItemCallback
  - ✅ VERIFIED: DiffUtil compares by path (areItemsTheSame) and full equality (areContentsTheSame)
  - ✅ VERIFIED: submitList() used everywhere (MainActivity, BrowseActivity) - async diff
  - ⚠️ FOUND: MediaFileAdapter.setGridMode() uses notifyDataSetChanged() - inefficient!
  - ⚠️ FOUND: DestinationsAdapter.submitList() uses notifyDataSetChanged() - no DiffUtil!
  - ⚠️ FOUND: ResourceToAddAdapter uses notifyDataSetChanged() - should use ListAdapter
  - ⚠️ TODO: Replace notifyDataSetChanged() with targeted notifyItemRangeChanged()
  - ⚠️ TODO: Refactor DestinationsAdapter to use ListAdapter<MediaResource, VH>(DiffCallback)
  - ⚠️ TODO: Refactor ResourceToAddAdapter to use ListAdapter pattern
  - ⚠️ TODO: Add RecyclerView.RecycledViewPool for multiple lists with same ViewHolder type
  - ⚠️ TODO: Set recyclerView.setItemViewCacheSize() for frequent scrolling
  
  **Layout and rendering:**
  - ✅ VERIFIED: ConstraintLayout used in most layouts (flat hierarchy)
  - ✅ VERIFIED: ViewBinding prevents findViewById() overhead
  - ✅ VERIFIED: Coil handles image loading/decoding on background threads
  - ⚠️ TODO: Run Layout Inspector to check overdraw (should be <2x on most screens)
  - ⚠️ TODO: Profile with GPU Rendering Profiler (target <16ms per frame for 60fps)
  - ⚠️ TODO: Check for expensive onBind operations in adapters (should be <1ms)
  
  **Pagination and large datasets:**
  - ✅ VERIFIED: MediaFilesPagingSource skeleton exists (commented: "implement chunked loading later")
  - ⚠️ MISSING: No actual pagination implementation for 1000+ files
  - ⚠️ TODO: Implement Paging3 library with PagingDataAdapter
  - ⚠️ TODO: Load files in pages (50-100 items) for large folders
  - ⚠️ TODO: Add "Loading more..." indicator at list end
  - ⚠️ TODO: Preload next page when scrolling near bottom
  
  **Preloading and caching:**
  - ✅ VERIFIED: Coil memory/disk cache enabled
  - ⚠️ PARTIALLY: No explicit preloading in PlayerActivity (V1 has preloadNextImage())
  - ⚠️ TODO: Implement PlayerViewModel.preloadAdjacentFiles() - load next/prev thumbnails
  - ⚠️ TODO: Use Coil's ImageRequest.Builder().memoryCacheKey() for predictable caching
  - ⚠️ TODO: Increase memory cache size for devices with >4GB RAM
  
  **Critical issues for "FAST" promise:**
  1. **BLOCKER**: notifyDataSetChanged() kills performance on grid mode switch (100+ items = freeze)
  2. **BLOCKER**: No pagination for 1000+ files (single query loads all = OOM risk)
  3. **HIGH**: No progress indication for slow SMB scans (>2 seconds feels frozen)
  4. **HIGH**: No preloading in player (each swipe = network request = delay)
  5. **MEDIUM**: Missing RecycledViewPool optimization (memory inefficiency)

- [ ] **Database optimization**
  - Profile database queries
  - Add indexes for frequent queries
  - Optimize Room DAO methods
  - Consider pagination for large datasets

- [ ] **Battery optimization**
  - Reduce background work frequency
  - Efficient use of JobScheduler/WorkManager
  - Pause synchronization on low battery
  - Release resources properly when backgrounded

- [ ] **Edge cases handling**
  - Empty folders handling
  - Folders with 1000+ files
  - Very long file names (>255 chars)
  - Special characters in file names
  - Corrupted or unsupported media files

---

## 📦 Release Preparation

### Build Configuration

- [ ] **ProGuard/R8 configuration**
  - Configure ProGuard rules for release build
  - Test obfuscated APK thoroughly
  - Keep classes used via reflection
  - Verify all functionality after ProGuard

- [ ] **APK signing**
  - Verify release keystore exists and is secure
  - Configure signing in build.gradle.kts
  - Test installation of signed APK
  - Store keystore securely (not in repository)

- [ ] **APK size optimization**
  - Enable resource shrinking
  - Enable code shrinking (R8)
  - Use vector drawables instead of PNG where possible
  - Remove unused resources and dependencies
  - Consider Android App Bundle (.aab)

- [ ] **Version management**
  - Update versionCode in build.gradle.kts
  - Update versionName (2.0.0)
  - Follow semantic versioning (MAJOR.MINOR.PATCH)
  - Tag release in Git

- [ ] **Dependencies update**
  - Update all libraries to latest stable versions
  - Test after each major update
  - Check for deprecated APIs
  - Fix any breaking changes

### Documentation

- [ ] **README updates**
  - Update README.md with v2 features
  - Update Russian (README.ru.md) and Ukrainian (README.ua.md) versions
  - Add new UI screenshots
  - Update build and installation instructions

- [ ] **CHANGELOG**
  - Document all changes since v1
  - Group by: Added, Changed, Fixed, Removed
  - Specify version and release date
  - Highlight breaking changes

- [ ] **User documentation**
  - Create comprehensive user guide
  - Document all features and gestures
  - Create FAQ section
  - Write troubleshooting guide

---

## 🚀 Google Play Store

### Store Materials

- [ ] **Store listing**
  - App title (max 30 characters)
  - Short description (max 80 characters)
  - Full description (max 4000 characters)
  - Translations: English, Russian, Ukrainian

- [ ] **Screenshots**
  - Capture 4-8 screenshots per device type (phone, tablet)
  - Show key features: Main screen, Browse, Player
  - Add device frames and annotations
  - Create localized versions (en, ru, uk)

- [ ] **Feature graphic**
  - Design 1024x500px feature graphic
  - Include app branding and key visual
  - Follow Google Play design guidelines
  - Create localized versions if needed

- [ ] **App icon**
  - Verify adaptive icon (foreground + background)
  - Test on different launchers
  - Ensure compliance with Google Play guidelines
  - Verify all mipmap sizes generated

- [ ] **Privacy Policy**
  - Update policy for v2 data usage
  - Document all permissions and their purposes
  - Include contact information
  - Host online (e.g., GitHub Pages)

- [ ] **Content rating**
  - Complete IARC questionnaire in Play Console
  - Verify age rating (likely Everyone)
  - Review content descriptors

### Release Process

- [ ] **Internal testing track**
  - Upload APK/AAB to Play Console Internal Testing
  - Test installation and update flow
  - Verify all functionality in production build
  - Upload ProGuard mapping file

- [ ] **Closed beta testing**
  - Promote to Closed Testing track
  - Add beta testers (5-20 users)
  - Monitor crash reports and ANRs
  - Collect and address feedback

- [ ] **Production release**
  - Promote to Production track
  - Start with staged rollout (10-20%)
  - Monitor crash-free rate and ANR rate
  - Gradually increase to 100%

- [ ] **Post-release monitoring**
  - Monitor Play Console metrics (installs, crashes, ratings)
  - Respond to user reviews promptly
  - Track Firebase Analytics events
  - Monitor Firebase Crashlytics reports

---

## 📊 Project Status

**Completed (Latest Build: 2.0.0-build2511140258):**
- ✅ FTP connection stability fixes
- ✅ SFTP file attributes (size, date)
- ✅ Mixed operations SMB↔SFTP
- ✅ Progress bars infrastructure
- ✅ Empty states with network error handling
- ✅ Touch zones numbered diagram with legend
- ✅ Display mode (list/grid) saved per resource
- ✅ Dynamic grid columns based on screen size
- ✅ Last viewed file position saved per resource
- ✅ Random sort mode for slideshow
- ✅ SMB delete operation fixed
- ✅ Splash screen removed (Welcome only on first launch)
- ✅ Enhanced local folder scanning (including Android/Media)
- ✅ IP address input validation improved
- ✅ Dark/Light theme support with system detection
- ✅ Scroll gesture detection in resource list

**Next priorities:**
1. Integrate progress dialogs with file operations
2. Accessibility improvements (content descriptions, TalkBack)
3. UI animations and transitions
4. Performance optimization (memory, database)
5. Unit and instrumented testing
6. Google Play Store preparation

