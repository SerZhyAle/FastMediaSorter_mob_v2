# Пошаговая реализация рефакторинга PlayerActivity

Основано на архитектурном плане: [PLAYER_ACTIVITY_REFACTOR_PLAN.md](PLAYER_ACTIVITY_REFACTOR_PLAN.md)

**Git Strategy:** Одна ветка `feature/player-viewpager2`, коммит после каждого выполненного чек-бокса.

**Target:** Android 9+ (API 28+), тестирование на Samsung devices.

**Critical Requirements:**
- [ ] BUILD + FIX + COMMIT после каждого шага (только если build работает)
- [ ] Отмечать реализованное в этом файле!

Реализация:
- [ ] Без анимаций переходов (простая замена фрагментов)
- [ ] Сохранить ощущение единого окна (без моргания интерфейса)
- [ ] Translation/OCR overlay — отдельно в каждом фрагменте
- [ ] Destination buttons — остаются в Activity (общие)
- [ ] Slideshow — работает для IMAGE/VIDEO/AUDIO/GIF, останавливается на PDF/EPUB/TEXT

---

## Фаза 1: Фундамент (Infrastructure)
**Цель:** Создать базовые классы и подготовить Activity для работы с ViewPager2.
**Git:** Commit после каждого подпункта.

- [ ] **1.1. Создать BasePlayerFragment**
    - [ ] Создать файл `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fragments/BasePlayerFragment.kt`
    - [ ] Определить абстрактный класс `BasePlayerFragment : Fragment`
    - [ ] Добавить интерфейс `ActivityCallback` (toggleControls, requests...)
    - [ ] Добавить методы жизненного цикла пейджера: `onPageSelected()`, `onPageUnselected()`
- [ ] **1.2. Создать макеты фрагментов (Skeletons)**
    - [ ] `fragment_image_player.xml` (PhotoView)
    - [ ] `fragment_video_player.xml` (PlayerView)
    - [ ] `fragment_pdf_player.xml` (PhotoView for pages)
    - [ ] `fragment_epub_player.xml` (WebView)
    - [ ] `fragment_text_player.xml` (ScrollView + TextView)
- [ ] **1.3. Обновить макет Activity**
    - [ ] В `activity_player_unified.xml` добавить `androidx.viewpager2.widget.ViewPager2` с `id="@+id/mediaViewPager"` и `visibility="gone"`.
    - [ ] Не удалять старые контейнеры (пока).
- [ ] **1.4. Реализовать адаптер**
    - [ ] Создать `PlayerFragmentAdapter.kt`
    - [ ] Реализовать `createFragment` с заглушками (пока возвращать пустые фрагменты).
    - [ ] Реализовать `updateFiles` с `DiffUtil`.

## Фаза 2: Image Fragment (Миграция изображений)
Цель: Перенести отображение картинок во фрагмент.

- [ ] **2.1. Реализовать ImagePlayerFragment**
    - [ ] Использовать `ImageLoadingManager` внутри фрагмента.
    - [ ] Передать `MediaFile` через аргументы (`newInstance`).
    - [ ] Настроить клик по `PhotoView` -> `activityCallback.toggleControls()`.
- [ ] **2.2. Подключить в Activity**
    - [ ] В `PlayerActivity`: инициализировать адаптер.
    - [ ] Логика переключения View:
        - [ ] Если файл IMAGE/GIF -> `mediaViewPager.isVisible = true`, остальные скрыты.
        - [ ] Иначе -> старое поведение.
- [ ] **2.3. ActivityCallback + OnPageChangeCallback**
    - [ ] Реализовать ActivityCallback в PlayerActivity
    - [ ] OnPageChangeCallback с navigateToIndex()

## Фаза 3: Video Fragment (Миграция видео)
**Цель:** Изолировать ExoPlayer и корректно обработать жизненный цикл.
**Risk:** Высокий (критичный lifecycle, память, network streaming).

**ExoPlayer Strategy (оптимизация памяти):**
- **Current page** (видимый) → ExoPlayer создан и **играет**
- **Next page** (+1 справа) → ExoPlayer создан и **готов** (preload buffering), но НЕ играет
- **Previous page** (-1 слева) → ExoPlayer **НЕ создаем** (экономим ~50-100MB RAM на видео)

```
[Prev Fragment]    [Current Fragment]    [Next Fragment]
  НЕТ плеера         ✅ ИГРАЕТ             ⏸️ ГОТОВ (пауза)
```

- [ ] **3.1. Обновить VideoPlayerManager для Fragment**
    - [ ] Уже принимает `context: Context, lifecycle: Lifecycle`
    - [ ] Добавлен метод `prepareForPreload()` — создать плеер, но НЕ стартовать (для Next page)
    - [ ] Добавлен `resume()`, `getCurrentPosition()`
    - [ ] **COMMIT:** "Phase 3.1: VideoPlayerManager: Added prepareForPreload() method"

- [ ] **3.2. Реализовать VideoPlayerFragment**
    - [ ] Создать `VideoPlayerFragment.kt` наследуя `BasePlayerFragment`
    - [ ] Добавить флаг `private var isNextPage = false` (определяем из position vs currentPosition)
    - [ ] В `onViewCreated()`:
        ```kotlin
        videoPlayerManager = VideoPlayerManager(requireContext(), viewLifecycleOwner, binding.playerView, ...)
        
        // Определяем роль фрагмента
        val currentPos = sharedViewModel.currentFileIndex.value
        val myPos = arguments?.getInt("position") ?: 0
        
        when {
            myPos == currentPos -> {
                // Текущая страница - будем играть
                isNextPage = false
            }
            myPos == currentPos + 1 -> {
                // Следующая страница - preload
                isNextPage = true
                videoPlayerManager.prepareForPreload(currentFile.path, currentFile.resourceType)
            }
            else -> {
                // Предыдущая или далекая - НЕ создаем плеер вообще
                // Оставляем videoPlayerManager null или не инициализируем
            }
        }
        ```
    - [ ] Реализовать `onPageSelected()`:
        ```kotlin
        override fun onPageSelected() {
            isPageActive = true
            if (::videoPlayerManager.isInitialized) {
                val savedPos = sharedViewModel.getSavedPosition(currentFile.path)
                videoPlayerManager.playVideo(currentFile.path, currentFile.resourceType, savedPos)
            } else {
                // Если плеер не был создан (Previous page стала Current) - создаем сейчас
                videoPlayerManager = VideoPlayerManager(...)
                videoPlayerManager.playVideo(...)
            }
        }
        ```
    - [ ] Реализовать `onPageUnselected()`:
        ```kotlin
        override fun onPageUnselected() {
            isPageActive = false
            if (::videoPlayerManager.isInitialized) {
                sharedViewModel.savePosition(currentFile.path, videoPlayerManager.getCurrentPosition())
                videoPlayerManager.pause()
                
                // Если уходим назад (стали Previous page) - убиваем плеер
                // Если уходим вперед (стали Next page) - оставляем на паузе для preload
                val currentPos = sharedViewModel.currentFileIndex.value
                val myPos = arguments?.getInt("position") ?: 0
                
                if (myPos < currentPos) {
                    // Мы теперь Previous - убиваем плеер
                    videoPlayerManager.release()
                }
                // Если myPos > currentPos - мы Next page, плеер на паузе (preload готов)
            }
        }
        ```
    - [ ] В `onDestroyView()`: полное освобождение
    - [ ] В `onDestroyView()`: полное освобождение
    - [ ] Настроить single-tap → toggle controls
    - [ ] **COMMIT:** "Phase 3.2: VideoPlayerFragment implemented with smart memory optimization"

- [ ] **3.3. Обновить адаптер для VIDEO/AUDIO**
    - [ ] В `createFragment()` добавлено:
        ```kotlin
        MediaType.VIDEO, MediaType.AUDIO -> VideoPlayerFragment.newInstance(file, position)
        ```
    - [ ] Передавать position в arguments для определения роли (Current/Next/Prev)
    - [ ] **COMMIT:** "Phase 3.3: Add VideoPlayerFragment to adapter with position"

- [ ] **3.4. Обновить hybrid mode в Activity**
    - [ ] Добавить VIDEO/AUDIO в условие ViewPager:
        ```kotlin
        if (file.type in listOf(MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO, MediaType.AUDIO)) {
            binding.mediaViewPager.isVisible = true
            // hide legacy views
        }
        ```
    - [ ] **COMMIT:** "Phase 3.4: Enable ViewPager for VIDEO/AUDIO"

- [ ] **3.5. OnPageChangeCallback** (уже реализовано в Phase 2.5):
        ```kotlin
        binding.mediaViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setCurrentFileIndex(position)
                
                // Уведомить текущий фрагмент
                val currentFragment = getCurrentFragment(position) as? BasePlayerFragment
                currentFragment?.onPageSelected()
                
                // Уведомить предыдущий фрагмент (стал Previous - убить плеер)
                val prevFragment = getCurrentFragment(position - 1) as? VideoPlayerFragment
                prevFragment?.onPageUnselected()
            }
        })
        ```
    - [ ] Реализовать `getCurrentFragment()` helper
    - [ ] **COMMIT:** "Phase 3.5: Wire OnPageChangeCallback with smart cleanup"

- [ ] **3.6. Тестирование VIDEO (Critical)**
    - [ ] Открыть видео → воспроизведение стартует
    - [ ] Свайп к следующему файлу → видео останавливается, звук пропадает
    - [ ] Свайп назад → видео продолжается с сохраненной позиции
    - [ ] Ротация экрана → позиция восстанавливается
    - [ ] Свернуть приложение → видео на паузе
    - [ ] Network streaming (SMB video) → работает без зависаний
    - [ ] Audio файлы → проигрываются с обложкой
    - [ ] Быстрый свайп через 5 видео → нет задержек, нет звука с других страниц
    - [ ] **Memory test:** Открыть 10 видео, свайп вперед-назад 20 раз
        - Android Profiler → RAM не должна расти (max 2 ExoPlayer одновременно)
        - Нет звука с Previous page (плеер должен быть убит)
    - [ ] Long session (10 минут свайпов) → нет утечек памяти
    - [ ] Fallback на MediaPlayer для unsupported форматов → работает
    - [ ] **COMMIT:** "Phase 3.6: VIDEO fragment tested - memory optimized, stable playback"

**Phase 3 VALIDATION:** 
- Видео стабильно, память не течет
- Максимум 2 ExoPlayer одновременно (Current + Next)
- Previous page без плеера (экономия памяти)

## Фаза 4: Document Fragments (Миграция документов)
Цель: Создать stub фрагменты для PDF, EPUB, TXT с placeholder для тестирования ViewPager навигации.

- [ ] **4.1. PdfPlayerFragment**
    - [ ] Создан stub с placeholder "PDF viewer - Coming in Phase 4+" 
    - [ ] Добавлен в адаптер для MediaType.PDF
- [ ] **4.2. EpubPlayerFragment**
    - [ ] Создан stub с placeholder "EPUB viewer - Coming in Phase 4+"
    - [ ] Добавлен в адаптер для MediaType.EPUB
- [ ] **4.3. TextPlayerFragment**
    - [ ] Создан stub с placeholder "Text viewer - Coming in Phase 4+"
    - [ ] Добавлен в адаптер для MediaType.TEXT
- [ ] **4.4. Включить ВСЕ типы медиа в ViewPager**
    - [ ] Обновлен PlayerUiStateCoordinator: useViewPager для IMAGE/GIF/VIDEO/AUDIO/PDF/EPUB/TEXT
    - [ ] Legacy display больше НЕ используется

**ВАЖНО:** Фрагменты PDF/EPUB/TEXT показывают placeholders. Полная интеграция PdfViewerManager/EpubViewerManager/TextViewerManager будет в следующих фазах рефакторинга.

## Фаза 5: Частичная очистка (Cleanup IMAGE/VIDEO/AUDIO)
Цель: Удалить legacy код только для IMAGE/VIDEO/AUDIO, которые ПОЛНОСТЬЮ мигрированы в фрагменты.

**Ожидаемый статус:** 
- IMAGE/GIF/VIDEO/AUDIO - ПОЛНОСТЬЮ в фрагментах (ImagePlayerFragment, VideoPlayerFragment)
- PDF/EPUB/TEXT - stub фрагменты, реальная функциональность ЕЩЁ через legacy views+менеджеры

**Решение:** Удалить только view и код для IMAGE/VIDEO/AUDIO. Оставить PDF/EPUB/TEXT до полной интеграции.

- [ ] **5.1. Анализ использования менеджеров**
    - [ ] imageLoadingManager - используется только для IMAGE/AUDIO → можно удалить после очистки
    - [ ] videoPlayerManager - используется только для VIDEO/AUDIO → можно удалить  
    - [ ] pdfViewerManager - АКТИВНО используется (search, OCR, translate, navigation) → ОСТАВИТЬ
    - [ ] epubViewerManager - АКТИВНО используется (search, translate, TOC, font) → ОСТАВИТЬ
    - [ ] textViewerManager - АКТИВНО используется (search, scroll, OCR display) → ОСТАВИТЬ
    - [ ] **Вывод:** Удалить можно только imageLoadingManager + videoPlayerManager + их view

- [ ] **5.2. Удаление IMAGE/VIDEO views из layout**
    - [ ] Удалить `playerView` (видео в VideoPlayerFragment)
    - [ ] Удалить `imageView` + `photoView` (картинки в ImagePlayerFragment)  
    - [ ] Удалить `audioCoverArtView` + `audioInfoOverlay` (аудио в VideoPlayerFragment)
    - [ ] Удалить файл `ImageLoadingManager.kt` (710 lines)
    - [ ] ОСТАВИТЬ `textViewerContainer`, `pdfControlsLayout`, `epubWebView`, `pdfFullscreenPhotoView`

- [ ] **5.3. Очистка PlayerActivity.kt от IMAGE/VIDEO кода**
    - [ ] Закомментировать videoPlayerManager, imageLoadingManager declarations
    - [ ] Удалить инициализацию VideoPlayerManager (70 lines callbacks)
    - [ ] Удалить инициализацию ImageLoadingManager (50 lines callbacks)
    - [ ] Удалить инициализацию PlayerSettingsManager (зависит от videoPlayerManager)
    - [ ] Конвертировать в no-op: displayImage(), playVideo(), adjustVolume(), releasePlayer()
    - [ ] Конвертировать в no-op: showAudioFileInfo(), updateAudioFormatInfo(), preloadNextImageIfNeeded()
    - [ ] Закомментировать setupExoPlayerNavigationButtons(), updateRepeatButtonIcon()
    - [ ] Закомментировать saveCurrentPlaybackPosition()
    - [ ] Закомментировать updatePlayPauseButton() videoPlayerManager usage
    - [ ] Закомментировать translateImage(), performOCR() bitmap extraction (IMAGE views removed)
    - [ ] Удалить touch listeners для playerView/photoView/imageView
    - [ ] Удалить IMAGE reload в onConfigurationChanged
    - [ ] Исправлены helpers: PdfViewerManager (photoView → pdfFullscreenPhotoView)
    - [ ] Закомментированы IMAGE/VIDEO views в EpubViewerManager, TextViewerManager
    - [ ] ОСТАВИТЬ pdf/epub/text менеджеры и их callbacks

- [ ] **5.4. Build + Test + Commit**
    - [ ] BUILD SUCCESSFUL без ошибок
    - [ ] Commit: "[Phase 5.3 Complete] Cleaned PlayerActivity: removed IMAGE/VIDEO/AUDIO legacy code"

**ИТОГО Phase 5:**

## Фаза 6: Полировка
**Цель:** Оптимизация памяти и обработка критических системных событий.

- [ ] **6.1. Оптимизация preloading**
    - [ ] Установлен `offscreenPageLimit = 1` в setupViewPager()
    - [ ] Стратегия памяти: Current (играет) + Next (preload на паузе)
    - [ ] Previous страница уничтожается (экономия ~50-100MB RAM на видео)
    - [ ] Trade-off: медленнее backward навигация, но лучше memory usage
    
- [ ] **6.2. Low memory handling**
    - [ ] Добавлен `onLowMemory()`: очистка Glide memory cache + отмена preload jobs
    - [ ] Добавлен `onTrimMemory(level)` с обработкой всех уровней:
        - RUNNING_MODERATE/LOW: очистка Glide cache + отмена preload
        - RUNNING_CRITICAL: сброс offscreenPageLimit до 0 (только текущая страница)
        - BACKGROUND/MODERATE/COMPLETE: агрессивная очистка + System.gc()
    - [ ] BUILD SUCCESSFUL, все обработчики интегрированы
    
**ИТОГО Phase 6:**

---

## 📊 ИТОГОВАЯ СТАТИСТИКА РЕФАКТОРИНГА
(Статистика будет заполнена после выполнения)
