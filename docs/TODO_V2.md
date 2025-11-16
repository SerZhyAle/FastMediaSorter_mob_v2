# TODO V2 - FastMediaSorter v2


## 🛠️ Recent Fixes (Build 2.0.2511161932)
- ✅ **Refresh button network validation** - Кнопка "Обновить" теперь проверяет доступность сетевых ресурсов (SMB/SFTP/FTP/CLOUD)
- ✅ **scanAllResources() enhancement** - Добавлена проверка testConnection() для сетевых ресурсов перед сканированием
- ✅ **Unavailable resources skip** - Недоступные сетевые ресурсы пропускаются при обновлении (не блокируют весь процесс)
- ✅ **Status message** - После обновления показывается сообщение с количеством недоступных ресурсов ("Resources scanned (2 unavailable)")
- ✅ **Retry button fix** - Кнопка "Повторить" при ошибке теперь также запускает полное сканирование с проверкой доступности

## 🛠️ Recent Fixes (Build 2.0.2511160344)
- ✅ **SMB connection pooling** - Добавлен пул переиспользуемых SMB-соединений (max 8 concurrent) для ускорения загрузки превью
- ✅ **Connection reuse** - NetworkFileFetcher теперь использует существующие аутентифицированные соединения вместо создания новых для каждого превью
- ✅ **Semaphore-based concurrency** - Ограничение параллельных SMB-операций через Semaphore(8) предотвращает перегрузку сети
- ✅ **Idle timeout** - Автоматическое закрытие неактивных соединений после 5 секунд (CONNECTION_IDLE_TIMEOUT_MS)
- ✅ **Connection validation** - Проверка isConnected() перед переиспользованием, автоматическое переподключение при сбое
- ✅ **Performance: 54s → ~1-2s** - Загрузка 50 превью изображений через SMB: было 54+ секунд (каждое соединение = новая аутентификация), стало ~1-2 секунды (переиспользование connection pool)

## 🛠️ Recent Fixes (Build 2.0.2511160336)
- ✅ **Resource validation before Browse** - Added validateAndOpenResource() method in MainViewModel
- ✅ **Local resource check** - Show error dialog if fileCount == 0 for local resources
- ✅ **Network resource check** - Test connection for SMB/SFTP/FTP/CLOUD before opening Browse
- ✅ **Detailed error logging setting** - Added showDetailedErrors boolean to AppSettings (default: false)
- ✅ **Smart error display** - Short Toast when showDetailedErrors=false, full ErrorDialog with "Copy to clipboard" when true
- ✅ **DisplayMode reset fix** - Changed updateDisplayMode() to suspend function, removed nested lifecycleScope.launch
- ✅ **Race condition eliminated** - updateDisplayMode now executes synchronously within state.collect{} coroutine
- ✅ **Grid/List mode persistence** - DisplayMode from DB now correctly applies on return from PlayerActivity

## 🛠️ Recent Fixes (Build 2.0.2511160328)
- ✅ **Pagination loading fix** - Changed pagingDataFlow from nullable var to MutableStateFlow for reactive subscription
- ✅ **Empty screen fix** - Activity now properly subscribes to pagingDataFlow updates (was null during initial subscribe)
- ✅ **BrowseViewModel.pagingDataFlow** - Now StateFlow<Flow<PagingData>?> instead of var (Activity gets notified when Flow is created)
- ✅ **BrowseActivity subscription** - Nested collect: outer for StateFlow updates, inner for PagingData emissions
- ✅ **Race condition fix** - Activity subscribes in onCreate(), ViewModel creates Flow 3+ seconds later in loadMediaFiles()

## 🛠️ Recent Fixes (Build 2.0.2511160324)
- ✅ **Pagination sort mode restriction** - Pagination enabled ONLY for NAME_ASC/NAME_DESC modes (other modes need full scan)
- ✅ **Performance warning dialog** - Alert when selecting DATE/SIZE/TYPE sorting in folders with 1000+ files (warns about 30+ second load time)
- ✅ **BrowseViewModel sort mode check** - canUsePagination logic: fileCount >= 1000 AND (sortMode == NAME_ASC OR NAME_DESC)
- ✅ **Dynamic loading strategy** - Automatically switches from pagination to full scan when user changes sort mode to DATE/SIZE/TYPE
- ✅ **BrowseActivity.showLargeFolderSortWarning()** - New dialog explaining performance impact of non-NAME sorting

## 🛠️ Recent Fixes (Build 2.0.2511160320)
- ✅ **SMB pagination optimization** - Added native offset/limit support to scanMediaFilesPaged() via scanDirectoryWithOffsetLimit()
- ✅ **Performance improvement for large folders** - Browse SMB folders (63000+ files): 70+ seconds → ~5-10 seconds for first page load
- ✅ **Sorted pagination** - Files and directories sorted by name (case-insensitive) before processing for consistent order across pages
- ✅ **PagingSource sort fix** - Removed per-page sorting for NAME_ASC/DESC modes (scanner already provides sorted data)
- ✅ **Empty screen fix** - Pagination now loads files incrementally instead of scanning all 63000+ files upfront
- ✅ **SmbClient.scanMediaFilesPaged()** - New method with offset/limit parameters, returns SmbResult<List<SmbFileInfo>>
- ✅ **SmbMediaScanner optimization** - Removed full scan + drop(offset).take(limit) pattern, uses native paged scan

## 🛠️ Recent Fixes (Build 2.0.2511160309)
- ✅ **SMB file counting optimization** - Added maxCount=10000 limit to countMediaFiles() to prevent 54-second scans for large folders (63995 files example)
- ✅ **Fast resource addition** - Stop counting after 10000 files and show "10000+ files" in UI instead of exact count
- ✅ **Early exit optimization** - countDirectoryRecursive() checks limit on each iteration and stops immediately when reached

## 🛠️ Recent Fixes (Build 2.0.2511160306)
- ✅ **SMB duplicate shares fix** - Changed shares list from MutableList to MutableSet with case-insensitive deduplication (shares.any { it.equals(shareName, ignoreCase = true) })
- ✅ **SMB shares sorted** - Result list now sorted alphabetically for consistent display
- ✅ **Debug logging added** - BrowseActivity.updateDisplayMode() and BrowseViewModel.loadResource() now log displayMode changes for diagnostics

## 🛠️ Recent Fixes (Build 2.0.2511160255)
- ✅ **ExoPlayer image file protection** - Added extension-based fallback in playVideo() to prevent UnrecognizedInputFormatException
- ✅ **PlayerActivity file type detection** - Added else branch in updateUI when{} to handle unknown/null file types by extension
- ✅ **Crash fix: UnrecognizedInputFormatException** - Double-check file extension before ExoPlayer init, redirect images to displayImage()
- ✅ **Performance validation (logcat 04:18:23-52)** - Video playback via SMB: 3.36s (onCreate→READY), no crashes, isFirstResume optimization confirmed (~500ms saved), updateUI skip working (files not loaded yet), single scan verified
- ✅ **SMB write permission check implemented** - Added SmbClient.checkWritePermission() that creates/writes/deletes test file to verify actual write access
- ✅ **Lock icon fix for writable SMB shares** - isWritable() now performs real write test instead of just connectivity check
- ✅ **PlayerActivity duplicate scan fix** - Added isFirstResume flag to skip reload on first onResume (saves ~500ms SMB scan)
- ✅ **PlayerActivity updateUI optimization** - Skip UI updates until files loaded (eliminates 3 redundant calls with null data)
- ✅ **PlayerActivity chunked loading fix** - Use fileCount >= 200 threshold instead of always enabling for SMB (12-file folder now loads instantly)
- ✅ **Thumbnail cache reuse optimization** - Added fixed size(512) to all Coil loads for consistent caching between List/Grid modes
- ✅ **List↔Grid switching performance** - Thumbnails no longer reload when switching display modes (shared cache with fixed dimensions)
- ✅ **Chunked loading threshold fix** - Changed from "always for SMB" to "only when fileCount >= 200 for network resources"
- ✅ **Performance improvement for small network folders** - 12-file folder now uses full scan instead of chunked (saves ~300ms of empty directory traversal)
- ✅ **SMB recursive scan optimization** - Two-pass directory scanning: process files first (no recursion), then directories
- ✅ **Performance improvement for network folders** - Reduces unnecessary directory traversal when files found early
- ✅ **BrowseActivity duplicate load fix** - Added isFirstResume flag to skip reload on first onResume (files already loaded in ViewModel.init{})
- ✅ **Performance improvement** - Eliminates duplicate file scanning (2 scans in 70ms → 1 scan on activity start)
- ✅ **App Startup Performance Optimizations** - Moved WorkManager to background thread, deferred version logging
- ✅ **WorkManager off main thread** - applicationScope.launch coroutine prevents 30-frame skip at startup
- ✅ **Version logging deferred** - Moved PackageManager call to binding.root.post (after UI inflation)
- ✅ **RecyclerView optimizations** - Shared RecycledViewPool with 30 list/40 grid ViewHolders, dynamic cache size
- ✅ **Dynamic itemViewCacheSize** - Calculated based on screen height (1.5 screens, 10-30 range)
- ✅ **ViewHolder reuse optimization** - setRecycledViewPool for efficient memory usage
- ✅ **Cloud thumbnail caching implemented** - MediaFileAdapter supports loading thumbnails from thumbnailUrl for cloud files
- ✅ **Cloud path detection** - Added cloud:// protocol detection in both List and Grid ViewHolders
- ✅ **Cloud thumbnail fallback** - Shows placeholder if thumbnailUrl is not available

## 🎯 Current Development Tasks

- [ ] **Google Drive Integration - Phase 3: Testing**
  - **Status**: 🟢 READY FOR TESTING - Implementation complete


## 🛠️ Recent Fixes (Build 2.0.2511160238)
- ✅ **PlayerActivity duplicate scan fix** - Added isFirstResume flag to skip reload on first onResume (saves ~500ms SMB scan)
- ✅ **PlayerActivity updateUI optimization** - Skip UI updates until files loaded (eliminates 3 redundant calls with null data)
- ✅ **PlayerActivity chunked loading fix** - Use fileCount >= 200 threshold instead of always enabling for SMB (12-file folder now loads instantly)
- ✅ **Thumbnail cache reuse optimization** - Added fixed size(512) to all Coil loads for consistent caching between List/Grid modes
- ✅ **List↔Grid switching performance** - Thumbnails no longer reload when switching display modes (shared cache with fixed dimensions)
- ✅ **Chunked loading threshold fix** - Changed from "always for SMB" to "only when fileCount >= 200 for network resources"
- ✅ **Performance improvement for small network folders** - 12-file folder now uses full scan instead of chunked (saves ~300ms of empty directory traversal)
- ✅ **SMB recursive scan optimization** - Two-pass directory scanning: process files first (no recursion), then directories
- ✅ **Performance improvement for network folders** - Reduces unnecessary directory traversal when files found early
- ✅ **BrowseActivity duplicate load fix** - Added isFirstResume flag to skip reload on first onResume (files already loaded in ViewModel.init{})
- ✅ **Performance improvement** - Eliminates duplicate file scanning (2 scans in 70ms → 1 scan on activity start)
- ✅ **App Startup Performance Optimizations** - Moved WorkManager to background thread, deferred version logging
- ✅ **WorkManager off main thread** - applicationScope.launch coroutine prevents 30-frame skip at startup
- ✅ **Version logging deferred** - Moved PackageManager call to binding.root.post (after UI inflation)
- ✅ **RecyclerView optimizations** - Shared RecycledViewPool with 30 list/40 grid ViewHolders, dynamic cache size
- ✅ **Dynamic itemViewCacheSize** - Calculated based on screen height (1.5 screens, 10-30 range)
- ✅ **ViewHolder reuse optimization** - setRecycledViewPool for efficient memory usage
- ✅ **Cloud thumbnail caching implemented** - MediaFileAdapter supports loading thumbnails from thumbnailUrl for cloud files
- ✅ **Cloud path detection** - Added cloud:// protocol detection in both List and Grid ViewHolders
- ✅ **Cloud thumbnail fallback** - Shows placeholder if thumbnailUrl is not available

## 🎯 Current Development Tasks

- [ ] **Google Drive Integration - Phase 3: Testing**
  - **Status**: 🟢 READY FOR TESTING - Implementation complete
  - **See**: "Cloud storage support (Google Drive)" in Network Features section

- [ ] **Pagination for large datasets (1000+ files) - TESTING**
  - **Status**: ✅ IMPLEMENTATION COMPLETED - Ready for real-world testing
  - **See**: Current Development Tasks → Pagination section for details

- [ ] **Pagination for large datasets (1000+ files)**
  - **Status**: ✅ COMPLETED - Integration done, ready for testing
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

- [ ] **Resource availability indicator (red dot for unavailable resources)**
  - **Description**: Add visual indicator (red dot) on MainActivity resource list when resource is unavailable
  - **Requirements**:
    - Refresh button checks all resources: connection test + file count + write access
    - Unavailable resources keep their data but show red dot indicator
    - Red dot disappears after successful refresh or Browse
    - Add `isAvailable: Boolean` field to ResourceEntity (Room schema migration needed)
    - Update ResourceAdapter to show red dot overlay on unavailable resources
  - **Files to modify**:
    - `ResourceEntity.kt` - Add isAvailable field
    - `AppDatabase.kt` - Create migration 6→7
    - `ResourceAdapter.kt` - Add red dot indicator in ViewHolder
    - `MainViewModel.kt` - Update refreshResources() logic
  - **Priority**: HIGH - User experience improvement

- [ ] **Undo support for network files (SMB/SFTP/FTP) - TESTING REQUIRED**
  - **Status**: ✅ IMPLEMENTATION COMPLETED - Soft-delete implemented for all network types
  - **Completed**:
    - Soft-delete implemented for SMB files (move to .trash folder on remote server)
    - Soft-delete implemented for SFTP files (move to .trash folder on remote server)
    - Soft-delete implemented for FTP files (move to .trash folder on remote server)
    - createDirectory() added to SftpClient and FtpClient
    - SmbClient already had createDirectory()
    - All network handlers now use rename to move files to .trash instead of permanent deletion
    - Trash directory path and original paths stored in result for undo restoration
  - **TODO - Real-world testing**:
    - Test undo for SMB operations in PlayerActivity and BrowseViewModel
    - Test undo for SFTP operations in PlayerActivity and BrowseViewModel
    - Test undo for FTP operations in PlayerActivity and BrowseViewModel
    - Handle permission errors for network trash folder creation
    - Add network-specific timeout handling for undo operations
    - Ensure trash cleanup works for network folders (CleanupTrashFoldersUseCase)

- [ ] **Image editing for network files (SMB/SFTP/FTP) - TESTING REQUIRED**
  - **Status**: ✅ IMPLEMENTATION COMPLETED - NetworkImageEditUseCase implemented and integrated
  - **Completed**:
    - NetworkImageEditUseCase created with download → edit → upload workflow
    - Downloads network image to temp folder before editing
    - Applies RotateImageUseCase/FlipImageUseCase to temp file
    - Uploads modified image back to network location
    - Automatic cleanup of temp files after operation
    - Integration with ImageEditDialog (rotateImage/flipImage methods)
    - Error handling for download/upload failures
  - **TODO - Testing and enhancements**:
    - Test with large images over slow network
    - Add progress reporting during download/upload phases (EditProgress sealed class exists but not used in ImageEditDialog)
    - Add cancellation support for download/upload operations

### 🟡 Medium Priority

- [ ] **Background sync for network file existence - TESTING REQUIRED**
  - **Status**: ✅ IMPLEMENTATION COMPLETED - WorkManager background sync implemented
  - **Completed**:
    - NetworkFilesSyncWorker created with Hilt integration
    - ScheduleNetworkSyncUseCase for scheduling/canceling sync
    - Runs every 4 hours with network and battery constraints
    - Syncs only SMB/SFTP/FTP resources
    - Updates fileCount when changes detected
    - Integrated into MainActivity onCreate()
  - **TODO - UI and testing**:
    - Add UI indicator for missing/unavailable network files
    - Show sync status in resource list (last sync time, sync errors)
    - Test sync behavior after 4+ hours idle

## 🎨 UI/UX Improvements

- [ ] **Animations and transitions**
  - Add screen transitions (slide, fade, shared element)
  - Implement RecyclerView item animations (add, remove, reorder)
  - Add ripple effects to all buttons and clickable items where missing
  - Animate progress indicators smoothly

- [ ] **Settings: Re-enable player hint toggle**
  - Add preference in Settings to show/hide player touch zones overlay
  - Allow user to re-trigger first-run hint
  - Test hint dismissal and re-activation

## ⚡ Performance Optimization (LOW PRIORITY)

- [ ] **Move ExoPlayer initialization off main thread**
  - **Issue**: ExoPlayer.Builder().build() занимает ~39ms на main thread (логи 03:52:47.287-326)
  - **Impact**: Блокирует UI при открытии видео в PlayerActivity
  - **Solution**: Вынести ExoPlayer.Builder().build() и SmbDataSourceFactory creation в coroutine с Dispatchers.Default, затем setPlayer() на main thread
  - **File**: PlayerActivity.kt, метод playVideo()
  - **Priority**: LOW - не критично, но улучшит отзывчивость UI

- [ ] **Investigate ExoPlayer audio discontinuity**
  - **Issue**: AudioSink$UnexpectedDiscontinuityException при SMB воспроизведении (логи 03:52:48.438)
  - **Details**: Expected timestamp 1000000021333, got 1000000326520 (~305µs разница)
  - **Impact**: ExoPlayer warning в логах, видео проигрывается корректно
  - **Possible cause**: SMB buffering задержки или codec timestamp issues
  - **Priority**: LOW - не влияет на функциональность

- [ ] **Network resource performance optimizations**
  - **Background file count duplication**: `startFileCountInBackground()` runs parallel SMB scan before main scan completes
    - Impact: 2 network requests instead of 1 (background count at 03:37:56.665, then full scan at 03:37:56.713)
    - Solution: Skip background count for network resources, update count after main scan completes
    - File: `BrowseViewModel.kt` lines ~145-155
  - **Coil sequential thumbnail loading**: 6 thumbnails load sequentially (175ms total) instead of parallel
    - Impact: Slower initial render for network folders
    - Solution: Verify Coil ImageLoader dispatcher uses Dispatchers.IO with parallelism >1
    - File: `CoilModule.kt` - check ImageLoader.Builder().dispatcher() configuration

- [ ] **RecyclerView optimizations**
  - **Status**: ✅ PARTIALLY COMPLETED - Core optimizations implemented in BrowseActivity
  - **Completed**:
    - ✅ Shared RecycledViewPool implemented for ViewHolder reuse (max 30 list, 40 grid)
    - ✅ Dynamic itemViewCacheSize based on screen size (10-30, 1.5 screens worth)
    - ✅ setHasFixedSize(true) for better layout performance
  - **TODO**:
    - Profile onBind operations (target <1ms per bind)
    - Test performance improvements on low-end devices

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

- [ ] **Cloud storage support (Google Drive) - PHASE 3: Testing & File Operations**
  - **Status**: 🟢 READY FOR TESTING - File operations handler implemented, build verification needed
  - **Priority**: HIGH - Core feature for v2
  - **Completed**:
    - ✅ CloudMediaScanner registered in MediaScannerFactory
    - ✅ MediaFile extended with thumbnailUrl/webViewUrl
    - ✅ Build successful (CloudMediaScanner compiles without errors)
    - ✅ CloudFileOperationHandler created (copy/move/delete/rename operations)
    - ✅ CloudFileOperationHandler integrated into FileOperationUseCase
    - ✅ Cloud path detection added (cloud:// protocol)
    - ✅ Cross-provider operations supported (download→upload buffer)
    - ✅ Thumbnail caching for cloud files (MediaFileAdapter loads thumbnailUrl)
  - **TODO - Testing required**:
    - [ ] Test cloud resource browsing: Add Google Drive folder → Navigate to BrowseActivity → Verify files display
    - [ ] Handle network errors and API quota limits gracefully
    - [ ] Test file operations: copy/move/delete local↔cloud, cloud↔cloud
    - [ ] Test undo operations for cloud files (if applicable)
    - [ ] Configure OAuth2 credentials in Google Cloud Console for production
  - **Next Steps**:
    1. Install APK and test Google Drive authentication flow
    2. Select folder from Google Drive and verify it appears in resource list
    3. Browse cloud resource and verify files load correctly
    4. Test file operations (copy file from local to Google Drive, move between folders)

- [ ] **Cloud storage support (OneDrive, Dropbox) - FUTURE**
  - OneDrive API integration with OAuth2 flow
  - Dropbox API integration with OAuth2 flow
  - Reuse CloudStorageClient interface and CloudMediaScanner
  - Test multi-cloud operations (Google Drive → OneDrive copy)

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

**Latest Build**: 2.0.2511160336  
**Version**: 2.0.0-build2511160336

**Recent Performance Optimizations**:
- ✅ DisplayMode persistence: Race condition fixed (Grid/List mode now persists correctly)
- ✅ Resource validation: Added smart error display (short Toast vs detailed ErrorDialog)
- ✅ PlayerActivity: Eliminated duplicate scan (~500ms saved per video open)
- ✅ PlayerActivity: Fixed chunked loading threshold (12-file folder loads instantly)
- ✅ BrowseActivity: Thumbnail cache reuse for List↔Grid switching
- ✅ SMB scan: Two-pass optimization (files first, then recursion)

**Next Priorities**:
1. Resource availability indicator (red dot for unavailable resources)
2. Test Google Drive integration (authentication + file operations)
3. Test pagination with 1000+ files folders
4. Test network undo/image editing operations

