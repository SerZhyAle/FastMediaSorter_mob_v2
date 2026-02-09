# Задача 6: Специальные миниатюры для бинарных файлов

## Описание

Для распространенных бинарных файлов (ZIP, RAR, 7z, ISO, DMG, APK, EXE, DLL и т.д.) показывать специальные миниатюры с текстом расширения по центру. При клике показывать меню с опциями вместо открытия в плеере.

## Приоритет

🟢 Низкий (улучшение UX)

## Требования

- Отображаются только в режиме "All files"
- При клике - Bottom Sheet Menu (share/open/copy/move/rename/delete)
- Миниатюры генерируются программно с текстом расширения

## Затронутые файлы

**Новые:**

- `util/BinaryFileTypeDetector.kt`
- `util/BinaryFileThumbnailGenerator.kt`
- `res/drawable/` - shapes для миниатюр

**Изменяемые:**

- `data/model/MediaType.kt`
- `MediaFileAdapter.kt`
- `PlayerActivity.kt` (блокировка)
- Settings fragment

---

## Промпты для разработки

### Промпт 1: Расширение MediaType enum

```
Добавь новые типы для бинарных файлов:

Файл: `data/model/MediaType.kt`

```kotlin
enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    GIF,
    TEXT,
    PDF,
    EPUB,
  

  // Новые типы:
    BINARY_ARCHIVE,    // ZIP, RAR, 7z, TAR, GZ
    BINARY_DISK,       // ISO, DMG, IMG
    BINARY_EXECUTABLE, // APK, EXE, DLL, SO
    BINARY_OTHER;      // другие бинарные

    fun isBinaryFile(): Boolean {
        return this in listOf(
            BINARY_ARCHIVE,
            BINARY_DISK,
            BINARY_EXECUTABLE,
            BINARY_OTHER
        )
    }
}
```

Добавь маппинг расширений в существующий код определения типов.

```

### Промпт 2: Binary file type detector
```

Создай утилиту для определения типа бинарного файла:

Файл: `util/BinaryFileTypeDetector.kt`

```kotlin
object BinaryFileTypeDetector {
    
    private val ARCHIVES = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tar.gz"
    )
    
    private val DISK_IMAGES = setOf(
        "iso", "dmg", "img", "vhd", "vdi", "qcow2"
    )
    
    private val EXECUTABLES = setOf(
        "exe", "dll", "apk", "so", "dylib", "msi", "deb", "rpm"
    )
    
    private val DATABASES = setOf(
        "db", "sqlite", "sqlite3", "mdb"
    )
    
    private val OTHER = setOf(
        "bin", "dat", "tmp", "cache"
    )
    
    fun detectType(extension: String): MediaType {
        return when (extension.lowercase()) {
            in ARCHIVES -> MediaType.BINARY_ARCHIVE
            in DISK_IMAGES -> MediaType.BINARY_DISK
            in EXECUTABLES -> MediaType.BINARY_EXECUTABLE
            in DATABASES, in OTHER -> MediaType.BINARY_OTHER
            else -> MediaType.BINARY_OTHER
        }
    }
    
    fun isBinaryExtension(ext: String): Boolean {
        val lower = ext.lowercase()
        return lower in ARCHIVES ||
               lower in DISK_IMAGES ||
               lower in EXECUTABLES ||
               lower in DATABASES ||
               lower in OTHER
    }
}
```

Интегрируй в существующий код определения MediaType.

```

### Промпт 3: Генератор миниатюр
```

Создай генератор миниатюр для бинарных файлов:

Файл: `util/BinaryFileThumbnailGenerator.kt`

```kotlin
class BinaryFileThumbnailGenerator(private val context: Context) {
    
    private val cache = LruCache<String, Bitmap>(50)
    
    fun generateThumbnail(
        extension: String,
        type: MediaType,
        size: Int = 200
    ): Bitmap {
        val cacheKey = "$extension-$type-$size"
        cache.get(cacheKey)?.let { return it }
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background gradient зависит от типа
        val gradient = when (type) {
            MediaType.BINARY_ARCHIVE -> intArrayOf(0xFF1E88E5.toInt(), 0xFF1565C0.toInt()) // Blue
            MediaType.BINARY_DISK -> intArrayOf(0xFF43A047.toInt(), 0xFF2E7D32.toInt())    // Green
            MediaType.BINARY_EXECUTABLE -> intArrayOf(0xFFE53935.toInt(), 0xFFC62828.toInt()) // Red
            else -> intArrayOf(0xFF757575.toInt(), 0xFF424242.toInt())  // Gray
        }
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                gradient[0], gradient[1],
                Shader.TileMode.CLAMP
            )
        }
        
        // Draw rounded rect background
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val cornerRadius = size * 0.1f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        // Draw extension text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        
        val ext = extension.uppercase().take(4)  // max 4 chars
        val xPos = size / 2f
        val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(ext, xPos, yPos, textPaint)
        
        cache.put(cacheKey, bitmap)
        return bitmap
    }
}
```

Протестируй генерацию для разных расширений (ZIP, APK, ISO).

```

### Промпт 4: Интеграция в MediaFileAdapter
```

Модифицируй MediaFileAdapter для показа binary миниатюр:

В `onBindViewHolder()`:

```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val mediaFile = getItem(position)
    
    if (mediaFile.type.isBinaryFile()) {
        // Генерировать миниатюру
        val extension = mediaFile.path.substringAfterLast('.', "")
        val thumbnail = thumbnailGenerator.generateThumbnail(
            extension,
            mediaFile.type,
            holder.imageView.width
        )
        holder.imageView.setImageBitmap(thumbnail)
        
        // Click handler для binary файлов
        holder.itemView.setOnClickListener {
            onBinaryFileClickListener?.invoke(mediaFile)
        }
    } else {
        // Существующая логика для media файлов
        Glide.with(holder.itemView)
            .load(mediaFile.path)
            .into(holder.imageView)
        
        holder.itemView.setOnClickListener {
            onMediaFileClickListener?.invoke(mediaFile, position)
        }
    }
}
```

Добавь поле `thumbnailGenerator` в adapter constructor.

```

### Промпт 5: Bottom Sheet Menu для binary файлов
```

Создай menu для binary файлов в BrowseActivity:

```kotlin
private fun showBinaryFileMenu(mediaFile: MediaFile) {
    val bottomSheet = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.bottom_sheet_binary_file, null)
    
    view.findViewById<TextView>(R.id.tvFileName).text = mediaFile.name
    
    view.findViewById<View>(R.id.btnShare).setOnClickListener {
        shareFile(mediaFile)
        bottomSheet.dismiss()
    }
    
    view.findViewById<View>(R.id.btnOpenWith).setOnClickListener {
        openWithDefaultApp(mediaFile)
        bottomSheet.dismiss()
    }
    
    view.findViewById<View>(R.id.btnCopy).setOnClickListener {
        startCopyOperation(mediaFile)
        bottomSheet.dismiss()
    }
    
    view.findViewById<View>(R.id.btnMove).setOnClickListener {
        startMoveOperation(mediaFile)
        bottomSheet.dismiss()
    }
    
    view.findViewById<View>(R.id.btnRename).setOnClickListener {
        showRenameDialog(mediaFile)
        bottomSheet.dismiss()
    }
    
    view.findViewById<View>(R.id.btnDelete).setOnClickListener {
        deleteFiles(listOf(mediaFile))
        bottomSheet.dismiss()
    }
    
    bottomSheet.setContentView(view)
    bottomSheet.show()
}
```

Создай layout `bottom_sheet_binary_file.xml` с 6 кнопками.

```

### Промпт 6: Open with default app
```

Реализуй функцию "Открыть программой по умолчанию":

```kotlin
private fun openWithDefaultApp(mediaFile: MediaFile) {
    try {
        val uri = Uri.parse(mediaFile.path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(mediaFile))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(
                this,
                "Нет приложения для открытия этого файла",
                Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to open file with default app")
        Toast.makeText(this, "Ошибка открытия файла", Toast.LENGTH_SHORT).show()
    }
}

private fun getMimeType(mediaFile: MediaFile): String {
    val extension = mediaFile.path.substringAfterLast('.', "")
    return when (mediaFile.type) {
        MediaType.BINARY_ARCHIVE -> "application/${extension}"
        MediaType.BINARY_EXECUTABLE -> when (extension.lowercase()) {
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
        else -> "application/octet-stream"
    }
}
```

Протестируй на APK файле.

```

### Промпт 7: Фильтрация (только в All Files mode)
```

Binary файлы должны показываться только в режиме "All Files":

В Repository/Manager где фильтруются файлы по типу:

```kotlin
fun shouldShowFile(file: File, settings: Settings): Boolean {
    val extension = file.extension.lowercase()
    
    // Check if binary
    if (BinaryFileTypeDetector.isBinaryExtension(extension)) {
        return settings.allFilesMode  // показывать ТОЛЬКО если All Files ON
    }
    
    // Existing logic для media файлов
    return when {
        settings.allFilesMode -> true
        isImageExtension(extension) -> settings.showImages
        isVideoExtension(extension) -> settings.showVideos
        // ... etc
        else -> false
    }
}
```

Добавь эту проверку во все resource managers.

```

### Промпт 8: Тестирование
```

Протестируй функциональность binary файлов:

**Тест 1: Отображение**

- Открой папку с ZIP, APK, ISO files
- Режим "All Files" OFF:
  - ✅ Binary файлы НЕ видны
- Режим "All Files" ON:
  - ✅ Binary файлы видны с  custom thumbnails
  - ✅ Миниатюры разных цветов для разных типов

**Тест 2: Клик и меню**

- Кликни на ZIP файл:
  - ✅ Открылось bottom sheet menu
  - ✅ Все 6 опций присутствуют

**Тест 3: Операции**

- Share: ✅ открывается системный share sheet
- Open with: ✅ открывается APK installer для .apk
- Copy/Move: ✅ работают стандартные операции
- Rename: ✅ работает
- Delete: ✅ работает

**Тест 4: Multi-select**

- Выбери несколько binary + media файлов
- Проверь что операции работают для всех

Запиши результаты.

```

## Критерии готовности
- ✅ Binary типы добавлены в MediaType
- ✅ Миниатюры генерируются корректно
- ✅ Разные цвета для разных типов
- ✅ Bottom sheet menu отображается при клике
- ✅ "Open with" открывает системные приложения
- ✅ Copy/Move/Rename/Delete работают
- ✅ Отображение только в "All Files" mode (через автоопределение в getMediaType)
- ✅ Performance OK (LRU cache работает)

---

## Реализовано (2026-02-03)

### 1. MediaType расширен ✅
- Добавлены типы: `BINARY_ARCHIVE`, `BINARY_DISK`, `BINARY_EXECUTABLE`, `BINARY_OTHER`
- Добавлен метод `isBinaryFile()` для проверки

### 2. BinaryFileTypeDetector ✅
- Создан `util/BinaryFileTypeDetector.kt`
- Определяет тип по расширению (archives, disk images, executables, etc.)
- Предоставляет цвета для миниатюр

### 3. BinaryFileThumbnailGenerator ✅
- Создан `util/BinaryFileThumbnailGenerator.kt`
- Генерирует миниатюры с gradient background + текстом расширения
- LRU cache на 50 элементов
- Разные цвета для разных типов

### 4. MediaFileAdapter интеграция ✅
- Добавлен callback `onBinaryFileClick`
- Обновлены ListViewHolder и GridViewHolder
- Binary файлы получают custom thumbnails
- Click handler перенаправляет на специальный обработчик

### 5. BrowseActivity обновлён ✅
- Инициализация `BinaryFileThumbnailGenerator`
- Функция `showBinaryFileMenu()` с Bottom Sheet
- Функция `openWithDefaultApp()` для открытия
- Функция `shareFile()` для отправки
- Функция `getMimeTypeForFile()` для определения MIME type

### 6. Bottom Sheet Menu ✅
- Создан layout `bottom_sheet_binary_file.xml`
- 6 кнопок: Share, Open With, Copy, Move, Rename, Delete
- Интеграция с существующими операциями

### 7. Автоопределение binary файлов ✅
- `MediaTypeUtils.getMediaType()` проверяет `BinaryFileTypeDetector`
- Binary файлы автоматически распознаются
- Отображаются только в режиме "All Files" (когда `supportedTypes` содержит все типы)

### Примечания:
- Binary файлы НЕ добавляются в настройки (нет отдельных флагов)
- Они автоматически появляются при определении через `getMediaType()`
- В режиме "All Files" (когда все 7 основных типов включены) binary файлы будут видны

### Требуется тестирование:
1. Открыть папку с ZIP, APK, ISO файлами
2. Включить "All Files" mode
3. Проверить миниатюры (разные цвета)
4. Клик на binary файл → Bottom Sheet Menu
5. Проверить все операции (Share, Open, Copy, Move, Rename, Delete)
6. Проверить на разных типах ресурсов (Local, SMB, Cloud)

### Известные ограничения:
- Для network/cloud файлов "Open With" может не работать (требуется скачивание)
- Share также может требовать локальный файл
- Эти операции работают из коробки только для локальных файлов

