# TODO V2 - FastMediaSorter

**Latest Build**: 2.25.1118.0356  
**Version**: 2.25.1118.0356
**Package**: com.sza.fastmediasorter



### Build 2.25.1118.0356 ✅
- ✅ **FEATURE: 3-zone touch layout with PhotoView for pinch-to-zoom and rotation gestures**
- **User request**: Enable pinch-to-zoom and rotation gestures in command panel mode when "Load images at full resolution" setting is ON
- **Implementation**: 
  - Added PhotoView library 2.3.0 via JitPack repository for gesture support
  - Created 3-zone touch overlay layout (25% left = Previous, 50% center = Gestures, 25% right = Next)
  - Standard ImageView used when `loadFullSizeImages=false` (default, 2-zone mode)
  - PhotoView used when `loadFullSizeImages=true` (3-zone mode with gesture area)
  - Automatic mode switching based on setting state
- **Changes**:
  - **build.gradle.kts**: Added `com.github.chrisbanes:PhotoView:2.3.0` dependency
  - **settings.gradle.kts**: Added JitPack Maven repository (`maven { url = uri("https://jitpack.io") }`)
  - **activity_player_unified.xml**:
    - Added `PhotoView` widget (id: `photoView`, initially hidden)
    - Added 3-zone touch overlay (`touchZones3Overlay`) with weighted columns (0.25 / 0.50 / 0.25)
    - Kept legacy 2-zone overlay (`touchZonesOverlay`) for compatibility
  - **PlayerActivity.kt**:
    - Added PhotoView import
    - Updated `setupTouchZones()`: Added listeners for 3-zone overlay (Previous/Next zones)
    - Refactored `displayImage()`: 
      - Reads `loadFullSizeImages` setting from repository
      - Conditionally shows ImageView (2-zone) or PhotoView (3-zone) based on setting
      - Switches touch overlay visibility (`touchZonesOverlay` vs `touchZones3Overlay`)
      - Loads images into correct view (ImageView or PhotoView)
      - PhotoView center zone has no click handler (gestures handled by library)
    - Updated `updatePanelVisibility()`: Comment clarifies touch zones managed by `displayImage()`
  - **strings.xml (en/ru/uk)**: Updated `load_full_size_images_hint` to mention "pinch-to-zoom and rotation gestures in command panel mode"
- **How it works**:
  1. User enables "Load images at full resolution" in Settings → Media
  2. Opens static image in PlayerActivity with command panel visible
  3. App automatically switches from 2-zone to 3-zone layout
  4. PhotoView loads full-resolution image
  5. User can:
     - Tap left 25% → Previous image
     - Tap right 25% → Next image
     - Pinch center 50% → Zoom in/out
     - Rotate fingers in center 50% → Rotate image clockwise/counterclockwise
  6. When setting OFF → returns to standard 2-zone ImageView (1920px resolution, no gestures)
- **PhotoView features**:
  - Pinch-to-zoom (2-finger spread/pinch)
  - Rotation gestures (2-finger rotate clockwise/counterclockwise)
  - Double-tap to zoom
  - Pan/scroll when zoomed
  - Smooth animations
- **Result**: Full gesture support for static images in command panel mode. Conditional activation via existing setting. No changes to fullscreen mode behavior. Memory-efficient (only loads full resolution when explicitly enabled).

### Build 2.0.0-build2511171445 ✅
- ✅ **REFACTOR: Network settings restructure**
- **Issue**: Network settings had dedicated tab (5 tabs total), underutilized space
- **Changes**:
  - Removed Network tab from SettingsActivity (5 tabs → 4 tabs)
  - Moved "Show video thumbnails" from Playback tab → Media tab (Video section)
  - Moved all Network settings → General tab (new "Network Sync" section before User/Password)
  - Added background sync controls: enable switch, interval slider (1-24 hours), manual sync button, status text
- **Architecture updates**:
  - `AppSettings.kt`: Added `enableBackgroundSync: Boolean = true`, `backgroundSyncIntervalHours: Int = 4`
  - `SettingsRepositoryImpl.kt`: Added DataStore keys + read/write logic for new fields
  - `strings.xml` (en/ru/uk): Added `sync_interval_hours` plurals resource
- **UI changes**:
  - `fragment_settings_general.xml`: Added 7 widgets (header, switch, description, slider label, slider, button, status)
  - `fragment_settings_media.xml`: Added `switchShowVideoThumbnails` after supported formats
  - `fragment_settings_playback.xml`: Removed `switchShowVideoThumbnails` widget
  - `SettingsPagerAdapter.kt`: Changed itemCount 5 → 4, removed NetworkSettingsFragment
  - `SettingsFragments.kt`: Removed `NetworkSettingsFragment` class, added network sync logic to `GeneralSettingsFragment`
- **Pattern**: Interval slider updates label dynamically using plurals (1 hour / 2-24 hours)
- **Result**: Cleaner tab structure. Network settings grouped logically in General tab. All persistence working correctly.

---

### Build 2.25.1117.1748 ✅
- ✅ **BUG FIX: Infinite "Settings updated successfully" loop**
- **Issue**: Toast message appeared infinitely when opening Settings, causing UI lag
- **Root cause**: Race condition - `observeData()` programmatically updated switch states → listeners triggered → `updateSettings()` called → Flow emitted → `observeData()` triggered again → infinite loop
- **Solution**: Added `isUpdatingFromSettings` flags to all 4 settings fragments
  - Flag set to `true` before programmatic UI updates
  - All `setOnCheckedChangeListener` callbacks check flag and early-return if updating from code
  - Flag reset to `false` after UI update complete
- **Changed files**:
  - `SettingsFragments.kt`: Added flags to `MediaSettingsFragment`, `PlaybackSettingsFragment`, `DestinationsSettingsFragment`, `GeneralSettingsFragment`
  - Modified 22+ switch listeners (all media type toggles, playback options, copy/move/destinations settings)
  - Wrapped `observeData()` UI updates in flag checks
- **Pattern**: `if (isUpdatingFromSettings) return@setOnCheckedChangeListener` in every listener
- **Result**: Settings open instantly without loops. User interactions update settings once. Programmatic updates don't trigger listeners.

- ✅ **UI: Welcome screen improvements**
- **Changes**:
  - **Touch Zones title**: Added "use in full screen view mode" clarification (en/ru/uk)
  - **Resource Types slide**: Replaced app icon with `resource_types.png` drawable
  - **Resources and Destinations slide**: Replaced app icon with `destinations.png` drawable
- **Changed files**:
  - `strings.xml` (en/ru/uk): Updated `welcome_title_3` with newline + fullscreen mode hint
  - `WelcomeActivity.kt`: Updated page 2 iconRes → `R.drawable.resource_types`, page 4 iconRes → `R.drawable.destinations`
- **Result**: Welcome screen now uses prepared visual assets instead of launcher icon. Touch zones purpose clearly stated (fullscreen mode only).

---


## 🚀 Pre-Release Tasks (Ready to Implement)

### 🔴 Critical (Blocking Release)

- [x] **ProGuard/R8 Configuration** *(Build 2.25.1117.1223)*
  - ✅ ProGuard rules extended for all network protocols (SMB, SFTP, FTP)
  - ✅ Cloud services rules added (Google Drive, Dropbox, OneDrive)
  - ✅ Logging removal in release (Timber stripped)
  - ✅ Missing classes warnings fixed (Apache HTTP, Tink, OpenTelemetry, Nimbus JOSE)
  - ✅ Release APK built successfully (26.4 MB)
  - 🔧 Full feature testing on release APK pending

- [x] **APK Signing Verification** *(Build 2.25.1117.1223)*
  - ✅ Keystore file exists (created 2025-10-17)
  - ✅ Signing configuration verified in build.gradle.kts
  - ✅ Release APK signed successfully
  - ✅ APK location: `app_v2/build/outputs/apk/release/app_v2-release.apk`

- [x] **File Operations Matrix Verification** *(Build 2.25.1117.1223)*
  - ✅ **Copy/Move Operations**: All combinations implemented
    - Local↔Local: ✅ Standard File API
    - Local↔SMB: ✅ SmbFileOperationHandler (upload/download)
    - Local↔SFTP: ✅ SftpFileOperationHandler (upload/download)
    - Local↔FTP: ✅ FtpFileOperationHandler (upload/download)
    - Local↔Cloud: ✅ CloudFileOperationHandler (upload/download)
    - SMB↔SFTP: ✅ Via memory buffer (download→upload)
    - SMB↔FTP: ✅ Via memory buffer (download→upload)
    - SMB↔Cloud: ✅ Via memory buffer (download→upload)
    - SFTP↔FTP: ✅ Via memory buffer (download→upload)
    - SFTP↔Cloud: ✅ Via memory buffer (download→upload)
    - FTP↔Cloud: ✅ Via memory buffer (download→upload)
    - Cloud↔Cloud: ✅ Native API copy (Google Drive)
  - ✅ **Delete Operations**: All resource types
    - Local: ✅ Soft-delete (trash folder) + hard delete
    - SMB: ✅ Soft-delete + hard delete
    - SFTP: ✅ Soft-delete + hard delete
    - FTP: ✅ Soft-delete + hard delete
    - Cloud: ✅ Trash API (Google Drive)
  - ✅ **Rename Operations**: All resource types
    - Local: ✅ File.renameTo()
    - SMB: ✅ SmbClient.rename()
    - SFTP: ✅ SftpClient.rename()
    - FTP: ✅ FTPClient.rename()
    - Cloud: ✅ Drive API update()

### 🟠 High Priority (Quality & UX)

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

### 🟡 Medium Priority (Documentation & Polish)

- [ ] **README Update**
  - Document v2 features and changes
  - Add screenshots of main screens
  - Localize in en/ru/uk
  - Add installation instructions

- [ ] **CHANGELOG Creation**
  - Format: Added/Changed/Fixed/Removed
  - Document migration from v1 to v2
  - List all major features

- [ ] **Size Optimization**
  - Enable resource shrinking in release build
  - Check APK/AAB size
  - Remove unused resources and assets
  - Optimize images and drawables

- [ ] **Dependencies Update**
  - Update libraries to latest stable versions
  - Check compatibility and breaking changes
  - Test after updates

### 🔵 Low Priority (Store Preparation)

- [ ] **Play Store Materials**
  - Feature graphic (1024x500px) with app highlights
  - Screenshots (4-8 per device type)
  - Localized screenshots (en/ru/uk)
  - App icon verification on different launchers

- [ ] **Privacy Policy**
  - Document v2 data usage
  - Host online (GitHub Pages or own site)
  - Link in app and store listing

- [ ] **User Guide**
  - Features overview
  - FAQ section
  - Troubleshooting common issues
  - Localized (en/ru/uk)

---

## 🎯 Current Development - In Progress

- [ ] **FEATURE: OneDrive Integration - Phase 4** (Core REST API Implementation Complete)
  - ✅ MSAL 6.0.1 authentication library added (without Graph SDK)
  - ✅ OneDriveRestClient implemented with Microsoft Graph REST API v1.0
  - ✅ CloudMediaScanner updated to support OneDrive
  - ✅ Localized strings added (en/ru/uk)
  - ✅ msal_config.json template created
  - ⏳ **Remaining Tasks**:
    - Register Azure AD application in Microsoft Entra admin center
    - Configure Azure AD Client ID and redirect URI in `msal_config.json`
    - Create OneDriveFolderPickerActivity (similar to GoogleDriveFolderPickerActivity)
    - Add OneDrive authentication UI in AddResourceActivity
    - Handle MSAL interactive authentication flow
    - Test OAuth 2.0 flow and Graph API calls
  - **Technical Notes**:
    - **REST API approach** (no Graph SDK dependency) - avoids CompletableFuture/Coroutine conflicts
    - Direct HTTP calls to `graph.microsoft.com/v1.0` endpoints
    - MSAL 6.0.1 for OAuth 2.0 authentication only
    - Manual JSON parsing with org.json (no SDK models)
    - All CRUD operations: list, download, upload, delete, rename, move, copy, search
    - Thumbnail support with 3 sizes: small (96px), medium (176px), large (800px)
    - Uses `@microsoft.graph.downloadUrl` for efficient file downloads
    - ISO 8601 date parsing for `lastModifiedDateTime`

- [ ] **FEATURE: Dropbox Integration - Phase 4** (Core Implementation Complete)
  - ✅ Dropbox SDK 5.4.5 added to dependencies
  - ✅ DropboxClient implemented with full CloudStorageClient interface
  - ✅ CloudMediaScanner updated to support Dropbox
  - ✅ Localized strings added (en/ru/uk)
  - ⏳ **Remaining Tasks**:
    - Add Dropbox APP_KEY to `strings.xml` (requires Dropbox App Console registration)
    - Configure `Auth.startOAuth2PKCE()` in Application class or AddResourceActivity
    - Create DropboxFolderPickerActivity (similar to GoogleDriveFolderPickerActivity)
    - Add Dropbox authentication UI in AddResourceActivity
    - Add auth_callback scheme to AndroidManifest.xml
  - **Technical Notes**:
    - Uses OAuth 2.0 PKCE flow (more secure than legacy OAuth 1.0)
    - Paths use "/" prefix (e.g., "/Photos/vacation.jpg"), "" for root
    - Credentials serialized as JSON (access_token, refresh_token, expires_at, app_key)
    - All CRUD operations implemented (list, download, upload, delete, rename, move, copy)
    - Thumbnail support with 8 size options (64px to 2048px)

- [ ] **FEATURE: Google Drive Testing** - Phase 3
  - Requires Android OAuth client setup in Google Cloud Console
  - Package name + SHA-1 fingerprint needed
  - OAuth consent screen configuration
  - Test authorization flow and file operations

- [ ] **OPTIMIZATION: Pagination Testing**
  - Test with 1000+ files across all resource types
  - Verify PagingMediaFileAdapter performance
  - Test 5000+ file scenario
  - Check threshold behavior

---

## 🎯 Current Development Tasks

### 🔴 Critical (Blocking Release)

- [ ] **Google Drive OAuth Configuration**
  - **Status**: Implementation complete, needs OAuth2 client configuration in Google Cloud Console
  - **Blocker**: Cannot test without valid client ID + SHA-1 fingerprint
  - **Action**: Create Android OAuth client, add credentials to project
  - **Testing**: Add Google Drive folder → Browse → File operations

- [ ] **Pagination Testing (1000+ files)**
  - **Status**: Implementation complete, needs real-world testing
  - **Test scenarios**:
    - LOCAL: 1000+, 5000+ files (images/videos mix)
    - SMB: Large network shares (test over slow connection)
    - SFTP/FTP: 1000+ files with thumbnails
  - **Expected**: No lag, smooth scrolling, memory efficient

### 🟠 High Priority

- [ ] **Network Undo Operations - Testing**
  - **Status**: Implementation complete, needs verification
  - **Test cases**:
    - SMB/SFTP/FTP: Delete file → Undo → Verify restoration
    - Check trash folder creation permissions
    - Network timeout handling (slow connections)
    - Trash cleanup after 24 hours

- [ ] **Network Image Editing - Performance Testing**
  - **Status**: Implementation complete, needs performance validation
  - **Test with**:
    - Large images (10MB+) over slow network
    - Multiple edits (rotate, flip) in sequence
    - Connection interruption during download/upload
  - **Add**: Progress reporting, cancellation support

### 🔵 Low Priority (Polish)

- [ ] **Animations and Transitions**
  - Screen transitions (slide, fade, shared element)
  - RecyclerView item animations (add, remove, reorder)
  - Ripple effects for missing buttons
  - Smooth progress indicators

## ⚡ Performance Optimization (LOW PRIORITY)

- [ ] **ExoPlayer initialization off main thread** (~39ms blocking)
- [ ] **ExoPlayer audio discontinuity investigation** (warning in logs, не критично)
- [ ] **Background file count optimization** (duplicate SMB scans)
- [ ] **RecyclerView profiling** (onBind <1ms target, test on low-end devices)
- [ ] **Layout overdraw profiling** (<2x target)
- [ ] **Memory leak detection** (LeakCanary integration)
- [ ] **Battery optimization** (reduce sync on low battery)

## 🌐 Network Features

- [ ] **Cloud storage expansion** (OneDrive, Dropbox)
  - OneDrive/Dropbox API integration with OAuth2
  - Reuse CloudStorageClient interface
  - Test multi-cloud operations

- [ ] **Offline mode**
  - Cache thumbnails and metadata locally
  - Show cached data when network unavailable
  - Operation queue for delayed sync

## 🧪 Testing

- [ ] **Unit tests** (domain layer, >80% coverage)
- [ ] **Instrumented tests** (Room, Espresso UI flows)
- [ ] **Manual testing** (Android 8-14, tablets, file types, edge cases)
- [ ] **Security audit** (credentials, input validation, permissions)

## 🧰 Code Quality

- [ ] **Static analysis** (detekt/ktlint integration)
- [ ] **Edge cases** (empty folders, 1000+ files, long names, special chars)

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

## 🚀 Google Play Store

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
- ✅ **FEATURE: OneDrive REST API Implementation**
- **Implementation**: Microsoft Graph REST API v1.0 approach without Graph SDK
- **Components**:
  - **OneDriveRestClient.kt**: Full CloudStorageClient implementation via REST API
    - Authentication: MSAL 6.0.1 OAuth 2.0 with ISingleAccountPublicClientApplication
    - API: Direct HttpURLConnection calls to `graph.microsoft.com/v1.0`
    - Endpoints: `/me/drive`, `/me/drive/items/{id}`, `/me/drive/items/{id}/children`
    - File operations: download (via `@microsoft.graph.downloadUrl`), upload (PUT with InputStream)
    - Management: create/delete/rename/move/copy folders, search files, get thumbnails
    - JSON parsing: Manual with org.json.JSONObject/JSONArray
    - Progress callbacks: Supported for upload/download operations
  - **CloudMediaScanner**: OneDrive provider routing added
  - **Localization**: 7 strings per language (en/ru/uk) - sign_in, signed_in, sign_out, select_folder, etc.
  - **Configuration**: `msal_config.json` template for Azure AD setup
- **Build Status**: Successful (1m 57s), 3 nullable-type warnings (non-critical)
- **Key Advantage**: Avoids Graph SDK v5 CompletableFuture incompatibility with Kotlin coroutines

### Build 2.0.2511171110 ✅
- ✅ **FEATURE: Dropbox Core Implementation**
- **Implementation**: Complete CloudStorageClient implementation for Dropbox with OAuth 2.0 PKCE
- **Components**:
  - **DropboxClient.kt**: Full implementation of CloudStorageClient interface
    - Authentication: OAuth 2.0 PKCE flow with DbxCredential serialization
    - File operations: list, download, upload (with progress), getThumbnail
    - Management: create/delete/rename/move/copy files and folders
    - Search: Full-text search with optional MIME filter
    - Connection test: Validates authentication via currentAccount API
  - **CloudMediaScanner.kt**: Injected DropboxClient, updated getClient() to return dropboxClient for DROPBOX provider
  - **build.gradle.kts**: Added Dropbox Core SDK 5.4.5 dependency
  - **Localized strings**: Added 7 Dropbox-specific strings (sign_in, signed_in, sign_out, select_folder, authentication_failed, connection_test_success/failed) in English, Russian, Ukrainian
- **Changed files**: 6 files
  - Data layer: `DropboxClient.kt` (new, 700+ lines), `CloudMediaScanner.kt`
  - Build: `build.gradle.kts`
  - Resources: `strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
- **Technical Details**:
  - Uses DbxClientV2 with OAuth2 PKCE (more secure than OAuth 1.0)
  - Credentials stored as JSON: {access_token, refresh_token, expires_at, app_key}
  - Path convention: "/" prefix for all paths, "" for root folder
  - Thumbnail sizes: 8 options from 64x64 to 2048x1536
  - File type detection: Extension-based MIME type guessing
- **Next Steps**: UI integration (DropboxFolderPickerActivity, AddResourceActivity updates), APP_KEY configuration
- **Result**: Dropbox backend ready for UI integration, follows same pattern as Google Drive

### Build 2.0.2511170339 ✅ (Now Build 2.0.2511171110)
- ✅ **POLISH: UI Animations and Transitions**
- **Implementation**: Added smooth animations throughout the app following Material Design standards
- **Components**:
  - **RecyclerView animations**: `DefaultItemAnimator` in MainActivity with 300ms durations for add/remove/move/change operations
  - **Activity transitions**: Slide animations for forward navigation (slide_in_right, slide_out_left) and back navigation (slide_in_left, slide_out_right)
    - MainActivity → BrowseActivity: Slide left with 300ms animation
    - BrowseActivity → PlayerActivity: Slide left with 300ms animation
    - Back button navigation: Slide right with 300ms animation
  - **Ripple effects**: All buttons already have `?attr/selectableItemBackgroundBorderless` or Material styles with ripple effects
  - **Progress indicators**: Standard `ProgressBar` with smooth indeterminate animations (platform default)
- **Changed files**: 6 files
  - Animation resources: `slide_in_right.xml`, `slide_out_left.xml`, `slide_in_left.xml`, `slide_out_right.xml`
  - Activities: `MainActivity.kt`, `BrowseActivity.kt`, `PlayerActivity.kt`
- **Impact**: Smoother, more polished user experience with consistent 300ms animations
- **Result**: App now has professional Material Design motion throughout all interactions

### Build 2.0.2511170338 ✅
- ✅ **OPTIMIZATION: Database indexes for resources table**
- **Implementation**: Added 3 composite indexes to speed up frequently used queries
- **Indexes**:
  - `idx_resources_display_order_name` on `(displayOrder, name)` - Main resource list sorting
  - `idx_resources_type_display_order_name` on `(type, displayOrder, name)` - Filter by type queries
  - `idx_resources_is_destination_order` on `(isDestination, destinationOrder)` - Destinations retrieval
- **Migration 11→12**: Creates indexes using `CREATE INDEX IF NOT EXISTS`
- **Impact**: Speeds up resource list queries, especially with 50+ resources
- **Changed files**: 3 files
  - `ResourceEntity.kt`: Added `@Entity(indices = [...])` annotation
  - `AppDatabase.kt`: Version 11→12, created MIGRATION_11_12
  - `DatabaseModule.kt`: Registered MIGRATION_11_12
- **Testing**: Performance measurement with 100+ resources recommended
- **Result**: Optimized ORDER BY queries on displayOrder and name columns

### Build 2.0.2511170337 ✅
- ✅ **FEATURE: Background Sync UI for Network Resources**
- **Implementation**: Complete UI for periodic background sync of network resources (SMB/SFTP/FTP)
- **Components**:
  - **Database (Migration 10→11)**: Added `lastSyncDate: Long?` field to `MediaResource`/`ResourceEntity` to track last sync timestamp
  - **Worker**: `NetworkFilesSyncWorker` now updates `lastSyncDate` after each sync (both on file count change and unchanged)
  - **UI Indicator**: `ResourceAdapter` shows "Last sync: 2h ago" or "Never synced" for network resources using `DateUtils.getRelativeTimeSpanString()`
  - **Settings Tab**: New "Network" tab in Settings with:
    - Enable/disable background sync toggle (SwitchMaterial)
    - Sync interval slider (1-24 hours, default 4 hours)
    - "Sync Now" button for manual sync
    - Sync status indicator (Idle/In Progress/Completed/Failed)
  - **UseCase**: `SyncNetworkResourcesUseCase` - Manual sync trigger (all resources or single by ID)
- **Changed files**: 15 files
  - Domain: `Models.kt` (lastSyncDate field)
  - Data: `ResourceEntity.kt`, `AppDatabase.kt` (MIGRATION_10_11), `DatabaseModule.kt`, `ResourceRepositoryImpl.kt` (mappings)
  - Worker: `NetworkFilesSyncWorker.kt` (timestamp save)
  - UI: `ResourceAdapter.kt` (sync indicator), `item_resource.xml` (TextView), `NetworkSettingsFragment.kt` (new), `fragment_settings_network.xml` (new), `SettingsPagerAdapter.kt`, `SettingsActivity.kt` (5 tabs)
  - UseCase: `SyncNetworkResourcesUseCase.kt` (new)
  - Localization: `strings.xml` (en/ru/uk) - 20+ new strings (network_sync_settings, sync_interval, last_sync_time, etc.)
- **Testing**: Verify auto-sync after interval, manual sync trigger, timestamp updates in resource list, UI indicators
- **Result**: Full visibility into background sync status. Users can manually trigger sync and adjust interval. Last sync time visible in resource cards.

### Build 2.0.2511170336 ✅
- ✅ **FEATURE: Add video thumbnail extraction toggle in Settings**
- **Implementation**: User-controlled setting "Show video thumbnails" in Playback section
- **Default behavior**: OFF (instant placeholder icons for network videos - preserves optimization)
- **When enabled**: Attempts to extract first frame from network videos (may take 2+ seconds)
- **Architecture**:
  - Domain: `AppSettings.showVideoThumbnails` field (default: false)
  - Data: `SettingsRepositoryImpl` - DataStore key + read/write logic
  - UI: `fragment_settings_playback.xml` - SwitchMaterial widget
  - Binding: `SettingsFragments.kt` - ObserveData updates switch state
  - Adapters: `MediaFileAdapter` + `PagingMediaFileAdapter` - Conditional `load()` for network videos
  - Activity: `BrowseActivity` - Observes settings, passes `showVideoThumbnails` callback to adapters
- **Localization**: English, Russian, Ukrainian strings with "2+ seconds delay" warning
- **Changed files**: 8 files modified (AppSettings, SettingsRepositoryImpl, 3x strings.xml, fragment layout, SettingsFragments, 2x adapters, BrowseActivity)
- **Testing**: Toggle ON/OFF in Settings, verify network videos show placeholder when OFF, attempt extraction when ON
- **Result**: Users get choice between fast placeholders (default) or informative thumbnails (opt-in)

### Build 2.0.2511170301 ✅
- ✅ **BUG FIX: FTP file copy operation failing with "both source and destination are local"**
- **Issue**: FTP file operations incorrectly detected as Local→Local, causing "Invalid operation" error
- **Root cause**: FTP paths arriving with single slash format `ftp:/host:port/path` instead of `ftp://host:port/path`, bypassing `startsWith("ftp://")` protocol detection
- **Solution**:
  - Added `normalizeFtpPath()` utility method to fix malformed paths (converts `ftp:/` → `ftp://`)
  - Applied normalization to all FTP operation entry points:
    - `executeCopy()` - 8 path usages normalized (source/dest paths, all download/upload/copy calls)
    - `executeMove()` - 6 path usages normalized (source/dest/delete paths)
    - `executeDelete()` - 3 path usages normalized (trash folder creation, file loop, hard delete)
    - `parseFtpPath()` - Entry point normalization before parsing
  - Pattern matches existing SFTP fix in `SmbFileOperationHandler`
- **Changed files**: `FtpFileOperationHandler.kt` (~20 path usages updated)
- **Testing**: Verify FTP→Local copy, Local→FTP upload, FTP→FTP copy, move, delete operations
- **Log evidence**: Original error showed `path='ftp:/193.178.50.43:21/...'` instead of `ftp://`

### Build 2.0.2511170256 ✅
- ✅ **BUG FIX: Panel collapse state incorrectly saved on Back navigation**
- **Issue**: When user collapses Copy/Move panels and presses Back, panels briefly expand before exit and app saves expanded state
- **Root cause**: `populateDestinationButtons()` read collapsed state from settings instead of current UI state, causing state loss during button rebuild
- **Solution**: 
  - Changed `populateDestinationButtons()` to read CURRENT UI visibility (`binding.copyToButtonsGrid.isVisible`)
  - Preserves actual visual state during button grid rebuild
  - State no longer changes during Activity destruction
- **Changed files**:
  - **PlayerActivity.kt**: Modified `populateDestinationButtons()` to use `!binding.copyToButtonsGrid.isVisible` instead of `settings.copyPanelCollapsed`
- **Result**: Panel collapse state persists correctly. No visual "flash" on Back navigation. User's last interaction state preserved.

### Build 2.0.2511170250 ✅
- ✅ **FEATURE: Visible scrollbar for resource list**
- **Task**: Make scrollbar visible in MainActivity resource list when list doesn't fit in window
- **Solution**: 
  - Added `android:scrollbars="vertical"` to RecyclerView - enables vertical scrollbar
  - Added `android:scrollbarThumbVertical="?android:attr/colorControlNormal"` - theme-aware scrollbar color
  - Added `android:fadeScrollbars="false"` - keeps scrollbar always visible (no auto-hide)
- **Changed files**:
  - **activity_main.xml**: Updated `rvResources` RecyclerView with scrollbar attributes
- **Result**: Scrollbar immediately visible when resource list exceeds screen height. No fade-out animation. Theme-aware color (light/dark mode).

### Build 2.0.2511170242 ✅
- ✅ **FEATURE: Display sort mode in resource info**
- **Task**: Add current sort mode to resource info bar (e.g., "by Name ↑", "по имени ↑")
- **Solution**: 
  - Updated `buildResourceInfo()` in `BrowseActivity` to display sort mode with arrows
  - Added 8 localized strings: `sort_by_name_asc/desc`, `sort_by_date_asc/desc`, `sort_by_size_asc/desc`, `sort_by_type_asc/desc`
  - Format: "ResourceName (count) • path • by Name ↑ • selected"
- **Changed files**:
  - **strings.xml (en/ru/uk)**: Added sort mode display strings with arrows (↑/↓)
  - **BrowseActivity.kt**: Added `when` expression in `buildResourceInfo()` to map `SortMode` to localized strings

- ✅ **BUG FIX: Sort mode resets after refresh or resource reopen**
- **Issues**: 
  1. Sort mode resets to "by Name" on refresh button click
  2. Sort mode doesn't persist when closing and reopening resource
- **Root cause**: `setSortMode()` updated state but didn't save to database, unlike `toggleDisplayMode()`
- **Solution**: 
  - Modified `setSortMode()` to call `updateResourceUseCase()` with new sortMode (same pattern as displayMode)
  - Added database save before `loadMediaFiles()`
  - Refresh button already works correctly: `reloadFiles()` → `loadResource()` → loads sortMode from DB (line 132)
- **Changed files**:
  - **BrowseViewModel.kt**: Modified `setSortMode()` to save sortMode to ResourceEntity via `updateResourceUseCase()`

- ✅ **FEATURE: Hide invalid FTP metadata**
- **Task**: For FTP resources, hide size/date if invalid (size=0 or date=1970-01-01)
- **Solution**: Updated `buildFileInfo()` in `MediaFileAdapter` to display "—" for zero values
- **Changed files**:
  - **MediaFileAdapter.kt**: Modified `buildFileInfo()` to check `file.size > 0` and `file.createdDate > 0`, display "—" for invalid values

### Build 2.0.2511170227 ✅
- ✅ **BUG FIX: FTP/SFTP background file count fails without credentials**
- **Issue**: FTP/SFTP resources fail background file count with "No credentials ID provided" error
- **Root cause**: `startFileCountInBackground()` called `scanner.getFileCount()` without `credentialsId` parameter
- **Solution**: Added `credentialsId = resource.credentialsId` parameter to background file count call
- **Changed files**:
  - **BrowseViewModel.kt**: Added `credentialsId` parameter to `scanner.getFileCount()` in `startFileCountInBackground()` method

- ✅ **OPTIMIZATION: Prevent preload job memory leaks in PlayerActivity**
- **Issue**: Preload coroutines continue after PlayerActivity.onDestroy(), causing JobCancellationException in logs
- **Solution**: Track all preload jobs in list and cancel them in onDestroy()
- **Changed files**:
  - **PlayerActivity.kt**:
    - Added `preloadJobs: MutableList<Job>` field to track active preload jobs
    - Modified `preloadNextImageIfNeeded()`: Store network preload job in list
    - Modified `onDestroy()`: Cancel all preload jobs and clear list
    - Added `import kotlinx.coroutines.Job`

### Build 2.0.2511170220 ✅
- ✅ **BUG FIX: Panel collapse state persistence in PlayerActivity**
- **Issue**: Copy/Move panels flash expanded when returning to PlayerActivity, collapsed state not persisting
- **Root cause**: Race condition - `populateDestinationButtons()` cleared button grids asynchronously while state restoration ran in parallel coroutine
- **Solution**: 
  - Moved state restoration inside `populateDestinationButtons()` after button addition
  - State now loads before clearing buttons and applies after grid rebuild in same coroutine
  - Removed duplicate restoration code from `updatePanelVisibility()`
- **Changed files**:
  - **PlayerActivity.kt**:
    - Modified `populateDestinationButtons()`: Reads `copyPanelCollapsed`/`movePanelCollapsed` before clearing grids, applies after button addition
    - Simplified `updatePanelVisibility()`: Removed duplicate state restoration (now handled internally)
    - Removed unused `buttonCount` variable

### Build 2.0.2511170214 ✅
- ✅ **FEATURE: Copy Resource functionality**
- **User request**: "Unlike the 'create' button, when copying, all values for the new resource are taken from the currently selected resource in the list. The user only needs to specify the changes (differences from the original)."
- **Solution**: 
  - Modified `copySelectedResource()` to launch `AddResourceActivity` with resource ID instead of auto-creating copy
  - Added `EXTRA_COPY_RESOURCE_ID` intent extra and factory method to `AddResourceActivity`
  - Added copy mode detection in `onCreate()` with dynamic toolbar title (Add/Copy Resource)
  - Added `loadResourceForCopy()` method in `AddResourceViewModel` to fetch resource data
  - Added `preFillResourceData()` method to auto-populate fields based on resource type
  - Added `NavigateToAddResourceCopy` event to `MainViewModel` events
- **Changed files**:
  - **strings.xml (en/ru/uk)**: Added `add_resource_title` and `copy_resource_title` for toolbar differentiation
  - **AddResourceActivity.kt**:
    - Added `copyResourceId: Long?` field
    - Overridden `onCreate()` to detect copy mode and load resource
    - Added `preFillResourceData()` method with type-specific logic:
      - LOCAL: Shows folder picker with hint message
      - SMB: Pre-fills server, share name, port from path
      - SFTP: Pre-fills host, port, remote path from URI
      - FTP: Pre-fills host, port, remote path, sets FTP radio button
      - CLOUD: Shows cloud storage options with sign-in prompt
    - Added companion object with `createIntent(context, copyResourceId)` factory
  - **AddResourceViewModel.kt**:
    - Added `copyFromResource: MediaResource?` to state
    - Added `LoadResourceForCopy` event
    - Added `loadResourceForCopy(resourceId)` method with null-safety check
  - **MainViewModel.kt**:
    - Simplified `copySelectedResource()` to just emit navigation event
    - Added `NavigateToAddResourceCopy(copyResourceId)` event
  - **MainActivity.kt**:
    - Updated event handling to use `AddResourceActivity.createIntent()` factory
    - Added handler for `NavigateToAddResourceCopy` event
- **How it works**:
  1. User selects resource in MainActivity → clicks "Copy Resource" button (or "Copy From" in adapter)
  2. `MainViewModel.copySelectedResource()` emits `NavigateToAddResourceCopy` event
  3. MainActivity launches `AddResourceActivity` with `copyResourceId` extra
  4. AddResourceActivity detects copy mode → changes toolbar title → loads resource data
  5. ViewModel fetches resource from DB → emits `LoadResourceForCopy` event
  6. Activity receives event → calls `preFillResourceData()` → auto-fills fields based on type
  7. User reviews/modifies values (server, path, credentials) → adds resource normally
- **Result**: Full Copy Resource workflow per specification. Pre-fills all editable fields (server, port, path). User modifies only differences (e.g., different folder on same SMB server). No auto-creation → user controls final result.

### Build 2.0.2511170152 ✅
- ✅ **FEATURE: Player hint toggle in Settings + "Show Hint Now" button**
- **Issue**: No UI control for showing/hiding touch zones hint overlay on first PlayerActivity launch
- **Solution**: 
  - Added `showPlayerHintOnFirstRun: Boolean` field to AppSettings (domain model)
  - Added toggle switch in PlaybackSettings fragment to enable/disable first-run hint
  - Added "Show Hint Now" button to manually trigger hint display (resets first-run flag)
  - Implemented `isPlayerFirstRun` flag tracking in SharedPreferences (persistent across app restarts)
  - Updated PlayerActivity to check settings + flag, show hint overlay on first media load with 500ms delay
  - Added methods `setPlayerFirstRun()` and `isPlayerFirstRun()` to SettingsRepository/RepositoryImpl
  - Added `resetPlayerFirstRun()` method to SettingsViewModel
- **Changed files**:
  - **strings.xml (en/ru/uk)**: Added `show_player_hint`, `show_player_hint_description`, `show_hint_now` strings
  - **fragment_settings_playback.xml**: Added SwitchMaterial `switchShowPlayerHint` + Button `btnShowHintNow` after `switchDetailedErrors`
  - **SettingsFragments.kt (PlaybackSettingsFragment)**: 
    - Added switch listener for `switchShowPlayerHint` → updates `settings.showPlayerHintOnFirstRun`
    - Added button listener for `btnShowHintNow` → calls `viewModel.resetPlayerFirstRun()` + shows Toast
    - Added observeData binding for switch state
  - **SettingsViewModel.kt**: Added `resetPlayerFirstRun()` method (calls `settingsRepository.setPlayerFirstRun(true)`)
  - **SettingsRepository.kt**: Added interface methods `setPlayerFirstRun(Boolean)` and `isPlayerFirstRun(): Boolean`
  - **SettingsRepositoryImpl.kt**:
    - Added `KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN` DataStore key
    - Added read/write for `showPlayerHintOnFirstRun` field in getSettings()/updateSettings()
    - Implemented `setPlayerFirstRun()` and `isPlayerFirstRun()` using SharedPreferences (for synchronous onCreate access)
  - **AppSettings.kt**: Added `showPlayerHintOnFirstRun: Boolean = true` field (Playback settings section)
  - **PlayerActivity.kt**:
    - Added `hasShownFirstRunHint: Boolean` flag to prevent multiple hints in one session
    - Added import for `kotlinx.coroutines.delay`
    - Updated `updateUI()`: checks `settings.showPlayerHintOnFirstRun` + `settingsRepository.isPlayerFirstRun()`
    - If both true + currentFile != null: delays 500ms → calls `showFirstRunHintOverlay()` → sets flag to false
- **How it works**:
  1. User opens Settings → Playback tab → sees "Show touch zones hint on first run" toggle (enabled by default)
  2. First PlayerActivity launch with hint enabled: overlay shows after 500ms delay (auto-dismiss after 5s or on tap)
  3. Subsequent launches: hint not shown (flag persisted in SharedPreferences)
  4. User can click "Show Hint Now" button → resets flag → hint shows on next PlayerActivity launch
  5. Toggle can be disabled anytime to prevent hint on fresh installs or after app data clear
- **Result**: Full UI control for first-run hint display. Users can re-trigger hint manually without clearing app data. Hint only shows once per install unless manually reset.

### Build 2.0.2511170144 ✅
- ✅ **FEATURE: PlayerActivity respects settings for overwrite and goToNext behavior**
- **Issue**: PlayerActivity hardcoded overwriteFiles=false and didn't respect goToNextAfterCopy setting
- **Solution**: 
  - Updated `showCopyDialog()` to read settings and pass `settings.overwriteOnCopy` + `settings.goToNextAfterCopy`
  - Updated `showMoveDialog()` to read settings and pass `settings.overwriteOnMove`
  - Updated `performCopyOperation()` to use `settings.overwriteOnCopy` and respect `settings.goToNextAfterCopy`
  - Updated `performMoveOperation()` to use `settings.overwriteOnMove`
  - Added undo operation saving if `settings.enableUndo` enabled
  - Fixed sourceFolderName to display actual resource name instead of "Current folder"
- **Changed files**:
  - `PlayerActivity.kt`: 4 methods updated (showCopyDialog, showMoveDialog, performCopyOperation, performMoveOperation)
- **Settings used**:
  - `overwriteOnCopy: Boolean = false`
  - `overwriteOnMove: Boolean = false`
  - `goToNextAfterCopy: Boolean = true`
  - `enableUndo: Boolean = true`
- **Result**: Copy/move operations in PlayerActivity now fully respect user preferences from Settings

### Build 2.0.2511170110 ✅
- ✅ **FEATURE: Resource metadata tracking and display**
- **User requests**:
  - "в одной из SMB папок 63000 файлов. Я вижу в списке ресурсов 10000. Если ты не можешь быстро сосчитать до 63000, то хотя бы показывай текст '>10000'"
  - "При обновлении списка ресурсов - число не меняется. Нужно чтобы 1. при ручном обновлении ресурсов (кнопка обновить) обновлялось число медиафайлов внутри. 2. После работы с ресурсом (кнопка назад) можно обновить запись этого ресурса в списке ресурсов (число файлов, доступность)"
  - "В карточке ресурса доступной по кнопке 'редактировать' для ресурса нужно информационо в конце показывать рядом с датой и временем создания ресурса дополнительно дату и время последнего Browse ресурса"
- **Solution**: 
  - **Display ">10000" for large folders**: ResourceAdapter shows ">10000 files" when fileCount > 10000 (performance optimization)
  - **Auto-update fileCount after browse**: BrowseViewModel updates resource metadata (fileCount + lastBrowseDate) after successful file load
  - **Track last browse date**: New DB field `lastBrowseDate` in resources table (migration 9→10)
  - **Show dates in EditResourceActivity**: Display creation date and last browse date (or "Never browsed")
- **Changes**:
  - **DB Migration 9→10**: Added `lastBrowseDate INTEGER DEFAULT NULL` column to resources table
  - **Models.kt**: Added `lastBrowseDate: Long?` field to MediaResource
  - **ResourceEntity.kt**: Added `lastBrowseDate: Long?` field to database entity
  - **AppDatabase.kt**: Version 9→10, created MIGRATION_9_10
  - **DatabaseModule.kt**: Registered MIGRATION_9_10 in migrations list
  - **ResourceRepositoryImpl.kt**: Updated toDomain/toEntity mappings for lastBrowseDate field
  - **ResourceAdapter.kt**: 
    - Conditional fileCount display: `when { resource.fileCount > 10000 -> ">10000 files" else -> "${resource.fileCount} files" }`
  - **BrowseViewModel.kt**:
    - Added `updateResourceMetadataAfterBrowse()` method - updates fileCount and lastBrowseDate after file load
    - Called after standard loading (line ~258) and pagination setup (line ~295)
    - Updates database via `updateResourceUseCase(resource.copy(fileCount = actualCount, lastBrowseDate = System.currentTimeMillis()))`
  - **activity_edit_resource.xml**: 
    - Added full-width row with lastBrowseDate display (below Created/FileCount row)
    - TextView `tvLastBrowseDate` with label `tvLastBrowseDateLabel`
  - **EditResourceActivity.kt**: 
    - Display lastBrowseDate formatted or "Never browsed" if null
    - Code: `binding.tvLastBrowseDate.text = resource.lastBrowseDate?.let { dateFormat.format(Date(it)) } ?: getString(R.string.never_browsed)`
  - **String resources** (en/ru/uk):
    - `last_browse_date`: "Last Browse Date" / "Дата последнего просмотра" / "Дата останнього перегляду"
    - `never_browsed`: "Never browsed" / "Никогда не просматривался" / "Ніколи не переглядався"
- **How it works**:
  1. User browses resource in BrowseActivity → files load → ViewModel updates `fileCount` (actual) + `lastBrowseDate` (timestamp)
  2. User returns to MainActivity (Back button) → onResume() calls refreshResources() → loads updated data from DB
  3. Large folders (>10000 files): Display shows ">10000 files", EditResourceActivity shows exact count
  4. EditResourceActivity: Shows creation date + last browse date (or "Never browsed" for new resources)
- **Result**: 
  - Resource list always shows accurate file counts after browsing
  - Large folders display ">10000 files" for performance (real count stored in DB)
  - Users can track when they last browsed each resource
  - Manual refresh button in MainActivity reloads from database (already implemented in previous builds)

### Build 2.0.2511170100 ✅
- ✅ **FEATURE: Кнопка "Полноэкранный режим" в командной панели**
- **User request**: Нужна команда для возврата к полноэкранному проигрыванию из командной панели, перед кнопкой слайдшоу
- **Solution**: Добавлена кнопка btnFullscreenCmd с иконкой полноэкранного режима
- **Changes**:
  - **Created**: `ic_fullscreen.xml` - vector drawable с иконкой fullscreen (24x24dp, 4 угловых стрелки)
  - **Updated**: `strings.xml` (en/ru/uk) - добавлена строка `fullscreen_mode`
  - **Updated**: `player_command_panel_mode.xml` - добавлена btnFullscreenCmd перед btnSlideshowCmd
  - **Updated**: `activity_player_unified.xml` - добавлена btnFullscreenCmd перед btnSlideshowCmd
  - **Updated**: `PlayerActivity.kt` - добавлен setOnClickListener для btnFullscreenCmd, вызывает `viewModel.toggleCommandPanel()`
- **How it works**:
  - Пользователь открывает командную панель (зона 7 или swipe сверху)
  - Нажимает кнопку полноэкранного режима (иконка 4 стрелки в углы)
  - Командная панель скрывается, возвращается полноэкранный режим
  - Предпочтение сохраняется в базе данных для текущего ресурса
- **Result**: Удобный быстрый возврат к полноэкранному проигрыванию. Кнопка расположена перед слайдшоу (как запрошено).

### Build 2.0.2511170056 ✅
- ✅ **FIXED: Rename и Edit команды имели одинаковую иконку**
- **User complaint**: В командных панелях проигрывателя команды Rename и Edit отображались с одинаковой иконкой (ic_menu_edit)
- **Root cause**: Обе кнопки использовали `@android:drawable/ic_menu_edit`
- **Solution**: Создан кастомный drawable `ic_rename.xml` с иконкой карандаша + подчёркивание (символизирует редактирование имени)
- **Changes**:
  - **Created**: `ic_rename.xml` - vector drawable с иконкой edit + line (24x24dp, adaptive color)
  - **Updated**: `player_command_panel_mode.xml` - заменён `android:src` для btnRenameCmd: `ic_menu_edit` → `@drawable/ic_rename`
  - **Updated**: `activity_player_unified.xml` - заменён `android:src` для btnRenameCmd: `ic_menu_edit` → `@drawable/ic_rename`
- **Result**: Команды теперь визуально различимы:
  - **Rename**: карандаш с подчёркиванием (редактирование имени)
  - **Edit**: карандаш без подчёркивания (редактирование изображения)

### Build 2.0.2511170053 ✅
- ✅ **FIXED: SmbDataSource logging InterruptedException as ERROR on player exit**
- **User report**: В логах ERROR при выходе из проигрывателя (InterruptedException → SMBRuntimeException)
- **Root cause**: ExoPlayer прерывает фоновый поток при `onDestroy()` → `inputStream.read()` получает `InterruptedException` → SMBJ оборачивает в `SMBRuntimeException` → `SmbDataSource.read()` логирует как ERROR
- **Solution**: Добавлен check в catch block - различаем нормальное прерывание от реальных ошибок
- **Changes**:
  - **SmbDataSource.kt** (line ~172):
    - Added interruption detection: checks `InterruptedException` in exception chain or message
    - Normal interruption: logs as DEBUG "Read operation interrupted (player closed)"
    - Real errors: logs as ERROR with full stacktrace
- **Result**: Логи чище - штатные прерывания (Back button, orientation change, app switch) не показываются как ошибки. Реальные сетевые проблемы логируются как ERROR.

### Build 2.0.2511170048 ✅
- ✅ **FEATURE: UI для выбора метода SFTP аутентификации (Password / SSH Key)**
- **User request**: Добавить UI в AddResourceActivity для выбора метода SFTP аутентификации (пароль или SSH ключ)
- **Backend**: SSH key authentication полностью реализован в build 2511170026 (DB migration 8→9, SftpClient, UseCases)
- **Changes**:
  - **activity_add_resource.xml**:
    - Added RadioGroup для выбора метода (Password / SSH Key)
    - Added layoutSftpPasswordAuth (visible by default) - EditText для пароля
    - Added layoutSftpSshKeyAuth (hidden by default):
      - EditText для private key (multiline, monospace, scrollable)
      - Button "Load File" для загрузки ключа из файла
      - EditText для key passphrase (optional, password toggle)
  - **AddResourceActivity.kt**:
    - Added `sshKeyFilePickerLauncher` (ActivityResultLauncher для OpenDocument)
    - Added RadioGroup listener для переключения visibility между password/key layouts
    - Added Button listener для загрузки SSH ключа из файла
    - Updated `testSftpConnection()`: auto-selects password or key auth based on RadioButton
    - Updated `addSftpResource()`: auto-selects password or key auth based on RadioButton
    - Added `loadSshKeyFromFile()`: reads PEM key from ContentResolver
  - **AddResourceViewModel.kt**:
    - Added `testSftpConnectionWithKey()`: calls `smbOperationsUseCase.testSftpConnection()` with privateKey + keyPassphrase
    - Added `addSftpResourceWithKey()`: saves credentials with privateKey, creates resource, scans folder
    - **IMPORTANT**: Passphrase stored in `password` field when using SSH key auth (encrypted via CryptoHelper)
  - **SftpMediaScanner.kt**:
    - Updated `scanFolder()`: pass passphrase to `connectWithPrivateKey(passphrase = connectionInfo.password.ifEmpty { null })`
    - Updated `isWritable()`: pass passphrase to `testConnectionWithPrivateKey(passphrase = connectionInfo.password.ifEmpty { null })`
  - **String resources** (en/ru/uk):
    - sftp_auth_method, sftp_auth_password, sftp_auth_ssh_key
    - sftp_private_key, sftp_private_key_hint, sftp_load_key
    - sftp_key_passphrase, sftp_key_passphrase_hint
    - sftp_key_load_error, sftp_key_invalid
- **How it works**:
  1. User selects "SSH Key" radio button → password layout hides, SSH key layout shows
  2. User pastes key or clicks "Load File" → file picker opens → key content loaded into EditText
  3. User optionally enters passphrase (if key is encrypted)
  4. Test Connection → `testSftpConnectionWithKey()` → SftpClient.testConnectionWithPrivateKey()
  5. Add Resource → `addSftpResourceWithKey()` → saves credentials (password field = passphrase, sshPrivateKey field = encrypted key)
  6. Future scans → SftpMediaScanner retrieves privateKey + passphrase (from password field) → connects via `connectWithPrivateKey()`
- **Result**: Full UI для SSH key authentication. Supports encrypted keys with passphrase. Backend реализован в build 2511170026, frontend готов для тестирования.

### Build 2.0.2511170039 ✅
- ✅ **FIXED: Touch zones scheme - крупные читаемые цифры**
- **User complaint**: На схеме зон вместо цифр отображались точки/белиберда
- **Root cause**: `touch_zones_numbered.xml` использовал pathData для рисования цифр со strokeWidth=2.5 - масштаб слишком мелкий для viewport 360x640
- **Solution**: Created `touch_zones_with_labels.xml` с крупными читаемыми цифрами:
  - Белый круг диаметром 70px (radius=35) вместо 36px (radius=18)
  - Цифры нарисованы заливкой fillColor (не stroke) с шириной ~24px и высотой ~40-60px
  - Цвет цифры соответствует цвету зоны для контраста
  - Каждая цифра: крупная, жирная, хорошо видна на белом фоне
- **Changes**:
  - **Created**: `touch_zones_with_labels.xml` - новый drawable с крупными цифрами 1-9
  - **Updated**: `fragment_settings_playback.xml` - заменён @drawable/touch_zones_numbered → @drawable/touch_zones_with_labels
  - **Updated**: `page_welcome_touch_zones.xml` - заменён @drawable/touch_zones_numbered → @drawable/touch_zones_with_labels
- **Zones**:
  - 1 = BACK (red) | 2 = COPY (cyan) | 3 = RENAME (yellow)
  - 4 = PREVIOUS (light cyan) | 5 = MOVE (light green) | 6 = NEXT (light cyan)
  - 7 = COMMAND PANEL (purple) | 8 = DELETE (light red) | 9 = SLIDESHOW (orange)
- **Result**: Цифры теперь крупные, читаемые, видны на любом экране

### Build 2.0.2511170035 ✅
- ✅ **CONFIRMED: touch_zones_numbered.xml used in app_v2**
- **User complaint**: touch_zones_scheme.xml shows "белиберда вместо цифр" (gibberish instead of numbers)
- **Investigation**:
  - V2 has TWO zone scheme files:
    - `touch_zones_scheme.xml` - OLD version without numbers (colored zones only, from V1)
    - `touch_zones_numbered.xml` - NEW version with numbered zones 1-9 in white circles
  - **Confirmed**: app_v2 uses `touch_zones_numbered.xml` (verified via grep)
    - `page_welcome_touch_zones.xml`: android:src="@drawable/touch_zones_numbered"
    - `fragment_settings_playback.xml`: android:src="@drawable/touch_zones_numbered"
  - `touch_zones_scheme.xml` is UNUSED legacy file from V1
- **Zones in touch_zones_numbered**:
  - 1 = BACK (top-left, red)
  - 2 = COPY (top-center, cyan)
  - 3 = RENAME (top-right, yellow)
  - 4 = PREVIOUS (middle-left, light cyan)
  - 5 = MOVE (middle-center, light green)
  - 6 = NEXT (middle-right, light cyan)
  - 7 = COMMAND PANEL (bottom-left, purple)
  - 8 = DELETE (bottom-center, light red)
  - 9 = SLIDESHOW (bottom-right, orange)
- **Result**: Rebuild выполнен, APK содержит правильную схему с цифрами
- **Possible cause**: User had old APK or looked at V1 version

### Build 2.0.2511170026 ✅
- ✅ **FEATURE: SSH Private Key authentication support for SFTP**
- **User request**: Allow both password and SSH private key authentication methods for SFTP
- **Changes**:
  - **DB Migration 8→9**: Added `sshPrivateKey TEXT DEFAULT NULL` column to network_credentials table
  - **NetworkCredentialsEntity.kt**:
    - Added `sshPrivateKey: String?` field (encrypted, PEM format)
    - Added `decryptedSshPrivateKey` computed property with decryption logic
    - Updated `create()` factory method to accept and encrypt SSH private key
  - **SftpClient.kt**:
    - Added `connectWithPrivateKey()` method using JSch identity API
    - Added `testConnectionWithPrivateKey()` for key-based connection testing
    - Both methods support optional passphrase for encrypted keys
  - **SmbOperationsUseCase.kt**:
    - Updated `testSftpConnection()` to accept privateKey + keyPassphrase parameters
    - Auto-selects password or key auth based on privateKey presence
    - Updated `saveSftpCredentials()` to save SSH private key
  - **ResourceRepositoryImpl.kt**:
    - Updated `testSftpConnection()` to pass decrypted private key from credentials
  - **SftpMediaScanner.kt**:
    - Updated `SftpConnectionInfo` data class to include `privateKey: String?`
    - Modified `scanFolder()` and `isWritable()` to auto-select auth method
    - Updated `parseSftpPath()` to retrieve privateKey from credentials
  - **DatabaseModule.kt**: Added MIGRATION_8_9 to migration list
  - **AppDatabase.kt**: Version 8→9, added MIGRATION_8_9
- **Result**: SFTP теперь поддерживает оба метода аутентификации:
  - Password authentication (keyboard-interactive/password) - работает с build 2511170019
  - SSH Private Key authentication (publickey) - новая функциональность
  - Автоматический выбор метода на основе наличия privateKey в credentials
- **TODO**: Добавить UI для ввода/загрузки SSH ключа в AddResourceActivity (RadioGroup для выбора метода)

### Build 2.0.2511170019 ✅
- ✅ **FIXED: SFTP keyboard-interactive authentication**
- **Root cause**: Server требует keyboard-interactive auth, JSch не передавал password через UserInfo callback
- **Solution**: Added UserInfo implementation with password callbacks in connect() and testConnection()
- **Changes**:
  - **SftpClient.kt**:
    - Added `userInfo` object implementing JSch's UserInfo interface
    - `getPassword()` returns password, `promptPassword()` returns true
    - Changed `PreferredAuthentications` to "keyboard-interactive,password" (keyboard-interactive first)
- **Result**: SFTP connects to servers requiring keyboard-interactive auth with password

### Build 2.0.2511170017 ✅
- ✅ **FIXED: SettingsRepositoryImpl compilation errors**
- **Root cause**: Forgot to update DataStore key constant and read/write operations after renaming fullScreenMode→defaultShowCommandPanel
- **Changes**:
  - **SettingsRepositoryImpl.kt**:
    - Renamed `KEY_FULL_SCREEN_MODE` → `KEY_DEFAULT_SHOW_COMMAND_PANEL`
    - Updated getSettings(): reads `KEY_DEFAULT_SHOW_COMMAND_PANEL` instead of `KEY_FULL_SCREEN_MODE`
    - Updated updateSettings(): writes `settings.defaultShowCommandPanel` instead of `settings.fullScreenMode`
- **Result**: Build successful, Task 2 complete

### Build 2.0.2511170016 ❌ (FAILED - Missing SettingsRepositoryImpl updates)
- ❌ **ATTEMPTED: Task 2 - Command panel default setting**
- **Changes made**:
  - String resources updated (en/ru/uk)
  - All Kotlin code updated
- **FAILED**: Compilation error - SettingsRepositoryImpl still referenced old field name
- **Next**: Fix SettingsRepositoryImpl key constant and read/write operations

### Build 2.0.2511170006 ✅
- ✅ **UI: Changed unavailable resource indicator from red dot to background highlight + N/A text**
- **User request**: Replace red dot with pale pink/dark gray background highlight and "N/A" text near lock icon
- **Changes**:
  - **colors.xml (light)**: Added `unavailable_resource_bg` = #FFFFE0E6 (pale pink)
  - **colors.xml (dark)**: Added `unavailable_resource_bg` = #FF3A3A3A (dark gray)
  - **item_resource.xml**: 
    - Added `android:id="@+id/rootLayout"` to ConstraintLayout for background control
    - Changed `vAvailabilityIndicator` (red dot View) → `tvAvailabilityIndicator` (TextView with "N/A")
    - Positioned tvAvailabilityIndicator between lock icon and edit button
    - Removed old red_dot_indicator.xml drawable
  - **ResourceAdapter.kt**:
    - Added `ColorStateList` and `ContextCompat` imports
    - Changed `vAvailabilityIndicator` → `tvAvailabilityIndicator` visibility control
    - Added `rootLayout.backgroundTintList` setting: pale pink/dark gray when unavailable, null when available
- **Result**: Unavailable resources now have subtle background highlight (theme-aware) with "N/A" text indicator. More user-friendly than red dot.

### Build 2.0.2511162358 ✅
- ✅ **CRITICAL: Migrated SSHJ → JSch for SFTP**
- **Root cause**: Android BouncyCastle 1.78.1 missing critical algorithms:
  - ❌ X25519 (Curve25519SHA256 KEX)
  - ❌ SHA-256 MessageDigest (DHGexSHA256 KEX)  
  - ❌ EC KeyPairGenerator (ECDHNistP - all ECDH variants)
  - ✅ Only DHGexSHA1 available (weak, rejected by modern SSH servers)
- **Solution**: Complete migration to JSch 0.2.16 (com.github.mwiede)
  - JSch has built-in KEX implementations (ECDH, DH-group14/16/18) without BC dependency
  - Supports modern SSH servers requiring ECDH or DH-group-exchange-sha256
- **Changed files**:
  - `build.gradle.kts`: Replaced `sshj:0.37.0` with `jsch:0.2.16`, removed `eddsa:0.3.0`, added META-INF wildcard exclusion
  - `SftpClient.kt`: Complete rewrite (444 lines)
    - Uses `com.jcraft.jsch.*` instead of `net.schmizz.sshj.*`
    - Core types: `Session` + `ChannelSftp` instead of `SSHClient` + `SFTPClient`
    - Added backward-compatibility wrappers: `renameFile()`, `createDirectory()`, `getFileAttributes()`
    - Added `PreferredAuthentications = "password,publickey,keyboard-interactive"` for password-first auth
  - `SftpFileOperationHandler.kt`: Fixed `uploadFile()` calls - converted InputStream to ByteArray (3 locations)
  - `SftpDataSource.kt`: Rewritten for JSch (ExoPlayer SFTP streaming)
- **API Changes**:
  - `uploadFile(remotePath: String, data: ByteArray)` - no longer accepts InputStream
  - `rename(oldPath, newPath)` - replaces SSHJ's `renameFile()` (wrapper added for compatibility)
  - `mkdir(remotePath)` - replaces SSHJ's `createDirectory()` (wrapper added)
  - `stat(remotePath): Result<SftpFileAttributes>` - replaces SSHJ's `getFileAttributes()` (wrapper added)
- **Result**: SFTP now works with modern servers requiring ECDH/modern KEX. Password authentication prioritized. Ready for production testing.

### Build 2.0.2511162338 ✅
- ✅ **FIXED: isAvailable not updating on exceptions**
- **Root cause**: `openResource()` и `scanAllResources()` catch blocks не обновляли `isAvailable = false`
- **Solution**: Added `updateResourceUseCase(resource.copy(isAvailable = false))` in both exception handlers
- **Changed files**:
  - `MainViewModel.kt`: 
    - `openResource()` catch block (line 205): Update isAvailable=false on any exception during testConnection
    - `scanAllResources()` catch block (line 448): Update isAvailable=false on resource check failure
- **Result**: Красная точка появляется при любой ошибке подключения (timeout, network unreachable, authentication failure)

### Build 2.0.2511162331 ✅
- ✅ **HIGH PRIORITY: Resource Availability Indicator**
- **Feature**: Red dot indicator showing unavailable resources in MainActivity
- **Changes**:
  - **DB Migration 6→7**: Added `isAvailable BOOLEAN NOT NULL DEFAULT 1` column to resources table
  - **ResourceEntity.kt**: Added `isAvailable: Boolean = true` field
  - **MediaResource.kt**: Added `isAvailable: Boolean = true` domain field
  - **ResourceRepositoryImpl.kt**: Updated `toDomain()` and `toEntity()` mapping
  - **item_resource.xml**: Added red circle View (`vAvailabilityIndicator`) at top-right corner
  - **red_dot_indicator.xml**: Created drawable (12dp red circle #F44336)
  - **ResourceAdapter.kt**: Added visibility logic (VISIBLE when !isAvailable, GONE when isAvailable)
  - **MainViewModel.kt**: Auto-update isAvailable on testConnection success/failure + scanAllResources()
  - **BrowseViewModel.kt**: Auto-update isAvailable=false on connection errors (handleLoadingError)
  - **DatabaseModule.kt**: Added MIGRATION_6_7 to migration list
- **Result**: Пользователи видят красную точку на недоступных ресурсах (отключенный сервер, неверные credentials). Статус автоматически обновляется при попытке подключения.
- **Migration tested**: Existing resources default to isAvailable=true on upgrade from DB v6

### Build 2.0.2511162325 ⚠️ (ATTEMPTED - FAILED)
- ⚠️ **ATTEMPTED: SFTP ECDH KEX support**
- **Root cause**: Server rejects weak DHGexSHA1, requires modern KEX (ecdh-sha2-nistp256/384/521)
- **Attempted solution**: Add ECDHNistP with NIST curves
- **FAILED**: `no such algorithm: EC for provider BC` - Android BouncyCastle 1.78.1 does NOT support EC (Elliptic Curve) KeyPairGenerator
- **Android BC Limitations**:
  - ❌ X25519 (Curve25519SHA256 KEX)
  - ❌ SHA-256 MessageDigest (DHGexSHA256 KEX)
  - ❌ EC KeyPairGenerator (ECDHNistP - all ECDH variants)
  - ✅ DHGexSHA1 (old/weak, modern servers reject)
- **Reverted**: Removed ECDHNistP import, back to DHGexSHA1 only
- **Status**: **BLOCKED** - SSHJ incompatible with modern SSH servers on Android without EC support
- **Options**:
  1. Ask server admin to enable DHGexSHA1 (security risk)
  2. Switch to JSch library (has own KEX, no BC dependency)
  3. Use FTP instead of SFTP for this server
  4. Upgrade BouncyCastle to full JVM version (may break Android compatibility)

### Build 2.0.2511162316 ✅
- ✅ **FIXED: SFTP SHA-256 algorithm error**
- **Root cause**: DHGexSHA256 KEX требует SHA-256 MessageDigest, который отсутствует в Android BouncyCastle 1.78.1 (есть только SHA-1, SHA-224, SHA-384, SHA-512)
- **Solution**: Оставлен только DHGexSHA1.Factory() (использует SHA-1) в кастомном config. Удалён DHGexSHA256.Factory()
- **Changed files**:
  - `SftpClient.kt`: Удалён import DHGexSHA256, убран из `connect()` и `testConnection()` KEX lists
- **Result**: SFTP connections используют Diffie-Hellman Group Exchange с SHA-1 hashing (совместимо с Android BC)

### Build 2.0.2511162309 ⚠️ (PARTIAL FIX)
- ⚠️ **ATTEMPTED: SFTP testConnection() X25519 error**
- Fixed `testConnection()` creating `SSHClient()` without custom config, but DHGexSHA256 still required SHA-256
- Real issue: Android BC missing SHA-256 for MessageDigest (only has SHA-1, SHA-224, SHA-384, SHA-512)

### Build 2.0.2511162305 ✅
- ✅ **FIXED: Неполная загрузка FTP-файлов (progressive JPEG error)**
- **Root cause**: `ByteArrayOutputStream` не закрывался после `downloadFileWithNewConnection()`, данные не были полностью записаны в буфер
- **Solution**: 
  - `NetworkFileFetcher.kt`: Добавлен `try-finally` с явным `outputStream.close()`
  - `FtpClient.kt`: Добавлен `outputStream.flush()` перед возвратом из `downloadFileWithNewConnection()`
- **Result**: Все байты записываются в stream до его использования в Coil

### Build 2.0.2511162254 ✅
- ✅ **FIXED: FTP parallel download race condition (FTPConnectionClosedException)**
- **Root cause**: `@Singleton FtpClient` держит одно TCP-соединение. При параллельной загрузке thumbnails (3 одновременных coroutine) второе/третье соединение пытается использовать занятый socket → "Connection closed without indication"
- **Solution**: Добавлен `downloadFileWithNewConnection()` - создаёт временный FTPClient для каждой загрузки. Каждая параллельная операция получает независимое TCP-соединение
- **Changed files**:
  - `FtpClient.kt`: Метод `downloadFileWithNewConnection()` с полным lifecycle (connect → download → disconnect)
  - `NetworkFileFetcher.kt`: Вместо singleton `connect()`+`downloadFile()`+`disconnect()` использует `downloadFileWithNewConnection()`
- **Result**: Параллельная загрузка thumbnails работает корректно. Video streaming использует singleton connection (безопасно: один активный поток за раз)

### Build 2.0.2511162305 ⚠️ (PARTIAL FIX)
- ⚠️ **ATTEMPTED: FTP parallel download NPE (synchronization)**
- Added `synchronized(mutex)` для `downloadFile()` и `listFiles()`
- **Не решило проблему**: Race condition на уровне TCP socket, не на уровне thread safety
- **Реальная проблема**: Single FTPClient socket не может обрабатывать несколько одновременных `retrieveFile()` вызовов

### Build 2.0.2511162246 ⚠️ (FAILED)
- ✅ **FIXED: FTP video playback error (ParserException: Invalid NAL length)**
- Root cause: FtpDataSource не получал размер файла (fileSize=0), ExoPlayer не мог корректно парсить MP4
- Solution: Добавлен SIZE FTP-команда, убран `completePendingCommand()`, используется `abort()`
- Result: FTP видео воспроизводятся без ошибок парсинга

### Build 2.0.2511162234 ✅
- ✅ **FIXED: FTP video playback error** - Added FTP to network resource check in `playVideo()`
- Root cause: Condition checked only SMB/SFTP, FTP fell through to local file playback
- Solution: Added `ResourceType.FTP` to network resource condition (line 1121)
- Result: FTP videos now use FtpDataSource streaming (already implemented in build 2511162212)

### Build 2.0.2511162232 ✅
- ✅ **FIXED: FTP thumbnails not loading** - Added active mode fallback for `downloadFile()` on passive timeout
- Root cause: Parallel thumbnail requests create multiple data connections, emulator can't connect to passive ports
- Solution: Catch `SocketTimeoutException` in `downloadFile()`, retry with active mode, restore passive
- Test: FTP folder with images/videos → thumbnails load via active mode fallback

### Build 2.0.2511162226 ✅ CONFIRMED WORKING
- ✅ **FIXED: Loading state text bug** - "No media files found" during loading → now shows "Loading..."
- ✅ **FIXED: SMB file selection bug** - Clicked file opens correctly (indexOf by path instead of object reference)

### Build 2.0.2511162151 ✅ CONFIRMED WORKING
- ✅ **FIXED: submitList redundancy during navigation** - Moved list tracking from Activity to ViewModel
- ✅ **Root cause**: BrowseActivity destroyed/recreated on Back → local variables lost
- ✅ **Solution**: `BrowseViewModel.lastEmittedMediaFiles` survives Activity recreation
- ✅ **Test confirmed**: "Skipping submitList: list unchanged (size=12, sameRef=true)" in logs
- ✅ **Performance**: 32 skipped frames (was 48-67), NO redundant submitList calls
- 📊 **Metrics**: Same reference detection works perfectly (`shouldSubmit=false: Same reference (===)`)

## 🎯 Current Development Tasks

### 🔴 Critical (Blocking Release)

- [ ] **Google Drive OAuth Configuration**
  - **Status**: Implementation complete, needs OAuth2 client configuration in Google Cloud Console
  - **Blocker**: Cannot test without valid client ID + SHA-1 fingerprint
  - **Action**: Create Android OAuth client, add credentials to project
  - **Testing**: Add Google Drive folder → Browse → File operations

- [ ] **Pagination Testing (1000+ files)**
  - **Status**: Implementation complete, needs real-world testing
  - **Test scenarios**:
    - LOCAL: 1000+, 5000+ files (images/videos mix)
    - SMB: Large network shares (test over slow connection)
    - SFTP/FTP: 1000+ files with thumbnails
  - **Expected**: No lag, smooth scrolling, memory efficient

### 🟠 High Priority

- [ ] **Network Undo Operations - Testing**
  - **Status**: Implementation complete, needs verification
  - **Test cases**:
    - SMB/SFTP/FTP: Delete file → Undo → Verify restoration
    - Check trash folder creation permissions
    - Network timeout handling (slow connections)
    - Trash cleanup after 24 hours

- [ ] **Network Image Editing - Performance Testing**
  - **Status**: Implementation complete, needs performance validation
  - **Test with**:
    - Large images (10MB+) over slow network
    - Multiple edits (rotate, flip) in sequence
    - Connection interruption during download/upload
  - **Add**: Progress reporting, cancellation support

### 🟡 Medium Priority

- [x] **Background Sync - UI Enhancement** ✅ Build 2.0.2511170337
  - **Status**: COMPLETED - Full UI implementation with settings controls and indicators
  - **Added**:
    - Sync status in resource list (last sync time with DateUtils formatting)
    - Settings → Network tab with enable/disable toggle, interval slider (1-24h), manual sync button
    - Sync status indicator (Idle/In Progress/Completed/Failed)
    - Localized in 3 languages (en/ru/uk)
  - **Backend**: NetworkFilesSyncWorker updates lastSyncDate timestamps
  - **Test**: 4+ hours idle → auto-sync behavior, manual sync trigger, UI indicators

### 🔵 Low Priority (Polish)

- [ ] **Animations and Transitions**
  - Screen transitions (slide, fade, shared element)
  - RecyclerView item animations (add, remove, reorder)
  - Ripple effects for missing buttons
  - Smooth progress indicators

- [x] **Slideshow Countdown Display** ✅ ALREADY IMPLEMENTED (Undocumented)
  - **Status**: COMPLETE - Implementation discovered during code review
  - **Implementation**:
    - UI: `activity_player_unified.xml` - TextView `tvCountdown` (top|end, 32sp, white with shadow)
    - Logic: `PlayerActivity.kt` - `countdownRunnable` updates text "3..", "2..", "1.." every 1000ms
    - Integration: Starts 3 seconds before file change (`postDelayed(countdownRunnable, interval - 3000)`)
    - Visibility: Shows only during slideshow, respects pause state
  - **Location**: PlayerActivity lines 133-142 (countdownRunnable), line 1405 (start trigger)
  - **Result**: Visual countdown working as per specification, just never documented in TODO

### 🌐 Network Features (Future)

- [ ] **Cloud Storage Expansion**
  - OneDrive API integration (OAuth2)
  - Dropbox API integration (OAuth2)
  - Multi-cloud operations testing

- [ ] **Offline Mode**
  - Cache thumbnails and metadata locally
  - Show cached data when network unavailable
  - Operation queue for delayed sync

## ⚡ Performance Optimization (LOW PRIORITY)

- [ ] **ExoPlayer initialization off main thread** (~39ms blocking)
- [ ] **ExoPlayer audio discontinuity investigation** (warning in logs, не критично)
- [ ] **Background file count optimization** (duplicate SMB scans)
- [ ] **RecyclerView profiling** (onBind <1ms target, test on low-end devices)
- [ ] **Layout overdraw profiling** (<2x target)
- [x] **Database indexes** ✅ Build 2.0.2511170338
  - **Completed**: Added 3 composite indexes on resources table (displayOrder, type, isDestination)
  - **Impact**: Faster ORDER BY queries, especially with 50+ resources
- [ ] **Memory leak detection** (LeakCanary integration)
- [ ] **Battery optimization** (reduce sync on low battery)

## 🌐 Network Features

- [ ] **Cloud storage (OneDrive, Dropbox)**
  - OneDrive/Dropbox API integration with OAuth2
  - Reuse CloudStorageClient interface
  - Test multi-cloud operations

- [ ] **Offline mode**
  - Cache thumbnails and metadata locally
  - Show cached data when network unavailable
  - Operation queue for delayed sync

## 🧪 Testing

- [ ] **Unit tests** (domain layer, >80% coverage)
- [ ] **Instrumented tests** (Room, Espresso UI flows)
- [ ] **Manual testing** (Android 8-14, tablets, file types, edge cases)
- [ ] **Security audit** (credentials, input validation, permissions)

## 🧰 Code Quality

- [ ] **Static analysis** (detekt/ktlint integration)
- [ ] **Edge cases** (empty folders, 1000+ files, long names, special chars)

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

## 🚀 Google Play Store

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

## 📋 Next Priorities

1. **Test Google Drive integration** (OAuth, file operations)
2. **Test pagination** (1000+ files on all resource types)
3. **Test network undo/editing** (SMB/SFTP/FTP)
4. **Resource availability indicator** (red dot for unavailable)

