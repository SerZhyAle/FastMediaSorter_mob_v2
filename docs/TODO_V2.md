# TODO V2 - FastMediaSorter

**Latest Build**: 2.25.1119.2013
**Version**: 2.25.1119.2013
**Package**: com.sza.fastmediasorter

---

## 🚨 Critical Architecture Errors (Must Fix Before Release)

В коде обнаружены критические архитектурные ошибки, которые приведут к сбоям приложения (OOM, зависания, гонки потоков) при работе с реальными медиафайлами.

### 1. Критическая ошибка: OutOfMemoryError (OOM) при операциях с файлами
**Где**: FtpFileOperationHandler.kt, SftpFileOperationHandler.kt
**Суть**: При скачивании (FTP/SFTP → Local) и копировании (FTP↔FTP, SFTP↔SFTP, SMB→SFTP) файлы целиком загружаются в оперативную память через ByteArrayOutputStream.
**Последствия**: При попытке скопировать или открыть видеофайл размером >500MB, приложение гарантированно упадет с OutOfMemoryError.

### 2. Потенциальная ошибка: Блокировка UI и операций (FTP)
**Где**: FtpClient.kt
**Суть**: Класс является @Singleton и использует mutex для синхронизации всех операций на одном экземпляре FTPClient.
**Последствия**: Если пользователь запустит скачивание большого файла, любые другие операции будут заблокированы до завершения скачивания.

### 3. Ошибка конкурентного доступа (Race Condition) в SFTP
**Где**: SftpClient.kt
**Суть**: Класс является @Singleton и хранит состояние сессии (session, channel) в полях класса без синхронизации.
**Последствия**: Параллельные операции могут разорвать соединение друг друга, приводя к ошибкам.

### 4. Проблема исчерпания пула потоков (SMB)
**Где**: SmbClient.kt
**Суть**: Фиксированный пул потоков (20) для блокирующих операций. При отмене корутины потоки не прерываются мгновенно.
**Последствия**: При активной навигации все потоки могут зависнуть, новые операции перестанут выполняться.

**Рекомендации по исправлению**:
- OOM: Переписать хендлеры на потоковую передачу или временные файлы
- FTP Blocking: Использовать пул соединений или новые экземпляры для длительных операций
- SFTP Concurrency: Убрать состояние из синглтона или реализовать пул соединений
- SMB Threads: Добавить таймауты и обработку InterruptedException

---

## 🎯 Current Development - Active Tasks

### High Priority - Core Features

- [ ] **Welcome Screen Implementation**
  - Launch on first start and via settings button
  - Multiple changing pages with instructions
  - "Skip" button closes without completing all pages
  - Spec: V2_p1_2.md - Welcome Screen

- [ ] **Add and Scan Resources Screen - Full Implementation**
  - Local folder: Auto-scan predefined folders + manual selection
  - Network folder: IP input with auto-fill, scan open shares, manual subfolder entry
  - Cloud folder: Google Drive/OneDrive/Dropbox authorization dialogs
  - SFTP: Host/port/credentials input with test connection
  - Dynamic "resources to add" list with checkboxes and short name editing
  - "Add to resources" button with destination assignment
  - Spec: V2_p1_2.md - Add and Scan Resources Screen

- [ ] **Resource Profile Screen**
  - Edit all resource fields (name, path, credentials, media types, slideshow interval)
  - Test connection functionality
  - Reset/Save/Cancel buttons
  - Spec: V2_p1_2.md - Resource Profile Screen

- [ ] **Browse Screen - Multi-Select via Long Press**
  - First long press: Select single file (don't launch player)
  - Second long press: Select all files between first and second
  - Allow scrolling while selecting
  - Show selection count in header
  - Spec: V2_p1_2.md - Browse Screen

- [ ] **Browse Screen - Selected Files Counter**
  - Display "N files selected" in text header below toolbar
  - Update dynamically as selection changes
  - Spec: V2_p1_2.md - Browse Screen

- [ ] **File Operations - Undo System Enhancement**
  - Implement undo for all operations: Copy, Move, Rename, Delete
  - Store operation details until next operation
  - Undo button enabled only when operation exists
  - Spec: V2_p1_2.md - all operation dialogs mention undo

- [ ] **Filter and Sort Resource List Dialog**
  - Sorting dropdown (by name)
  - Resource type checkboxes filter
  - Media type checkboxes filter
  - "By part of name" text field (substring, case-insensitive)
  - Show filter description at bottom when active
  - Spec: V2_p1_2.md - "Filter and Sort Resource List Screen"

### High Priority - UI/UX Polish

- [ ] **Player Screen - Full Implementation**
  - Fullscreen mode with 9 touch zones
  - Command panel mode with top/bottom panels
  - Dynamic destination buttons (1-10)
  - Slideshow with countdown display
  - Video/audio specific behavior
  - Spec: V2_p1_2.md - Player Screen

- [ ] **Settings Screen - Full Implementation**
  - General tab: Language, keep-awake, small controls, default credentials, logs
  - Media Files tab: Enable/disable types, size limits with sliders
  - Playback and Sorting: Default sort, slideshow interval, file operations toggles
  - Destinations tab: Manage recipient list, order, colors, copy/move behavior
  - Spec: V2_p1_2.md - Settings Screen

- [ ] **Copy/Move/Rename/Delete Dialogs - Full Implementation**
  - Proper headers and button layouts
  - Progress bars for long operations
  - Error handling with detailed messages
  - Spec: V2_p1_2.md - operation dialogs

### Medium Priority - Testing & Validation

- [ ] **Pagination Testing (1000+ files)**
  - LOCAL: 1000+, 5000+ files (images/videos mix)
  - SMB: Large network shares over slow connection
  - SFTP/FTP: 1000+ files with thumbnails
  - Verify no lag, smooth scrolling, memory efficient

- [ ] **Network Undo Operations - Testing**
  - SMB/SFTP/FTP: Delete → Undo → Verify restoration
  - Check trash folder permissions
  - Network timeout handling
  - Trash cleanup after undo window

- [ ] **Network Image Editing - Performance Testing**
  - Large images (10MB+) over slow network
  - Multiple edits in sequence
  - Connection interruption handling
  - Progress reporting, cancellation

### Medium Priority - Cloud Integration

- [ ] **Google Drive Testing**
  - OAuth2 client configuration in Google Cloud Console
  - Add folder → Browse → File operations
  - Requires package name + SHA-1 fingerprint

- [ ] **OneDrive Integration - Phase 4 (UI Integration)**
  - OAuth configuration in Azure AD
  - FolderPickerActivity, AddResourceActivity UI
  - Requires Azure AD application registration

- [ ] **Dropbox Integration - Phase 4 (UI Integration)**
  - APP_KEY configuration
  - FolderPickerActivity, AddResourceActivity UI
  - AndroidManifest auth_callback
  - Requires Dropbox App Console registration

### Low Priority - Performance & Polish

- [ ] **SMB Connection Blocking After Errors**
  - Monitor for remaining edge cases after partial fix

- [ ] **Edge Cases Handling**
  - Empty folders: Empty state indicators
  - Long filenames: Ellipsize and text overflow
  - Special characters: Verify display in all UI
  - Large file counts: Test >10000 files

- [ ] **Animations and Transitions**
  - Screen transitions (slide, fade, shared element)
  - RecyclerView item animations
  - Ripple effects for missing buttons
  - Smooth progress indicators

---

## 📋 Known Issues (Non-Critical)

- [ ] копирование ресурса это операция при которой нужно открыть диалог создания нового ресурса такого же типа как исходный ресурс, заполнить её всеми значениями и исходного ресурса, чтобы пользователь мог изменить одно или несколько значений и сохранить как новый ресурс

- [ ] при редактировании ресурса в поле Share символы вводятся задом наперед. Там какой то обработчик текста?

- [ ] мне нужно вводить в поле нового сетевого ресурса или ресурса sftp/ftp не только имя открытой сетевой папки, но и необходимой подпапки. Например "photos/2025/11", где "photos" - это открытая папка на данном сервере, а "2025/11" - интересующие меня подпапки внутри неё. Сейчас если я ввожу такой текст, при соединении используется только имя основной подпапки, подпапки куда то отрезаются и отредактировать не удаётся.

- [ ] я выставляю в настройках GRID и "полный экран", но по-умолчанию для новых ресурсов открывается режим "список" и режим "с командной панелью"

- [ ] Строки таблицы ресурсов в основном окне, когда экран устройства очень большой, очень малоинформативны. Только текст скраю слева и где то очень далеко справа - кнопки. Нужно расцветить каждую строку списка ресурсов немного разным цветом фона, чтобы пользователь не следил взглядом слева напрааво к кнопкам. Можно ли демонстрировать список ресурсов "как GRID из двух колонок", если размер экрана в ширину больше 600 пикселей?

---

## 📦 Release Preparation

### Build & Quality
- [ ] **Static Analysis Integration** (detekt to build.gradle.kts, baseline rules, CI/CD)
- [ ] **ProGuard/R8 Rules** (test obfuscated APK)
- [ ] **APK Signing** (keystore setup, test signed APK)
- [ ] **Size Optimization** (resource/code shrinking, AAB < 50MB target)
- [ ] **Dependencies Update** (latest stable versions)
- [ ] **Versioning** (versionCode/Name, Git tag v2.0.0)

### Testing
- [ ] **Unit Tests** (domain layer, >80% coverage)
- [ ] **Instrumented Tests** (Room, Espresso UI flows)
- [ ] **Manual Testing** (Android 8-14, tablets, all file types, edge cases)
- [ ] **Security Audit** (credentials, input validation, permissions)

### Documentation
- [ ] **README Update** (v2 features, screenshots, en/ru/uk)
- [ ] **CHANGELOG Creation** (Added/Changed/Fixed/Removed format)
- [ ] **User Guide** (features, FAQ, troubleshooting, localized)

### Store Materials
- [ ] **Google Play Listing** (title, descriptions en/ru/uk)
- [ ] **Screenshots** (4-8 per device type, localized)
- [ ] **Feature Graphic** (1024x500px)
- [ ] **App Icon** (adaptive, test on launchers)
- [ ] **Privacy Policy** (v2 data usage, host online)
- [ ] **Content Rating** (IARC questionnaire)

### Release Process
- [ ] **Internal Testing** (APK/AAB upload, ProGuard mapping)
- [ ] **Closed Beta** (5-20 testers, crash monitoring)
- [ ] **Production Release** (staged rollout 10→100%)
- [ ] **Post-Release Monitoring** (metrics, reviews, analytics)

Рекомендации по исправлению:
OOM: Переписать хендлеры на использование потоковой передачи (Streams/Pipes) или временных файлов. Никогда не буферизовать файлы целиком в памяти.
FTP Blocking: Использовать пул соединений или создавать новый экземпляр FTPClient для каждой длительной операции (как это сделано в downloadFileWithNewConnection, но не используется в хендлерах).
SFTP Concurrency: Убрать состояние из синглтона SftpClient (передавать сессию в методы) или реализовать пул соединений, аналогичный SmbClient.
SMB Threads: Рассмотреть использование таймаутов на уровне сокетов или механизма прерывания потоков, а также корректную
обработку InterruptedException при закрытии.

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

1. **Test Cloud Storage** - Google Drive/OneDrive/Dropbox OAuth setup and testing
2. **Test Google Drive integration** (OAuth, file operations) - needs OAuth setup
3. **Test pagination** (1000+ files on all resource types)
4. **Test network undo/editing** (SMB/SFTP/FTP)
5. **Monitor SMB connection recovery** (verify no blocking issues remain)
6. **Browse Screen - Multi-Select via Long Press** (High Priority UX)
7. **Browse Screen - Selected Files Counter** (High Priority UX)
8. **File Operations - Undo System Enhancement** (High Priority)
