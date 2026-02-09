# План разработки (ИСПРАВЛЕННЫЙ): Фотографии во время воспроизведения музыки

**Версия:** 2.0 (Revised)  
**Дата:** 6 февраля 2026  
**Архитектура:** Clean Architecture + MVVM + Manager Pattern  

---

## ПРЕДВАРИТЕЛЬНАЯ ПРОВЕРКА ✅

### Что УЖЕ реализовано

1. **Модель данных (`AppSettings.kt`):**
   - `enablePhotosDuringAudio: Boolean = false`
   - `audioBackgroundPhotosResourceId: String? = null`

2. **Репозиторий (`SettingsRepositoryImpl.kt`):**
   - `KEY_ENABLE_PHOTOS_DURING_AUDIO`
   - `KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID`
   - Сохранение/загрузка работают

3. **Паттерны:**
   - Resource Picker Dialog - `ImagesSettingsFragment.kt` (строки 128-136)
   - Manager Pattern - `BackgroundMusicManager.kt`

**Вывод:** Миграция БД не требуется. Начинаем с UI.

---

## ШАГ 1: UI Настроек (AudioSettingsFragment)

### Цель

Добавить элементы управления в `Settings -> Media -> Audio`.

### Файлы для изменения

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt`
- `app_v2/src/main/res/layout/fragment_settings_audio.xml`
- `app_v2/src/main/res/values/strings.xml`

### Prompt

```markdown
Задача: Добавить UI для настройки "Фото при воспроизведении музыки" в AudioSettingsFragment.

Контекст:
- Аналогичная фича для слайдшоу реализована в ImagesSettingsFragment.kt (строки 120-145)
- Используется ResourcePickerDialog для выбора папки
- Все настройки уже есть в AppSettings (enablePhotosDuringAudio, audioBackgroundPhotosResourceId)

Реализация:

1. В fragment_settings_audio.xml (после блока "Search covers only on WiFi", перед "Audio size limit"):

```xml
<!-- Enable photos during audio playback -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:minHeight="@dimen/button_height"
    android:layout_marginStart="16dp"
    android:layout_marginBottom="@dimen/margin_small">

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchEnablePhotosDuringAudio"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="@dimen/settings_switch_margin_end"
        android:checked="false" />

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/enable_photos_during_audio_description"
        android:textSize="@dimen/resource_card_desc_text_size" />

    <ImageButton
        android:id="@+id/iconHelpPhotosDuringAudio"
        android:layout_width="@dimen/settings_help_icon_size"
        android:layout_height="@dimen/settings_help_icon_size"
        android:layout_marginStart="@dimen/settings_help_icon_margin"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/tooltip_photos_during_audio_title"
        android:src="@android:drawable/ic_menu_help"
        android:tint="?android:attr/textColorSecondary" />
</LinearLayout>

<!-- Background photos source selector -->
<LinearLayout
    android:id="@+id/layoutPhotosSourceSelector"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:layout_marginStart="@dimen/settings_nested_margin_start"
    android:layout_marginEnd="@dimen/settings_margin_standard"
    android:layout_marginBottom="@dimen/margin_small"
    android:visibility="gone">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/audio_background_photos_source_label"
        android:textSize="@dimen/resource_card_desc_text_size"
        android:textColor="?android:textColorSecondary"
        android:layout_marginBottom="8dp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnSelectPhotosSource"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/select_photos_source"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:textAllCaps="false" />

    <TextView
        android:id="@+id/tvSelectedPhotosSource"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/no_photos_source_selected"
        android:textSize="@dimen/resource_card_desc_text_size"
        android:textColor="?android:textColorSecondary"
        android:layout_marginTop="4dp"
        android:paddingStart="16dp"
        android:paddingEnd="16dp" />
</LinearLayout>
```

1. В AudioSettingsFragment.kt, метод setupViews() (после блока "Search covers only on WiFi"):

```kotlin
// Enable photos during audio playback
binding.switchEnablePhotosDuringAudio.setOnCheckedChangeListener { _, isChecked ->
    if (!isUpdatingFromSettings) {
        val current = viewModel.settings.value
        viewModel.updateSettings(current.copy(enablePhotosDuringAudio = isChecked))
        binding.layoutPhotosSourceSelector.isVisible = isChecked
    }
}

// Select photos source button
binding.btnSelectPhotosSource.setOnClickListener {
    com.sza.fastmediasorter.ui.dialog.ResourcePickerDialog(
        context = requireContext(),
        lifecycleOwner = viewLifecycleOwner,
        getResourcesUseCase = viewModel.getResourcesUseCase,
        currentSelection = viewModel.settings.value.audioBackgroundPhotosResourceId?.toLongOrNull(),
        title = getString(com.sza.fastmediasorter.R.string.select_photos_source),
        allowClear = true,
        onResourceSelected = { resource ->
            val current = viewModel.settings.value
            val updated = current.copy(audioBackgroundPhotosResourceId = resource?.id?.toString())
            viewModel.updateSettings(updated)
        }
    ).show()
}

// Help icon
binding.iconHelpPhotosDuringAudio.setOnClickListener {
    com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
        requireContext(),
        com.sza.fastmediasorter.R.string.tooltip_photos_during_audio_title,
        com.sza.fastmediasorter.R.string.tooltip_photos_during_audio_message
    )
}
```

1. В AudioSettingsFragment.kt, метод observeData() (в конце, перед isUpdatingFromSettings = false):

```kotlin
// Photos during audio playback
binding.switchEnablePhotosDuringAudio.isChecked = settings.enablePhotosDuringAudio
binding.layoutPhotosSourceSelector.isVisible = settings.enablePhotosDuringAudio

// Update selected photos source text
if (settings.audioBackgroundPhotosResourceId != null) {
    viewLifecycleOwner.lifecycleScope.launch {
        val resourceId = settings.audioBackgroundPhotosResourceId.toLongOrNull()
        if (resourceId != null) {
            val resource = viewModel.resourceRepository.getResourceById(resourceId)
            binding.tvSelectedPhotosSource.text = resource?.name
                ?: getString(com.sza.fastmediasorter.R.string.resource_not_found)
        } else {
            binding.tvSelectedPhotosSource.setText(com.sza.fastmediasorter.R.string.no_photos_source_selected)
        }
    }
} else {
    binding.tvSelectedPhotosSource.setText(com.sza.fastmediasorter.R.string.no_photos_source_selected)
}
```

1. В strings.xml добавить:

```xml
<!-- Audio Background Photos -->
<string name="enable_photos_during_audio_description">Show random photos during audio playback</string>
<string name="audio_background_photos_source_label">Background photos source:</string>
<string name="select_photos_source">Select Photos Source</string>
<string name="no_photos_source_selected">No resource selected</string>
<string name="resource_not_found">Resource not found</string>
<string name="tooltip_photos_during_audio_title">Photos During Audio</string>
<string name="tooltip_photos_during_audio_message">When enabled, random photos from selected resource will be displayed during audio playback. Each song displays one random photo. Only static images are used (videos are ignored).</string>
```

Проверка:

1. Собери проект: `.\gradlew.bat assembleStandardDebug`
2. Открой Settings -> Media -> Audio
3. Проверь, что свич отображается и работает
4. При включении свича должен появиться selector
5. При нажатии на кнопку должен открыться ResourcePickerDialog

После успешной сборки - сделай коммит:
`git commit -m "feat: add UI for audio background photos settings"`

```

---

## ШАГ 2: Создание AudioBackgroundPhotosManager

### Цель:
Создать Manager для управления списком фото во время воспроизведения аудио.

### Файл для создания:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioBackgroundPhotosManager.kt`

### Prompt:

```markdown
Задача: Создать AudioBackgroundPhotosManager для управления фоновыми фотографиями во время воспроизведения аудио.

Контекст:
- Аналог: BackgroundMusicManager.kt (для музыки в слайдшоу)
- Архитектура: Singleton + Hilt DI + Clean Architecture
- Место: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/

Создай новый файл AudioBackgroundPhotosManager.kt:

```kotlin
package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for displaying random background photos during audio playback.
 * 
 * Logic:
 * - Activated only when audio file is playing AND settings.enablePhotosDuringAudio == true
 * - Loads photos from settings.audioBackgroundPhotosResourceId
 * - Filters only static images (excludes videos)
 * - Shuffles list randomly
 * - One song = One photo (circular navigation)
 */
@Singleton
class AudioBackgroundPhotosManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val getMediaFilesUseCase: GetMediaFilesUseCase
) {
    private var photosList: List<MediaFile> = emptyList()
    private var currentPhotoIndex: Int = 0
    private var isActive: Boolean = false
    
    // Current state tracking
    private var currentResourceId: String? = null
    private var enablePhotosDuringAudio: Boolean = false
    
    // Callback for UI updates
    private var onPhotoChangedListener: ((photo: MediaFile?) -> Unit)? = null
    private var onErrorListener: ((errorMessage: String) -> Unit)? = null
    
    // Coroutine scope for background loading
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadJob: Job? = null
    
    /**
     * Set listener for photo changes (for UI display)
     */
    fun setOnPhotoChangedListener(listener: ((photo: MediaFile?) -> Unit)?) {
        this.onPhotoChangedListener = listener
    }
    
    /**
     * Set listener for errors
     */
    fun setOnErrorListener(listener: ((errorMessage: String) -> Unit)?) {
        this.onErrorListener = listener
    }
    
    /**
     * Initialize manager (if needed)
     */
    fun initialize() {
        Timber.d("AudioBackgroundPhotos: Manager initialized")
    }
    
    /**
     * Update state from PlayerViewModel
     * Determines if feature should be active based on:
     * 1. Current file is AUDIO
     * 2. enablePhotosDuringAudio == true
     * 3. audioBackgroundPhotosResourceId is set
     */
    fun updateState(state: PlayerViewModel.PlayerState) {
        val currentFile = state.currentFile
        val resourceId = state.audioBackgroundPhotosResourceId
        val enabled = state.enablePhotosDuringAudio
        
        // Check if feature should be active
        val shouldBeActive = currentFile?.type == MediaType.AUDIO && enabled && resourceId != null
        
        Timber.d("AudioBackgroundPhotos: updateState - file=${currentFile?.name}, type=${currentFile?.type}, enabled=$enabled, resourceId=$resourceId, shouldBeActive=$shouldBeActive")
        
        if (shouldBeActive && resourceId != null) {
            // Feature should be active
            if (!isActive || currentResourceId != resourceId) {
                // First activation or resource changed - load photos
                Timber.d("AudioBackgroundPhotos: Activating feature, loading photos from resource $resourceId")
                loadPhotosPlaylist(resourceId)
                currentResourceId = resourceId
                isActive = true
            }
        } else {
            // Feature should be inactive
            if (isActive) {
                Timber.d("AudioBackgroundPhotos: Deactivating feature")
                deactivate()
            }
        }
        
        enablePhotosDuringAudio = enabled
    }
    
    /**
     * Load photos playlist from resource
     */
    private fun loadPhotosPlaylist(resourceId: String) {
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val resourceIdLong = resourceId.toLongOrNull()
                if (resourceIdLong == null) {
                    Timber.e("AudioBackgroundPhotos: Invalid resource ID: $resourceId")
                    withContext(Dispatchers.Main) {
                        onErrorListener?.invoke("Invalid resource ID")
                    }
                    return@launch
                }
                
                // Get resource
                val resource = resourceRepository.getResourceById(resourceIdLong)
                if (resource == null) {
                    Timber.e("AudioBackgroundPhotos: Resource not found: $resourceIdLong")
                    withContext(Dispatchers.Main) {
                        onErrorListener?.invoke("Resource not found")
                    }
                    return@launch
                }
                
                Timber.d("AudioBackgroundPhotos: Loading photos from resource: ${resource.name}")
                
                // Load all files from resource
                val allFiles = getMediaFilesUseCase.execute(
                    resourceId = resourceIdLong,
                    sortMode = SortMode.NAME_ASC
                )
                
                // Filter only static images (exclude GIFs and videos)
                val images = allFiles.filter { file ->
                    file.type == MediaType.IMAGE && !file.name.endsWith(".gif", ignoreCase = true)
                }
                
                if (images.isEmpty()) {
                    Timber.w("AudioBackgroundPhotos: No static images found in resource")
                    withContext(Dispatchers.Main) {
                        onErrorListener?.invoke("No photos found in selected resource")
                    }
                    return@launch
                }
                
                // Shuffle list
                photosList = images.shuffled()
                currentPhotoIndex = 0
                
                Timber.d("AudioBackgroundPhotos: Loaded and shuffled ${photosList.size} photos")
                
                // Notify UI with first photo
                withContext(Dispatchers.Main) {
                    onPhotoChangedListener?.invoke(getCurrentPhoto())
                }
                
            } catch (e: Exception) {
                Timber.e(e, "AudioBackgroundPhotos: Error loading photos playlist")
                withContext(Dispatchers.Main) {
                    onErrorListener?.invoke("Error loading photos: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Get current photo for display
     */
    fun getCurrentPhoto(): MediaFile? {
        if (photosList.isEmpty()) return null
        return photosList.getOrNull(currentPhotoIndex)
    }
    
    /**
     * Advance to next photo (called on audio track change)
     */
    fun advanceToNextPhoto() {
        if (photosList.isEmpty()) {
            Timber.w("AudioBackgroundPhotos: Cannot advance - no photos loaded")
            return
        }
        
        // Circular increment
        currentPhotoIndex = (currentPhotoIndex + 1) % photosList.size
        
        val nextPhoto = getCurrentPhoto()
        Timber.d("AudioBackgroundPhotos: Advanced to photo ${currentPhotoIndex + 1}/${photosList.size}: ${nextPhoto?.name}")
        
        // Notify UI
        onPhotoChangedListener?.invoke(nextPhoto)
    }
    
    /**
     * Deactivate feature and clear state
     */
    private fun deactivate() {
        loadJob?.cancel()
        photosList = emptyList()
        currentPhotoIndex = 0
        currentResourceId = null
        isActive = false
        
        // Notify UI to clear photo
        onPhotoChangedListener?.invoke(null)
        
        Timber.d("AudioBackgroundPhotos: Deactivated and cleared")
    }
    
    /**
     * Release resources
     */
    fun release() {
        loadJob?.cancel()
        photosList = emptyList()
        onPhotoChangedListener = null
        onErrorListener = null
        isActive = false
        Timber.d("AudioBackgroundPhotos: Released")
    }
}
```

Требования:

1. Используй ТОЛЬКО Timber для логирования (не Log.d)
2. Все загрузки файлов через Dispatchers.IO
3. Callback в Main thread через withContext(Dispatchers.Main)
4. Обработай все ошибки (try-catch)
5. Фильтруй только MediaType.IMAGE и исключай .gif файлы

Проверка:

1. Собери проект: `.\gradlew.bat assembleStandardDebug`
2. Проверь, что нет ошибок компиляции

После успешной сборки - сделай коммит:
`git commit -m "feat: add AudioBackgroundPhotosManager for photo slideshow during audio"`

```

---

## ШАГ 3: Интеграция Manager в PlayerViewModel

### Цель:
Добавить поля состояния в PlayerViewModel для передачи настроек в Manager.

### Файл для изменения:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`

### Prompt:

```markdown
Задача: Добавить поддержку настроек "Фото во время аудио" в PlayerViewModel.

Контекст:
- PlayerViewModel.PlayerState уже содержит slideshow-настройки (строки 45-49)
- Нужно добавить аналогичные поля для audio background photos

Реализация:

1. В PlayerViewModel.kt, в data class PlayerState (после строки 49 - enableSlideshowBackgroundMusic):

```kotlin
val enablePhotosDuringAudio: Boolean = false,
val audioBackgroundPhotosResourceId: String? = null,
```

1. В методе loadSettings() (после строки ~139 - slideshowMusicResourceId):

```kotlin
enablePhotosDuringAudio = settings.enablePhotosDuringAudio,
audioBackgroundPhotosResourceId = settings.audioBackgroundPhotosResourceId,
```

Проверка:

1. Собери проект: `.\gradlew.bat assembleStandardDebug`
2. Проверь, что нет ошибок компиляции

После успешной сборки - сделай коммит:
`git commit -m "feat: add audio background photos state to PlayerViewModel"`

```

---

## ШАГ 4: Интеграция Manager в PlayerActivity

### Цель:
Подключить AudioBackgroundPhotosManager к PlayerActivity и обработать загрузку фото.

### Файл для изменения:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`

### Prompt:

```markdown
Задача: Интегрировать AudioBackgroundPhotosManager в PlayerActivity.

Контекст:
- PlayerActivity использует 30+ Manager-классов
- Аналог: BackgroundMusicManager (инжекция + lifecycle)
- ExoPlayer listener для onMediaItemTransition уже существует

Реализация:

1. Найди (grep) строку с `@Inject lateinit var backgroundMusicManager: BackgroundMusicManager`
   
   ДОБАВЬ СРАЗУ ПОСЛЕ:
```kotlin
@Inject
lateinit var audioBackgroundPhotosManager: AudioBackgroundPhotosManager
```

1. Найди метод onCreate(), блок где вызывается `backgroundMusicManager.initialize()`

   ДОБАВЬ СРАЗУ ПОСЛЕ:

```kotlin
// Initialize audio background photos manager
audioBackgroundPhotosManager.initialize()
audioBackgroundPhotosManager.setOnPhotoChangedListener { photo ->
    lifecycleScope.launch(Dispatchers.Main) {
        if (photo != null) {
            loadBackgroundPhotoIntoImageView(photo)
        }
    }
}
audioBackgroundPhotosManager.setOnErrorListener { errorMessage ->
    lifecycleScope.launch(Dispatchers.Main) {
        Toast.makeText(this@PlayerActivity, errorMessage, Toast.LENGTH_SHORT).show()
    }
}
```

1. Найди метод observeState() (где `combine(viewModel.state, ...)`), в блоке .collect { ... }

   НАЙДИ строку с `backgroundMusicManager.updateState(state)`

   ДОБАВЬ СРАЗУ ПОСЛЕ:

```kotlin
// Update audio background photos manager
audioBackgroundPhotosManager.updateState(state)
```

1. Создай новый метод (добавь ПЕРЕД методом onDestroy()):

```kotlin
/**
 * Load background photo into ImageView during audio playback
 */
private fun loadBackgroundPhotoIntoImageView(photo: MediaFile) {
    Timber.d("PlayerActivity: Loading background photo: ${photo.name}")
    
    try {
        when (photo.resource.type) {
            ResourceType.LOCAL -> {
                // Local file
                val file = java.io.File(photo.path)
                Glide.with(this)
                    .load(file)
                    .priority(Priority.HIGH)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(binding.imageView)
            }
            ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> {
                // Network file
                val networkData = NetworkFileData(
                    resource = photo.resource,
                    filePath = photo.path
                )
                Glide.with(this)
                    .load(networkData)
                    .priority(Priority.HIGH)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .signature(ObjectKey("${photo.path}_${photo.modifiedDate}"))
                    .into(binding.imageView)
            }
            else -> {
                Timber.w("PlayerActivity: Unsupported resource type for background photo: ${photo.resource.type}")
            }
        }
        
        // Optional: Update top text with photo name (or keep audio file name)
        // binding.textCurrentFileName.text = photo.name
        
    } catch (e: Exception) {
        Timber.e(e, "PlayerActivity: Error loading background photo")
    }
}
```

1. Найди ExoPlayer listener onMediaItemTransition (grep "onMediaItemTransition")

   ДОБАВЬ в конце метода (перед закрывающей скобкой):

```kotlin
// Advance to next photo if audio background photos are active
if (viewModel.state.value.currentFile?.type == MediaType.AUDIO) {
    audioBackgroundPhotosManager.advanceToNextPhoto()
}
```

1. Найди метод onDestroy(), ДОБАВЬ перед `super.onDestroy()`:

```kotlin
audioBackgroundPhotosManager.release()
```

Проверка:

1. Собери проект: `.\gradlew.bat assembleStandardDebug`
2. Установи APK на устройство
3. Включи настройку "Show photos during audio"
4. Выбери папку с фото
5. Открой аудиофайл -> должно показать случайное фото
6. Next/Prev -> фото должно меняться

После успешной сборки - сделай коммит:
`git commit -m "feat: integrate AudioBackgroundPhotosManager into PlayerActivity"`

```

---

## ШАГ 5: Финальная Проверка и Тестирование

### Prompt:

```markdown
Задача: Протестировать фичу "Фото во время воспроизведения музыки".

Тестовый сценарий:

1. ✅ Открой Settings -> Media -> Audio
2. ✅ Проверь наличие переключателя "Show random photos during audio playback"
3. ✅ По умолчанию должен быть ВЫКЛЮЧЕН
4. ✅ Включи переключатель -> должен появиться селектор папки
5. ✅ Нажми "Select Photos Source" -> должен открыться ResourcePickerDialog
6. ✅ Выбери папку с фотографиями -> имя папки должно отобразиться
7. ✅ Открой любой аудиофайл в плеере:
   - Должно показаться случайное фото из выбранной папки
   - Сверху: имя аудиофайла (или имя фото - по желанию)
   - Снизу: панель управления плеером
8. ✅ Нажми Next -> фото должно смениться на другое случайное
9. ✅ Нажми Prev -> фото должно смениться
10. ✅ После последнего трека -> цикл с первого фото
11. ✅ Открой видеофайл -> фичу НЕ должна активироваться
12. ✅ Открой изображение -> должно работать стандартное слайдшоу (если включено)
13. ✅ Выключи переключатель -> при воспроизведении аудио не должно показываться фото

Логи для проверки:
- Ищи в Logcat: "AudioBackgroundPhotos:"
- Должны быть логи загрузки, shuffle, смены фото

Известные проблемы и решения:
- Если фото не загружается - проверь права доступа к папке
- Если фото не меняется - проверь ExoPlayer listener
- Если не работает для сетевых папок - проверь Glide ModelLoader

После успешного тестирования - сделай итоговый коммит:
`git commit -m "feat: complete audio background photos feature - tested and verified"`
```

---

## ИТОГОВЫЙ ЧЕКЛИСТ

- [ ] Шаг 1: UI в AudioSettingsFragment + XML + strings.xml
- [ ] Сборка: `.\gradlew.bat assembleStandardDebug`
- [ ] Коммит: "feat: add UI for audio background photos settings"
- [ ] Шаг 2: Создан AudioBackgroundPhotosManager.kt
- [ ] Сборка
- [ ] Коммит: "feat: add AudioBackgroundPhotosManager for photo slideshow during audio"
- [ ] Шаг 3: PlayerViewModel.PlayerState обновлён
- [ ] Сборка
- [ ] Коммит: "feat: add audio background photos state to PlayerViewModel"
- [ ] Шаг 4: Интеграция в PlayerActivity (injection, lifecycle, ExoPlayer listener)
- [ ] Сборка
- [ ] Коммит: "feat: integrate AudioBackgroundPhotosManager into PlayerActivity"
- [ ] Шаг 5: Полное тестирование по сценарию
- [ ] Коммит: "feat: complete audio background photos feature - tested and verified"

---

## ТЕХНИЧЕСКИЕ ДЕТАЛИ

### Архитектурные паттерны

- **Manager Pattern**: вся логика в `AudioBackgroundPhotosManager.kt`
- **Hilt DI**: `@Singleton` + инжекция в Activity
- **Clean Architecture**: UseCase для загрузки файлов, Repository для ресурсов
- **MVVM**: состояние через `PlayerViewModel.PlayerState`

### Зависимости

- `GetMediaFilesUseCase` - загрузка файлов из ресурса
- `ResourceRepository` - получение информации о ресурсе
- Glide - загрузка изображений (local + network)

### Логирование

- **ТОЛЬКО Timber** (`Timber.d`, `Timber.e`, `Timber.w`)
- **НЕ использовать** `Log.d()`, `Log.e()` и т.д.

### Корутины

- Загрузка файлов: `Dispatchers.IO`
- UI обновления: `Dispatchers.Main` (через `withContext`)
- Scope: `SupervisorJob()` для изоляции ошибок

---

**Конец плана.**
