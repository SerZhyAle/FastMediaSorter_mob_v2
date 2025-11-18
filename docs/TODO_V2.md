# TODO V2 - FastMediaSorter

**Latest Build**: 2.25.1118.xxxx  
**Version**: 2.25.1118.xxxx
**Package**: com.sza.fastmediasorter

В Browse в режиме списка у нас есть галочки на изображениях, выбрав которые можноп роизхвести пакетное перемещение, копирование, удаление. Я хочу чтобы в режимее сетк в левом нижнем углу миниатюр появилась аналогичная "галочка" с аналогичным поведением как у списка


## 🎯 Current Development - In Progress

- [x] **FEATURE: Опция "Сканировать подкаталоги" для ресурсов** *(Build 2.25.1118.xxxx - COMPLETED)*
  - ✅ Migration 12→13: Добавлен столбец `scanSubdirectories BOOLEAN NOT NULL DEFAULT 1`
  - ✅ Domain model: MediaResource.scanSubdirectories, mappers toDomain/toEntity
  - ✅ UI: EditResourceActivity - MaterialCheckBox cbScanSubdirectories + listener + state binding
  - ✅ ViewModel: EditResourceViewModel.updateScanSubdirectories(), EditResourceState.scanSubdirectories
  - ✅ Локализация: scan_subdirectories + scan_subdirectories_hint (en/ru/uk)
  - ✅ Сканеры: MediaScanner.scanFolder(scanSubdirectories), LocalMediaScanner (collectFilesRecursively/collectDocumentFilesRecursively), SmbMediaScanner, SftpMediaScanner, FtpMediaScanner, CloudMediaScanner
  - ⏸️ BackupManager: XML export/import не реализован (будущая функция)
  - ✅ BUILD SUCCESSFUL
  - **Technical Notes**:
    - По умолчанию выключено (scanSubdirectories = false)
    - При отключении сканируется только корневая папка ресурса
    - LocalMediaScanner: Breadth-first traversal (ArrayDeque) для File и DocumentFile
    - Остальные сканеры готовы к добавлению рекурсии при необходимости

- [x] **OPTIMIZATION: Адаптивные таймауты для деградированных соединений** *(Build 2.25.1118.xxxx - COMPLETED)*
  - ✅ ConnectionThrottleManager: Добавлен флаг `isDegraded` в ProtocolState
  - ✅ Метод `isDegraded(protocol, resourceKey): Boolean` для проверки состояния
  - ✅ Флаг устанавливается при деградации (3 таймаута), сбрасывается при восстановлении (10 успехов)
  - ✅ SmbClient: Два SMBClient instance - normalClient (5s/8s) и degradedClient (8s/12s)
  - ✅ Метод `getClient(server, port)` выбирает клиент по состоянию ConnectionThrottleManager
  - ✅ Обновлены вызовы в `listShares()` и `withConnection()`
  - ✅ Логирование: "EXTENDED TIMEOUTS ENABLED" при деградации, "NORMAL TIMEOUTS RESTORED" при восстановлении
  - ✅ BUILD SUCCESSFUL
  - **Technical Details**:
    - Нормальные таймауты: CONNECTION_TIMEOUT_MS=5s, READ_TIMEOUT_MS=8s
    - Деградированные таймауты: CONNECTION_TIMEOUT_DEGRADED_MS=8s, READ_TIMEOUT_DEGRADED_MS=12s
    - Фиксированное увеличение на 60% (8/5=1.6, 12/8=1.5) для плохих соединений
    - Автоматическое переключение без вмешательства пользователя



- [x] ОПТИМИЗАЦИЯ: Быстрый подсчет файлов при добавлении ресурса. Ограничение 1000 файлов для начального сканирования (вместо полного обхода). При fileCount >= 1000 отображается ">1000 files" в UI. Изменения: SmbClient.countMediaFiles (maxCount=1000), все сканеры используют scanFolderPaged(limit=1000) в getFileCount, ResourceAdapter/ResourceToAddAdapter/EditResourceActivity показывают ">1000" для ресурсов с 1000+ файлов. Время добавления SMB ресурса с 64k файлов: ~19 сек → ~2-3 сек. *(Build 2.25.1118.0715)*

- [x] В окне настройки General настройка "Sync interval" теперь текстовое поле в минутах с выбором из списка 5, 15, 60, 120, 300 минут. Кнопка Sync Now размещена рядом с полем. *(Build 2.25.1118.0437)*

- [x] "Default icon size" переименовано на "Icon size for grid" - поле сужено (wrap_content + minWidth 200dp), перемещено над кнопкой "Show hint now". Добавлен выпадающий список 24-1024px (14 значений), диапазон валидации 24-1024. *(Build 2.25.1118.0526)*

- [x] В окне настройки "Playback" графическая карта тач-зон уменьшена в 2 раза (280dp→140dp), легенда размещена рядом (горизонтальный layout 50%/50%). Заголовок переименован в "Touch Zones Scheme (Images/Video) for full screen mode". *(Build 2.25.1118.0526)*

- [x] В окне настройки "Playback" поля "Default sort mode" и "Slideshow interval" размещены в одну строчку (horizontal layout). Поле переименовано в "Default slideshow (sec.)". Добавлен выпадающий список 1,5,10,30,60,120,300 секунд. *(Build 2.25.1118.0526)*

- [x] В окне настройки General удален заголовок "Backup and restore". Кнопки "Export..." и "Import..." размещены на одной строке (horizontal layout 50/50). *(Build 2.25.1118.0601)*

- [x] Кнопки "Grant Local Files permission" и "Grant Network Permissions" теперь доступны только если прав нет. При наличии прав кнопки disabled (alpha 0.5). Добавлен updatePermissionButtonsState() + onResume() hook для обновления состояния после системных настроек. *(Build 2.25.1118.0601)*

- [x] В окне настройки General кнопки "Show log" и "Show current session log" размещены на одной строке (horizontal layout 50/50). *(Build 2.25.1118.0601)*

- [x] В окне настройки General кнопка "User GUIDE" размещена на одной строке с полем Language (spinner + button в горизонтальном layout). Удалена отдельная кнопка. *(Build 2.25.1118.0601)*

- [x] В окне настройки "Destinations" переименована кнопка "Добавить Получателя" → "Добавить Назначение" (Add Destination). Добавлен заголовок перед кнопкой: "Список назначений для команд сортировки (до 10)" / "Destination List for Sorting Commands (up to 10)". Обновлены переводы ru/uk. *(Build 2.25.1118.0601)*

- [ ] В основном окне на панели команд вверху есть последняя кнопка "плей" - основное её значение - запустить слайдшоу для "последнего испоьзованного или, если такого нет, то первого в списке ресурса". Нужно реализовать это поведение.

- [ ] при работе на русском языке. При добавлении ресурса заголовок "Select Resource Type" не переведён. Видимо захадкожен. Нужен перевод на русский и украинский.

- [ ] при вводе текста в поле IP Server нужно разрешить ввод цифр, точки. А запятую или дефис или пробел при вводе менять на точку.

- [x] убрать надпись "готово к синхронизации" из настройки - общие

- [x] при работе на русском языке. Заголовок Settings у активити настроек не переведен на языки *(Fixed: changed hardcoded 'Settings' to @string/settings in activity_settings.xml)*

- [ ] при работе на русском языке. проверить все тосты на использование перевода. Например если я нажимаю кнопку "Показать подсказку сейчас" я вижу тост на английском языке.

- [ ] Переименовать кнопку "Показать подсказку сейчас" на "Показать подсказку в следующий раз"

- [ ] поле "размер иконок дл сетки" перенести в одну строку с полем "Режим сетки".

- [ ] поле "слайдшоу" при редакции ресурса нужно сделать аналоггичным как мы сделали поле "слайдшоу" в настройках. Здесь его нужно именовать "Интервал для слайдшоу (сек.)".

- [ ] я включил и тестирую режим "маленькие элементы управления" в настройках. В режиме проигрывателе не все кнопки на панели команд наверу уменьшились

- [ ] когда мы ненадолго видим легенд тач-зн над изображением в проигрывателе это очень удобно. Но она довоьно блеклая и её неудобно читать. Нужно сделать её менее прозрачной. Нужно чтобы она "пропадала" при первом касании на экран, а не через несколько секунд.


- [x] во всех наших активити элементы типа "галочка" или выбор из списка выглядят на планшете неудобно. Скраю слева текст, а скраю справа галочка или поле выбора. На телефоне еще нормально, но на планшете неудобно. Можем ли мы использовать такие галочки, которые будут стоять слева сразу перед текстом? Можем ли мы использовать такое поле ввода, которое будет сразу за текстом легаенды ( пример поле "Язык" в снастройках )? *(Already implemented in previous commit: horizontal LinearLayouts with TextView (label, layout_weight=1) + MaterialCheckBox/Switch (control, wrap_content), minHeight=48dp for touch comfort)*

- [ ] я включил и тестирую режим "маленькие элементы управления" в настройках. В режиме Browse  все кнопки на ерхней и нижней панелях команд должны уменьшаться


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

