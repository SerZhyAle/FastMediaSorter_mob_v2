# TODO V2 - FastMediaSorter Project Tasks

---

## 📝 Recent Fixes & Completions (Build 2.51.2201.xxx)

### ✅ Slideshow Improvements - (Dec 20, 2024)
**Changes**:
- **Document Skipping**: Slideshow automatically skips non-media files (PDF, TXT, EPUB) during auto-advance
- **Resume Capability**: Slideshow starts from the last viewed file (if available)
- **Smart Launch**: 
    - If last viewed file was a document -> Opens viewer without slideshow active
    - If last viewed file was media -> Starts slideshow from that file
- **Feedback**: Added toast message when last viewed file is unavailable

### ✅ 16 KB Page Size Compatibility - Build .xxx (Dec 20, 2024)
**Problem**: APK not compatible with 16 KB page size devices (Android 15+ requirement). Native Tesseract OCR libraries (libjpeg.so, libleptonica.so, libpng.so, libtesseract.so) had LOAD segments not aligned at 16 KB boundaries.

**Solution**: 
- Added `android.bundle.enableNativeLibraryAlignment=true` to gradle.properties
- Added `androidResources { noCompress += "so" }` to app_v2/build.gradle.kts
- AGP 8.7.3 auto-aligns native libraries with useLegacyPackaging = false

**Google Play Requirement**: Mandatory for all apps targeting Android 15+ since November 1, 2025

**Documentation**: See [docs/16KB_PAGE_SIZE_FIX.md](c:/GIT/FastMediaSorter_mob_v2/docs/16KB_PAGE_SIZE_FIX.md) for verification steps

---

## 📝 Previous Fixes (Build 2.51.2191.xxx)

### ✅ Touch Zones Blocking Video/Audio Controls - Build .xxx (Dec 19, 2024)
**Problem**: Touch zones for image navigation (Previous/Next) remained active during video/audio playback, blocking ExoPlayer controls (play/pause, seek, speed adjust, forward/rewind buttons).

**Root Cause**: 
- `touchZonesOverlay` with `layout_height="match_parent"` covered entire screen including ExoPlayer controls
- Touch zones were designed for image navigation but remained visible for video/audio files
- Clicks on speed/playback controls were intercepted by touch zones, triggering Previous/Next navigation

**Solution**: Complete separation of touch zone logic by media type
- **VIDEO/AUDIO**: Touch zones fully DISABLED in command panel mode (ExoPlayer has own Previous/Next buttons)
- **IMAGE/GIF**: Touch zones ENABLED for quick Previous/Next navigation
- Updated `adjustTouchZonesForVideo()` to hide overlay completely for video/audio instead of resizing

**Changes**:
- [PlayerActivity.kt](c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt): Simplified touch zone logic - hide for video, show for images
- [PlayerUiStateCoordinator.kt](c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt): Updated comments for clarity
- [ExoPlayerControlsManager.kt](c:/GIT/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ExoPlayerControlsManager.kt): Added diagnostic logs for forward/rewind button clicks

**Benefits**:
- No more accidental file navigation when clicking video controls
- ExoPlayer buttons (speed, forward 30s, rewind 10s) now fully accessible
- Clear separation: touch zones for images, ExoPlayer controls for video/audio
- Aligns with specification: "нижняя часть отображения видео и аудио должна быть открыта для управления проигрывателем"

---

## 📝 Previous Fixes (Build 2.51.2172.xxx)

### ✅ UnifiedFileCache Implementation (Steps 1-5/5) - Build .336
**Problem**: Duplicate file downloads across components (metadata extraction, viewing, thumbnails) due to 5+ separate cache systems.

**Solution**: Single UnifiedFileCache shared by all network file operations.

**Changes**:
- Step 1: Created UnifiedFileCache.kt (Build .128)
- Step 2: Integrated NetworkFileDownloader (Build .136)
- Step 3: Integrated NetworkFileManager (Build .145)
- Step 4: Integrated NetworkPdfThumbnailLoader (Build .152)
- Step 5: Final cleanup and validation (Build .336)
  * Removed metadata_temp directory creation
  * Updated all cache clearing logic to use UnifiedFileCache
  * Added DI injections to MainActivity and FastMediaSorterApp
  * Legacy migration preserved for backward compatibility

**Benefits**:
- Single source for all network files
- 24-hour cache expiration
- Automatic size validation
- ~70% reduction in network traffic
- Consistent cache key format (hash + size)

---

## Original Tasks

1. Приоритеты типов медиа
Какой порядок реализации фрагментов предпочтительнее для вас?

-без разницы

2. Текущие проблемы PlayerActivity
Какие баги/неудобства в текущем плеере беспокоят больше всего?

- много логики лежит рядом (разное поведение для разных типов файлов) и сложно разрабатыва, тестрова и что-то добавлять. Чувствую, что если я добавлю пдржку ещё одного типа файла - всё рассыпется. Сейчас есть небольшие баги и неточности которые нужно исправить но очень тяжело найти причину и правильную ветку кода

3. Обратная совместимость
Нужна ли feature flag (переключатель в Settings "Новый плеер Beta")?
Или сразу хард-миграция для всех пользователей?
Готовы ли к возможному fallback на старую версию, если что-то пойдет не так?

При произвостве рефакторинга после каждого шага работ нужно производить сборку и коммит.

4. Пользовательский опыт
При свайпе между файлами разных типов (image→video→pdf):
Анимация перехода не нужна, но чувство что ты работаешь в том же окне должно сохраниться. Да, набор функций разный, но моргание всего интерфейса (при смене активити) нужно избежать.

Важна ли поддержка жестов "свайп вниз = закрыть плеер" (как в Google Photos)?
У всех жлементов свой набор жестов, и в зависимости от типа файла (изображение, видео, pdf),в зависимости от режима демонстрации (полноэкранный/ с командной панелью) - разные жесты.


🔧 Технические вопросы
5. Slideshow Mode
Сейчас есть SlideshowController - как его интегрировать с ViewPager2?
Слайдшоу работает для всех медиафайлов, документы (текст, PDF, EPUB) пропускаются при автоматической навигации. Если запуск слайдшоу происходит с документа, то слайдшоу не активируется.

6. Destination Buttons
Текущие быстрые кнопки копирования/перемещения:

Должны остаться в Activity (общие для всех фрагментов) ✅

7. Translation/OCR Overlays
TranslationManager сейчас работает поверх PhotoView/WebView:

Оставить в Activity как overlay над ViewPager2? ✅
Или перенести в каждый фрагмент отдельно?
Наверное полезно перенести в каждый фрагмент отдельно. У них могут появиться различия в поведении для разных типов файлов.

8. Network File Caching
При свайпе вперед нужно:

Preload следующего файла, если это файл изображения, видео, аудио или анимации или текстовый. PDF и EPUB не предзагружать. Видео и аудио по сети предзагружать только если они небольшие (до 100Мб)

9. Testing
Есть ли у вас реальные устройства для тестирования (или только эмулятор)?
У меня есть андроид телефоны самсунг.

Какие версии Android критичны (минимум API 24, но что используете вы)?
Мне нужно, чтобы запускалосьт и работало на андроид 9 и выше.

Готовы ли писать UI-тесты (Espresso) или только ручное тестирование?
Не готов, не умею.


Есть ли дедлайн или можем идти спокойно по фазам?
идти спокойно по фазам

Работать в одной ветке feature/player-viewpager2 до конца?
Работать в одной ветке

Нужны ли коммиты после каждого чек-бокса в плане?
да

12. Existing Managers
Вижу, что VideoPlayerManager, ImageLoadingManager и др. уже извлечены. Они:

Проблема: Зависят от Activity напрямую (принимают Activity в конструкторе)?
Решение: Можем передавать Fragment вместо Activity?
Или нужно переписать их интерфейсы (breaking changes)?

Я не знаю как лучше и правильно?


13. ExoPlayer Instance
Сейчас ExoPlayer создается в VideoPlayerManager. При ViewPager2:

Вопрос: Каждый VideoPlayerFragment создает свой ExoPlayer?
Проблема: Если offscreenPageLimit=1, у нас может быть 2-3 ExoPlayer одновременно (memory!)

Я не понял в чем проблема - в одном инстансе текущий файл. В другом - предзагрузка следующего. Для чего третий?Почему мы дожны создавать пул если их всего два?

14. Undo/Redo Operations
UndoOperationManager сейчас в Activity. При удалении файла из фрагмента:

Фрагмент вызывает activityCallback.requestDelete()
Activity показывает диалог → удаляет → обновляет адаптер ✅
Корректно ли это или нужна другая схема?

давай попробуем так

