# TODO V2 - FastMediaSorter v2

## 📋 Актуальні задачі для розработки

- [ ] в окне диалога "сортировка" для BrowseActivity вместо нормального текста для выбора - код. Пользователю может быть неудобно.

- [ ] Проверь, что элементы (наличие и порядок) BrowseActivity отвечают оригинальнойспецификации.

- [ ] Для BrowseActivity нужно изменить спецификацию и добвать новое: дополнительно нужны кнопки "Выбрать все" и "снять выбор со всех" в виде иконок наверху. А вот кнопки операций - копирование, переименование, перенос, удаление, пробел, запуск слайдшоу - пусть появляются внизу под списком (таблицей/сеткой). После реализации нужно внести изменения в спецификацию



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
