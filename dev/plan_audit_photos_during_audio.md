# АУДИТ ПЛАНА: Фотографии во время воспроизведения музыки

**Дата проверки:** 6 февраля 2026
**Статус:** ПЛАН ВЫПОЛНЕН, требуется корректировка промптов

---

## 1. ПРОВЕРКА ТЕКУЩЕГО СОСТОЯНИЯ КОДОВОЙ БАЗЫ

### ✅ Слой данных (Шаг 1) - УЖЕ РЕАЛИЗОВАН

**Статус:** `AppSettings.kt` и `SettingsRepositoryImpl.kt` УЖЕ содержат все необходимые поля.

**Обнаружено:**

```kotlin
// AppSettings.kt (строки 38-39)
val enablePhotosDuringAudio: Boolean = false
val audioBackgroundPhotosResourceId: String? = null

// SettingsRepositoryImpl.kt (строки 59-60)
private val KEY_ENABLE_PHOTOS_DURING_AUDIO = booleanPreferencesKey("enable_photos_during_audio")
private val KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID = stringPreferencesKey("audio_background_photos_resource_id")
```

**Вывод:** Шаг 1 можно пропустить. Миграция БД не требуется.

---

## 2. ПРОВЕРКА UI НАСТРОЕК (Шаг 2)

### ⚠️ ТРЕБУЕТ РЕАЛИЗАЦИИ

**Найден аналогичный паттерн:** `ImagesSettingsFragment.kt` (для слайдшоу с музыкой)
**Путь:** `c:\GIT\FastMediaSorter_mob_v2\app_v2\src\main\java\com\sza\fastmediasorter\ui\settings\fragments\ImagesSettingsFragment.kt`

**Пример Resource Selector (строки 120-140):**

```kotlin
// Slideshow background music resource selector
binding.tvSlideshowMusicResourceValue.setOnClickListener {
    ResourceSelectorDialog.show(
        context = requireContext(),
        resourceRepository = viewModel.resourceRepository,
        currentSelection = viewModel.settings.value.slideshowMusicResourceId,
        allowAllFormats = false, // Only show supported resources (audio)
        onResourceSelected = { resource ->
            val current = viewModel.settings.value
            val updated = current.copy(slideshowMusicResourceId = resource?.id)
            viewModel.updateSettings(updated)
        }
    )
}
```

**Замечание:** В плане указан `AudioSettingsFragment`, но в текущей кодовой базе фича слайдшоу находится в `ImagesSettingsFragment`. Это логично, т.к. это настройка **слайдшоу изображений** с музыкой. Для **аудиоплеера с фото** правильное место - `AudioSettingsFragment`.

---

## 3. ПРОВЕРКА ЛОГИКИ ПЛЕЕРА (Шаг 3-4)

### 🚨 КРИТИЧЕСКАЯ ОШИБКА В ПЛАНЕ

**Проблема:** План предлагает добавить код в `PlayerViewModel.kt`, но в проекте используется **Manager Pattern**.

**Архитектурные требования (из AGENTS.md):**

```
- **ACTIVITY_LOGIC**: **PROHIBITED**. Complex logic MUST reside in `helpers/*Manager`.
- **Example**: `PlayerActivity.kt` delegates to `PlaybackManager.kt`.
```

**Обнаружено:** В `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/` находится 30+ Manager-классов:

- `BackgroundMusicManager.kt` - готовый паттерн для музыки в слайдшоу
- `MediaDisplayCoordinator.kt` - координатор медиа-отображения
- `PlayerMediaLoaderManager.kt` - загрузка медиа
- И т.д.

**Вывод:** Нужен НОВЫЙ Manager: `AudioBackgroundPhotosManager.kt`

---

## 4. ИСПРАВЛЕННАЯ РАЗБИВКА

### Шаг 1: UI Настроек (AudioSettingsFragment) ✅

**Промпт (исправленный):**

```markdown
Задача: Добавить UI для настройки "Фото во время музыки" в `AudioSettingsFragment`.

Контекст:
- Файл: `c:\GIT\FastMediaSorter_mob_v2\app_v2\src\main\java\com\sza\fastmediasorter\ui\settings\fragments\AudioSettingsFragment.kt`
- Layout: `c:\GIT\FastMediaSorter_mob_v2\app_v2\src\main\res\layout\fragment_settings_audio.xml`
- Пример аналогичной фичи: `ImagesSettingsFragment.kt` (строки 115-145) - Resource Selector для музыки в слайдшоу

Реализация:
1. В `fragment_settings_audio.xml` (после блока "Search covers only on WiFi"):
   - Добавь `MaterialSwitch` с id `switchEnablePhotosDuringAudio`, текст "Enable photos during audio playback" (добавь строку в `strings.xml`)
   - Добавь `LinearLayout` (id `layoutAudioBackgroundPhotosSelector`) с:
     * `TextView` "Background photos source:"
     * `TextView` (id `tvAudioBackgroundPhotosResourceValue`) для отображения выбранного ресурса
     * `ImageButton` (Help icon)
   - Видимость selector'а = `isVisible` только если свич включен

2. В `AudioSettingsFragment.kt`:
   - Привяжи `switchEnablePhotosDuringAudio` к `settings.enablePhotosDuringAudio`
   - При изменении свича обновляй видимость `layoutAudioBackgroundPhotosSelector`
   - Реализуй клик на `tvAudioBackgroundPhotosResourceValue`:
     ```kotlin
     ResourceSelectorDialog.show(
         context = requireContext(),
         resourceRepository = viewModel.resourceRepository,
         currentSelection = viewModel.settings.value.audioBackgroundPhotosResourceId?.toLongOrNull(),
         allowAllFormats = false, // Only image resources
         onResourceSelected = { resource ->
             val current = viewModel.settings.value
             val updated = current.copy(audioBackgroundPhotosResourceId = resource?.id?.toString())
             viewModel.updateSettings(updated)
         }
     )
     ```
   - В `observeData()` обновляй текст `tvAudioBackgroundPhotosResourceValue` при изменении настроек

3. Добавь строки в `strings.xml`:
   - `enable_photos_during_audio_description` ("Show random photos during audio playback")
   - `audio_background_photos_source_label` ("Background photos source:")
   - `audio_background_photos_not_selected` ("Not selected")

Проверь сборку: `.\gradlew.bat assembleStandardDebug`
```

---

### Шаг 2: Создание AudioBackgroundPhotosManager 🆕

**Промпт (новый):**

```markdown
Задача: Создать Manager для управления фоновыми фото во время воспроизведения аудио.

Контекст:
- Путь: `c:\GIT\FastMediaSorter_mob_v2\app_v2\src\main\java\com\sza\fastmediasorter\ui\player\helpers\AudioBackgroundPhotosManager.kt`
- **Аналог:** `BackgroundMusicManager.kt` (для музыки в слайдшоу)
- **Архитектура:** Manager инжектится через Hilt, использует UseCases для загрузки файлов

Требования:
1. Класс должен быть `@Singleton` с Hilt DI
2. Инжекция зависимостей:
   ```kotlin
   @Inject constructor(
       @ApplicationContext private val context: Context,
       private val resourceRepository: ResourceRepository,
       private val getMediaFilesUseCase: GetMediaFilesUseCase
   )
   ```

1. **Основные методы:**
   - `initialize()` - подготовка (если нужна)
   - `updateState(state: PlayerViewModel.PlayerState)` - обновление при изменении состояния плеера
   - `loadPhotosPlaylist(resourceId: String)` - загрузка списка фото из ресурса
   - `getCurrentPhoto(): MediaFile?` - получить текущее фото для отображения
   - `advanceToNextPhoto()` - переключить на следующее фото (вызывается при смене трека)
   - `release()` - очистка ресурсов

2. **Логика:**
   - При загрузке ресурса:
     - Использовать `getMediaFilesUseCase` для получения файлов
     - Отфильтровать только `MediaType.IMAGE` (исключить GIF/VIDEO)
     - Перемешать список (`shuffled()`)
   - Хранить текущий индекс фото (циклически)
   - При `advanceToNextPhoto()` - инкремент индекса по кругу: `(currentIndex + 1) % photosList.size`

3. **Callback для UI:**

   ```kotlin
   private var onPhotoChangedListener: ((photo: MediaFile?) -> Unit)? = null
   fun setOnPhotoChangedListener(listener: ((photo: MediaFile?) -> Unit)?)
   ```

4. **Использовать корутины для загрузки:**

   ```kotlin
   private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
   ```

5. **Логирование:** Использовать `Timber` (не `Log.d()`)

Проверь сборку после создания.

```

---

### Шаг 3: Интеграция Manager в PlayerActivity 🆕
**Промпт (исправленный):**
```markdown
Задача: Интегрировать `AudioBackgroundPhotosManager` в `PlayerActivity`.

Контекст:
- `PlayerActivity.kt` уже использует 30+ Manager-классов
- **Аналог:** `BackgroundMusicManager` (строки ~250-300)

Реализация:
1. Инжекция Manager:
   ```kotlin
   @Inject
   lateinit var audioBackgroundPhotosManager: AudioBackgroundPhotosManager
   ```

1. В `onCreate()` (после инициализации других менеджеров):

   ```kotlin
   audioBackgroundPhotosManager.initialize()
   audioBackgroundPhotosManager.setOnPhotoChangedListener { photo ->
       if (photo != null && viewModel.currentFile?.type == MediaType.AUDIO) {
           loadPhotoIntoImageView(photo)
       }
   }
   ```

2. В `observeState()` (где подписка на `viewModel.state`):

   ```kotlin
   // После других менеджеров
   audioBackgroundPhotosManager.updateState(state)
   ```

3. Создать вспомогательный метод `loadPhotoIntoImageView(photo: MediaFile)`:
   - Используй Glide (как для обычных изображений)
   - Загружай фото в `binding.imageView`
   - Обнови `binding.textCurrentFileName` (если нужно показать имя файла фото)

4. В `onDestroy()`:

   ```kotlin
   audioBackgroundPhotosManager.release()
   ```

5. **Логика активации:**
   - Manager должен автоматически определять, что:
     - Текущий файл - Audio (`state.currentFile?.type == MediaType.AUDIO`)
     - Настройка `enablePhotosDuringAudio == true`
     - `audioBackgroundPhotosResourceId != null`
   - Если условия выполнены - загрузить плейлист фото

Проверь сборку и базовую работу.

```

---

### Шаг 4: Обработка событий ExoPlayer (PlayerViewModel) 📝
**Промпт (исправленный):**
```markdown
Задача: Добавить обработку смены трека для переключения фото.

Контекст:
- `PlayerViewModel.kt` уже имеет логику для работы с плейлистом
- Manager должен получать уведомление о смене трека

Реализация:
1. В `PlayerViewModel.kt` найди логику NavigationManager или аналогичную
2. Добавь метод (или расширь существующий):
   ```kotlin
   fun onAudioTrackChanged() {
       // Callback для Activity -> Manager
       // Activity подпишется на этот event через SharedFlow
       emitEvent(AudioTrackChangedEvent)
   }
   ```

1. В `PlayerActivity.kt`, в listener'е ExoPlayer (где обрабатывается `onMediaItemTransition`):

   ```kotlin
   override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
       // ... existing code ...
       
       // Notify photos manager about track change
       if (viewModel.currentFile?.type == MediaType.AUDIO) {
           audioBackgroundPhotosManager.advanceToNextPhoto()
       }
   }
   ```

Альтернатива (если используется другой паттерн):

- Если ExoPlayer обрабатывается в отдельном Manager (`ExoPlayerControlsManager`), добавь callback там

Проверь сборку и работу: при смене песни должно меняться фото.

```

---

## 5. ДОПОЛНИТЕЛЬНЫЕ РЕКОМЕНДАЦИИ

### 🔴 КРИТИЧЕСКИЕ ЗАМЕЧАНИЯ:
1. **Manager Pattern - Обязательно**: Весь код логики НЕ в Activity/ViewModel, а в Manager
2. **DI через Hilt**: Все Manager'ы - @Singleton с инжекцией
3. **Timber вместо Log.d()**: Строгое требование проекта
4. **Корутины**: Загрузка файлов только через `Dispatchers.IO`

### ✅ ЧТО УЖЕ ГОТОВО:
- `AppSettings.kt` - поля есть
- `SettingsRepositoryImpl.kt` - сохранение/загрузка работает
- Паттерн Resource Selector - есть в `ImagesSettingsFragment.kt`
- Паттерн Manager - `BackgroundMusicManager.kt`

### 📦 ЧТО НУЖНО СОЗДАТЬ:
1. UI в `AudioSettingsFragment` + XML
2. `AudioBackgroundPhotosManager.kt` (новый файл)
3. Интеграция в `PlayerActivity.kt`
4. Обработка событий смены трека

---

## 6. ПОРЯДОК ВЫПОЛНЕНИЯ (ИСПРАВЛЕННЫЙ)

1. **Шаг 1:** UI Настроек (AudioSettingsFragment + XML + strings.xml)
2. **Сборка:** `.\gradlew.bat assembleStandardDebug`
3. **Коммит:** "feat: add UI for audio background photos settings"
4. **Шаг 2:** Создать `AudioBackgroundPhotosManager.kt`
5. **Сборка**
6. **Коммит:** "feat: add AudioBackgroundPhotosManager for photo slideshow during audio"
7. **Шаг 3:** Интеграция Manager в `PlayerActivity.kt`
8. **Сборка**
9. **Коммит:** "feat: integrate AudioBackgroundPhotosManager into PlayerActivity"
10. **Шаг 4:** Обработка смены трека
11. **Сборка + Тестирование**
12. **Коммит:** "feat: implement photo switching on audio track change"

---

## 7. ТЕСТОВЫЙ СЦЕНАРИЙ

После реализации проверить:
1. ✅ Settings -> Media -> Audio -> Свич "Enable photos during audio" (по умолчанию OFF)
2. ✅ При включении свича - показать Resource Selector
3. ✅ Выбрать папку с фото -> сохранить в настройках
4. ✅ Открыть аудиофайл в плеере -> должно показать случайное фото
5. ✅ Next/Prev трек -> фото меняется
6. ✅ Выключить свич -> вернуться к стандартной заглушке/обложке
7. ✅ Открыть видео/изображение -> фича не активируется

---

**Заключение:**
План разработки **корректен по структуре**, но промпты требуют модификации под архитектуру проекта (Manager Pattern). Используй исправленные промpты выше.
