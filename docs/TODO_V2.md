# TODO V2 - FastMediaSorter v2# TODO V2 - FastMediaSorter v2# TODO V2 - FastMediaSorter v2



## 🎯 High Priority Tasks



### 🔴 Critical (BLOCKER)## 🎯 Active Development Tasks## 🎯 Current Development Tasks



- [ ] **Pagination for large datasets (1000+ files)**- [x] Сделать сворачиваемыми блоки "Copy to.." и "Move to.." При проигрывании медиафайла в режими с видимыми командами. Сворачивать каждый блок до полоски и разворачивать по нажатию на неё. Помнить последнее состояние для обоих блоков. И при проигрывании в следующий раз так и возобновлять - в свёрнутом или развернутом состоянии.

  - **Risk**: OOM crashes with folders containing 1000+ files  - ✅ IMPLEMENTED: Added copyPanelCollapsed and movePanelCollapsed fields to AppSettings (data class and SettingsManager)

  - Implement Paging3 library with PagingDataAdapter in MediaFileAdapter  - ✅ IMPLEMENTED: Collapsible headers with ▼/▶ indicators in activity_player_unified.xml (copyToPanelHeader, moveToPanelHeader)

  - Load files in pages (50-100 items per page)  - ✅ IMPLEMENTED: Click handlers in PlayerActivity (toggleCopyPanel, toggleMovePanel) with state persistence

  - Add "Loading more..." indicator at list end  - ✅ IMPLEMENTED: State restoration in updatePanelVisibility() on PlayerActivity start

  - Preload next page when scrolling near bottom  - ✅ IMPLEMENTED: updateCopyPanelVisibility() and updateMovePanelVisibility() methods toggle GridLayout visibility

  - Test with folders containing 5000+ files



### 🟠 High Priority- [x] In Player in command buttons line (if it enabled) we need the new button "information" with icon "i" which shows the dialog window with full possible information about the current file , such its exif ans os information.

  - ✅ IMPLEMENTED: ExtractExifMetadataUseCase for async EXIF extraction (orientation, dateTime, GPS)

- [ ] **FileObserver for external file changes**  - ✅ IMPLEMENTED: ExtractVideoMetadataUseCase for async video metadata extraction (duration, resolution, codec, bitrate, frame rate, rotation)

  - Add FileObserver to detect external deletions/moves in real-time  - ✅ IMPLEMENTED: LocalMediaScanner integration for both image and video metadata

  - Implement ContentObserver for MediaStore changes (local files)  - ✅ IMPLEMENTED: FileInfoDialog with formatted display (file size, duration HH:MM:SS, GPS coordinates, bitrate)

  - Update UI immediately when external changes detected  - ✅ IMPLEMENTED: dialog_file_info.xml layout with File/EXIF/Video sections, ScrollView with 400dp maxHeight

  - Handle gracefully when current file is deleted externally  - ✅ IMPLEMENTED: btnInfo button in activity_player.xml with ℹ icon

  - ✅ IMPLEMENTED: PlayerActivity.showFileInfo() method with dialog display

- [ ] **Byte-level progress for file operations**

  - Add bytesTransferred/totalBytes tracking in file operation handlers- [x] In Player in command buttons line (if it enabled) we need the new button for static images only "edit" with icon "edit" which shows the new dialog window. Later in thes dialog we will add some commands for image edition - like rotation, mirroring and etc.

  - Modify SmbFileOperationHandler methods to report byte progress during InputStream.copyTo()  - ✅ IMPLEMENTED: Added btnEditCmd button to activity_player_unified.xml (visible only for images, initially gone)

  - Modify SftpFileOperationHandler methods to report byte progress  - ✅ IMPLEMENTED: Created ImageEditDialog.kt with placeholder UI for future editing features

  - Modify FtpFileOperationHandler methods to report byte progress  - ✅ IMPLEMENTED: Created dialog_image_edit.xml layout with Rotate/Flip sections (buttons disabled, coming soon)

  - Update FileOperationProgress with actual byte counts  - ✅ IMPLEMENTED: Click handler btnEditCmd.setOnClickListener in PlayerActivity.setupCommandPanelControls()

  - Calculate and display transfer speed (MB/s)  - ✅ IMPLEMENTED: showImageEditDialog() method with MediaType.IMAGE validation

  - Report progress every ~100KB to avoid UI overhead  - ✅ IMPLEMENTED: Conditional visibility in updateCommandAvailability() - btnEditCmd visible only when showCommandPanel && currentFile.type == IMAGE



- [ ] **Undo support for network files (SMB/SFTP/FTP)**- [x] the new button Edit for static images must be enabled (visible) only if we have rights to write into this folder.

  - Implement soft-delete for SMB files (move to .trash folder on remote server)  - ✅ IMPLEMENTED: Added canWrite && canRead checks in updateCommandAvailability() for btnEditCmd visibility

  - Implement soft-delete for SFTP files (remote .trash folder)

  - Implement soft-delete for FTP files (remote .trash folder)- [x] the new dialog Edit for static images must have next commands to edit picture: rotate 90, rotate 180, rotate -90, mirror horisont, mirror vertical.

  - Test undo for SMB operations in PlayerActivity and BrowseViewModel  - ✅ IMPLEMENTED: RotateImageUseCase.kt - execute(imagePath, angle) with Bitmap rotation via Matrix.postRotate()

  - Test undo for SFTP operations in PlayerActivity and BrowseViewModel  - ✅ IMPLEMENTED: FlipImageUseCase.kt - execute(imagePath, FlipDirection) with Matrix.postScale() for HORIZONTAL/VERTICAL

  - Test undo for FTP operations in PlayerActivity and BrowseViewModel  - ✅ IMPLEMENTED: EXIF metadata preservation - reads original ExifInterface, copies 12 key attributes (TAG_DATETIME, GPS, device info)

  - Handle permission errors for network trash folder creation  - ✅ IMPLEMENTED: Orientation tag reset to NORMAL after physical rotation/flip

  - Add network-specific timeout handling for undo operations  - ✅ IMPLEMENTED: Format detection (PNG/WEBP/JPEG) with quality settings

  - Ensure trash cleanup works for network folders (CleanupTrashFoldersUseCase)  - ✅ IMPLEMENTED: ImageEditDialog wired with use cases - btnRotateLeft (90°), btnRotateRight (-90°), btnFlipHorizontal, btnFlipVertical

  - ✅ IMPLEMENTED: Progress indication during processing (ProgressDialog with status message)

- [ ] **Image editing for network files (SMB/SFTP/FTP)**  - ✅ IMPLEMENTED: Error handling with Toast messages

  - Download network image to temp folder before editing  - ✅ IMPLEMENTED: Button state management (disabled during operation)

  - Apply RotateImageUseCase/FlipImageUseCase to temp file  - ✅ IMPLEMENTED: Bitmap recycling for memory management

  - Upload modified image back to network location  - ⚠️ NOTE: Rotate 180° not implemented (can be achieved by two 90° rotations)

  - Show progress during download/upload phases

  - Handle network errors during download/upload### 🔧 High Priority

  - Clean up temp files after successful upload

  - Test with large images over slow network

  - Add cancellation support for download/upload operations

- [ ] **Pagination for large datasets (1000+ files)**- [ ] **Media processing optimization audit**

### 🟡 Medium Priority

  - **Risk**: OOM crashes with folders containing 1000+ files  - ✅ VERIFIED: Coil library used for image loading (v2.5.0 with video/GIF support)

- [ ] **Progress for LOCAL resource scanning**

  - Add ScanProgressCallback support for LocalMediaScanner  - Implement Paging3 library with PagingDataAdapter in MediaFileAdapter  - ✅ VERIFIED: All file operations use IO dispatcher (viewModelScope.launch(ioDispatcher))

  - Show progress overlay in MainActivity for LOCAL resources (>100 resources)

  - Report progress every N files during local scan  - Load files in pages (50-100 items per page)  - ✅ VERIFIED: Memory/disk cache enabled in CoilModule (memoryCachePolicy, diskCachePolicy)



- [ ] **Predictable image caching**  - Add "Loading more..." indicator at list end  - ✅ VERIFIED: Custom NetworkFileFetcher for SMB/SFTP/FTP with 2MB buffer limit for thumbnails

  - Use Coil's ImageRequest.Builder().memoryCacheKey() for consistent cache keys

  - Ensure preloaded images are found in cache during navigation  - Preload next page when scrolling near bottom  - ✅ VERIFIED: Async loading in all adapters (MediaFileAdapter, player activities)

  - Optimize cache hit rate for adjacent files in PlayerActivity

  - Test with folders containing 5000+ files  - ✅ IMPLEMENTED: Bitmap downsampling configuration in CoilModule (Precision.INEXACT, RGB_565)

- [ ] **Background sync for network file existence**

  - Periodically verify network file existence in background  - ✅ IMPLEMENTED: Memory cache size calculation based on device RAM (10-25% depending on total memory)

  - Mark missing files with error indicator in UI

  - Update fileCount when files are added/removed externally### 🔧 High Priority  - ✅ IMPLEMENTED: Hardware bitmap support enabled (allowHardware = true for Android 8+)



- [ ] **Handle stale thumbnails**  - ✅ IMPLEMENTED: Adjacent files preloading in PlayerActivity (previous + next images/GIFs)

  - Detect when file content changes (compare lastModified timestamp)

  - Invalidate Coil cache for changed files- [x] **EXIF metadata extraction**  - ✅ IMPLEMENTED: PlayerViewModel.getAdjacentFiles() method for preloading logic

  - Reload thumbnail on next display

  - ✅ IMPLEMENTED: ExtractExifMetadataUseCase for async EXIF extraction  - ✅ COMPLETED: EXIF metadata extraction (orientation, dateTime, GPS) implemented asynchronously

## 🎨 UI/UX Improvements

  - ✅ IMPLEMENTED: ExifInterface used on IO dispatcher (Dispatchers.IO)  - ⚠️ TODO: Add video metadata extraction (duration, resolution, codec) in background

- [ ] **Accessibility improvements**

  - Add content descriptions for all ImageView/ImageButton elements  - ✅ IMPLEMENTED: EXIF fields in MediaFile model (exifOrientation, exifDateTime, exifLatitude, exifLongitude)

  - Test with TalkBack screen reader

  - Verify minimum touch target size (48dp) for all interactive elements  - ✅ IMPLEMENTED: LocalMediaScanner extracts EXIF for IMAGE files (file:// and content:// URIs)

  - Test high contrast mode compatibility

  - Add accessibility labels for all progress indicators  - ✅ IMPLEMENTED: Coil auto-rotation enabled (automatic EXIF orientation transformations)



- [ ] **Animations and transitions**  - ⚠️ DEFERRED: Network files EXIF (requires downloading headers) - can be done on-demand during viewing

  - Add screen transitions (slide, fade, shared element)

  - Implement RecyclerView item animations (add, remove, reorder)- [ ] **State management and data consistency audit**

  - Add ripple effects to all buttons and clickable items where missing

  - Animate progress indicators smoothly  - ✅ VERIFIED: MVVM architecture with StateFlow (BrowseViewModel, PlayerViewModel, MainViewModel)



- [ ] **Settings: Re-enable player hint toggle**- [x] **Video metadata extraction**  - ✅ VERIFIED: Reactive UI updates via repeatOnLifecycle(State.STARTED) + collect

  - Add preference in Settings to show/hide player touch zones overlay

  - Allow user to re-trigger first-run hint  - ✅ IMPLEMENTED: ExtractVideoMetadataUseCase for async video metadata extraction  - ✅ VERIFIED: File existence check in MediaFileAdapter (localFile.exists()) before loading

  - Test hint dismissal and re-activation

  - ✅ IMPLEMENTED: MediaMetadataRetriever used on IO dispatcher (Dispatchers.IO)  - ✅ VERIFIED: Delete operations update state immediately (remove from list, adjust index)

## ⚡ Performance Optimization

  - ✅ IMPLEMENTED: Video metadata fields in MediaFile (duration, width, height, codec, bitrate, frameRate, rotation)  - ✅ VERIFIED: Undo system for Copy/Move/Delete operations (UndoOperation in BrowseViewModel)

- [ ] **RecyclerView optimizations**

  - Implement RecyclerView.RecycledViewPool for lists with same ViewHolder type  - ✅ IMPLEMENTED: LocalMediaScanner extracts video metadata for VIDEO files (file:// and content:// URIs)  - ✅ VERIFIED: Error placeholders shown for missing files (ic_image_error, ic_video_error)

  - Set optimal itemViewCacheSize based on screen size

  - Profile onBind operations (target <1ms per bind)  - ⚠️ DEFERRED: Network files video metadata (requires downloading) - can be done on-demand during viewing  - ✅ IMPLEMENTED: Automatic list refresh when returning from background (onResume in all activities)



- [ ] **Layout and rendering profiling**- [ ] **FileObserver for external file changes**  - ✅ IMPLEMENTED: MainActivity.onResume() calls viewModel.refreshResources()

  - Run Layout Inspector to check overdraw (<2x target)

  - Profile with GPU Rendering Profiler (target <16ms per frame for 60fps)  - Add FileObserver to detect external deletions/moves in real-time  - ✅ IMPLEMENTED: BrowseActivity.onResume() calls viewModel.reloadFiles() and clearExpiredUndoOperation()

  - Optimize expensive layouts identified by profiling

  - Implement ContentObserver for MediaStore changes (local files)  - ✅ IMPLEMENTED: PlayerActivity.onResume() calls viewModel.reloadFiles()

- [ ] **Database optimization**

  - Profile Room DAO queries with Database Inspector  - Update UI immediately when external changes detected  - ✅ IMPLEMENTED: PlayerViewModel.reloadFiles() public method added

  - Add indexes for frequently queried columns (name, type, size, date)

  - Optimize getAllResourcesSync() query  - Handle gracefully when current file is deleted  - ⚠️ PARTIALLY: File system watcher missing (no ContentObserver/FileObserver for external changes)

  - Implement database pagination for large result sets

  - ⚠️ TODO: Add FileObserver to detect external file deletions/moves in real-time

- [ ] **Memory leak detection**

  - Integrate LeakCanary- [ ] **Byte-level progress for file operations**  - ⚠️ TODO: Handle MediaStore changes with ContentObserver for local files

  - Fix any detected memory leaks

  - Profile with Android Profiler to verify no activity/fragment leaks  - Add bytesTransferred/totalBytes tracking in file operation handlers  - ⚠️ TODO: Add file integrity validation before file operations



- [ ] **Battery optimization**  - Modify SmbFileOperationHandler methods to report byte progress  - ⚠️ TODO: Implement optimistic UI updates with rollback on operation failure

  - Reduce WorkManager task frequency based on battery level

  - Pause background sync on low battery (<15%)  - Update FileOperationProgress with actual byte counts  - ⚠️ TODO: Add background sync to verify network file existence

  - Release player resources immediately when backgrounded

  - Optimize Coil cache trimming  - Calculate and display transfer speed (MB/s)  - ⚠️ TODO: Handle stale thumbnails when file content changes



## 🌐 Network Features  - Report progress every ~100KB to avoid UI overhead



- [ ] **Cloud storage support**- [ ] **Interactivity and feedback audit: Progress indicators & Undo functionality**

  - Google Drive API integration with OAuth2 flow

  - OneDrive API integration- [ ] **Undo button in PlayerActivity**  

  - Dropbox API integration

  - Cloud file operations (browse, copy, move, delete)  - Add undo button for single-file delete operations  **Progress indicators for long operations:**

  - Undo support for cloud operations

  - Integrate with existing UndoOperation system  - ✅ VERIFIED: FileOperationProgressDialog exists and ready (dialog_file_operation_progress.xml)

- [ ] **Offline mode**

  - Cache thumbnails and metadata locally  - Show undo toast after PlayerActivity file operations  - ✅ VERIFIED: FileOperationUseCase.executeWithProgress() returns Flow<FileOperationProgress>

  - Show cached data when network unavailable

  - Add offline status indication in UI  - Test undo for network files (SMB/SFTP)  - ✅ VERIFIED: Progress states: Starting (indeterminate) → Processing (file count) → Completed

  - Implement operation queue for later sync when connection restored

  - ✅ VERIFIED: Shows current file name, progress counter (N/M), transfer speed (B/s, KB/s, MB/s)

## 🧪 Testing

- [ ] **Predictable image caching**  - ✅ VERIFIED: Dialog non-cancelable during operation (setCancelable(false))

- [ ] **Unit tests for domain layer**

  - Write JUnit tests for all UseCase classes  - Use Coil's ImageRequest.Builder().memoryCacheKey() for consistent cache keys  - ✅ VERIFIED: PlayerActivity shows loading indicator after 1 second (showLoadingIndicatorRunnable)

  - Test ViewModels with kotlinx-coroutines-test

  - Mock Repository dependencies with Mockito  - Ensure preloaded images are found in cache during navigation  - ✅ VERIFIED: Simple ProgressBar in CopyToDialog/MoveToDialog (visibility toggle)

  - **Goal**: >80% code coverage for domain layer

  - Optimize cache hit rate for adjacent files  - ✅ IMPLEMENTED: FileOperationProgressDialog integrated in CopyToDialog.copyToDestination()

- [ ] **Instrumented tests**

  - Test Room database operations with Room testing library  - ✅ IMPLEMENTED: FileOperationProgressDialog integrated in MoveToDialog.moveToDestination()

  - UI flow tests with Espresso (MainActivity → BrowseActivity → PlayerActivity)

  - Test file operations with temporary test folders### 🎨 UI/UX Improvements  - ✅ IMPLEMENTED: FileOperationProgressDialog integrated in DeleteDialog.deleteFiles()

  - Test navigation between screens

  - ✅ IMPLEMENTED: Cancel button added to FileOperationProgressDialog with Material3 OutlinedButton

- [ ] **Manual testing checklist**

  - Test on Android 8.0, 9.0, 10, 11, 12, 13, 14- [ ] **Accessibility improvements**  - ✅ IMPLEMENTED: Cancellation support via coroutine Job.cancel() in Copy/Move/Delete dialogs

  - Test on different screen sizes (phone, 7" tablet, 10" tablet)

  - Test all file types: JPEG, PNG, GIF, MP4, WEBM, MOV, MKV, MP3, WAV  - Add content descriptions for all ImageView/ImageButton elements  - ✅ IMPLEMENTED: CancellationException handling with "operation cancelled" messages

  - Test connection scenarios: slow network, no internet, connection drops

  - Test edge cases: empty folders, 5000+ files, 500+ char filenames, special chars  - Test with TalkBack screen reader  - ✅ IMPLEMENTED: ensureActive() checks in FileOperationUseCase.executeWithProgress()



- [ ] **Security audit**  - Verify minimum touch target size (48dp) for all interactive elements  - ✅ IMPLEMENTED: ScanProgressCallback interface for progress reporting during scan operations

  - Check for hardcoded credentials (should be none)

  - Validate input sanitization (IP addresses, file paths, credentials)  - Test high contrast mode compatibility  - ✅ IMPLEMENTED: SmbClient.scanMediaFiles() with progressCallback parameter (reports every 10 files)

  - Test file path traversal prevention

  - Review permission usage (ensure minimal necessary permissions)  - Add accessibility labels for progress indicators  - ✅ IMPLEMENTED: SmbMediaScanner.scanFolderWithProgress() method with callback support



## 🧰 Code Quality & Static Analysis  - ✅ IMPLEMENTED: MainViewModel.scanAllResources() uses callback for SMB resources



- [ ] **Kotlin static analysis (detekt/ktlint)**- [ ] **Animations and transitions**  - ✅ IMPLEMENTED: MainActivity shows scan progress overlay (scanned count + current file name)

  - Add detekt Gradle plugin in root `build.gradle.kts`

  - Create `detekt.yml` configuration (complexity thresholds, naming, style rules)  - Add screen transitions (slide, fade, shared element)  - ⚠️ MISSING: No progress for MainViewModel.scanAllResources() for LOCAL resources (only SMB has callback)

  - Wire `detekt` task into CI/CD pipeline (run before assemble)

  - Optionally add ktlint for strict Kotlin code style  - Implement RecyclerView item animations (add, remove, reorder)  - ⚠️ MISSING: Byte-level progress (bytesTransferred/totalBytes) not tracked in handlers

  - Gradually tighten rules (start warnings-only, later fail build)

  - Add ripple effects to all buttons and clickable items  - ⚠️ TODO: Add byte-level progress in SmbFileOperationHandler:

- [ ] **Edge cases handling**

  - Empty folders handling (show empty state)  - Animate progress indicators smoothly    * Requires callback parameter in downloadFromSmb/uploadToSmb/copySmbToSmb methods

  - Folders with 1000+ files (pagination)

  - Very long file names (>255 chars, truncate display)    * Track bytes read/written during InputStream.copyTo() operations

  - Special characters in file names (test: `файл#123 (copy).jpg`)

  - Corrupted or unsupported media files (show error placeholder)- [ ] **Settings: Re-enable player hint toggle**    * Report progress every ~100KB to avoid too frequent updates



## 📦 Release Preparation  - Add preference in Settings to show/hide player touch zones overlay    * Calculate speed: (currentBytes - lastBytes) / (currentTime - lastTime)



### Build Configuration  - Allow user to re-trigger first-run hint    * Update FileOperationProgress.Processing in Flow with actual byte counts



- [ ] **ProGuard/R8 configuration**  - Test hint dismissal and re-activation    * Challenge: needs refactoring of entire handler call chain to pass callback

  - Configure ProGuard rules for release build

  - Keep classes used via reflection (Room, Coil, ExoPlayer, Hilt)  - ⚠️ TODO: Show progress dialog in MainActivity.scanAllResources() for >100 resources

  - Test obfuscated APK thoroughly on multiple devices

  - Verify all functionality after ProGuard### ⚡ Performance Optimization  



- [ ] **APK signing**  **Undo functionality (rollback last operation):**

  - Configure signing config in `app_v2/build.gradle.kts`

  - Test installation of signed APK- [ ] **RecyclerView optimizations**  - ✅ VERIFIED: Full Undo system implemented (UndoOperation data class)

  - Ensure keystore is NOT in repository (add to .gitignore)

  - Implement RecyclerView.RecycledViewPool for lists with same ViewHolder type  - ✅ VERIFIED: BrowseViewModel.saveUndoOperation() stores last operation

- [ ] **APK size optimization**

  - Enable resource shrinking in release build  - Set optimal itemViewCacheSize based on screen size  - ✅ VERIFIED: BrowseViewModel.undoLastOperation() reverses Copy/Move/Rename/Delete

  - Enable code shrinking (R8) in release build

  - Use vector drawables instead of PNG where possible  - Profile onBind operations (target <1ms per bind)  - ✅ VERIFIED: Undo button in BrowseActivity (btnUndo, visibility based on lastOperation)

  - Remove unused resources with lint

  - Consider Android App Bundle (.aab) for Play Store  - ✅ VERIFIED: CopyToDialog creates UndoOperation with copiedFiles paths



- [ ] **Version management**- [ ] **Layout and rendering profiling**  - ✅ VERIFIED: MoveToDialog creates UndoOperation with source/dest paths

  - Update versionCode in `app_v2/build.gradle.kts`

  - Update versionName to "2.0.0"  - Run Layout Inspector to check overdraw (<2x target)  - ✅ VERIFIED: Rename creates UndoOperation with oldNames pairs (oldPath, newPath)

  - Follow semantic versioning (MAJOR.MINOR.PATCH)

  - Create Git tag for release (e.g., `v2.0.0`)  - Profile with GPU Rendering Profiler (target <16ms per frame for 60fps)  - ✅ IMPLEMENTED: Delete with soft-delete pattern (files moved to .trash folder)



- [ ] **Dependencies update**  - Optimize expensive layouts identified by profiling  - ✅ IMPLEMENTED: Delete undo restores files from trash folder to original locations

  - Update all libraries to latest stable versions

  - Test after each major update  - ✅ VERIFIED: Settings toggle: AppSettings.enableUndo (default true)

  - Check for deprecated APIs and fix

  - Update Kotlin version if stable release available- [ ] **Database optimization**  - ✅ VERIFIED: Undo logic:



### Documentation  - Profile Room DAO queries with Database Inspector    * COPY → delete copied files from destination



- [ ] **README updates**  - Add indexes for frequently queried columns (name, type, size, date)    * MOVE → move files back to source (renameTo)

  - Update `README.md` with v2 features

  - Update `README.ru.md` (Russian version)  - Optimize getAllResourcesSync() query    * RENAME → rename back to old name

  - Update `README.ua.md` (Ukrainian version)

  - Add new screenshots (MainActivity, BrowseActivity, PlayerActivity)  - Implement database pagination for large result sets    * DELETE → restore files from .trash folder

  - Update build instructions

  - Document new features: SFTP, FTP, Undo, Preloading, Image Editing, etc.  - ✅ IMPLEMENTED: Undo toast notification after operations ("Files copied/moved/deleted. Tap UNDO to revert.")



- [ ] **CHANGELOG**- [ ] **Memory leak detection**  - ✅ IMPLEMENTED: Clear undo on new operation (saveUndoOperation clears previous undo automatically)

  - Create `CHANGELOG.md` documenting all changes since v1

  - Group by: Added, Changed, Fixed, Removed  - Integrate LeakCanary  - ✅ IMPLEMENTED: Undo operation expiry (5 minutes timeout tracked via undoOperationTimestamp)

  - Specify version and release date

  - Highlight breaking changes from v1  - Fix any detected memory leaks  - ✅ IMPLEMENTED: clearExpiredUndoOperation() called in BrowseActivity.onResume()



- [ ] **User documentation**  - Profile with Android Profiler to verify no activity/fragment leaks  - ⚠️ PARTIALLY: No undo button in PlayerActivity (only BrowseActivity)

  - Create comprehensive user guide in docs/

  - Document all features: resources, browsing, player, settings- [x] **Undo button in PlayerActivity for single-file operations**

  - Document all gestures: swipe, long press, double press  - ✅ IMPLEMENTED: Added lastOperation and undoOperationTimestamp fields to PlayerState

  - Create FAQ section  - ✅ IMPLEMENTED: Modified deleteCurrentFile() to use soft-delete (move to .trash_<timestamp> folder)

  - Write troubleshooting guide (network issues, permissions)  - ✅ IMPLEMENTED: saveUndoOperation() method stores UndoOperation with timestamp

  - ✅ IMPLEMENTED: undoLastOperation() restores file from trash folder to original location

## 🚀 Google Play Store  - ✅ IMPLEMENTED: clearExpiredUndoOperation() clears operations older than 5 minutes

  - ✅ IMPLEMENTED: btnUndoCmd button click handler in PlayerActivity.setupCommandPanelControls()

### Store Materials  - ✅ IMPLEMENTED: Undo button visibility logic in updateCommandAvailability() (visible when lastOperation != null)

  - ✅ IMPLEMENTED: clearExpiredUndoOperation() called in PlayerActivity.onResume()

- [ ] **Store listing**  - ✅ IMPLEMENTED: Toast message after delete: "File deleted. Tap UNDO to restore."

  - App title (max 30 characters): "FastMediaSorter"  - ✅ IMPLEMENTED: reloadFiles() called after successful undo to update file list

  - Short description (max 80 characters)  - ⚠️ NOTE: Only local file operations supported (network files undo not yet implemented)

  - Full description (max 4000 characters)

  - Translations: English, Russian, Ukrainian- [ ] **Battery optimization**  - ✅ IMPLEMENTED: Automatic cleanup of old trash folders (older than 5 minutes)



- [ ] **Screenshots**  - Reduce WorkManager task frequency based on battery level  - ✅ IMPLEMENTED: CleanupTrashFoldersUseCase - recursively searches and deletes old .trash_* folders

  - Capture 4-8 screenshots per device type (phone, 7" tablet, 10" tablet)

  - Show key features: Main screen, Browse (list/grid), Player (fullscreen/panels)  - Pause background sync on low battery (<15%)  - ✅ IMPLEMENTED: TrashCleanupWorker - periodic WorkManager task (runs every 15 minutes)

  - Add device frames and text annotations

  - Create localized versions (en, ru, uk)  - Release player resources immediately when backgrounded  - ✅ IMPLEMENTED: WorkManagerScheduler - schedules periodic cleanup on app start



- [ ] **Feature graphic**  - Optimize Coil cache trimming  - ✅ IMPLEMENTED: FastMediaSorterApp calls scheduleTrashCleanup() in onCreate()

  - Design 1024x500px feature graphic in Figma/Photoshop

  - Include app branding and key visual  - ⚠️ TODO: Test undo for network files (SMB/SFTP operations)

  - Follow Google Play design guidelines

  - Create localized versions if needed### 🌐 Network Features  



- [ ] **App icon**  **Critical issues:**

  - Verify adaptive icon (foreground + background)

  - Test on different launchers (Pixel, Samsung, OnePlus)- [ ] **Progress for LOCAL resource scanning**  1. **RESOLVED**: FileOperationProgressDialog now INTEGRATED in Copy/Move/Delete dialogs (executeWithProgress used)

  - Ensure compliance with Google Play guidelines

  - Verify all mipmap sizes generated correctly  - Add ScanProgressCallback support for LocalMediaScanner  2. **RESOLVED**: Delete undo now WORKS (soft-delete pattern with .trash folder restoration)



- [ ] **Privacy Policy**  - Show progress overlay in MainActivity for LOCAL resources (>100 resources)  3. **RESOLVED**: SMB scanning progress IMPLEMENTED (ScanProgressCallback, UI overlay in MainActivity)

  - Update `PRIVACY_POLICY.md` for v2 data usage

  - Document all permissions and their purposes  - Report progress every N files during local scan  4. **RESOLVED**: Cancellation support IMPLEMENTED for long operations (cancel button, Job.cancel(), CancellationException handling)

  - Include developer contact information

  - Host online (GitHub Pages or dedicated site)  5. **MEDIUM**: No byte-level progress (users can't see transfer speed for large files)



- [ ] **Content rating**  6. **RESOLVED**: Undo in PlayerActivity IMPLEMENTED (soft-delete with .trash folder, 5 min expiry, btnUndoCmd visibility)

  - Complete IARC questionnaire in Play Console

  - Expected rating: Everyone (no violence, no ads)- [ ] **Background sync for network file existence**  

  - Review content descriptors

  - Periodically verify network file existence in background

### Release Process

  - Mark missing files with error indicator- [ ] **UI: Accessibility improvements**

- [ ] **Internal testing track**

  - Upload APK/AAB to Play Console Internal Testing  - Update fileCount when files are added/removed externally  - Add content descriptions for all images/icons

  - Test installation and update flow from v1

  - Verify all functionality in production build  - Test with TalkBack screen reader

  - Upload ProGuard mapping file for stack trace deobfuscation

- [ ] **Handle stale thumbnails**  - Verify minimum touch target size (48dp) for all buttons

- [ ] **Closed beta testing**

  - Promote to Closed Testing track  - Detect when file content changes (compare lastModified)  - Test high contrast mode compatibility

  - Add beta testers (10-20 users)

  - Monitor crash reports and ANRs in Play Console  - Invalidate Coil cache for changed files  - Add accessibility labels for all interactive elements

  - Collect and address feedback via Google Forms

  - Reload thumbnail on next display

- [ ] **Production release**

  - Promote to Production track- [x] **Player: first-run touch zones overlay**

  - Start with staged rollout (10% for 24h)

  - Monitor crash-free rate (target >99%)### 🧪 Testing  - ✅ IMPLEMENTED: Added `showPlayerHintOnFirstRun` flag to AppSettings (default true)

  - Monitor ANR rate (target <0.5%)

  - Gradually increase to 50%, then 100%  - ✅ IMPLEMENTED: Added preference key and setter in SettingsManager



- [ ] **Post-release monitoring**- [ ] **Unit tests for domain layer**  - ✅ IMPLEMENTED: Show audioTouchZonesOverlay on first PlayerActivity launch (alpha 0.9)

  - Monitor Play Console metrics (installs, uninstalls, crashes, ratings)

  - Respond to user reviews within 24-48h  - Write JUnit tests for all UseCase classes  - ✅ IMPLEMENTED: Dismiss overlay on tap or after 5 seconds timeout

  - Track Firebase Analytics events (if integrated)

  - Monitor Firebase Crashlytics reports (if integrated)  - Test ViewModels with kotlinx-coroutines-test  - ✅ IMPLEMENTED: Save flag to DataStore after first dismissal



---  - Mock Repository dependencies with Mockito  - ⚠️ TODO: Add optional setting in Settings screen to re-enable or disable this hint



## 🆕 Additional Features from Specification  - **Goal**: >80% code coverage for domain layer



### Missing from Current Implementation- [ ] **UI: Animations and transitions**



- [ ] **File filtering in BrowseActivity**- [ ] **Instrumented tests**  - Add screen transitions (slide, fade animations)

  - Filter by name (text search)

  - Filter by date range (date picker dialog)  - Test Room database operations with Room testing library  - Implement list animations (add, remove, reorder items)

  - Filter by size range (size slider)

  - Temporarily apply filter without changing sort order  - UI flow tests with Espresso (MainActivity → BrowseActivity → PlayerActivity)  - Add button ripple effects where missing



- [ ] **Destination management improvements**  - Test file operations with temporary test folders  - Improve progress indicators animations

  - Support up to 10 destinations (currently unlimited)

  - Colored buttons for each destination  - Test navigation between screens

  - Reorder destinations

  - Configure per-destination settings (overwrite, subfolder)- [ ] **Performance: Memory optimization**



- [ ] **File integrity validation**- [ ] **Manual testing checklist**  - Profile with Android Profiler

  - Validate file existence before copy/move operations

  - Check available space on destination  - Test on Android 8.0, 9.0, 10, 11, 12, 13, 14  - Fix memory leaks using LeakCanary

  - Verify file is readable/writable

  - Show warning if operation might fail  - Test on different screen sizes (phone, 7" tablet, 10" tablet)  - ✅ IMPLEMENTED: Bitmap downsampling configuration in CoilModule (Precision.INEXACT, RGB_565)



- [ ] **Advanced rename features**  - Test all file types: JPEG, PNG, GIF, MP4, WEBM, MOV, MKV, MP3, WAV  - ✅ IMPLEMENTED: Memory cache size calculation based on device RAM (10-25% depending on total memory)

  - Batch rename with pattern (e.g., `photo_{index}.jpg`)

  - Rename with metadata (e.g., add date to filename)  - Test connection scenarios: slow network, no internet, connection drops  - ✅ IMPLEMENTED: Hardware bitmap support enabled (allowHardware = true)

  - Preview rename before applying

  - Test edge cases: empty folders, 5000+ files, 500+ char filenames, special chars  - Implement pagination for large file lists (1000+ files)

- [ ] **Player caching improvements**

  - Cache next 2-3 files (not just next 1)

  - Preload video first frame for instant display

  - Cache files in both directions (prev + next)- [ ] **Security audit**### 🐛 Bug Fixes



- [ ] **Audio file display**  - Check for hardcoded credentials (should be none)

  - Show audio file metadata (title, artist, album, duration)

  - Display album art if available  - Validate input sanitization (IP addresses, file paths, credentials)- [ ] **SMB Authentication test after password migration**

  - Waveform visualization (optional)

  - Test file path traversal prevention  - Test SMB connection with migrated passwords

- [ ] **Settings enhancements**

  - Default network credentials storage  - Review permission usage (ensure minimal necessary permissions)  - Verify plaintext fallback works correctly

  - Log access in Settings (view app logs)

  - Per-destination overwrite toggle  - Document password encryption migration process

  - Slideshow "play to end" option for videos

### 🧰 Code Quality

- [ ] **Localization completeness**

  - Verify all UI strings are translatable### 🌐 Network Features

  - Complete Russian translations

  - Complete Ukrainian translations- [ ] **Static analysis integration**

  - Test language switching without restart

  - Add detekt Gradle plugin to root `build.gradle.kts`- [ ] **Cloud: Google Drive API Integration**

- [ ] **Logical sort by name**

  - Implement natural sort (file1, file2, file10 vs file1, file10, file2)  - Create `detekt.yml` configuration with complexity thresholds  - Implement Google Sign-In and Drive API

  - Test with numeric filenames

  - Wire detekt into CI/CD pipeline (run before assemble)  - Add folder browsing and file operations

---

  - Optionally add ktlint for strict code style  - Handle OAuth2 flow and token storage

## 📊 Implementation Status Summary

  - Gradually tighten rules (start warnings-only, later fail build)  - Adapt copy/move operations for cloud files

### ✅ Completed Features



**Core Architecture:**

- MVVM architecture with StateFlow- [ ] **Edge cases handling**- [ ] **Cloud: Dropbox API Integration**

- Hilt dependency injection

- Room database with migrations  - Empty folders handling (show empty state)  - Integrate Dropbox SDK

- Kotlin Coroutines with structured concurrency

- Repository pattern for data layer  - Folders with 1000+ files (pagination)  - Implement authentication and file access



**File Operations:**  - Very long file names (>255 chars, truncate display)  - Add folder sync and operations

- Copy, Move, Rename, Delete operations for local files

- Progress tracking with Flow  - Special characters in file names (test: `файл#123 (copy).jpg`)  - Ensure compatibility with existing file operations

- Cancellation support for long operations

- Soft-delete with .trash folders for local files  - Corrupted or unsupported media files (show error placeholder)

- Automatic trash cleanup (5-minute retention, 15-minute periodic cleanup)

- [ ] **Background synchronization**

**Undo System:**

- Full undo for Copy/Move/Rename/Delete in BrowseActivity### 🐛 Bug Fixes  - Use WorkManager for periodic sync

- Single-file delete undo in PlayerActivity

- Toast notifications after operations  - Check for new/deleted files in network/cloud resources

- 5-minute expiry timeout

- Automatic cleanup of expired undo operations- [ ] **SMB Authentication test after password migration**  - Update fileCount and thumbnail cache



**Image Editing:**  - Test SMB connection with migrated encrypted passwords  - Add synchronization status indicator in UI

- Rotate images (90°, -90°) with RotateImageUseCase

- Flip images (horizontal, vertical) with FlipImageUseCase  - Verify plaintext fallback works correctly if decryption fails

- EXIF metadata preservation during editing

- Progress indication during image processing  - Document password encryption migration process in README- [ ] **Offline mode**

- Only local files supported (network files pending)

  - Cache thumbnails and metadata locally

**Network Support:**

- SMB (Samba) file operations---  - Show cached data when network unavailable

- SFTP file operations

- FTP file operations (basic)  - Add offline status indication in UI

- Mixed operations between protocols

## 📦 Release Preparation  - Implement operation queue for later sync

**UI/UX:**

- Dark/Light theme with system detection

- Display modes: List and Grid (saved per resource)

- Dynamic grid columns based on screen size### Build Configuration### 🧪 Testing

- Last viewed file position saved per resource

- Random sort mode for slideshow

- Player first-run touch zones overlay

- Collapsible Copy/Move panels in PlayerActivity- [ ] **ProGuard/R8 configuration**- [ ] **Unit tests**

- Information button with FileInfoDialog (EXIF, video metadata)

- Edit button for images (visible only with write permissions)  - Configure ProGuard rules for release build  - Write tests for all UseCase classes with JUnit



**Performance:**  - Keep classes used via reflection (Room, Coil, ExoPlayer, Hilt)  - Test ViewModels with kotlinx-coroutines-test

- Coil image loading with memory/disk cache

- Bitmap downsampling (Precision.INEXACT, RGB_565)  - Test obfuscated APK thoroughly on multiple devices  - Mock Repository dependencies

- Hardware bitmaps (Android 8+)

- Memory cache sizing based on device RAM (10-25%)  - Verify all functionality after ProGuard  - Goal: >80% code coverage for domain layer

- Adjacent files preloading (previous + next)

- DiffUtil for efficient RecyclerView updates

- Automatic list refresh on resume (all activities)

- [ ] **APK signing**- [ ] **Instrumented tests**

**Progress Indicators:**

- FileOperationProgressDialog for Copy/Move/Delete  - ✅ VERIFIED: Release keystore exists (`fastmediasorter.keystore`)  - Test database operations with Room testing library

- SMB scan progress overlay in MainActivity

- Loading indicators in PlayerActivity  - Configure signing config in `app_v2/build.gradle.kts`  - UI flow tests with Espresso

- Cancel button for long operations

  - Test installation of signed APK  - Navigation between screens testing

**Build System:**

- Gradle 8.2.1 with Kotlin DSL  - Ensure keystore is NOT in repository (add to .gitignore)  - File operations with temporary test folders

- AGP 8.2.1

- Automatic version code/name generation

- Multi-module structure (app_v2 + V1)

- [ ] **APK size optimization**- [ ] **Manual testing checklist**

---

  - Enable resource shrinking in release build  - Test on Android versions 8.0 - 14.0

## 🔄 Recent Changes

  - Enable code shrinking (R8) in release build  - Test on different screen sizes (phone, tablet)

**Latest Build**: 2.0.0-build2511141808

  - Use vector drawables instead of PNG where possible  - Test different file types and sizes

- 2024-11-14: Image editing functionality (rotate, flip) for local files

- 2024-11-14: Undo functionality in PlayerActivity for single-file delete  - Remove unused resources with lint  - Test connection scenarios (slow network, no internet, connection drops)

- 2024-11-14: Collapsible Copy/Move panels with persistent state

- 2024-11-14: Information button with comprehensive file metadata  - Consider Android App Bundle (.aab) for Play Store

- 2024-11-14: Edit button with write permission checks

- [ ] **Security audit**

---

- [ ] **Version management**  - Check for hardcoded credentials

## 🎯 Next Sprint (Priority Order)

  - Update versionCode in `app_v2/build.gradle.kts`  - Validate input sanitization

1. **Pagination for 1000+ files** (BLOCKER - OOM risk)

2. **Undo support for network files** (SMB/SFTP/FTP)  - Update versionName to "2.0.0"  - Test file path traversal prevention

3. **Image editing for network files** (download → edit → upload)

4. **Byte-level progress** for file operations  - Follow semantic versioning (MAJOR.MINOR.PATCH)  - Review permission usage

5. **FileObserver** for real-time external change detection

  - Create Git tag for release (e.g., `v2.0.0`)

### 🧰 Code Quality & Static Analysis

- [ ] **Dependencies update**

  - Update all libraries to latest stable versions- [ ] **Kotlin static analysis integration (detekt/ktlint)**

  - Test after each major update  - ✅ VERIFIED: Android Lint is enabled in `app_v2/build.gradle.kts` (checkAllWarnings = true, baseline = lint-baseline.xml)

  - Check for deprecated APIs and fix  - ⚠️ MISSING: No dedicated Kotlin static analysis tool (detekt or ktlint) configured yet

  - Update Kotlin version if stable release available  - ⚠️ TODO: Add detekt Gradle plugin in root `build.gradle.kts` for project-wide Kotlin analysis

  - ⚠️ TODO: Create `detekt.yml` configuration (complexity thresholds, naming, style rules) in project root

### Documentation  - ⚠️ TODO: Wire `detekt` task into CI/CD pipeline (run before assemble / test stages)

  - ⚠️ TODO: Optionally add ktlint (or detekt-formatting) for strict Kotlin code style and auto-format support

- [ ] **README updates**  - ⚠️ TODO: Gradually tighten detekt/ktlint rules (start as warnings-only, later fail build on violations)

  - Update `README.md` with v2 features

  - Update `README.ru.md` (Russian version)### ⚡ Performance Optimization

  - Update `README.ua.md` (Ukrainian version)

  - Add new screenshots (MainActivity, BrowseActivity, PlayerActivity)- [ ] **Performance audit: FastMediaSorter FAST promise verification**

  - Update build instructions  

  - Document new features: SFTP, FTP, Undo, Preloading, etc.  **File operations and scanning:**

  - ✅ VERIFIED: LocalMediaScanner uses MediaStore API (efficient for local files)

- [ ] **CHANGELOG**  - ✅ VERIFIED: SmbMediaScanner.scanFolderChunked() - lazy loading first 100 files (maxFiles parameter)

  - Create `CHANGELOG.md` documenting all changes since v1  - ✅ VERIFIED: useChunkedLoading flag in BrowseViewModel/PlayerViewModel for SMB resources

  - Group by: Added, Changed, Fixed, Removed  - ✅ VERIFIED: File operations on IO dispatcher (viewModelScope.launch(ioDispatcher))

  - Specify version and release date  - ✅ VERIFIED: Structured concurrency with proper error handling (exceptionHandler)

  - Highlight breaking changes from v1  - ⚠️ PARTIALLY: No progress callback for SMB scanning (unlike V1's ScanProgressCallback)

  - ⚠️ TODO: Add ScanProgressCallback to SmbMediaScanner for long operations (>2 seconds)

- [ ] **User documentation**  - ⚠️ TODO: Implement batch processing with UI updates for large folders (1000+ files)

  - Create comprehensive user guide in docs/  - ⚠️ TODO: Add cancellation support for long-running scans (Job cancellation)

  - Document all features: resources, browsing, player, settings  

  - Document all gestures: swipe, long press, double press  **RecyclerView optimization:**

  - Create FAQ section  - ✅ VERIFIED: MediaFileAdapter extends ListAdapter with DiffUtil.ItemCallback

  - Write troubleshooting guide (network issues, permissions)  - ✅ VERIFIED: DiffUtil compares by path (areItemsTheSame) and full equality (areContentsTheSame)

  - ✅ VERIFIED: submitList() used everywhere (MainActivity, BrowseActivity) - async diff

---  - ✅ FIXED: MediaFileAdapter.setGridMode() now uses notifyItemRangeChanged() with payload

  - ✅ FIXED: DestinationsAdapter refactored to ListAdapter with DiffUtil

## 🚀 Google Play Store  - ✅ FIXED: ResourceToAddAdapter.setSelectedPaths() uses targeted notifyItemChanged()

  - ⚠️ TODO: Add RecyclerView.RecycledViewPool for multiple lists with same ViewHolder type

### Store Materials  - ⚠️ TODO: Set recyclerView.setItemViewCacheSize() for frequent scrolling

  

- [ ] **Store listing**  **Layout and rendering:**

  - App title (max 30 characters): "FastMediaSorter"  - ✅ VERIFIED: ConstraintLayout used in most layouts (flat hierarchy)

  - Short description (max 80 characters)  - ✅ VERIFIED: ViewBinding prevents findViewById() overhead

  - Full description (max 4000 characters)  - ✅ VERIFIED: Coil handles image loading/decoding on background threads

  - Translations: English, Russian, Ukrainian  - ⚠️ TODO: Run Layout Inspector to check overdraw (should be <2x on most screens)

  - ⚠️ TODO: Profile with GPU Rendering Profiler (target <16ms per frame for 60fps)

- [ ] **Screenshots**  - ⚠️ TODO: Check for expensive onBind operations in adapters (should be <1ms)

  - Capture 4-8 screenshots per device type (phone, 7" tablet, 10" tablet)  

  - Show key features: Main screen, Browse (list/grid), Player (fullscreen/panels)  **Pagination and large datasets:**

  - Add device frames and text annotations  - ✅ VERIFIED: MediaFilesPagingSource skeleton exists (commented: "implement chunked loading later")

  - Create localized versions (en, ru, uk)  - ⚠️ MISSING: No actual pagination implementation for 1000+ files

  - ⚠️ TODO: Implement Paging3 library with PagingDataAdapter

- [ ] **Feature graphic**  - ⚠️ TODO: Load files in pages (50-100 items) for large folders

  - Design 1024x500px feature graphic in Figma/Photoshop  - ⚠️ TODO: Add "Loading more..." indicator at list end

  - Include app branding and key visual  - ⚠️ TODO: Preload next page when scrolling near bottom

  - Follow Google Play design guidelines  

  - Create localized versions if needed  **Preloading and caching:**

  - ✅ IMPLEMENTED: Coil memory/disk cache enabled with device-adaptive sizing

- [ ] **App icon**  - ✅ IMPLEMENTED: Bitmap downsampling (Precision.INEXACT) reduces memory usage for large images

  - ✅ VERIFIED: Adaptive icon exists (foreground + background)  - ✅ IMPLEMENTED: Hardware bitmaps enabled (Android 8+) for better memory efficiency

  - Test on different launchers (Pixel, Samsung, OnePlus)  - ✅ IMPLEMENTED: RGB_565 bitmap config for non-transparent images (50% memory savings)

  - Ensure compliance with Google Play guidelines  - ✅ IMPLEMENTED: Memory cache size scales with device RAM (10-25% allocation)

  - Verify all mipmap sizes generated correctly  - ✅ IMPLEMENTED: PlayerViewModel.getAdjacentFiles() - returns previous + next files for preloading

  - ✅ IMPLEMENTED: PlayerActivity preloads both previous and next images/GIFs in background

- [ ] **Privacy Policy**  - ✅ IMPLEMENTED: Coil's ImageLoader.enqueue() used for background preloading

  - Update `PRIVACY_POLICY.md` for v2 data usage  - ⚠️ TODO: Use Coil's ImageRequest.Builder().memoryCacheKey() for predictable caching

  - Document all permissions and their purposes  

  - Include developer contact information  **Critical issues for "FAST" promise:**

  - Host online (GitHub Pages or dedicated site)  1. **RESOLVED**: notifyDataSetChanged() replaced with efficient updates (notifyItemRangeChanged, DiffUtil)

  2. **BLOCKER**: No pagination for 1000+ files (single query loads all = OOM risk)

- [ ] **Content rating**  3. **RESOLVED**: SMB scan progress indication implemented (ScanProgressCallback, UI overlay)

  - Complete IARC questionnaire in Play Console  4. **RESOLVED**: Preloading implemented (previous + next images/GIFs in PlayerActivity)

  - Expected rating: Everyone (no violence, no ads)  5. **MEDIUM**: Missing RecycledViewPool optimization (memory inefficiency)

  - Review content descriptors

- [ ] **Database optimization**

### Release Process  - Profile database queries

  - Add indexes for frequent queries

- [ ] **Internal testing track**  - Optimize Room DAO methods

  - Upload APK/AAB to Play Console Internal Testing  - Consider pagination for large datasets

  - Test installation and update flow from v1

  - Verify all functionality in production build- [ ] **Battery optimization**

  - Upload ProGuard mapping file for stack trace deobfuscation  - Reduce background work frequency

  - Efficient use of JobScheduler/WorkManager

- [ ] **Closed beta testing**  - Pause synchronization on low battery

  - Promote to Closed Testing track  - Release resources properly when backgrounded

  - Add beta testers (10-20 users)

  - Monitor crash reports and ANRs in Play Console- [ ] **Edge cases handling**

  - Collect and address feedback via Google Forms  - Empty folders handling

  - Folders with 1000+ files

- [ ] **Production release**  - Very long file names (>255 chars)

  - Promote to Production track  - Special characters in file names

  - Start with staged rollout (10% for 24h)  - Corrupted or unsupported media files

  - Monitor crash-free rate (target >99%)

  - Monitor ANR rate (target <0.5%)---

  - Gradually increase to 50%, then 100%

## 📦 Release Preparation

- [ ] **Post-release monitoring**

  - Monitor Play Console metrics (installs, uninstalls, crashes, ratings)### Build Configuration

  - Respond to user reviews within 24-48h

  - Track Firebase Analytics events (if integrated)- [ ] **ProGuard/R8 configuration**

  - Monitor Firebase Crashlytics reports (if integrated)  - Configure ProGuard rules for release build

  - Test obfuscated APK thoroughly

---  - Keep classes used via reflection

  - Verify all functionality after ProGuard

## 📊 Implementation Status Summary

- [ ] **APK signing**

### ✅ Completed Features  - Verify release keystore exists and is secure

  - Configure signing in build.gradle.kts

**Core Architecture:**  - Test installation of signed APK

- MVVM architecture with StateFlow  - Store keystore securely (not in repository)

- Hilt dependency injection

- Room database with migrations- [ ] **APK size optimization**

- Kotlin Coroutines with structured concurrency  - Enable resource shrinking

- Repository pattern for data layer  - Enable code shrinking (R8)

  - Use vector drawables instead of PNG where possible

**File Operations:**  - Remove unused resources and dependencies

- Copy, Move, Rename, Delete operations  - Consider Android App Bundle (.aab)

- Progress tracking with Flow

- Cancellation support- [ ] **Version management**

- Soft-delete with .trash folders  - Update versionCode in build.gradle.kts

- Automatic trash cleanup (5-minute retention, 15-minute periodic cleanup)  - Update versionName (2.0.0)

  - Follow semantic versioning (MAJOR.MINOR.PATCH)

**Undo System:**  - Tag release in Git

- Full undo for Copy/Move/Rename/Delete

- Toast notifications after operations- [ ] **Dependencies update**

- 5-minute expiry timeout  - Update all libraries to latest stable versions

- Automatic cleanup of expired undo operations  - Test after each major update

  - Check for deprecated APIs

**Network Support:**  - Fix any breaking changes

- SMB (Samba) file operations

- SFTP file operations### Documentation

- FTP file operations (basic)

- Mixed operations between protocols- [ ] **README updates**

  - Update README.md with v2 features

**UI/UX:**  - Update Russian (README.ru.md) and Ukrainian (README.ua.md) versions

- Dark/Light theme with system detection  - Add new UI screenshots

- Display modes: List and Grid (saved per resource)  - Update build and installation instructions

- Dynamic grid columns based on screen size

- Last viewed file position saved per resource- [ ] **CHANGELOG**

- Random sort mode for slideshow  - Document all changes since v1

- Player first-run touch zones overlay  - Group by: Added, Changed, Fixed, Removed

  - Specify version and release date

**Performance:**  - Highlight breaking changes

- Coil image loading with memory/disk cache

- Bitmap downsampling (Precision.INEXACT, RGB_565)- [ ] **User documentation**

- Hardware bitmaps (Android 8+)  - Create comprehensive user guide

- Memory cache sizing based on device RAM (10-25%)  - Document all features and gestures

- Adjacent files preloading (previous + next)  - Create FAQ section

- DiffUtil for efficient RecyclerView updates  - Write troubleshooting guide

- Automatic list refresh on resume (all activities)

---

**Progress Indicators:**

- FileOperationProgressDialog for Copy/Move/Delete## 🚀 Google Play Store

- SMB scan progress overlay in MainActivity

- Loading indicators in PlayerActivity### Store Materials

- Cancel button for long operations

- [ ] **Store listing**

**Build System:**  - App title (max 30 characters)

- Gradle 8.2.1 with Kotlin DSL  - Short description (max 80 characters)

- AGP 8.2.1  - Full description (max 4000 characters)

- Automatic version code/name generation  - Translations: English, Russian, Ukrainian

- Multi-module structure (app_v2 + V1)

- [ ] **Screenshots**

### 🚧 In Progress  - Capture 4-8 screenshots per device type (phone, tablet)

  - Show key features: Main screen, Browse, Player

None currently.  - Add device frames and annotations

  - Create localized versions (en, ru, uk)

### ⏳ Next Priorities (by impact)

- [ ] **Feature graphic**

1. **BLOCKER**: Pagination (1000+ files OOM risk)  - Design 1024x500px feature graphic

2. **HIGH**: FileObserver (real-time external change detection)  - Include app branding and key visual

3. **HIGH**: EXIF/Video metadata extraction  - Follow Google Play design guidelines

4. **MEDIUM**: Byte-level progress for file operations  - Create localized versions if needed

5. **MEDIUM**: Accessibility improvements

6. **LOW**: UI animations and transitions- [ ] **App icon**

  - Verify adaptive icon (foreground + background)

---  - Test on different launchers

  - Ensure compliance with Google Play guidelines

## 🔄 Version History  - Verify all mipmap sizes generated



**Latest Build**: 2.0.0-build2511141236- [ ] **Privacy Policy**

  - Update policy for v2 data usage

**Recent Milestones:**  - Document all permissions and their purposes

- 2024-11-14: Undo enhancements (toast, expiry, auto-clear)  - Include contact information

- 2024-11-14: Adjacent files preloading (previous + next)  - Host online (e.g., GitHub Pages)

- 2024-11-14: Bitmap downsampling and memory optimization

- 2024-11-14: Automatic list refresh on resume (all activities)- [ ] **Content rating**

- 2024-11-14: Trash cleanup automation (WorkManager)  - Complete IARC questionnaire in Play Console

- 2024-11-14: SMB scan progress with UI overlay  - Verify age rating (likely Everyone)

- 2024-11-14: Cancellation support for long operations  - Review content descriptors

- 2024-11-14: FileOperationProgressDialog integration

### Release Process

---

- [ ] **Internal testing track**

## 🆕 Additional Tasks from Specification Analysis  - Upload APK/AAB to Play Console Internal Testing

  - Test installation and update flow

### Missing from Current Implementation  - Verify all functionality in production build

  - Upload ProGuard mapping file

- [ ] **Cloud storage support (per specification 1.3.2)**

  - Google Drive API integration- [ ] **Closed beta testing**

  - OneDrive API integration  - Promote to Closed Testing track

  - Dropbox API integration  - Add beta testers (5-20 users)

  - OAuth2 flow implementation  - Monitor crash reports and ANRs

  - Cloud file operations (copy, move, delete)  - Collect and address feedback



- [ ] **File filtering in BrowseActivity (per specification 1.3.3)**- [ ] **Production release**

  - Filter by name (text search)  - Promote to Production track

  - Filter by date range (date picker dialog)  - Start with staged rollout (10-20%)

  - Filter by size range (size slider)  - Monitor crash-free rate and ANR rate

  - Temporarily apply filter without changing sort order  - Gradually increase to 100%



- [ ] **Destination management improvements (per specification 1.3.4)**- [ ] **Post-release monitoring**

  - Support up to 10 destinations (currently unlimited)  - Monitor Play Console metrics (installs, crashes, ratings)

  - Colored buttons for each destination  - Respond to user reviews promptly

  - Reorder destinations  - Track Firebase Analytics events

  - Configure per-destination settings (overwrite, subfolder)  - Monitor Firebase Crashlytics reports



- [ ] **File integrity validation (per specification 1.3.4)**---

  - Validate file existence before copy/move operations

  - Check available space on destination## 📊 Project Status

  - Verify file is readable/writable

  - Show warning if operation might fail**Completed (Latest Build: 2.0.0-build2511140258):**

- ✅ FTP connection stability fixes

- [ ] **Advanced rename features (per specification 1.3.4)**- ✅ SFTP file attributes (size, date)

  - Batch rename with pattern (e.g., `photo_{index}.jpg`)- ✅ Mixed operations SMB↔SFTP

  - Rename with metadata (e.g., add date to filename)- ✅ Progress bars infrastructure

  - Preview rename before applying- ✅ Empty states with network error handling

- ✅ Touch zones numbered diagram with legend

- [ ] **Player caching improvements (per specification 1.3.5)**- ✅ Display mode (list/grid) saved per resource

  - Cache next 2-3 files (not just next 1)- ✅ Dynamic grid columns based on screen size

  - Preload video first frame for instant display- ✅ Last viewed file position saved per resource

  - Cache files in both directions (prev + next)- ✅ Random sort mode for slideshow

- ✅ SMB delete operation fixed

- [ ] **Audio file display (per specification 1.3.5)**- ✅ Splash screen removed (Welcome only on first launch)

  - Show audio file metadata (title, artist, album, duration)- ✅ Enhanced local folder scanning (including Android/Media)

  - Display album art if available- ✅ IP address input validation improved

  - Waveform visualization (optional)- ✅ Dark/Light theme support with system detection

- ✅ Scroll gesture detection in resource list

- [ ] **Settings enhancements (per specification 1.3.6)**

  - Default network credentials storage**Next priorities:**

  - Log access in Settings (view app logs)1. Integrate progress dialogs with file operations

  - Per-destination overwrite toggle2. Accessibility improvements (content descriptions, TalkBack)

  - Slideshow "play to end" option for videos3. UI animations and transitions

4. Performance optimization (memory, database)

- [ ] **Localization completeness (per specification 1.3.1)**5. Unit and instrumented testing

  - Verify all UI strings are translatable6. Google Play Store preparation

  - Complete Russian translations

  - Complete Ukrainian translations
  - Test language switching without restart

- [ ] **Logical sort by name (per specification 1.3.3)**
  - Implement natural sort (file1, file2, file10 vs file1, file10, file2)
  - Test with numeric filenames
