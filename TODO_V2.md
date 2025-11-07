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


- [ ] При первом запуске после инсталляции пользователь должен увидеть Welcome Screen, А затем одну за другой запросить разрешение дать доступ к локальным файлам, сети, и так далее что есть в программе. Если хоть один доступ дан - перезапустить программу.

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

## 📊 Project Status

**Milestone 2 (basic functionality):** ✅ Completed
**Milestone 3 (UI improvements):** ✅ Completed
**Current phase:** Testing and bug fixes

### Priorities:
1. **Critical:** Fix discovered bugs, test on device
2. **High:** Permissions handling, Welcome Screen, language selection
3. **Medium:** UI/UX polishing, optimization
4. **Low:** Network/cloud features, promotional materials
