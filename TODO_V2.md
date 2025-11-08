# TODO V2 - FastMediaSorter v2

- [ ]  диалогии Копировать в.. и Переместить в.. из окна BrowseActivity не работают. Я не вижу там набора кнопок destinations, как описанов в спецификации

## 📋 Актуальні задачі для розработки

- [ ]  Network: Implement SFTP support

- [ ] Add SSHJ library for SFTP connections
- [ ] Create SftpScanner for remote folders
- [ ] Support authentication (username/password/key)
- [ ] Handle connection pooling and errors
- [ ]  Cloud: Integrate Google Drive API
  - [ ] Add Google Sign-In and Drive API
  - [ ] Implement folder browsing and file operations
  - [ ] Handle OAuth2 flow and token storage
  - [ ] Adapt copy/move for cloud files
- [ ] Cloud: Integrate Dropbox API
  - [ ] Add Dropbox SDK
  - [ ] Implement authentication and file access
  - [ ] Support folder sync and operations
  - [ ] Ensure compatibility with existing file operations

- [ ] Bug fix: Handle specification compliance issues

- [ ] Optimization: Implement logging strategy

- [ ] Document all gestures, touch zones, and workflows
- [ ] Include screenshots and examples
- [ ] Documentation: Update architecture docs

## � Permissions & Security (3 tasks)

- [ ] **Permissions: Implement Android 13+ photo picker**
  - Use PhotoPicker API for Android 13+ (API 33+)
  - Fallback to SAF (Storage Access Framework) for older versions
  - Request READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO for Android 13+

- [ ] **Permissions: Handle scoped storage properly**
  - Use MediaStore API for media file access
  - Request MANAGE_EXTERNAL_STORAGE only if absolutely necessary
  - Use ACTION_OPEN_DOCUMENT_TREE for folder selection

- [ ] **Permissions: Add runtime permission handling**
  - Create PermissionManager class in domain layer
  - Show rationale dialogs before requesting permissions
  - Handle permission denial gracefully with informative messages

---

## 🌐 Network & Cloud Features (5 tasks)

- [ ] **Network: Implement SMB/CIFS support**
  - Add jcifs-ng library for SMB protocol
  - Create NetworkScanner for SMB shares
  - Support authentication (username/password)
  - Handle connection errors and timeouts

- [ ] **Network: Add SFTP support**
  - Add SSHJ or JSch library for SFTP
  - Create SftpScanner for remote folders
  - Support key-based and password authentication
  - Handle connection pooling

- [ ] **Cloud: Add cloud storage providers**
  - Google Drive API integration
  - Dropbox API integration
  - OneDrive API integration (optional)
  - OAuth2 authentication flow

- [ ] **Network: Implement background sync**
  - Use WorkManager for periodic sync
  - Check for new/deleted files in network/cloud resources
  - Update fileCount and thumbnail cache
  - Show sync status in resource list

- [ ] **Network: Add offline mode**
  - Cache thumbnails and metadata locally
  - Show cached data when network unavailable
  - Indicate offline status in UI
  - Queue operations for later sync

---

## 🎨 UI/UX Enhancements (6 tasks)

- [ ] **UI: Implement Dark/Light theme**
  - Use Material Design 3 theming
  - Support system theme detection
  - Add theme toggle in Settings
  - Test all screens in both themes

- [ ] **UI: Add animations and transitions**
  - Screen transitions (slide, fade)
  - List item animations (add, remove, reorder)
  - Button ripple effects
  - Progress indicators

- [ ] **UI: Improve thumbnail loading**
  - Use Coil disk/memory cache effectively
  - Add placeholder images during loading
  - Add error placeholders for failed loads
  - Implement thumbnail prefetching for smoother scrolling

- [ ] **UI: Add empty states**
  - Empty resource list: "No resources added yet" with Add button
  - Empty file list: "No media files found in this folder"
  - Empty search results: "No files match your criteria"
  - Network error state: "Connection failed" with Retry button

- [ ] **UI: Implement accessibility features**
  - Add content descriptions for all images/icons
  - Support TalkBack screen reader
  - Ensure minimum touch target size (48dp)
  - Add high contrast mode support

- [ ] **UI: Add onboarding/tutorial**
  - Show welcome screen on first launch
  - Explain main features and gestures
  - Add "Skip" and "Next" buttons
  - Show tips for touch zones in Player Screen

---

## 🧪 Testing (6 tasks)

- [ ] **Testing: Write unit tests**
  - Test all UseCase classes with JUnit
  - Test ViewModels with kotlinx-coroutines-test
  - Test Repository classes with mocked dependencies
  - Target >80% code coverage for domain layer

- [ ] **Testing: Write instrumented tests**
  - Test database operations with Room testing library
  - Test UI flows with Espresso
  - Test navigation between screens
  - Test file operations with temporary test folders

- [ ] **Testing: Add UI tests**
  - Test all user interactions (clicks, long presses, gestures)
  - Test dialogs and their actions
  - Test RecyclerView scrolling and item interactions
  - Test ExoPlayer playback

- [ ] **Testing: Perform manual testing**
  - Test on different Android versions (8.0 - 14.0)
  - Test on different screen sizes (phone, tablet)
  - Test with different file types and sizes
  - Test network connectivity scenarios (slow, no internet)

- [ ] **Testing: Beta testing**
  - Create closed beta track in Google Play Console
  - Recruit 10-20 beta testers
  - Collect feedback and crash reports
  - Fix critical bugs before release

- [ ] **Testing: Perform security audit**
  - Check for hardcoded credentials
  - Validate input sanitization
  - Test file path traversal prevention
  - Review permission usage

---

## 🐛 Bug Fixes & Optimization (5 tasks)

- [ ] **Optimization: Memory management**
  - Profile memory usage with Android Profiler
  - Fix memory leaks (use LeakCanary)
  - Optimize bitmap loading (downsampling)
  - Implement pagination for large file lists

- [ ] **Optimization: Performance tuning**
  - Profile CPU usage and frame drops
  - Optimize database queries (add indexes)
  - Use background threads for heavy operations
  - Reduce overdraw in layouts

- [ ] **Optimization: Battery optimization**
  - Reduce background work
  - Use JobScheduler/WorkManager efficiently
  - Pause sync when battery low
  - Release resources when app in background

- [ ] **Bug fix: Handle edge cases**
  - Empty folders, folders with many files (1000+)
  - Very long file names
  - Special characters in file names
  - Corrupted media files

- [ ] **Bug fix: Crash fixes**
  - Add try-catch blocks for file operations
  - Handle OutOfMemoryError gracefully
  - Add null checks for optional values
  - Fix ANR (Application Not Responding) issues

---

## 📦 Build & Release Preparation (8 tasks)

- [ ] **Build: Configure ProGuard/R8**
  - Add ProGuard rules for release build
  - Test obfuscated APK thoroughly
  - Keep necessary classes for reflection
  - Verify ProGuard doesn't break functionality

- [ ] **Build: Sign APK with release keystore**
  - Create release keystore (if not exists)
  - Store keystore safely (not in git)
  - Configure signing in build.gradle.kts
  - Test signed APK installation

- [ ] **Build: Optimize APK size**
  - Enable resource shrinking
  - Enable code shrinking (R8)
  - Use vector drawables instead of PNGs
  - Remove unused resources and dependencies
  - Consider App Bundle (.aab) format

- [ ] **Build: Set version numbers**
  - Update versionCode in build.gradle.kts (increment for each release)
  - Update versionName (e.g., 2.0.0 for major release)
  - Follow semantic versioning (MAJOR.MINOR.PATCH)

- [ ] **Build: Update dependencies**
  - Update all libraries to latest stable versions
  - Test app after each dependency update
  - Check for deprecated APIs
  - Fix any breaking changes

- [ ] **Documentation: Update README files**
  - Update README.md with v2 features
  - Update README.ru.md and README.ua.md
  - Add screenshots of new UI
  - Update build instructions

- [ ] **Documentation: Update CHANGELOG**
  - Document all changes in CHANGELOG.md
  - Group by Added, Changed, Fixed, Removed
  - Add version number and release date
  - Mention breaking changes if any

- [ ] **Documentation: Create user documentation**
  - Write user guide (how to use app)
  - Document all features and gestures
  - Add FAQ section
  - Create troubleshooting guide

---

## 🚀 Google Play Store Preparation (7 tasks)

- [ ] **Store: Prepare store listing**
  - Write app title (30 chars max)
  - Write short description (80 chars max)
  - Write full description (4000 chars max, feature list, benefits)
  - Translate descriptions to Russian and Ukrainian

- [ ] **Store: Create screenshots**
  - Create 4-8 screenshots per screen (phone and tablet)
  - Show key features (Main, Browse, Player screens)
  - Use device frames and annotations
  - Create localized screenshots (en, ru, uk)

- [ ] **Store: Create feature graphic**
  - Design 1024x500px feature graphic
  - Use app branding and key visual
  - Follow Google Play design guidelines
  - Create localized versions if needed

- [ ] **Store: Create app icon**
  - Design adaptive icon (foreground + background)
  - Test on different launchers
  - Ensure icon meets Google Play guidelines
  - Export all required sizes (mipmap-*)

- [ ] **Store: Prepare promotional video (optional)**
  - Create 30-second YouTube video
  - Show app features and UI
  - Add voiceover or text overlays
  - Upload to YouTube and link in Play Console

- [ ] **Store: Update Privacy Policy**
  - Update PRIVACY_POLICY.md with v2 data usage
  - Mention permissions and their purposes
  - Add contact information
  - Host online (GitHub Pages or website)

- [ ] **Store: Content rating questionnaire**
  - Complete IARC questionnaire in Play Console
  - Answer questions about content
  - Get age rating (e.g., Everyone, Teen)
  - Review rating and update if needed

---

## 🎯 Release Process (6 tasks)

- [ ] **Release: Create internal testing release**
  - Upload APK/AAB to Play Console (Internal Testing track)
  - Test installation and updates
  - Verify all features work in production build
  - Check ProGuard mapping file uploaded

- [ ] **Release: Create closed beta release**
  - Promote to Closed Testing track
  - Add beta testers (email list)
  - Monitor crash reports in Play Console
  - Collect feedback and fix issues

- [ ] **Release: Create open beta release (optional)**
  - Promote to Open Testing track
  - Allow public opt-in for testing
  - Monitor reviews and ratings
  - Fix critical bugs before production

- [ ] **Release: Production release**
  - Promote to Production track
  - Choose rollout percentage (start with 10-20%)
  - Monitor crash-free rate and ANR rate
  - Gradually increase rollout to 100%

- [ ] **Release: Post-release monitoring**
  - Monitor Play Console metrics (installs, crashes, ratings)
  - Respond to user reviews (especially negative)
  - Track Firebase Analytics events
  - Monitor Firebase Crashlytics reports

- [ ] **Release: Plan updates and maintenance**
  - Create roadmap for future updates (v2.1, v2.2)
  - Monitor user feature requests
  - Fix reported bugs in timely manner
  - Maintain compatibility with new Android versions

---

## ✅ Completed Tasks (Session History)

### 2025-01-07 (Current Session)
- [x] **Settings: Fix language switching**
  - Fixed language reset bug (Ukrainian → English on Settings navigation)
  - Synchronized DataStore and SharedPreferences for language storage
  - LocaleHelper now reads correct language from SharedPreferences in attachBaseContext
  - Commit: d7f1c6e

- [x] **Settings: Fix Playback tab crash**
  - Fixed slider validation error (defaultIconSize 100 incompatible with stepSize 8)
  - Changed defaultIconSize: 100 → 96 with validation (must be 32 + 8*N)
  - Commit: 91884c6

- [x] **Browse Screen: Add filter status indicator**
  - Added TextView at bottom of Browse Screen to show active filter description
  - Indicator shows: name filter, date range, size range in yellow background
  - Automatically hides when no filter active
  - Matches V2_Specification.md requirement: "When a filter is applied on this screen, a warning with a description of the applied filter appears at the bottom"

- [x] **Browse Screen: Implement delete operation with undo**
  - Implemented deleteSelectedFiles() in BrowseViewModel
  - Delete operation now creates UndoOperation with list of deleted files
  - Undo button appears after delete (restores files if possible)
  - Shows success/error messages with deleted count and failures

- [x] **Browse Screen: Add undo support for rename operations**
  - Single file rename now saves UndoOperation with old/new path pair
  - Multiple file rename saves all renamed pairs for batch undo
  - Undo button appears after rename, restores original file names
  - Works for both single and multiple rename dialogs

- [x] **Settings: Fix infinite update loop in Media/Playback fragments**
  - Fixed бесконечный цикл: observeData() обновлял UI → listeners вызывали updateSettings() → снова observeData()
  - Добавлены проверки перед обновлением UI: обновление только при реальном изменении значений
  - MediaSettingsFragment: проверка switches и range sliders (imageSizeMin/Max, videoSizeMin/Max, audioSizeMin/Max)
  - PlaybackSettingsFragment: проверка switches и sliders (slideshowInterval, defaultIconSize)
  - GeneralSettingsFragment: уже имел защиту от цикла

- [x] **Settings: Fix language settings not applying**
  - GeneralSettingsFragment: убрана инициализация spinner из LocaleHelper.getLanguage() (SharedPreferences до загрузки из DataStore)
  - onItemSelected: сравнение с viewModel.settings.value.language вместо LocaleHelper.getLanguage()
  - observeData: добавлен параметр `false` в setSelection() для предотвращения триггера onItemSelected
  - Settings tab names: использованы string resources вместо хардкода ("General" → R.string.settings_tab_general)
  - Добавлены переводы для табов: английский, русский, украинский (Общие/Загальні, Медиа, Воспроизведение/Відтворення, Назначения/Призначення)

- [x] **Settings: Fix switch sizes and touch targets**
  - Все MaterialSwitch/SwitchMaterial элементы: добавлены minHeight="48dp" и paddingVertical="12dp"
  - Material Design guideline: минимум 48dp для touch targets
  - Исправлено во всех фрагментах: General (2 switches), Media (4 switches), Playback (7 switches), Destinations (5 switches)
  - Улучшена доступность: легче попадать по галочкам, больше пространство для нажатия

### 2025-01-07 (Evening Session)
- [x] **Main Screen: Fix resource move up/down buttons**
  - Added SortMode.MANUAL enum value for manual ordering
  - Changed default sort mode from NAME_ASC to MANUAL
  - Updated applyFiltersAndSort() to sort by displayOrder in MANUAL mode
  - moveResourceUp/moveResourceDown now switch to MANUAL mode after reordering
  - Updated FilterResourceDialog to include "Manual Order" option
  - Commit: (pending)

- [x] **Main Screen: Fix resource selection flickering**
  - Fixed GestureDetector touch event handling in ResourceAdapter
  - Changed from always returning true to only consuming handled gestures
  - Added performClick() call for unhandled ACTION_UP events
  - Resources now select properly without visual flickering
  - Commit: (pending)

- [x] **Main Screen: Fix filter dialog button icons**
  - Created new ic_refresh.xml icon (circular arrow) for Clear/Reset button
  - Changed btnClear icon from ic_clear (X) to ic_refresh
  - btnCancel keeps ic_cancel (X in circle)
  - btnApply keeps ic_check (checkmark)
  - Icons now clearly distinguish Cancel vs Reset actions
  - Commit: (pending)

### 2025-01-07 (Evening Session 2)
- [x] **Browse Screen: Remove toolbar and move Back button to command bar**
  - Removed MaterialToolbar from activity_browse.xml
  - Added Back button (btnBack) at the beginning of layoutControls
  - Updated BrowseActivity.kt: removed toolbar setup, added btnBack click handler
  - Removed toolbar.title update in observeData()
  - Matches specification requirement: no separate header bar

- [x] **Browse Screen: Implement thumbnail loading with Coil**
  - Added Coil video frames library (io.coil-kt:coil-video:2.5.0) to build.gradle.kts
  - Updated MediaFileAdapter to load real thumbnails instead of generic icons
  - Images/GIFs: load actual image preview using Coil
  - Videos: load first frame using Coil video decoder
  - Audio: generated custom bitmap with file extension text (e.g., "MP3")
  - Created placeholder/error drawables (ic_image_*, ic_video_*)
  - All thumbnails use RoundedCornersTransformation(8f) for consistent appearance
  - Commit: 58d3f72

### 2025-01-07 (Evening Session 3)
- [x] **Player Screen: Implement Delete file functionality**
  - Added PlayerEvent.ShowMessage event for success messages
  - Implemented deleteCurrentFile() in PlayerViewModel:
    * Deletes file from filesystem using File.delete()
    * Removes deleted file from files list
    * Navigates to next file if available
    * Navigates to previous if deleted last file
    * Closes activity if no files remain (sends FinishActivity event)
    * Returns Boolean? (true=success, false=error, null=closing)
  - Replaced "Delete functionality coming soon" stub in PlayerActivity
  - Added AlertDialog with confirmation (uses delete_file_confirmation string)
  - Added missing imports (AlertDialog, R)
  - All results handled via events (ShowMessage/ShowError/FinishActivity)
  - Commit: d764649

- [x] **Settings/Destinations: Fix list item layout**
  - Fixed button container orientation from vertical to horizontal
  - Buttons now display in a single row instead of column
  - Increased button size from 40dp to 48dp for better touch targets
  - Added 4dp margins between buttons for spacing
  - Increased text size for better readability:
    * tvDestinationName: textAppearanceBodyLarge → textAppearanceTitleMedium
    * tvDestinationPath: textAppearanceBodySmall → textAppearanceBodyMedium
  - Added 8dp left margin to button container
  - All three buttons (Move Up, Move Down, Delete) now properly visible in row
  - Commit: 38a697a

### 2025-01-08 (Development Session)
- [x] **Browse Screen: Fix sort dialog - show user-friendly names**
  - Changed sort dialog from showing enum codes (NAME_ASC, DATE_DESC, etc.) to readable names
  - Added getSortModeName() helper function in BrowseActivity
  - Now displays: "Name (A-Z)", "Date (Old first)", "Size (Small first)", etc.
  - Matches existing implementation in SettingsFragments
  - Improves UX - users see clear, localized sort options
  - Commit: (pending)

- [x] **Browse Screen: Reorganize layout per specification**
  - **Top bar changes:**
    * Added Space (8dp) after Back button per spec
    * Added btnSelectAll button with checkbox_on_background icon
    * Added btnDeselectAll button with checkbox_off_background icon
  - **Bottom operations bar (NEW):**
    * Created layoutOperations LinearLayout at bottom
    * Moved Copy, Move, Rename, Delete, Undo buttons to bottom bar
    * Added flexible Space to push Play button to right
    * Added elevation (4dp) and background (colorSurface) for distinction
  - **RecyclerView:**
    * Changed constraintBottom from tvFilterWarning to layoutOperations
    * Now sandwiched between tvResourceInfo (top) and layoutOperations (bottom)
  - **ViewModel:**
    * Added selectAll() function to select all files in current list
    * Updated clearSelection() usage for Deselect All button
  - **Strings:**
    * Added select_all / deselect_all in all 3 languages (en/ru/uk)
  - **Result:** Operations buttons now at bottom, selection controls at top
  - Commit: (pending)

- [x] **Player Screen: Add Swipe UP/DOWN gestures for file operations**
  - **Problem:** Copy/Move operations only available via touch zones (3x3 grid), not via vertical swipes per spec
  - **Solution:**
    * Updated onFling() in PlayerActivity to detect vertical vs horizontal gestures
    * Horizontal fling (Left/Right) → navigate between files (Previous/Next)
    * Vertical fling UP → showCopyDialog() (copy current file to destination)
    * Vertical fling DOWN → showMoveDialog() (move current file to destination)
  - **Existing infrastructure:**
    * CopyToDialog, MoveToDialog, RenameDialog already implemented
    * PlayerViewModel already has fileOperationUseCase and getDestinationsUseCase injected
    * Touch zones (3x3 grid) still work for alternative access
  - **Result:** Users can now Copy (SwipeUP) or Move (SwipeDown) files during playback
  - **Build:** Successful (4s, 9 tasks executed, only warnings)
  - Commit: (pending)

### 2025-01-08 (SMB Integration Session)
- [x] **AddResourceActivity: Add SMB network folder UI**
  - **Layout Changes:**
    * Created layoutSmbFolder in activity_add_resource.xml (ScrollView with LinearLayout)
    * Added TextInputLayouts for: server (IP/hostname), shareName, username, password, domain, port
    * Password field with toggle visibility (endIconMode="password_toggle")
    * Port field defaults to 445 (standard SMB port)
    * Added helper texts for server, shareName, domain, port fields
    * Added buttons: Test Connection, Scan Shares, Add to Resources
    * Added RecyclerView for resources to add (rvSmbResourcesToAdd)
  - **String Resources:**
    * Added SMB strings in values/strings.xml (English)
    * Added SMB strings in values-ru/strings.xml (Russian)
    * Added SMB strings in values-uk/strings.xml (Ukrainian)
    * Strings: smb_server, smb_server_hint, smb_share_name, smb_share_name_hint, smb_username, smb_password, smb_domain, smb_domain_hint, smb_port, smb_port_hint, smb_test_connection, smb_scan_shares
  - **Activity Code:**
    * Activated cardNetworkFolder click handler to show layoutSmbFolder
    * Added showSmbFolderOptions() to display SMB configuration UI
    * Added testSmbConnection() with validation (requires server address)
    * Added scanSmbShares() with validation (requires server address)
    * Added addSmbResources() stub for future implementation
    * All methods extract values from UI: server, shareName, username, password, domain, port
  - **Next Steps:** Implement ViewModel logic (testSmbConnection, scanSmbFolder, saveSmbResource) using SmbOperationsUseCase
  - **Build:** Successful (41s, 24 executed tasks, only warnings)
  - Commit: (pending)

- [x] **AddResourceViewModel: Add SMB network operations logic**
  - **ViewModel Methods:**
    * Added SmbOperationsUseCase injection to constructor
    * testSmbConnection() - validates SMB connection with provided credentials, shows success/error messages
    * scanSmbShares() - lists available shares on SMB server, creates MediaResource for each share (ResourceType.SMB)
    * addSmbResources() - saves credentials via SmbOperationsUseCase, attaches credentialsId to resources, adds to database
  - **Activity Integration:**
    * Updated AddResourceActivity to call ViewModel methods instead of showing "Coming Soon" toasts
    * Added smbResourceToAddAdapter for separate SMB resources RecyclerView
    * Updated observeData() to filter resources by type (LOCAL vs SMB) and update both adapters
    * Added validation in testSmbConnection() and addSmbResources() (requires shareName)
  - **Resource Creation:**
    * SMB resources created with path format: "smb://server/shareName"
    * Resources marked as ResourceType.SMB with credentialsId link
    * Default values: fileCount=0 (determined on scan), isWritable=true, slideshowInterval=10
    * Supports all media types by default
  - **Error Handling:**
    * testSmbConnection: shows "Connection successful" or "Connection failed: [message]"
    * scanSmbShares: shows "Found N shares" or "Scan failed: [message]"
    * addSmbResources: shows "Added N SMB resources" or error messages
  - **Build:** Successful (11s, 12 executed tasks)
  - Commit: (pending)

- [x] **EditResourceActivity: Add SMB credentials editing support**
  - **ResourceRepositoryImpl:**
    * Added SmbOperationsUseCase injection
    * Implemented testConnection() for SMB resources - gets credentials by credentialsId, calls smbOperationsUseCase.testConnection()
    * Local resources return "no connection test needed", CLOUD/SFTP return "not yet implemented"
  - **Layout Changes (activity_edit_resource.xml):**
    * Added layoutSmbCredentials section (LinearLayout, visibility=gone by default)
    * SMB fields: server, shareName, username, password (with toggle), domain, port (default 445)
    * Section only visible for SMB resource types
  - **EditResourceViewModel:**
    * Added SMB credential fields to EditResourceState: smbServer, smbShareName, smbUsername, smbPassword, smbDomain, smbPort, hasSmbCredentialsChanges
    * Added loadSmbCredentials() method - loads credentials from database via SmbOperationsUseCase.getConnectionInfo()
    * Added update methods: updateSmbServer(), updateSmbShareName(), updateSmbUsername(), updateSmbPassword(), updateSmbDomain(), updateSmbPort()
    * Updated saveChanges() - saves new credentials when hasSmbCredentialsChanges=true, validates server/shareName, updates resource.credentialsId
  - **EditResourceActivity:**
    * Added focus change listeners for all SMB input fields
    * Updated observeData() - shows/hides layoutSmbCredentials based on ResourceType.SMB, displays SMB credentials from state
    * Save/Reset buttons enabled when hasChanges OR hasSmbCredentialsChanges
  - **String Resources:**
    * Added "SMB Network Credentials" in 3 languages (en/ru/uk)
  - **Test Connection:**
    * Button now works for SMB resources - tests connection with current credentials
    * Shows success message or error with details
  - **Build:** Successful (13s, 16 executed tasks)
  - Commit: (pending)

### 2025-11-08 (Bug Fixes & UI Improvements Session)
- [x] **Settings: Fix GIF support and slideshow interval defaults**
  - Fixed AppSettings.supportGifs default to false (was true)
  - Fixed AppSettings.slideshowInterval default to 10 seconds (was 3)
  - Fixed ScanLocalFoldersUseCase to use settings from SettingsRepository
  - Fixed: supportedMediaTypes now built dynamically from settings (supportImages, supportVideos, supportAudio, supportGifs)
  - Build: Successful (6s)

- [x] **Destinations: UI improvements**
  - Fixed: minHeight reduced from 56dp to 40dp in item_destination.xml
  - Fixed: Text sizes already correct (tvDestinationName: 18sp, tvDestinationPath: 15sp)
  - Fixed: Created DestinationColors utility with 10 unique predefined colors
  - Fixed: AddResourceUseCase now assigns unique color based on destinationOrder (1-10)
  - Fixed: SettingsViewModel.addDestination now assigns unique color
  - Colors: Pink(1), Purple(2), Deep Purple(3), Indigo(4), Blue(5), Cyan(6), Green(7), Yellow(8), Orange(9), Red(10)
  - Build: Successful (16s)

- [x] **Browse Screen: Back button icon**
  - Created ic_arrow_back.xml drawable (left arrow icon)
  - Updated BrowseActivity, AddResourceActivity, SettingsActivity to use ic_arrow_back
  - MainActivity Exit button keeps "X" icon (appropriate for app exit)
  - Build: Successful (3s)

- [x] **Browse Screen: Grid mode implementation**
  - Created item_media_file_grid.xml layout (thumbnail + filename, no checkbox/play button)
  - Updated MediaFileAdapter to support both LIST and GRID view types
  - Added GridViewHolder with dynamic thumbnail sizing from settings.defaultIconSize
  - Updated BrowseActivity.updateDisplayMode() to switch adapter mode and get icon size
  - Added SettingsRepository injection and kotlinx.coroutines.flow.first import
  - Grid layout uses GridLayoutManager with 3 columns
  - Build: Successful (24s)

- [x] **Browse Screen: Grid/List toggle icons**
  - Created ic_view_list.xml (list icon with horizontal lines)
  - Created ic_view_grid.xml (grid icon with squares)
  - Updated BrowseActivity.updateDisplayMode() to change button icon dynamically
  - Logic: LIST mode shows grid icon (to switch TO grid), GRID mode shows list icon (to switch TO list)
  - Build: Successful (34s)

- [x] **Main Screen: Fix infinite progress on refresh**
  - Fixed: MainViewModel.refreshResources() was using .collect{} on Flow which never completes
  - Changed to use .first() to get single snapshot of resources
  - Ensured setLoading(false) in finally block
  - Simplified logic with forEach instead of map
  - Build: Successful (26s)

### 2025-11-08 (AddResource Network Folder Fixes)
- [x] **AddResourceActivity: Fix Network Folder button appearance**
  - Removed android:alpha="0.5" from cardNetworkFolder - button now fully visible
  - Network Folder card now clickable and visually enabled
  - Build: Part of full session build

- [x] **AddResourceActivity: Update title when Network Folder selected**
  - Added showLocalFolderOptions() title update to "Add Local Folder"
  - Added showSmbFolderOptions() title update to "Add Network Folder (SMB)"
  - String resources added in 3 languages (en/ru/uk): add_local_folder, add_network_folder
  - Build: Part of full session build

- [x] **AddResourceActivity: Fix IP field input validation**
  - Added InputFilter to etSmbServer field
  - Filter accepts only: digits, dots, replaces comma with dot
  - Invalid characters (backslash, letters, etc.) silently ignored
  - Import added: android.text.InputFilter
  - Build: Part of full session build

- [x] **AddResourceActivity: Reorganize SMB layout fields**
  - Moved Server IP to top (first field)
  - Username/Password on second line (horizontal layout)
  - Test Connection and Scan Shares buttons on third line (right after credentials)
  - Added divider and "Add Manually" label before Share Name section
  - Domain and Port moved to bottom (optional fields)
  - Build: Part of full session build

- [x] **AddResourceActivity: Update field hints and labels**
  - Changed smb_server from "Server (IP or hostname)" to "Server IP"
  - Changed smb_server_hint from "e.g. 192.168.1.100 or myserver" to "Enter IP address only (e.g. 192.168.1.100)"
  - Updated all 3 language files (en/ru/uk)
  - Added smb_add_manually string resource
  - Build: Part of full session build

- [x] **AddResourceActivity: Implement detailed error dialog**
  - Added AlertDialog.Builder import to AddResourceActivity
  - Created showError() helper function - checks settings.showDetailedErrors
  - If showDetailedErrors=true → shows AlertDialog with error details
  - If showDetailedErrors=false → shows Toast (short notification)
  - AddResourceViewModel.getSettings() method added for accessing settings
  - Build: Successful (48s, 43 tasks executed)

### 2025-11-08 (UI Polish Session)
- [x] **Destinations: Reduce path text size**
  - Changed tvDestinationPath textSize from 15sp to 12sp
  - Changed textAppearance from BodyMedium to BodySmall
  - More text now fits in the destination list items
  - Build: Successful (44s, 43 tasks executed)

- [x] **Browse/AddResource/Settings: Fix back button visibility**
  - Removed android:tint="?attr/colorControlNormal" from ic_arrow_back.xml
  - Arrow now displays as solid white icon on all backgrounds
  - Visible on purple toolbar background
  - Build: Successful (44s, 43 tasks executed)

### 2025-11-08 (Permissions & SMB Improvements)
- [x] **Settings: Fix GRANT NETWORK PERMISSION button**
  - Network permissions (INTERNET, ACCESS_NETWORK_STATE) don't require runtime permissions
  - Button now shows informative message: "Network permissions are already granted automatically"
  - These permissions are declared in AndroidManifest.xml and granted at install time
  - Build: Part of full session build

- [x] **Settings: Implement GRANT LOCAL FILES PERMISSION button**
  - Added requestStoragePermissions() method in GeneralSettingsFragment
  - Android 11+ (API 30+): Opens Settings to request MANAGE_EXTERNAL_STORAGE
  - Android 6-10 (API 23-29): Requests READ/WRITE_EXTERNAL_STORAGE via runtime permissions
  - Android 5.x and below: Shows "already granted" (permissions granted at install)
  - Proper handling for all Android versions
  - Build: Part of full session build

- [x] **SMB: Improve Test Connection functionality**
  - Test now works with OR without shareName specified
  - **Without shareName**: Tests server accessibility and lists all available shares with count
  - **With shareName**: Tests specific share access and provides statistics (subfolders, media files, total items)
  - SmbClient.testConnection() enhanced with conditional logic
  - Fixed type error: changed fileAttributes comparison from Int to Long (0x10L)
  - Build: Part of full session build

- [x] **SMB: Add detailed test result dialog**
  - Created AddResourceEvent.ShowTestResult with isSuccess flag
  - Test results now shown in AlertDialog instead of Toast
  - Dialog includes "OK" and "Copy" buttons
  - Copy button copies full test result to clipboard
  - Added ClipboardManager imports to AddResourceActivity
  - showTestResultDialog() method displays formatted results
  - Build: Successful (20s, 43 tasks executed)

- [x] **SMB: Fix Scan Shares returning 0 resources**
  - SMBJ library v0.12.1 lacks direct share enumeration API
  - Implemented trial connection approach in SmbClient.listShares()
  - Tries common share names: Public, Users, Documents, Photos, Videos, Music, Shared, Share, Data, Files, Media, Downloads, Pictures, Movies, Common, Transfer
  - Returns list of successfully connected shares
  - Shows helpful error message if no shares found
  - Build: Successful (29s, 43 tasks executed)

### 2025-11-08 (AddResource IP Field Improvements)
- [x] **AddResource: Implement IP auto-fill and enhanced validation**
  - Added getLocalIpAddress() method using WifiManager and NetworkInterface
  - Auto-fills IP field with device subnet (e.g., "192.168.1." from device IP "192.168.1.100")
  - Cursor positioned at end for immediate typing
  - Enhanced InputFilter with octet validation:
    * Only digits, dots allowed (comma → dot)
    * Blocks 4th dot (max 3 dots for IP)
    * Validates each octet: max 3 digits, max value 255
    * Blocks invalid octets (e.g., "256", "1234")
  - Added ACCESS_WIFI_STATE permission to AndroidManifest.xml
  - Removed unused variable warning (newText)
  - Spec requirement: "convenient IP address input field" - IMPLEMENTED
  - Build: Successful (28s, 43 tasks executed)

- [x] **SMB: Document share enumeration limitations**
  - Updated SmbClient.listShares() with detailed documentation
  - Explained SMBJ library limitations:
    * No direct API for share enumeration (unlike jCIFS)
    * Cannot use IPC$ without admin rights
    * Trial connection approach is the only workaround
  - Expanded common share names list (added NAS names, admin shares)
  - Improved error messages with instructions:
    * How to find share names on Windows
    * Explanation of library limitations
    * Clear guidance to enter share name manually
  - Note: This is a known limitation of SMBJ v0.12.1
  - Alternative: Use jCIFS library (older but has share enumeration)
  - Build: Successful (30s, 43 tasks executed)

### 2025-11-08 (AddResource SMB Buttons Fix)
- [x] **AddResource: Fix "ShareName is required" error for scanned resources**
  - Problem: btnSmbAddToResources called addSmbResources() which validates manual ShareName input
  - Solution: Split functionality into two methods:
    * btnSmbAddToResources → viewModel.addSelectedResources() (for scan results)
    * btnSmbAddManually → addSmbResourceManually() (for manual entry)
  - Added new button "Add Manual Resource" after ShareName field in layout
  - Renamed addSmbResources() → addSmbResourceManually() for clarity
  - Now scanned resources can be added without "ShareName is required" error
  - Manual entry still validates ShareName as expected
  - Build: Successful (35s, 43 tasks executed)

- [x] **SMB: Improve share scanning with extended name list**
  - Problem: Scan found only 1 of 4 shares (limited common names list)
  - Solution: Significantly expanded commonShareNames list:
    * Added Work/Personal variations (Work, Personal, Private, Projects)
    * Added Archive/Storage variations (Archive, Storage, Repository, Vault)
    * Added year-based names (2024, 2025, Archive2024)
    * Added department names (IT, Finance, HR, Sales)
    * Added media server names (Plex, Library, Content)
    * Total: 43 common names tried (was 26)
  - Added IPC$ connection attempt to detect admin rights
  - Improved error messages with detailed instructions:
    * How to find share names on Windows (File Explorer method)
    * Command line method (net share)
  - Added contextual warnings in UI:
    * If 1-2 shares found: warns about possible custom-named shares
    * If 3+ shares found: brief note about manual addition
    * If 0 shares found: clear guidance to use manual entry
  - Removed unused variable sharesFromIPC
  - Build: Successful (36s, 43 tasks executed)

### 2025-11-08 (EditResource Destinations Fix)
- [x] **EditResource: Fix "Add to Destinations" not assigning destinationOrder**
  - Problem: Checkbox sets isDestination=true but doesn't assign destinationOrder
  - Result: Resource marked as destination but not visible in Destinations list
  - Root cause: updateIsDestination() only toggled flag, unlike AddResourceUseCase.addMultiple()
  - Solution: Enhanced updateIsDestination() with full logic:
    * Check if destinations are full (max 10) before adding
    * Assign next available destinationOrder (max + 1)
    * Assign color using DestinationColors.getColorForDestination()
    * When unchecking: clear destinationOrder and destinationColor
    * Show error if destinations are full
  - Added Flow.first() import for getAllResources()
  - Fixed lambda parameter shadowing (it → res)
  - Fixed destinationColor type (null → 0 for Int)
  - Now edited resources properly appear in Destinations tab
  - Build: Successful (42s, 43 tasks executed)

### 2025-11-08 (Manual SMB Resource Addition Fix)
- [x] **Fix "ADD MANUAL RESOURCE" button not working for manually entered SMB resources**
  - Problem: Button checked for selected resources from scan list, but manual entry has no list
  - Error: "No SMB RESOURCE SELECTED" when adding manually entered resource
  - Root cause: `addSmbResources()` filtered `resourcesToAdd` for selected items
  - Solution:
    * Created new `addSmbResourceManually()` function in AddResourceViewModel
    * Directly creates MediaResource from form fields (server, shareName, credentials)
    * Bypasses scan list check, saves credentials and adds resource to database
    * Updated AddResourceActivity to call new function
  - Renamed button: "Add Manual Resource" → "Add This Resource" (clearer intent)
  - Updated contextual messages referencing old button name
  - Now manual SMB resource entry works without scanning
  - Build: Successful (36s, 43 tasks executed)

### 2025-11-08 (Welcome Screen Verification)
- [x] **Welcome Screen: Verify implementation completeness**
  - Existing components verified:
    * WelcomeActivity with ViewPager2 navigation (3 pages)
    * WelcomePagerAdapter with RecyclerView pattern
    * page_welcome.xml layout (icon, title, description)
    * indicator_active.xml and indicator_inactive.xml drawables
    * Welcome strings in 3 languages (en/ru/uk)
    * WelcomeViewModel with SharedPreferences persistence
    * First launch check in MainActivity.onCreate()
    * Permission request flow after finishing welcome
  - Navigation buttons: Previous, Next, Finish, Skip
  - Skip button allows closing before completing tour
  - Matches V2_Specification.md requirements
  - Build: Successful (45s, 43 tasks executed)

### 2025-11-08 (SFTP Support - Initial Implementation)
- [x] **SFTP: Verify SSHJ library dependency**
  - Confirmed: com.hierynomus:sshj:0.37.0 already in build.gradle.kts
  - Library provides SSH and SFTP protocol support
  
- [x] **SFTP: Create low-level client wrapper**
  - Created SftpClient.kt in data/remote/sftp package
  - Implemented methods:
    * connect() - establish SFTP connection with password auth
    * listFiles() - list files in remote directory
    * testConnection() - verify credentials without persistent connection
    * disconnect() - cleanup resources
    * isConnected() - check connection status
  - Uses SSHJ library SSHClient and SFTPClient
  - PromiscuousVerifier for host key verification (accepts all hosts)
  - All methods use Dispatchers.IO for blocking operations
  - Comprehensive logging via Timber
  - Result<T> return type for error handling

- [ ] **SFTP: Add credentials storage (Next)**
  - Create SftpCredentials entity in database
  - Add DAO methods for CRUD operations
  - Store: host, port, username, password (encrypted)
  - Associate credentials with MediaResource via credentialsId

- [ ] **SFTP: Create domain UseCase (Next)**
  - SftpOperationsUseCase with repository pattern
  - Methods: testConnection, listFiles, saveCredentials
  - Error handling and mapping to domain errors

- [ ] **SFTP: Add UI to AddResourceActivity (Next)**
  - SFTP section similar to SMB (host, port, username, password)
  - Default port 22, Test Connection button
  - Remote path field for listing files
  - Add Resource button to save SFTP resource

- [ ] **SFTP: Integrate with ViewModel (Next)**
  - Add SFTP methods to AddResourceViewModel
  - Handle test/add operations
  - Update UI with results

---
  - Audio: generate bitmap with file extension text (e.g., "MP3", "WAV")
  - Created placeholder drawables: ic_image_placeholder, ic_video_placeholder
  - Created error drawables: ic_image_error, ic_video_error
  - Added audio_icon_bg color (#FF607D8B) for audio file icons
  - All thumbnails use RoundedCornersTransformation(8f) for consistent look
  - Commit: (pending)

---

## 📊 Project Status

**Milestone 2 (basic functionality):** ✅ Completed
**Milestone 3 (UI improvements):** ✅ Completed
**Current phase:** Testing and bug fixes

### Priorities:
1. **Critical:** Fix discovered bugs, test on device
2. **High:** Permissions handling, Welcome Screen, language selection
3. **Medium:** UI/UX polishing, optimization
4. **Low:** Network/cloud features, promotional materials
