# TODO V2 - FastMediaSorter v2

## 🐛 Обнаруженные проблемы при тестировании

- [x] В окне "фильтр и сортировка" кнопки внизу "Очистить все", "Отмена", "Применить" текст кнопки очень микроскопический. Неудобно прочитать. Нужно сменить на иконки.
  - ✅ Созданы векторные иконки: ic_clear.xml, ic_cancel.xml, ic_check.xml
  - ✅ Кнопки заменены на MaterialButton с иконками (icon-only режим)
  - ✅ Добавлены contentDescription для accessibility
  - ✅ Добавлена строка "clear_all" в strings.xml (en, ru, uk)

- [x] Я добавил несколько локальных папок в качестве ресурсов, вижу их в списке ресурсов. Но кнопки перемещения вверх вниз не работают. Кнопка вызова "редактора ресурсов" также не работает.
  - ✅ Добавлено поле `displayOrder` в MediaResource и ResourceEntity
  - ✅ Создана миграция БД v3→v4: добавление колонки displayOrder
  - ✅ Обновлены запросы DAO для сортировки по displayOrder
  - ✅ Реализованы методы moveResourceUp() и moveResourceDown() в MainViewModel
  - ✅ Логика обмена displayOrder между соседними ресурсами
  - ✅ Автоматическая установка displayOrder при создании ресурса (max+1)
  - ✅ Реализован экран редактирования ресурса (EditResourceActivity)
  - ✅ Создан EditResourceViewModel для управления состоянием
  - ✅ Добавлена навигация из MainActivity по кнопке Edit
  - ✅ Реализованы поля: name, path (read-only), type (read-only), createdDate (read-only), fileCount (read-only), slideshowInterval, supportedMediaTypes, isDestination
  - ✅ Кнопки: Back, Test, Reset, Save
  - ✅ Добавлены переводы для en, ru, uk


- [x] При первом запуске после инсталляции пользователь должен увидеть Welcome Screen, А затем одну за другой запросить разрешение дать доступ к локальным файлам, сети, и так далее что есть в программе. Если хоть один доступ дан - перезапустить программу.
  - ✅ Создан layout activity_welcome.xml с ViewPager2 для страниц
  - ✅ Добавлены кнопки Skip, Previous, Next, Finish
  - ✅ LinearLayout для индикаторов страниц
  - ✅ Созданы drawable индикаторов: indicator_active.xml, indicator_inactive.xml
  - ✅ Создан WelcomeActivity с ViewPager2 setup
  - ✅ WelcomePagerAdapter для отображения страниц
  - ✅ WelcomeViewModel для управления состоянием первого запуска
  - ✅ Layout страницы page_welcome.xml с иконкой, заголовком, описанием
  - ✅ Три страницы приветствия с описанием функций
  - ✅ Проверка первого запуска в MainActivity
  - ✅ Переводы для en, ru, uk
  - ✅ Зарегистрирована WelcomeActivity в AndroidManifest.xml
  - ✅ Создан PermissionHelper для управления разрешениями
  - ✅ Реализован запрос READ_EXTERNAL_STORAGE для Android 6-10
  - ✅ Реализован запрос MANAGE_EXTERNAL_STORAGE для Android 11+
  - ✅ Диалоги с rationale перед запросом разрешений
  - ✅ Обработка onRequestPermissionsResult и onActivityResult
  - ✅ Перезагрузка приложения через LocaleHelper.restartApp() если разрешения даны
  - ✅ Переводы permission dialogs для en, ru, uk


- [x] Языки приложения четко описаны в спецификации. Все текстовые сообщения и надписи должны быть продублированы для трёх языкав. А после смены языка в окне "Settings" из "General" программа должна сохранить язык, перезагрузиться и показать уже везде новый выбранный язык.
  - ✅ Созданы директории values-ru и values-uk с полными переводами strings.xml
  - ✅ Переведены все базовые строки интерфейса (кнопки, диалоги, настройки)
  - ✅ Создан LocaleHelper для управления локалью приложения
  - ✅ Реализован метод changeLanguage с перезагрузкой приложения
  - ✅ Применение локали в FastMediaSorterApp.attachBaseContext()
  - ✅ Применение локали в BaseActivity.attachBaseContext()
  - ✅ Spinner выбора языка в GeneralSettingsFragment с начальным значением
  - ✅ Диалог подтверждения перезагрузки при смене языка
  - ✅ Сохранение языка в SharedPreferences
  - ✅ Отмена смены языка возвращает spinner к предыдущему значению

- [x] Диалог демонстрации деталей ошибок и диалоги демонстрации логов должны показывать текст в текстовом поле, доступном для выделения и копирования части/целликом текста с вертикальной и горизонтальной прокруткой. Текста очень мальнького размера. Сейчас это поле как "caption" на фоне окна
  - ✅ Обновлён dialog_log_view.xml: добавлен HorizontalScrollView для горизонтальной прокрутки
  - ✅ TextView с textIsSelectable="true" для выделения и копирования текста
  - ✅ Размер текста 10sp, моноширинный шрифт
  - ✅ Создан ErrorDialog utility для показа детальных ошибок
  - ✅ Кнопка "Copy to Clipboard" в диалогах
  - ✅ Поддержка stack trace в ErrorDialog
  - ✅ Добавлены переводы для en, ru, uk

- [x] мне нужно везде при переходе между закладками - моментальная смена закладки. Никакой "анимации".
  - ✅ Добавлен instant PageTransformer в SettingsActivity
  - ✅ Отключены анимации переходов между вкладками
  - ✅ Установлен offscreenPageLimit = 1

- [x] В окне "Settings" из "General" переход на любую другую закладку - приложение закрывается.
  - ✅ Исправлено: массив languages уже существовал в strings.xml, дубликат не создавался

- [x] Краш при переключении на вкладку "Media Files" в настройках:
  ```
  IllegalStateException: Value(0.09765625) must be equal to valueFrom(0.0) plus a multiple of stepSize(1.0)
  ```
  - ✅ Причина: `sizeToSlider()` возвращает дробные значения (например, 0.09765625), но RangeSlider с stepSize=1 требует целые числа
  - ✅ Решение: Округление результата до целого числа через `sliderValue.roundToInt().toFloat()`
  - ✅ Добавлен импорт `kotlin.math.roundToInt` в SettingsFragments.kt

- [x] Краш NullPointerException при переключении на вкладку "Playback" (PlaybackSettingsFragment):
  ```
  NullPointerException: Attempt to invoke interface method 'int java.lang.CharSequence.length()' on a null object reference
  at android.text.StaticLayout.<init>
  at androidx.appcompat.widget.SwitchCompat.makeLayout
  at androidx.appcompat.widget.SwitchCompat.onMeasure
  ```
  - ✅ Причина: MaterialSwitch наследует от SwitchCompat, который вызывает text.length() на null-значении textOn/textOff
  - ✅ Решение: Добавлены `android:textOn=""` и `android:textOff=""` во все MaterialSwitch в fragment_settings_playback.xml и fragment_settings_destinations.xml
  - ✅ Коммит: 959078d

- [x] **Browse Screen: Button reordering per specification**
  - ✅ Reordered buttons: [space], Sort, Filter, Grid/List toggle, Copy, Move, Rename, Delete, Undo, [space], Play
  - ✅ Added btnUndo button (visibility="gone" by default)
  - ✅ Removed btnSlideshow button (slideshow mode via Play button per spec)
  - ✅ Added string resources: sort, toggle_view, play for EN/RU/UK
  - ✅ Updated BrowseActivity to remove btnSlideshow.setOnClickListener
  - ✅ Коммит: de899a3

- [x] **Browse Screen: Filter dialog implementation**
  - ✅ Created FileFilter data class with nameContains, minDate, maxDate, minSizeMb, maxSizeMb fields
  - ✅ Created dialog_filter.xml layout with name filter, date range pickers, size range (MB)
  - ✅ Implemented showFilterDialog() in BrowseActivity with DatePickerDialog
  - ✅ Added filter field to BrowseState
  - ✅ Implemented setFilter() and applyFilter() in BrowseViewModel
  - ✅ Filter applies case-insensitive name search, date range (>=minDate, <=maxDate), size range (>=minSizeMb MB, <=maxSizeMb MB)
  - ✅ Filter not persisted after exiting Browse Screen (per specification)
  - ✅ Added filter string resources for EN/RU/UK
  - 📝 Note: Filter status indicator at screen bottom not yet implemented

- [x] **Browse Screen: Undo functionality**
  - ✅ Created FileOperationType enum (COPY, MOVE, RENAME, DELETE)
  - ✅ Created UndoOperation data class to store operation details (sourceFiles, destinationFolder, copiedFiles, oldNames, timestamp)
  - ✅ Added lastOperation field to BrowseState
  - ✅ Implemented undoLastOperation() in BrowseViewModel
  - ✅ COPY undo: deletes copied files
  - ✅ MOVE undo: moves files back to original location
  - ✅ RENAME undo: renames files back to original names
  - ✅ DELETE undo: placeholder for restore functionality
  - ✅ Added btnUndo click handler in BrowseActivity
  - ✅ btnUndo visibility controlled by lastOperation state

- [x] **Browse Screen: Copy/Move operations with Undo**
  - ✅ Updated FileOperationResult.Success to include copiedFilePaths field
  - ✅ Updated executeCopy() to track destination file paths
  - ✅ Updated executeMove() to track moved file paths
  - ✅ Updated executeRename() to track new file path
  - ✅ Updated executeDelete() to track deleted file paths
  - ✅ Changed CopyToDialog onComplete callback to return UndoOperation
  - ✅ Changed MoveToDialog onComplete callback to return UndoOperation
  - ✅ CopyToDialog creates UndoOperation with COPY type after successful copy
  - ✅ MoveToDialog creates UndoOperation with MOVE type after successful move
  - ✅ Added saveUndoOperation() method to BrowseViewModel
  - ✅ Injected FileOperationUseCase and GetDestinationsUseCase into BrowseActivity
  - ✅ Implemented showCopyDialog() in BrowseActivity
  - ✅ Implemented showMoveDialog() in BrowseActivity
  - ✅ Both dialogs now functional (replaced "Coming Soon" toasts)
  - ✅ After successful operation: save undo info, reload files, clear selection
  - ✅ Build successful

- [x] **Browse Screen: Rename dialog**
  - ✅ Created dialog_rename_single.xml for single file rename (EditText with current name)
  - ✅ Created dialog_rename_multiple.xml for multiple files (RecyclerView)
  - ✅ Created item_rename_file.xml for rename list items
  - ✅ Implemented showRenameSingleDialog() with file exists validation
  - ✅ Implemented showRenameMultipleDialog() with RenameFilesAdapter
  - ✅ File rename validation: empty name check, duplicate name check
  - ✅ Error handling with toast messages per specification
  - ✅ Added reloadFiles() public method to BrowseViewModel
  - ✅ Yellow background per specification (TODO: apply via bg_rename_dialog drawable)
  - 📝 Note: Undo operation saving for rename to be implemented separately

- [x] **Player Screen: Verification**
  - ✅ TouchZoneDetector class implements 9 touch zones in 3x3 grid per specification
  - ✅ Touch zones: BACK (30%x30%), COPY (40%x30%), RENAME (30%x30%), PREVIOUS (30%x40%), MOVE (40%x40%), NEXT (30%x40%), COMMAND_PANEL (30%x30%), DELETE (40%x30%), SLIDESHOW (30%x30%)
  - ✅ Fullscreen mode with touch zones for static images
  - ✅ Command panel mode with toolbar buttons
  - ✅ Slideshow mode with configurable interval
  - ✅ Video/Audio playback with ExoPlayer (Media3)
  - ✅ Gesture detection for video controls
  - ✅ Touch zone height adjustment for video (upper 50% in command panel mode)
  - ✅ PlayerViewModel manages state (current file, slideshow, controls visibility)
  - ✅ Copy/Move/Rename/Delete dialogs integration
  - ✅ Activity layout activity_player_unified.xml with both modes



- [x] **Settings: Add destination color picker**
  - ✅ destinationColor field already exists in MediaResource and ResourceEntity models
  - ✅ Created ColorPalette utility with DEFAULT_COLORS (10 colors) and EXTENDED_PALETTE (20 colors)
  - ✅ Created ColorPickerDialog with color grid (5 columns)
  - ✅ Created item_color.xml layout for color cells
  - ✅ Added ic_check_circle.xml drawable for selection indicator
  - ✅ Color preview with name display
  - ✅ Translations for EN/RU/UK
  - ✅ Integrated ColorPickerDialog into DestinationsSettingsFragment
  - ✅ Added updateDestinationColor() to SettingsViewModel
  - ✅ DestinationAdapter already uses destinationColor for button backgrounds in CopyToDialog/MoveToDialog
  - 📝 Note: Touch zones in Player Screen don't currently use destination colors - deferred to future enhancement

- [x] **Settings: Add file type filters**
  - ✅ Already implemented in MediaSettingsFragment (switchSupportImages, switchSupportGifs, switchSupportVideos, switchSupportAudio)
  - ✅ AppSettings model contains supportImages, supportGifs, supportVideos, supportAudio flags
  - ✅ File size sliders for each type (imageSizeMin/Max, videoSizeMin/Max, audioSizeMin/Max)
  - ✅ Filters stored in SettingsRepositoryImpl via SharedPreferences
  - ✅ Applied globally to all resources

- [x] **Settings: Add language selection**
  - ✅ Already implemented in GeneralSettingsFragment (spinnerLanguage)
  - ✅ Support for English, Russian, Ukrainian (values-en, values-ru, values-uk)
  - ✅ LocaleHelper for managing locale changes
  - ✅ Restart dialog when language changed
  - ✅ Language saved to AppSettings and applied immediately

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

## 📊 Project Status

**Milestone 2 (basic functionality):** ✅ Completed
**Milestone 3 (UI improvements):** ✅ Completed
**Current phase:** Testing and bug fixes

### Priorities:
1. **Critical:** Fix discovered bugs, test on device
2. **High:** Permissions handling, Welcome Screen, language selection
3. **Medium:** UI/UX polishing, optimization
4. **Low:** Network/cloud features, promotional materials
