# Спецификация: Кэширование списка файлов ресурса

## Обзор задачи

Реализовать новую опцию "Запоминать список файлов" для ускорения открытия ресурсов с большим количеством файлов путем сохранения списка файлов в базе данных вместо повторного сканирования при каждом открытии.

## Бизнес-требования

### Проблема
При открытии ресурса с большим количеством файлов (например, библиотека музыки или изображений) происходит полное сканирование директории, что занимает время, особенно для сетевых ресурсов.

### Решение  
Опциональное кэширование списка файлов в базе данных. При включенной опции:
- ✅ **Первое сканирование**: сохраняется в БД
- ✅ **Последующие открытия**: загружается из БД мгновенно
- ✅ **Внутренние операции** (rename/delete/move): автоматически обновляют кэш
- ⚠️ **Внешние изменения**: не отслеживаются автоматически
- 🔄 **Ручное обновление**: кнопка "Обновить" в BrowseActivity

---

## 1. Обновление базы данных

### 1.1. Новая таблица: `cached_file_lists`

Создать новую Entity для хранения кэшированных списков файлов.

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/CachedFileListEntity.kt`

```kotlin
package com.sza.fastmediasorter.data.local.db

import androidx.room.*
import com.sza.fastmediasorter.domain.model.MediaFile

@Entity(
    tableName = "cached_file_lists",
    indices = [
        Index(value = ["resourceId"], name = "idx_cached_files_resource_id")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.CASCADE // При удалении ресурса удаляются его cached files
        )
    ]
)
data class CachedFileListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val resourceId: Long, // FK to resources.id
    
    // Сериализованный MediaFile в JSON
    // Хранит все данные необходимые для отображения: path, name, size, modifiedDate, mediaType, extension, thumbnailPath etc.
    @ColumnInfo(name = "media_file_json")
    val mediaFileJson: String, // JSON representation of MediaFile
    
    // Дата добавления записи (для отладки)
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

### 1.2. DAO для cached_file_lists

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/CachedFileListDao.kt`

```kotlin
package com.sza.fastmediasorter.data.local.db

import androidx.room.*

@Dao
interface CachedFileListDao {
    
    @Query("SELECT * FROM cached_file_lists WHERE resourceId = :resourceId ORDER BY id ASC")
    suspend fun getCachedFiles(resourceId: Long): List<CachedFileListEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<CachedFileListEntity>)
    
    @Query("DELETE FROM cached_file_lists WHERE resourceId = :resourceId")
    suspend fun deleteByResourceId(resourceId: Long)
    
    @Query("DELETE FROM cached_file_lists")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM cached_file_lists WHERE resourceId = :resourceId")
    suspend fun getCount(resourceId: Long): Int
    
    // Метод для обновления одного файла (rename)
    @Query("UPDATE cached_file_lists SET media_file_json = :newMediaFileJson WHERE resourceId = :resourceId AND media_file_json LIKE '%' || :oldPath || '%'")
    suspend fun updateFile(resourceId: Long, oldPath: String, newMediaFileJson: String): Int
    
    // Метод для удаления файла
    @Query("DELETE FROM cached_file_lists WHERE resourceId = :resourceId AND media_file_json LIKE '%' || :filePath || '%'")
    suspend fun deleteFile(resourceId: Long, filePath: String): Int
}
```

### 1.3. Обновление ResourceEntity

Добавить новое поле `rememberFileList` в ResourceEntity.

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt`

```kotlin
// Добавить в data class ResourceEntity:
val rememberFileList: Boolean = false, // Запоминать список файлов в БД (по умолчанию выключено)
```

### 1.4. Обновление AppSettings

Добавить глобальную настройку в AppSettings (дефолтное значение для новых ресурсов).

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`

```kotlin
// Добавить в data class AppSettings:
val defaultRememberFileList: Boolean = false, // Глобальная настройка: запоминать список файлов для новых ресурсов
```

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt`

```kotlin
// В companion object добавить:
private val DEFAULT_REMEMBER_FILE_LIST = booleanPreferencesKey("default_remember_file_list")

// В settings Flow добавить:
defaultRememberFileList = preferences[DEFAULT_REMEMBER_FILE_LIST] ?: false,

// Добавить setter:
suspend fun setDefaultRememberFileList(value: Boolean) {
    dataStore.edit { preferences ->
        preferences[DEFAULT_REMEMBER_FILE_LIST] = value
    }
}
```

### 1.5. Миграция базы данных (MIGRATION_7_8)

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`

```kotlin
// Изменить version на 8
@Database(
    entities = [
        ResourceEntity::class,
        NetworkCredentialsEntity::class,
        ResourceFtsEntity::class,
        FavoritesEntity::class,
        PlaybackPositionEntity::class,
        ThumbnailCacheEntity::class,
        CachedFileListEntity::class // НОВАЯ ENTITY
    ],
    version = 8, // Было 7
    exportSchema = false
)

// Добавить DAO
abstract fun cachedFileListDao(): CachedFileListDao

// Добавить миграцию в companion object
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Добавить поле rememberFileList в resources
        db.execSQL("ALTER TABLE resources ADD COLUMN rememberFileList INTEGER NOT NULL DEFAULT 0")
        
        // 2. Создать новую таблицу cached_file_lists
        db.execSQL("""
            CREATE TABLE cached_file_lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                resourceId INTEGER NOT NULL,
                media_file_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(resourceId) REFERENCES resources(id) ON DELETE CASCADE
            )
        """)
        
        // 3. Создать индекс
        db.execSQL("CREATE INDEX idx_cached_files_resource_id ON cached_file_lists (resourceId)")
    }
}
```

**В DatabaseModule.kt** добавить миграцию в `addMigrations()`:
```kotlin
.addMigrations(
    AppDatabase.MIGRATION_1_2,
    AppDatabase.MIGRATION_2_3,
    AppDatabase.MIGRATION_3_4,
    AppDatabase.MIGRATION_4_5,
    AppDatabase.MIGRATION_5_6,
    AppDatabase.MIGRATION_6_7,
    AppDatabase.MIGRATION_7_8 // НОВАЯ МИГРАЦИЯ
)
```

---

## 2. Обновление UI: GeneralSettingsFragment

### 2.1. Глобальная настройка

Добавить чекбокс в секцию "Файлы и Данные" (Files and Data).

**Файл**: `app_v2/src/main/res/layout/fragment_settings_general.xml`

Найти секцию `containerFiles` и добавить после существующих настроек:

```xml
<!-- Remember File List (Global Default) -->
<com.google.android.material.materialswitch.MaterialSwitch
    android:id="@+id/switchDefaultRememberFileList"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:layout_marginStart="16dp"
    android:layout_marginEnd="16dp"
    android:text="@string/default_remember_file_list"
    android:textSize="16sp"
    app:layout_constraintTop_toBottomOf="@id/previousElement"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent" />

<!-- Help Button for Remember File List -->
<ImageButton
    android:id="@+id/btnHelpRememberFileList"
    android:layout_width="40dp"
    android:layout_height="40dp"
    android:layout_marginEnd="8dp"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:src="@drawable/ic_help_outline"
    android:contentDescription="@string/help_remember_file_list"
    app:layout_constraintEnd_toEndOf="@id/switchDefaultRememberFileList"
    app:layout_constraintTop_toTopOf="@id/switchDefaultRememberFileList"
    app:layout_constraintBottom_toBottomOf="@id/switchDefaultRememberFileList"
    app:tint="?attr/colorPrimary" />
```

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`

```kotlin
// В методе observeData() добавить:
binding.switchDefaultRememberFileList.isChecked = settings.defaultRememberFileList

// В методе setupViews() добавить:
binding.switchDefaultRememberFileList.setOnCheckedChangeListener { _, isChecked ->
    if (!isUpdatingUi) {
        val current = viewModel.settings.value
        viewModel.updateSettings(current.copy(defaultRememberFileList = isChecked))
    }
}

binding.btnHelpRememberFileList.setOnClickListener {
    showRememberFileListHelpDialog()
}

// Добавить метод для показа Help-диалога
private fun showRememberFileListHelpDialog() {
    androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle(R.string.remember_file_list_help_title)
        .setMessage(R.string.remember_file_list_help_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
```

---

## 3. Обновление UI: ResourceEditorFragment

### 3.1. Добавление чекбокса в Resource Editor

**Файл**: `app_v2/src/main/res/layout/fragment_resource_editor.xml`

Добавить в секцию "Scanning Settings" (после scanSubdirectories):

```xml
<!-- Remember File List -->
<com.google.android.material.materialswitch.MaterialSwitch
    android:id="@+id/switchRememberFileList"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:layout_marginStart="16dp"
    android:layout_marginEnd="8dp"
    android:text="@string/remember_file_list"
    android:textSize="16sp"
    app:layout_constraintTop_toBottomOf="@id/switchScanSubdirectories"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toStartOf="@id/btnHelpRememberFileListEditor" />

<ImageButton
    android:id="@+id/btnHelpRememberFileListEditor"
    android:layout_width="40dp"
    android:layout_height="40dp"
    android:layout_marginEnd="16dp"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:src="@drawable/ic_help_outline"
    android:contentDescription="@string/help_remember_file_list"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintTop_toTopOf="@id/switchRememberFileList"
    app:layout_constraintBottom_toBottomOf="@id/switchRememberFileList"
    app:tint="?attr/colorPrimary" />
```

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt`

```kotlin
// В методе setupFieldListeners() добавить:
binding.switchRememberFileList.setOnCheckedChangeListener { _, isChecked ->
    viewModel.updateField(ResourceFieldKey.REMEMBER_FILE_LIST, isChecked)
}

binding.btnHelpRememberFileListEditor.setOnClickListener {
    showRememberFileListHelpDialog()
}

// В методе renderFormData() добавить:
binding.switchRememberFileList.isChecked = formData.rememberFileList

// Добавить метод
private fun showRememberFileListHelpDialog() {
    androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle(R.string.remember_file_list_help_title)
        .setMessage(R.string.remember_file_list_help_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
```

### 3.2. Обновление ResourceFormData

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceFormData.kt`

```kotlin
// Добавить в data class:
val rememberFileList: Boolean = false, // Значение для поля rememberFileList
```

### 3.3. Обновление ResourceFieldKey

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceFieldKey.kt`

```kotlin
enum class ResourceFieldKey {
    // ... existing keys ...
    REMEMBER_FILE_LIST, // Новый ключ
}
```

### 3.4. Обновление ResourceFormViewModel

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt`

```kotlin
// При инициализации формы (CREATE mode) установить значение из глобальных настроек:
private fun initializeCreateForm(type: ResourceType) {
    val defaultRememberFileList = settingsRepository.getSettings().first().defaultRememberFileList
    
    _formData.update { current ->
        current.copy(
            // ... other fields ...
            rememberFileList = defaultRememberFileList, // Берем из глобальных настроек
        )
    }
}

// При загрузке существующего ресурса (EDIT mode):
private suspend fun loadExistingResource(resourceId: Long) {
    val resource = resourceRepository.getResourceById(resourceId)
    
    _formData.update {
        ResourceFormData(
            // ... other fields ...
            rememberFileList = resource.rememberFileList,
        )
    }
}

// В методе updateField обрабатывать новый ключ:
when (key) {
    ResourceFieldKey.REMEMBER_FILE_LIST -> {
        _formData.update { it.copy(rememberFileList = value as Boolean) }
    }
    // ... other cases ...
}

// При сохранении ресурса передавать значение в entity:
private suspend fun saveResource() {
    val entity = ResourceEntity(
        // ... other fields ...
        rememberFileList = formData.value.rememberFileList,
    )
    resourceRepository.insertOrUpdateResource(entity)
}
```

---

## 4. Логика кэширования файлов

### 4.1. Создание Repository для кэшированных файлов

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt`

```kotlin
package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.CachedFileListEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedFileListRepository @Inject constructor(
    private val cachedFileListDao: CachedFileListDao,
    private val json: Json
) {
    
    /**
     * Сохранить список файлов для ресурса в БД
     */
    suspend fun saveCachedFiles(resourceId: Long, files: List<MediaFile>) {
        try {
            // Сначала удалить старые записи
            cachedFileListDao.deleteByResourceId(resourceId)
            
            // Конвертировать MediaFile в Entity
            val entities = files.map { mediaFile ->
                CachedFileListEntity(
                    resourceId = resourceId,
                    mediaFileJson = json.encodeToString(mediaFile)
                )
            }
            
            // Сохранить в БД
            cachedFileListDao.insertAll(entities)
            Timber.d("CachedFileList: Saved ${files.size} files for resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: Failed to save files for resource $resourceId")
            throw e
        }
    }
    
    /**
     * Загрузить список файлов для ресурса из БД
     */
    suspend fun getCachedFiles(resourceId: Long): List<MediaFile>? {
        return try {
            val entities = cachedFileListDao.getCachedFiles(resourceId)
            if (entities.isEmpty()) {
                Timber.d("CachedFileList: No cached files found for resource $resourceId")
                return null
            }
            
            val files = entities.mapNotNull { entity ->
                try {
                    json.decodeFromString<MediaFile>(entity.mediaFileJson)
                } catch (e: Exception) {
                    Timber.e(e, "CachedFileList: Failed to parse MediaFile JSON")
                    null
                }
            }
            
            Timber.d("CachedFileList: Loaded ${files.size} files for resource $resourceId")
            files
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: Failed to load files for resource $resourceId")
            null
        }
    }
    
    /**
     * Удалить кэшированные файлы для ресурса
     */
    suspend fun deleteCachedFiles(resourceId: Long) {
        cachedFileListDao.deleteByResourceId(resourceId)
        Timber.d("CachedFileList: Deleted cached files for resource $resourceId")
    }
    
    /**
     * Удалить ВСЕ кэшированные файлы (вызывается при "Очистить кэш")
     */
    suspend fun deleteAllCachedFiles() {
        cachedFileListDao.deleteAll()
        Timber.d("CachedFileList: Deleted ALL cached files")
    }
    
    /**
     * Проверить, есть ли кэшированные файлы для ресурса
     */
    suspend fun hasCachedFiles(resourceId: Long): Boolean {
        return cachedFileListDao.getCount(resourceId) > 0
    }
    
    /**
     * Обновить файл после переименования
     */
    suspend fun updateFile(resourceId: Long, oldPath: String, newFile: MediaFile) {
        try {
            val newJson = json.encodeToString(newFile)
            cachedFileListDao.updateFile(resourceId, oldPath, newJson)
            Timber.d("CachedFileList: Updated file $oldPath -> ${newFile.path} in resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: Failed to update file in resource $resourceId")
        }
    }
    
    /**
     * Удалить файл из кэша (после delete/move)
     */
    suspend fun deleteFile(resourceId: Long, filePath: String) {
        try {
            cachedFileListDao.deleteFile(resourceId, filePath)
            Timber.d("CachedFileList: Deleted file $filePath from resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: Failed to delete file from resource $resourceId")
        }
    }
}
```

> **Примечание**: Для сериализации MediaFile используется kotlinx.serialization. Если MediaFile еще не имеет @Serializable аннотации, добавить её.

### 4.2. Обновление BrowseViewModel

Интегрировать логику загрузки из кэша или сканирования.

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`

```kotlin
@Inject lateinit var cachedFileListRepository: CachedFileListRepository

// Изменить метод сканирования файлов
private suspend fun scanFilesInternal(forceRescan: Boolean = false) {
    val resource = _resource.value ?: return
    
    // Проверить, включен ли режим "Запоминать список файлов"
    val shouldUseCache = resource.rememberFileList && !forceRescan
    
    if (shouldUseCache) {
        Timber.d("BrowseViewModel: Trying to load cached files for resource ${resource.id}")
        val cachedFiles = cachedFileListRepository.getCachedFiles(resource.id)
        
        if (cachedFiles != null && cachedFiles.isNotEmpty()) {
            Timber.i("BrowseViewModel: Loaded ${cachedFiles.size} files from cache for resource ${resource.id}")
            
            // Использовать кэшированный список
            _allMediaFiles.value = cachedFiles.toMutableList()
            applyFiltersAndSort()
            
            // Обновить статистику ресурса
            updateResourceStatistics(cachedFiles.size)
            
            _isLoading.value = false
            return
        } else {
            Timber.d("BrowseViewModel: No cached files found, will perform scan")
        }
    }
    
    // Если кэш не используется или пуст - сканировать как обычно
    Timber.d("BrowseViewModel: Scanning files for resource ${resource.id}")
    
    val scannedFiles = performActualScan() // Существующая логика сканирования
    
    // Сохранить в кэш если опция включена
    if (resource.rememberFileList) {
        try {
            cachedFileListRepository.saveCachedFiles(resource.id, scannedFiles)
            Timber.i("BrowseViewModel: Saved ${scannedFiles.size} files to cache for resource ${resource.id}")
        } catch (e: Exception) {
            Timber.e(e, "BrowseViewModel: Failed to save files to cache")
        }
    }
    
    _allMediaFiles.value = scannedFiles.toMutableList()
    applyFiltersAndSort()
    updateResourceStatistics(scannedFiles.size)
    _isLoading.value = false
}

// Метод для принудительного обновления (вызывается кнопкой "Обновить")
fun forceRescan() {
    viewModelScope.launch {
        _isLoading.value = true
        scanFilesInternal(forceRescan = true)
    }
}

// При операциях с файлами обновлять кэш
suspend fun onFileRenamed(oldPath: String, newFile: MediaFile) {
    val resource = _resource.value ?: return
    
    // Обновить в памяти
    updateFileInList(oldPath, newFile)
    
    // Обновить в MediaFilesCacheManager (существующая логика)
    MediaFilesCacheManager.updateFile(resource.id, oldPath, newFile)
    
    // Обновить в DB-кэше
    if (resource.rememberFileList) {
        cachedFileListRepository.updateFile(resource.id, oldPath, newFile)
    }
}

suspend fun onFileDeleted(filePath: String) {
    val resource = _resource.value ?: return
    
    // Удалить из памяти
    removeFileFromList(filePath)
    
    // Удалить из MediaFilesCacheManager (существующая логика)
    MediaFilesCacheManager.removeFile(resource.id, filePath)
    
    // Удалить из DB-кэша
    if (resource.rememberFileList) {
        cachedFileListRepository.deleteFile(resource.id, filePath)
    }
    
    // Обновить статистику
    updateResourceStatistics(_allMediaFiles.value.size)
}

suspend fun onFileMoved(filePath: String, fromResourceId: Long, toResourceId: Long) {
    // Удалить из источника
    if (_resource.value?.id == fromResourceId) {
        removeFileFromList(filePath)
        MediaFilesCacheManager.removeFile(fromResourceId, filePath)
        
        val fromResource = resourceRepository.getResourceById(fromResourceId)
        if (fromResource.rememberFileList) {
            cachedFileListRepository.deleteFile(fromResourceId, filePath)
        }
    }
    
    // Добавить в целевой ресурс (если это текущий ресурс)
    if (_resource.value?.id == toResourceId) {
        // Пересканировать или обновить список
        forceRescan()
    }
}
```

---

## 5. Обработка несуществующих файлов

При попытке открыть файл, который был удален вне приложения, показать дружелюбное сообщение и обновить кэш.

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` (или соответствующий класс проигрывания)

```kotlin
// При попытке открыть файл:
private fun openMediaFile(mediaFile: MediaFile) {
    try {
        // Проверить существование файла
        if (!checkFileExists(mediaFile.path)) {
            handleMissingFile(mediaFile)
            return
        }
        
        // Открыть файл
        // ... existing playback logic ...
    } catch (e: FileNotFoundException) {
        handleMissingFile(mediaFile)
    }
}

private fun handleMissingFile(mediaFile: MediaFile) {
    Timber.w("PlayerActivity: File not found: ${mediaFile.path}")
    
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(R.string.file_not_found_title)
        .setMessage(getString(R.string.file_not_found_message, mediaFile.name))
        .setPositiveButton(R.string.refresh_resource) { _, _ ->
            // Удалить файл из кэша и обновить ресурс
            lifecycleScope.launch {
                viewModel.onFileMissingFromDisk(mediaFile)
                viewModel.forceRescan()
                finish() // Вернуться в BrowseActivity
            }
        }
        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
            finish()
        }
        .show()
}
```

**В BrowseViewModel добавить**:

```kotlin
suspend fun onFileMissingFromDisk(mediaFile: MediaFile) {
    val resource = _resource.value ?: return
    
    Timber.w("BrowseViewModel: File missing from disk: ${mediaFile.path}")
    
    // Удалить из всех кэшей
    removeFileFromList(mediaFile.path)
    MediaFilesCacheManager.removeFile(resource.id, mediaFile.path)
    
    if (resource.rememberFileList) {
        cachedFileListRepository.deleteFile(resource.id, mediaFile.path)
    }
    
    // Обновить статистику
    updateResourceStatistics(_allMediaFiles.value.size)
}
```

---

## 6. Интеграция с "Очистить кэш"

При нажатии "Очистить кэш" в настройках удалять все сохраненные списки файлов.

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`

В методе `clearCache()` добавить:

```kotlin
// 4. Clear MediaFilesCacheManager (cached file lists for resources)
try {
    com.sza.fastmediasorter.core.cache.MediaFilesCacheManager.clearAllCaches()
    Timber.d("Cleared MediaFilesCacheManager (resource file lists)")
} catch (e: Exception) {
    Timber.e(e, "Failed to clear MediaFilesCacheManager")
}

// НОВЫЙ КОД: 5. Clear DB-cached file lists
try {
    val app = requireActivity().application as com.sza.fastmediasorter.FastMediaSorterApp
    app.cachedFileListRepository.deleteAllCachedFiles()
    Timber.d("Cleared CachedFileListRepository (DB-cached file lists)")
} catch (e: Exception) {
    Timber.e(e, "Failed to clear CachedFileListRepository")
}

// Позже идут playback positions...
```

---

## 7. Локализация (strings.xml)

### 7.1. English (values/strings.xml)

```xml
<!-- Remember File List Feature -->
<string name="default_remember_file_list">Remember file lists (default for new resources)</string>
<string name="remember_file_list">Remember file list for this resource</string>
<string name="help_remember_file_list">Help: Remember File List</string>
<string name="remember_file_list_help_title">Remember File List</string>
<string name="remember_file_list_help_message"><![CDATA[
<b>What it does:</b><br/>
When enabled, the app saves the list of files in the database after the first scan. On subsequent openings, the list loads instantly from the database instead of scanning the folder again.<br/><br/>

<b>✅ Advantages:</b><br/>
• Faster opening of resources with many files<br/>
• Ideal for stable libraries (music, photos) that rarely change<br/>
• Works great with network resources (SMB, FTP, SFTP)<br/><br/>

<b>⚠️ Disadvantages:</b><br/>
• External changes (files added/deleted outside the app) won\'t be detected automatically<br/>
• Requires manual refresh (tap \"Refresh\" button) to see external changes<br/><br/>

<b>📌 Note:</b><br/>
• Internal operations (rename, delete, move within the app) update the cached list automatically<br/>
• If the app encounters a missing file during playback, it will notify you and suggest refreshing the resource<br/><br/>

<b>💡 Recommended for:</b><br/>
Large, stable libraries where file changes are rare<br/><br/>

<b>❌ Not recommended for:</b><br/>
Frequently changing folders (e.g., document folders, download folders)
]]></string>

<!-- File Not Found Dialog -->
<string name="file_not_found_title">File Not Found</string>
<string name="file_not_found_message">The file \"%1$s\" no longer exists.\n\nIt may have been deleted or moved outside the app. Would you like to refresh the resource to update the file list?</string>
<string name="refresh_resource">Refresh Resource</string>
```

### 7.2. Russian (values-ru/strings.xml)

```xml
<!-- Remember File List Feature -->
<string name="default_remember_file_list">Запоминать списки файлов (по умолчанию для новых ресурсов)</string>
<string name="remember_file_list">Запоминать список файлов для этого ресурса</string>
<string name="help_remember_file_list">Справка: Запоминать список файлов</string>
<string name="remember_file_list_help_title">Запоминать список файлов</string>
<string name="remember_file_list_help_message"><![CDATA[
<b>Что это делает:</b><br/>
При включении приложение сохраняет список файлов в базе данных после первого сканирования. При последующих открытиях список загружается мгновенно из базы данных вместо повторного сканирования папки.<br/><br/>

<b>✅ Преимущества:</b><br/>
• Быстрое открытие ресурсов с большим количеством файлов<br/>
• Идеально для стабильных библиотек (музыка, фото), которые редко меняются<br/>
• Отлично работает с сетевыми ресурсами (SMB, FTP, SFTP)<br/><br/>

<b>⚠️ Недостатки:</b><br/>
• Внешние изменения (файлы добавлены/удалены вне приложения) не будут обнаружены автоматически<br/>
• Требуется ручное обновление (кнопка \"Обновить\") для отображения внешних изменений<br/><br/>

<b>📌 Примечание:</b><br/>
• Внутренние операции (перейменування, удаление, перемещение в приложении) автоматически обновляют кэшированный список<br/>
• Если приложение обнаружит отсутствующий файл во время воспроизведения, оно уведомит вас и предложит обновить ресурс<br/><br/>

<b>💡 Рекомендуется для:</b><br/>
Больших, стабильных библиотек, где изменения файлов редки<br/><br/>

<b>❌ Не рекомендуется для:</b><br/>
Часто изменяющихся папок (например, папка документов, папка загрузок)
]]></string>

<!-- File Not Found Dialog -->
<string name="file_not_found_title">Файл не найден</string>
<string name="file_not_found_message">Файл \"%1$s\" больше не существует.\n\nВозможно, он был удален или перемещен вне приложения. Хотите обновить ресурс, чтобы актуализировать список файлов?</string>
<string name="refresh_resource">Обновить ресурс</string>
```

### 7.3. Ukrainian (values-uk/strings.xml)

```xml
<!-- Remember File List Feature -->
<string name="default_remember_file_list">Запам\'ятовувати списки файлів (за замовчуванням для нових ресурсів)</string>
<string name="remember_file_list">Запам\'ятовувати список файлів для цього ресурсу</string>
<string name="help_remember_file_list">Довідка: Запам\'ятовувати список файлів</string>
<string name="remember_file_list_help_title">Запам\'ятовувати список файлів</string>
<string name="remember_file_list_help_message"><![CDATA[
<b>Що це робить:</b><br/>
При увімкненні додаток зберігає список файлів у базі даних після першого сканування. При наступних відкриттях список завантажується миттєво з бази даних замість повторного сканування папки.<br/><br/>

<b>✅ Переваги:</b><br/>
• Швидке відкриття ресурсів з великою кількістю файлів<br/>
• Ідеально для стабільних бібліотек (музика, фото), які рідко змінюються<br/>
• Чудово працює з мережевими ресурсами (SMB, FTP, SFTP)<br/><br/>

<b>⚠️ Недоліки:</b><br/>
• Зовнішні зміни (файли додані/видалені поза додатком) не будуть виявлені автоматично<br/>
• Потрібне ручне оновлення (кнопка \"Оновити\") для відображення зовнішніх змін<br/><br/>

<b>📌 Примітка:</b><br/>
• Внутрішні операції (перейменування, видалення, переміщення в додатку) автоматично оновлюють кешований список<br/>
• Якщо додаток виявить відсутній файл під час відтворення, він повідомить вас і запропонує оновити ресурс<br/><br/>

<b>💡 Рекомендується для:</b><br/>
Великих, стабільних бібліотек, де зміни файлів рідкісні<br/><br/>

<b>❌ Не рекомендується для:</b><br/>
Папок, які часто змінюються (наприклад, папка документів, папка завантажень)
]]></string>

<!-- File Not Found Dialog -->
<string name="file_not_found_title">Файл не знайдено</string>
<string name="file_not_found_message">Файл \"%1$s\" більше не існує.\n\nМожливо, його було видалено або переміщено поза додатком. Бажаєте оновити ресурс, щоб актуалізувати список файлів?</string>
<string name="refresh_resource">Оновити ресурс</string>
```

---

## 8. Ограничения и валидация

### 8.1. Максимальное количество файлов

**Ограничение**: 1,000,000 (миллион) файлов на ресурс

**Где применять**: При сохранении кэша проверять размер списка.

**Файл**: `CachedFileListRepository.kt`

```kotlin
suspend fun saveCachedFiles(resourceId: Long, files: List<MediaFile>) {
    // Проверить лимит
    if (files.size > 1_000_000) {
        Timber.w("CachedFileList: File count (${files.size}) exceeds limit (1,000,000) for resource $resourceId. Skipping cache save.")
        return
    }
    
    // ... существующий код ...
}
```

### 8.2. Минимальное количество файлов

**Работает от 0 файлов** (пустые ресурсы тоже можно кэшировать).

---

## 9. Тестирование

### 9.1. Unit Tests

**Файл**: `CachedFileListRepositoryTest.kt`

- Тест сохранения и загрузки списка файлов
- Тест обновления файла
- Тест удаления файла
- Тест удаления всех файлов
- Тест лимита в 1 миллион файлов

### 9.2. Integration Tests

**Сценарии**:

1. **Новый ресурс с включенной опцией**:
   - Создать ресурс с `rememberFileList = true`
   - Открыть ресурс → сканирование → сохранение в БД
   - Закрыть и открыть снова → загрузка из БД

2. **Переименование файла**:
   - Включить опцию, открыть ресурс
   - Переименовать файл
   - Закрыть и открыть → файл должен иметь новое имя

3. **Удаление файла**:
   - Включить опцию, открыть ресурс
   - Удалить файл
   - Закрыть и открыть → файл должен отсутствовать

4. **Отсутствующий файл**:
   - Включить опцию, открыть ресурс
   - Удалить файл ВНЕ приложения
   - Попытаться открыть файл → должен показаться диалог
   - Обновить ресурс → файл должен исчезнуть из списка

5. **Очистка кэша**:
   - Включить опцию, открыть несколько ресурсов
   - Перейти в настройки → Очистить кэш
   - Открыть ресурсы снова → должно произойти сканирование

6. **Удаление ресурса**:
   - Включить опцию, открыть ресурс (сохранить в БД)
   - Удалить ресурс
   - Проверить БД → записи в `cached_file_lists` должны быть удалены (CASCADE)

### 9.3. Manual Testing Checklist

- [ ] Создать новый ресурс с включенной опцией
- [ ] Открыть ресурс первый раз (должно быть сканирование)
- [ ] Закрыть и открыть снова (должна быть мгновенная загрузка)
- [ ] Переименовать файл в приложении
- [ ] Перезапустить ресурс (файл должен быть переименован)
- [ ] Удалить файл в приложении
- [ ] Перезапустить ресурс (файл должен отсутствовать)
- [ ] Удалить файл ВНЕ приложения
- [ ] Попытаться открыть файл (должен показаться диалог)
- [ ] Нажать "Обновить ресурс" (файл должен исчезнуть)
- [ ] Очистить кэш в настройках
- [ ] Открыть ресурс (должно быть сканирование)
- [ ] Проверить Help-диалог в настройках
- [ ] Проверить Help-диалог в Resource Editor

---

## 10. Производительность и оптимизация

### 10.1. Размер БД

- **Средний MediaFile в JSON**: ~500 байт
- **1 миллион файлов**: ~500 MB
- **Рекомендация**: Для очень больших библиотек (>100k файлов) предупреждать пользователя о потенциальном размере БД

### 10.2. Скорость загрузки

- **SQLite** хорошо справляется с чтением ~100k записей за 1-2 секунды
- Для ускорения можно использовать пагинацию (lazy loading), но это выходит за рамки текущей задачи

### 10.3. Память

- При загрузке списка из БД все файлы десериализуются в память
- **Оптимизация**: MediaFilesCacheManager уже использует LruCache, который автоматически очищает данные при нехватке памяти

---

## 11. Debug Logging

Добавить Timber-логирование для отладки:

- Когда загружаем из кэша
- Когда сохраняем в кэш
- Когда обновляем/удаляем файлы
- Когда превышен лимит
- Когда очищаем все кэши

---

## 12. Дополнительные замечания

### 12.1. Иконка Help

Убедиться, что в проекте есть иконка `@drawable/ic_help_outline`. Если нет, добавить:

```xml
<!-- res/drawable/ic_help_outline.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.

41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z"/>
</vector>
```

### 12.2. MediaFile Serialization

Убедиться, что `MediaFile` имеет аннотацию `@Serializable` для kotlinx.serialization:

```kotlin
@Serializable
data class MediaFile(
    val path: String,
    val name: String,
    // ... остальные поля
)
```

Если используется Gson вместо kotlinx.serialization, заменить в Repository на:

```kotlin
private val gson = Gson()
val mediaFileJson = gson.toJson(mediaFile)
val mediaFile = gson.fromJson(entity.mediaFileJson, MediaFile::class.java)
```

---

## Итоговый чеклист для разработчика

### Обязательные шаги

- [ ] Создать `CachedFileListEntity.kt`
- [ ] Создать `CachedFileListDao.kt`
- [ ] Обновить `ResourceEntity.kt` (добавить поле `rememberFileList`)
- [ ] Обновить `AppSettings.kt` (добавить поле `defaultRememberFileList`)
- [ ] Обновить `SettingsManager.kt` (добавить key и setter)
- [ ] Создать MIGRATION_7_8 в `AppDatabase.kt`
- [ ] Обновить `DatabaseModule.kt` (добавить миграцию)
- [ ] Добавить в `AppDatabase.kt` метод `cachedFileListDao()`
- [ ] Создать `CachedFileListRepository.kt`
- [ ] Обновить `GeneralSettingsFragment.kt` (UI для глобальной настройки)
- [ ] Обновить `fragment_settings_general.xml` (чекбокс + help-кнопка)
- [ ] Обновить `ResourceEditorFragment.kt` (UI для ресурс-специфичной настройки)
- [ ] Обновить `fragment_resource_editor.xml` (чекбокс + help-кнопка)
- [ ] Обновить `ResourceFormData.kt` (добавить поле)
- [ ] Обновить `ResourceFieldKey.kt` (добавить enum)
- [ ] Обновить `ResourceFormViewModel.kt` (логика инициализации и сохранения)
- [ ] Обновить `BrowseViewModel.kt` (логика кэширования и обновления)
- [ ] Обновить `PlayerActivity.kt` (обработка отсутствующих файлов)
- [ ] Обновить `GeneralSettingsFragment.kt::clearCache()` (удаление DB-кэша)
- [ ] Добавить все строки локализации (EN, RU, UK)
- [ ] Добавить иконку `ic_help_outline.xml` (если отсутствует)
- [ ] Убедиться, что `MediaFile` поддерживает сериализацию

### Дополнительные шаги

- [ ] Написать Unit Tests для `CachedFileListRepository`
- [ ] Написать Integration Tests для основных сценариев
- [ ] Провести Manual Testing согласно чеклисту

---

## Contacts / Questions

При возникновении вопросов или неясностей обращаться к автору спецификации.
