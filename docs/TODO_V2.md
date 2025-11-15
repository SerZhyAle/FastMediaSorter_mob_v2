# TODO V2 - FastMediaSorter v2

## 🎯 Current Development Tasks

- [ ] **Pagination for large datasets (1000+ files)**
  - **Status**: ✅ COMPLETED - Integration done, ready for testing
  - **Risk**: OOM crashes with folders containing 1000+ files
  - **Completed**:
    - Paging3 library (3.2.1) in dependencies
    - MediaFilesPagingSource created with PAGE_SIZE=50
    - PagingMediaFileAdapter implemented
    - BrowseViewModel auto-switches modes (PAGINATION_THRESHOLD=1000)
    - scanFolderPaged implemented in all scanners (LocalMediaScanner, SmbMediaScanner, SftpMediaScanner, FtpMediaScanner)
    - PagingMediaFileAdapter integrated into BrowseActivity observeData()
    - LoadState handling added (LoadState.Loading, LoadState.Error)
    - Footer adapter with "Loading more..." indicator and retry button
    - Dynamic adapter switching between standard and pagination modes
  - **TODO**:
    - Test with 1000+, 5000+ files on all resource types (LOCAL, SMB, SFTP, FTP)
    - Optimize scanFolderPaged implementations (currently loads all files, then applies offset/limit)

### 🟠 High Priority

- [ ] **Undo support for network files (SMB/SFTP/FTP)**
  - **Status**: ✅ COMPLETED - Soft-delete implemented for all network types
  - **Completed**:
    - Soft-delete implemented for SMB files (move to .trash folder on remote server)
    - Soft-delete implemented for SFTP files (move to .trash folder on remote server)
    - Soft-delete implemented for FTP files (move to .trash folder on remote server)
    - createDirectory() added to SftpClient and FtpClient
    - SmbClient already had createDirectory()
    - All network handlers now use rename to move files to .trash instead of permanent deletion
    - Trash directory path and original paths stored in result for undo restoration
  - **TODO**:
    - Test undo for SMB operations in PlayerActivity and BrowseViewModel
    - Test undo for SFTP operations in PlayerActivity and BrowseViewModel
    - Test undo for FTP operations in PlayerActivity and BrowseViewModel
    - Handle permission errors for network trash folder creation
    - Add network-specific timeout handling for undo operations
    - Ensure trash cleanup works for network folders (CleanupTrashFoldersUseCase)

- [ ] **Image editing for network files (SMB/SFTP/FTP)**
  - **Status**: ✅ COMPLETED - NetworkImageEditUseCase implemented and integrated
  - **Completed**:
    - NetworkImageEditUseCase created with download → edit → upload workflow
    - Downloads network image to temp folder before editing
    - Applies RotateImageUseCase/FlipImageUseCase to temp file
    - Uploads modified image back to network location
    - Automatic cleanup of temp files after operation
    - Integration with ImageEditDialog (rotateImage/flipImage methods)
    - Error handling for download/upload failures
  - **TODO**:
    - Add progress reporting during download/upload phases (EditProgress sealed class exists but not used in ImageEditDialog)
    - Test with large images over slow network
    - Add cancellation support for download/upload operations

### 🟡 Medium Priority

- [ ] **Progress for LOCAL resource scanning**
  - **Status**: ✅ COMPLETED - Progress callback implemented
  - **Completed**:
    - scanFolderWithProgress() method added to LocalMediaScanner
    - scanFolderSAFWithProgress() for content:// URIs
    - Progress reported every 10 files during scan
    - Integrated into MainViewModel.scanAllResources() for LOCAL resources with >100 files
    - Uses existing ScanProgress event and overlay in MainActivity

- [ ] **Predictable image caching**
  - **Status**: ✅ COMPLETED - Consistent cache keys implemented
  - **Completed**:
    - Added .memoryCacheKey(path) to PlayerActivity network image loading
    - Added .diskCacheKey(path) for disk cache consistency
    - Preload requests now use consistent cache keys (both network and local)
    - Cache hit rate optimized for adjacent files navigation
  - **Note**: MediaFileAdapter already used memoryCacheKey/diskCacheKey

- [ ] **Background sync for network file existence**
  - **Status**: ✅ COMPLETED - WorkManager background sync implemented
  - **Completed**:
    - NetworkFilesSyncWorker created with Hilt integration
    - ScheduleNetworkSyncUseCase for scheduling/canceling sync
    - Runs every 4 hours with network and battery constraints
    - Syncs only SMB/SFTP/FTP resources
    - Updates fileCount when changes detected
    - Integrated into MainActivity onCreate()
  - **TODO**:
    - Add UI indicator for missing/unavailable network files
    - Show sync status in resource list (last sync time, sync errors)

- [ ] **Handle stale thumbnails**
  - Detect when file content changes (compare lastModified timestamp)
  - Invalidate Coil cache for changed files
  - Reload thumbnail on next display

## 🎨 UI/UX Improvements

- [ ] **Accessibility improvements**
  - Add content descriptions for all ImageView/ImageButton elements
  - Test with TalkBack screen reader
  - Verify minimum touch target size (48dp) for all interactive elements
  - Test high contrast mode compatibility
  - Add accessibility labels for all progress indicators

- [ ] **Animations and transitions**
  - Add screen transitions (slide, fade, shared element)
  - Implement RecyclerView item animations (add, remove, reorder)
  - Add ripple effects to all buttons and clickable items where missing
  - Animate progress indicators smoothly

- [ ] **Settings: Re-enable player hint toggle**
  - Add preference in Settings to show/hide player touch zones overlay
  - Allow user to re-trigger first-run hint
  - Test hint dismissal and re-activation

## ⚡ Performance Optimization

- [ ] **RecyclerView optimizations**
  - Implement RecyclerView.RecycledViewPool for lists with same ViewHolder type
  - Set optimal itemViewCacheSize based on screen size
  - Profile onBind operations (target <1ms per bind)

- [ ] **Layout and rendering profiling**
  - Run Layout Inspector to check overdraw (<2x target)
  - Profile with GPU Rendering Profiler (target <16ms per frame for 60fps)
  - Optimize expensive layouts identified by profiling

- [ ] **Database optimization**
  - Profile Room DAO queries with Database Inspector
  - Add indexes for frequently queried columns (name, type, size, date)
  - Optimize getAllResourcesSync() query
  - Implement database pagination for large result sets

- [ ] **Memory leak detection**
  - Integrate LeakCanary
  - Fix any detected memory leaks
  - Profile with Android Profiler to verify no activity/fragment leaks

- [ ] **Battery optimization**
  - Reduce WorkManager task frequency based on battery level
  - Pause background sync on low battery (<15%)
  - Release player resources immediately when backgrounded
  - Optimize Coil cache trimming

## 🌐 Network Features

- [ ] **Cloud storage support**
  - Google Drive API integration with OAuth2 flow
  - OneDrive API integration
  - Dropbox API integration
  - Cloud file operations (browse, copy, move, delete)
  - Undo support for cloud operations
  - Integrate with existing UndoOperation system

- [ ] **Offline mode**
  - Cache thumbnails and metadata locally
  - Show cached data when network unavailable
  - Add offline status indication in UI
  - Implement operation queue for later sync when connection restored

## 🧪 Testing

- [ ] **Unit tests for domain layer**
  - Write JUnit tests for all UseCase classes
  - Test ViewModels with kotlinx-coroutines-test
  - Mock Repository dependencies with Mockito
  - **Goal**: >80% code coverage for domain layer

- [ ] **Instrumented tests**
  - Test Room database operations with Room testing library
  - UI flow tests with Espresso (MainActivity → BrowseActivity → PlayerActivity)
  - Test file operations with temporary test folders
  - Test navigation between screens

- [ ] **Manual testing checklist**
  - Test on Android 8.0, 9.0, 10, 11, 12, 13, 14
  - Test on different screen sizes (phone, 7" tablet, 10" tablet)
  - Test all file types: JPEG, PNG, GIF, MP4, WEBM, MOV, MKV, MP3, WAV
  - Test connection scenarios: slow network, no internet, connection drops
  - Test edge cases: empty folders, 5000+ files, 500+ char filenames, special chars

- [ ] **Security audit**
  - Check for hardcoded credentials (should be none)
  - Validate input sanitization (IP addresses, file paths, credentials)
  - Test file path traversal prevention
  - Review permission usage (ensure minimal necessary permissions)

## 🧰 Code Quality & Static Analysis

- [ ] **Kotlin static analysis integration (detekt/ktlint)**
  - Add detekt Gradle plugin to root `build.gradle.kts`
  - Create `detekt.yml` configuration with complexity thresholds
  - Wire detekt into CI/CD pipeline (run before assemble)
  - Optionally add ktlint for strict code style
  - Gradually tighten rules (start warnings-only, later fail build)

- [ ] **Edge cases handling**
  - Empty folders handling (show empty state)
  - Folders with 1000+ files (pagination)
  - Very long file names (>255 chars, truncate display)
  - Special characters in file names (test: `файл#123 (copy).jpg`)
  - Corrupted or unsupported media files (show error placeholder)

## 📦 Release Preparation

### Build Configuration

- [ ] **ProGuard/R8 configuration**
  - Configure ProGuard rules for release build
  - Keep classes used via reflection (Room, Coil, ExoPlayer, Hilt)
  - Test obfuscated APK thoroughly on multiple devices
  - Verify all functionality after ProGuard

- [ ] **APK signing**
  - Verify release keystore exists and is secure
  - Configure signing in build.gradle.kts
  - Test installation of signed APK
  - Store keystore securely (not in repository)

- [ ] **APK size optimization**
  - Enable resource shrinking in release build
  - Enable code shrinking (R8) in release build
  - Use vector drawables instead of PNG where possible
  - Remove unused resources and dependencies
  - Consider Android App Bundle (.aab)

- [ ] **Version management**
  - Update versionCode in build.gradle.kts
  - Update versionName to "2.0.0"
  - Follow semantic versioning (MAJOR.MINOR.PATCH)
  - Tag release in Git (e.g., `v2.0.0`)

- [ ] **Dependencies update**
  - Update all libraries to latest stable versions
  - Test after each major update
  - Check for deprecated APIs and fix
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

**Latest Build**: 2.0.0-build2511142205

