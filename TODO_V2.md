# TODO V2 - FastMediaSorter v2

## ✅ Completed (Milestone 2: Working with Local Files)

- [x] **Create UseCase classes for resource management**
  - Implement AddResourceUseCase, GetResourcesUseCase, UpdateResourceUseCase, DeleteResourceUseCase, ScanLocalFoldersUseCase in domain/usecase/

- [x] **Implement local folder scanning**
  - Create LocalMediaScanner for finding media files on device, implement file counting, write permissions detection

- [x] **Enhance MainViewModel and MainActivity**
  - Add full main screen functionality: resource loading, sorting, filtering, list management

- [x] **Create Add Resource Screen (AddResourceActivity)**
  - Implement Add and Scan Resources Screen for local folders with SCAN and Add Manually buttons

- [x] **Create Browse Screen (BrowseActivity)**
  - Implement screen for viewing list/grid of media files with sorting and filtering

- [x] **Create Player Screen (PlayerActivity)**
  - Implement playback screen with ExoPlayer for video/audio and ImageView for images

---

## 🔧 Main Screen Improvements (5 tasks)

- [x] **Main Screen: Add Copy Resource button**
  - According to specification, there should be 'Copy Resource' button (copy of selected resource)
  - ✅ IMPLEMENTED: btnCopyResource added to activity_main.xml and handled in MainActivity
  - When copying, all values are taken from selected resource, user changes only differences

- [x] **Main Screen: Add Exit button**
  - According to specification, there should be exit button with 'Exit Door' icon
  - ✅ IMPLEMENTED: btnExit added to activity_main.xml and calls finish()

- [x] **Main Screen: Implement double-click on resource**
  - According to specification, double-click on resource should open Browse Screen
  - ✅ IMPLEMENTED: onItemDoubleClick implemented in MainActivity and calls startPlayer()
  - Single click - select resource, double click - open Browse, long press - also Browse

- [ ] **Main Screen: Add filter and sorting**
  - btnFilter button exists, but resource list filtering and sorting functionality not implemented
  - Need filtering dialog by resource type (LOCAL/NETWORK/CLOUD/SFTP)
  - When filter applied, show warning at bottom with filter description

- [ ] **Main Screen: Implement resource refresh**
  - btnRefresh button exists, but existing folder rescanning procedure not implemented
  - On refresh: rescan all resources, update fileCount, check isWritable

---

## 🎨 Item Resource Improvements (1 task)

- [x] **Item Resource: Add Writable flag**
  - ✅ IMPLEMENTED: tvWritableIndicator (🔒) added to item_resource.xml
  - ✅ IMPLEMENTED: display logic in ResourceAdapter (visible only if isWritable = false)
  - Display icon/text if isWritable = false
  - Permissions determined during scanning via LocalMediaScanner.isWritable()

---

## 📂 Browse Screen Improvements (3 tasks)

- [x] **Browse Screen: Implement multi-selection**
  - According to specification, long press should select file range
  - ✅ IMPLEMENTED: Range selection logic in BrowseViewModel.selectFileRange()
  - ✅ IMPLEMENTED: Long press selects range from last selected to current file
  - If no file was selected: long press selects file without launching player
  - If file already selected: long press adds all files between current and previously selected
  - Selected files are highlighted with blue background, counter in header

- [x] **Browse Screen: Add Rename button**
  - ✅ IMPLEMENTED: btnRename added to activity_browse.xml between btnMove and btnDelete
  - ✅ IMPLEMENTED: visibility depends on isWritable = true and presence of selected files
  - ✅ IMPLEMENTED: showRenameDialog() handler in BrowseActivity
  - Visible only if current folder has isWritable = true
  - Can be disabled in settings

- [x] **Browse Screen: Add SlideShow button**
  - ✅ IMPLEMENTED: btnSlideshow added next to btnPlay in activity_browse.xml
  - ✅ IMPLEMENTED: startSlideshow() launches PlayerActivity with slideshow_mode flag
  - Launches Player Screen in slideshow mode
  - If no file selected - starts from first

---

## 🎬 Player Screen Improvements (3 tasks)

- [ ] **Player Screen: Implement touch zones for images**
  - According to specification, static image screen should have 9 touch zones (3x3)
  - Zones: Back (30%x30% top-left), Copy (40%x30% top), Rename (30%x30% top-right)
  - Previous (30%x40% left-center), Move (40%x40% center), Next (30%x40% right-center)
  - Command Panel (30%x30% bottom-left), Delete (40%x30% bottom), Slideshow (30%x30% bottom-right)
  - Currently using gestures (single tap, double tap, fling)

- [ ] **Player Screen: Add 'with command panel' mode**
  - According to specification, there should be mode with command panel above/below image
  - Command panel: Back, Previous, Next, Rename, Delete, Cancel, Slideshow
  - Below image: "Copy to.." and "Move to.." panels with destination buttons (1-10)
  - Destination buttons: color and order from destinations list, dynamic size
  - Image covered by two touch zones: left half - Previous, right half - Next
  - Mode switching via "Show command panel" touch zone

- [ ] **Player Screen: Adjust touch zones for video**
  - According to specification, for video touch zones should occupy only 75% height (upper part)
  - Leave bottom 25% for ExoPlayer controls
  - In "with command panel" mode: touch zones only upper 50% of video area

---

## 💬 Create Dialogs (4 tasks)

- [x] **Create 'Copy to..' dialog**
  - Title: "copying N files from [source folder name]"
  - Destination buttons (1 to 10), except current folder
  - Background: dark-green (dark theme) / light-green (light theme)
  - Progress bar if process >2 seconds
  - Toast message "copied N files"
  - Consider settings: overwrite or skip existing files
  - Error handling with toast messages
  - Result stored for operation undo

- [x] **Create 'Move to..' dialog**
  - Title: "moving N files from [source folder name]"
  - Destination buttons (1 to 10), except current folder
  - Background: dark-blue (dark theme) / light-blue (light theme)
  - Progress bar if process >2 seconds
  - Toast message "moved N files"
  - After move: next file remains selected
  - Error handling with toast messages
  - Result stored for operation undo

- [x] **Create 'Rename' dialog**
  - Title: "renaming N files from [source folder name]"
  - For single file: field with current name (with extension), editable
  - For multiple: list of file names, editable
  - Background: dark-yellow (dark theme) / light-yellow (light theme)
  - Buttons: "apply" and "cancel"
  - File existence check: if file with same name exists - skip and show message
  - Error handling with toast messages
  - Result stored for operation undo

- [x] **Create 'Delete' dialog**
  - Shown only if "Confirm deletion" setting is enabled
  - For single file: "Are you sure you want to delete file [name] from [folder name]?"
  - For multiple: "Are you sure you want to delete [N] files from [folder name]?"
  - Background: dark-red (dark theme) / light-red (light theme)
  - Buttons: "Delete" and "Cancel"
  - If operation undo enabled: files not deleted immediately, only disappear from list
  - Physical deletion on next operation or exit
  - Error handling with toast messages

---

## 🔨 Functionality (3 tasks)

- [x] **Implement FileOperationUseCase**
  - Create UseCase for copy, move, rename, delete local files
  - Support overwrite/skip existing files (from settings)
  - Operation undo mechanism (store result until next operation)
  - Any next operation: copy, move, rename, delete, exit, view another file
  - Progress bar for operations >2 seconds
  - Error handling with detailed messages

- [x] **Implement Destinations mechanism**
  - Add fields to ResourceEntity: isDestination (Boolean, default false), destinationOrder (Int nullable)
  - Support up to 10 destinations with order (1-10)
  - Add fields for destination colors (for dialog buttons)
  - When adding resource: "To destinations" checkbox (only for isWritable = true)
  - If destinations full (10): toast message, resource added without flag
  - In resource list: destination mark (arrow →)
  - Copy/move dialogs: buttons only for available destinations (except current)

- [ ] **AddResource: Show 'to add' list**
  - According to specification, after SCAN or Add Manually should show list of resources to add
  - Each element:
    - "Add" checkbox (enabled by default)
    - Short name (editable field)
    - Number of found media files
    - "To destinations" checkbox (only for isWritable = true, disabled by default)
  - "Add to resources" button below list (appears if list not empty)
  - On click: add all with "Add" checkbox enabled
  - If destinations full: toast message, resource added without destination flag

---

---

## ⚙️ Settings & Configuration (4 tasks)

- [ ] **Settings: Implement all settings from specification**
  - Confirm deletion (Boolean, default true)
  - Enable operation undo (Boolean, default true)
  - Overwrite existing files on copy/move (Boolean, default false)
  - Show rename button in Browse (Boolean, default true)
  - Default view mode (List/Grid)
  - Slideshow interval (seconds, default 3)
  - Theme selection (Light/Dark/System)

- [ ] **Settings: Add destination color picker**
  - Allow users to customize colors for destination buttons (1-10)
  - Default colors: color palette with good contrast
  - Preview of button appearance in dialogs

- [ ] **Settings: Add file type filters**
  - Enable/disable file types: Images, Videos, Audio, GIFs
  - Apply globally to all resources
  - Show in Settings Screen with checkboxes

- [ ] **Settings: Add language selection**
  - Support English, Russian, Ukrainian (as per V1)
  - Use Android localization resources (values-en, values-ru, values-uk)
  - Apply immediately on selection

---

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

## �📊 Project Status

**Milestone 2 (basic functionality):** ✅ Completed
**Specification improvements:** 🔄 In progress (19 tasks)
**Additional features:** 📋 Planned (50+ tasks)

### Priorities:
1. **Critical (pre-release):** FileOperationUseCase, Destinations mechanism, operation dialogs, all Settings, permissions handling
2. **High (for quality):** Testing (unit, instrumented, manual), bug fixes, optimization, ProGuard configuration
3. **Medium (for UX):** Player Screen improvements (touch zones, command panel mode), multi-selection, UI/UX enhancements, accessibility
4. **Low (nice to have):** Additional Main Screen buttons, filtering, network/cloud features, promotional materials

### Estimated Timeline:
- **Phase 1 (Specification compliance):** 2-3 weeks - complete all 19 spec tasks + settings
- **Phase 2 (Network & Cloud):** 2-3 weeks - SMB, SFTP, cloud providers
- **Phase 3 (Polish & Testing):** 2-3 weeks - UI/UX, testing, optimization
- **Phase 4 (Release preparation):** 1-2 weeks - build config, store materials, documentation
- **Phase 5 (Beta & Release):** 2-4 weeks - beta testing, fixes, gradual rollout
- **Total:** ~10-15 weeks to production release
