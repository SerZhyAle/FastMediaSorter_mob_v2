# TODO V2 - FastMediaSorter

**Latest Build**: 2.25.1119.xxxx  
**Version**: 2.25.1119.xxxx
**Package**: com.sza.fastmediasorter

## 🎯 Current Development - In Progress

- [ ] Нужна новая опция для записи ресурса "не показывать миниатюры". При создании ресурса, если в нём найдено более 10000 файлов эта опция включается сама по себе. Но потом, при редакции записи ресурса пользователь может сам изменить эту опцию. Включить или выключить. Если галочка включена то в окне Browse миниатюры показываются исключительно по расширению файла, даже если стоит галочка "показывать миниатюры видео". 

- [ ] В основном окне на панели команд вверху есть последняя кнопка "плей" - основное её значение - запустить слайдшоу для "последнего испоьзованного или, если такого нет, то первого в списке ресурса". Нужно реализовать это поведение.

- [ ] при вводе текста в поле IP Server нужно разрешить ввод цифр, точки. А запятую или дефис или пробел при вводе менять на точку.

- [ ] я включил и тестирую режим "маленькие элементы управления" в настройках. В режиме Browse  все кнопки на ерхней и нижней панелях команд должны уменьшаться

## 🚀 Recent Fixes

### Build 2.25.1119.xxxx ✅
- ✅ **FIXED: ExoPlayer MediaCodec Errors - Reduced Log Noise**
- **Problem**: MediaCodec decoder errors (0xe) logged as ERROR, creating noise when they're often recoverable
- **Root Cause**: 
  - Hardware decoders fail on some video formats (especially emulator: `c2.goldfish.h264.decoder`)
  - ExoPlayer automatically retries with software decoder, playback continues normally
  - User sees error toast unnecessarily
- **Solution**:
  - Added MediaCodec error detection in `onPlayerError()` listener
  - Downgraded MediaCodec/DecoderException errors to WARNING level
  - Suppressed user-facing error toast for recoverable decoder failures
  - Full error logs still captured for non-MediaCodec errors
- **Changed Files**:
  - `PlayerActivity.kt`: Updated `exoPlayerListener.onPlayerError()` (lines 208-235)
    - Added `isMediaCodecError` check (class name contains "MediaCodec" or "DecoderException")
    - Conditional logging: `Timber.w()` for MediaCodec, `Timber.e()` for others
    - Suppress `showError()` toast when MediaCodec error (ExoPlayer will auto-retry)
- **Impact**: Cleaner logs, no false-positive error toasts when video plays successfully after decoder retry

- ✅ **CRITICAL: BrowseActivity Thumbnail Loading - Fixed Network Starvation**
- **Problem**: "Network file unavailable" error when opening PlayerActivity after browsing 4000+ files
- **Root Cause**: 
  - `BrowseActivity.onPause()` had `if (!isFinishing)` check before clearing adapter
  - When navigating to PlayerActivity, `isFinishing=false`, so adapter **not cleared**
  - Coil thumbnail requests (6+ concurrent) kept running, exhausting SMB connection pool
  - PlayerActivity's full-image request timed out waiting for free connection (2s timeout)
  - `NetworkFileFetcher` returned `null` after timeout → "Network file unavailable" exception
- **Solution**:
  - Removed `!isFinishing` check - adapter now **always** cleared in `onPause()`
  - When leaving Browse (any reason), all Coil requests cancelled via adapter recycling
  - SMB connection pool freed immediately for PlayerActivity
- **Changed Files**:
  - `BrowseActivity.kt`: Removed conditional clearing (line 1158)
    - Old: `if (!isFinishing) { binding.rvMediaFiles.adapter = null }`
    - New: `binding.rvMediaFiles.adapter = null` (always)
  - `SmbClient.kt`: Added missing `ScanProgressCallback` import
- **Impact**: No more loading delays when opening images/videos from large network folders

- ✅ **CRITICAL: SMB Connection Recovery After Socket Errors**
- **Problem**: After `SocketException: Software caused connection abort`, SMB connections remain blocked until app restart
- **Root Cause**: 
  - SMBJ library's internal state becomes corrupted after critical socket errors
  - Old implementation only closed connections, but kept same `SMBClient` instances
  - After 20 consecutive errors, pool was cleared but clients retained corrupted state
- **Solution**:
  - Changed `normalClient`/`degradedClient` from `lazy val` to nullable `var` with synchronized getters
  - Added `resetClients()` method to fully recreate `SMBClient` instances
  - Immediate reset on critical socket errors: `Software caused connection abort`, `Connection reset`, `Broken pipe`
  - Automatic reset after 20 consecutive non-critical errors (timeout threshold)
- **Changed Files**:
  - `SmbClient.kt`: Refactored client lifecycle management (4 edits)
    - Lines 96-111: Converted to nullable vars with lazy initialization
    - Line 120: Updated `getClient()` to use getter methods
    - Lines 1367-1370: Added `resetClients()` call after critical threshold
    - Lines 1486-1506: Added critical error detection in catch block
    - Lines 1553-1569: Added `resetClients()` method with synchronized client recreation
    - Lines 1795-1796: Fixed `close()` to use safe calls (`?.`)
- **Technical Details**:
  - Critical errors detected via `e.cause is SocketException` with message matching
  - `resetClients()` calls `.close()` on old clients before nullifying
  - Next connection attempt will recreate fresh `SMBClient` with clean state
  - Thread-safe via `synchronized(this)` block during recreation
- **Impact**: SMB shares now auto-recover from network interruptions without app restart
- **Testing**: Trigger `Software caused connection abort` → verify next connection succeeds

### Build 2.25.1118.xxxx ✅ (UI Consistency + Field Width Fixes)
- ✅ **UI: Standardized All Boolean Controls (24 elements)**
- **Pattern Applied**: CheckBox/Switch moved to **left** of text labels (marginEnd=12dp)
- **Files Updated**: 6 layout files
  - `activity_add_resource.xml`: 1 MaterialCheckBox ("Add to Destinations")
  - `fragment_settings_destinations.xml`: 4 SwitchMaterial (Copy/Move options)
  - `fragment_settings_general.xml`: 3 SwitchMaterial (Prevent Sleep, Small Controls, Background Sync)
  - `fragment_settings_playback.xml`: 9 SwitchMaterial (Play to End, Rename, Delete, Confirm, Grid, Command Panel, Errors, Hint)
  - `fragment_settings_media.xml`: 6 SwitchMaterial (Images, GIFs, Videos, Audio, Thumbnails)
  - `fragment_settings_network.xml`: 1 SwitchMaterial (Background Sync)
- **Structure Changed**: `<SwitchMaterial text="..." />` → `<Switch marginEnd=12dp /> + <TextView weight=1 text="..." />`

- ✅ **UI: Fixed Short Numeric Input Fields (9 fields)**
- **Problem**: Port/interval fields stretched to full width with `layout_weight=1`, inconvenient for 3-4 digit values
- **Solution**: Changed to fixed width (`120dp` or `150dp`) instead of weight-based stretching
- **Files Updated**: 4 layout files
  - `activity_edit_resource.xml`: 3 fields (SMB port 120dp, SFTP port 150dp, Slideshow interval 120dp)
  - `activity_add_resource.xml`: 2 fields (SMB port 120dp, SFTP port 150dp)
  - `fragment_settings_playback.xml`: 2 fields (Slideshow interval 120dp, Icon size 120dp)
  - `fragment_settings_general.xml`: 1 field (Sync interval 160dp)
- **Impact**: Short fields no longer waste space, easier to scan visually

- [ ] После ошибку с определенным SMB  он как бы блокируется. Пок ане перезапустишь программу 

## 🚀 Pre-Release Tasks (Ready to Implement)

### 🔴 Critical (Blocking Release)


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

### Build 2.0.2511170016 ❌ (FAILED - Missing SettingsRepositoryImpl updates)
- ❌ **ATTEMPTED: Task 2 - Command panel default setting**
- **Changes made**:
  - String resources updated (en/ru/uk)
  - All Kotlin code updated
- **FAILED**: Compilation error - SettingsRepositoryImpl still referenced old field name
- **Next**: Fix SettingsRepositoryImpl key constant and read/write operations

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

### Build 2.0.2511162309 ⚠️ (PARTIAL FIX)
- ⚠️ **ATTEMPTED: SFTP testConnection() X25519 error**
- Fixed `testConnection()` creating `SSHClient()` without custom config, but DHGexSHA256 still required SHA-256
- Real issue: Android BC missing SHA-256 for MessageDigest (only has SHA-1, SHA-224, SHA-384, SHA-512)

### Build 2.0.2511162305 ⚠️ (PARTIAL FIX)
- ⚠️ **ATTEMPTED: FTP parallel download NPE (synchronization)**
- Added `synchronized(mutex)` для `downloadFile()` и `listFiles()`
- **Не решило проблему**: Race condition на уровне TCP socket, не на уровне thread safety
- **Реальная проблема**: Single FTPClient socket не может обрабатывать несколько одновременных `retrieveFile()` вызовов

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

