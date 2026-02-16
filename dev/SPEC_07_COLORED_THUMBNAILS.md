# Спецификация #7: Цветные фоны для генерируемых миниатюр

## Описание задачи
Генерировать миниатюры с различными цветовыми фонами в зависимости от типа и расширения файла. Группы файлов (музыка, документы, бинарные) должны иметь свои базовые цвета с градациями для конкретных форматов.

## Требования

### Функциональные требования
- Файлы одной категории имеют похожие цвета (например, все аудио - желтые оттенки)
- Разные форматы внутри категории имеют легкие градации цвета
- Цветовая схема должна быть интуитивно понятной
- Генерируемые миниатюры должны быть визуально привлекательными

### Технические требования
- Цвета должны быть четко различимы
- Контраст текста (расширение файла) на фоне должен быть достаточным
- Цвета должны работать в светлой и темной теме

### Цветовая схема (пример)

#### Аудио файлы (желтые оттенки)
- MP3: `#FFA726` (оранжевый)
- FLAC: `#FFF59D` (светло-желтый)
- APE: `#FFECB3` (очень светло-желтый)
- WAV: `#FFD54F` (золотистый)
- OGG: `#FFB74D` (темно-оранжевый)
- M4A: `#FFE082` (желтый)

#### Документы (синие оттенки)
- TXT: `#90CAF9` (светло-синий)
- PDF: `#42A5F5` (синий)
- EPUB: `#64B5F6` (средне-синий)
- DOC/DOCX: `#2196F3` (ярко-синий)
- RTF: `#BBDEFB` (очень светло-синий)

#### Изображения (зеленые оттенки)
- JPG/JPEG: `#81C784` (зеленый)
- PNG: `#66BB6A` (темно-зеленый)
- GIF: `#A5D6A7` (светло-зеленый)
- BMP: `#C8E6C9` (очень светло-зеленый)
- WEBP: `#4CAF50` (насыщенный зеленый)

#### Видео (фиолетовые оттенки)
- MP4: `#9575CD` (фиолетовый)
- AVI: `#7E57C2` (темно-фиолетовый)
- MKV: `#B39DDB` (светло-фиолетовый)
- MOV: `#673AB7` (глубокий фиолетовый)
- WEBM: `#D1C4E9` (очень светло-фиолетовый)

#### Архивы (серые оттенки)
- ZIP: `#BDBDBD` (серый)
- RAR: `#9E9E9E` (темно-серый)
- 7Z: `#E0E0E0` (светло-серый)
- TAR: `#757575` (очень темно-серый)

#### Бинарные/Другое (красные оттенки)
- EXE: `#E57373` (красный)
- APK: `#EF5350` (ярко-красный)
- BIN: `#FFCDD2` (светло-красный)
- По умолчанию: `#EEEEEE` (почти белый)

## Пошаговая реализация

### Шаг 1: Создать систему сопоставления расширений и цветов
1. Создать файл `ThumbnailColorMapper.kt`:
   ```kotlin
   object ThumbnailColorMapper {
       
       private val extensionColorMap = mapOf(
           // Аудио (желтые/оранжевые)
           "mp3" to "#FFA726",
           "flac" to "#FFF59D",
           "ape" to "#FFECB3",
           "wav" to "#FFD54F",
           "ogg" to "#FFB74D",
           "m4a" to "#FFE082",
           
           // Документы (синие)
           "txt" to "#90CAF9",
           "pdf" to "#42A5F5",
           "epub" to "#64B5F6",
           "doc" to "#2196F3",
           "docx" to "#2196F3",
           "rtf" to "#BBDEFB",
           
           // Изображения (зеленые)
           "jpg" to "#81C784",
           "jpeg" to "#81C784",
           "png" to "#66BB6A",
           "gif" to "#A5D6A7",
           "bmp" to "#C8E6C9",
           "webp" to "#4CAF50",
           
           // Видео (фиолетовые)
           "mp4" to "#9575CD",
           "avi" to "#7E57C2",
           "mkv" to "#B39DDB",
           "mov" to "#673AB7",
           "webm" to "#D1C4E9",
           
           // Архивы (серые)
           "zip" to "#BDBDBD",
           "rar" to "#9E9E9E",
           "7z" to "#E0E0E0",
           "tar" to "#757575",
           
           // Бинарные (красные)
           "exe" to "#E57373",
           "apk" to "#EF5350",
           "bin" to "#FFCDD2"
       )
       
       private const val DEFAULT_COLOR = "#EEEEEE"
       
       fun getColorForExtension(extension: String): Int {
           val colorHex = extensionColorMap[extension.lowercase()] ?: DEFAULT_COLOR
           return Color.parseColor(colorHex)
       }
       
       fun getColorForFile(fileName: String): Int {
           val extension = fileName.substringAfterLast('.', "")
           return getColorForExtension(extension)
       }
   }
   ```

### Шаг 2: Найти генератор миниатюр
1. Найти код, отвечающий за генерацию миниатюр (вероятно в `ThumbnailGenerator.kt`, `ImageLoader.kt` или аналогичном)
2. Найти метод, который создает миниатюры для файлов без превью

### Шаг 3: Модифицировать генерацию миниатюр
1. Обновить метод генерации:
   ```kotlin
   fun generateThumbnailForExtension(
       fileName: String,
       size: Int
   ): Bitmap {
       val extension = fileName.substringAfterLast('.', "").uppercase()
       val backgroundColor = ThumbnailColorMapper.getColorForFile(fileName)
       
       val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
       val canvas = Canvas(bitmap)
       
       // Рисуем фон
       val bgPaint = Paint().apply {
           color = backgroundColor
           style = Paint.Style.FILL
       }
       canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
       
       // Рисуем расширение файла
       val textPaint = Paint().apply {
           color = getContrastingTextColor(backgroundColor)
           textSize = size * 0.2f
           typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
           textAlign = Paint.Align.CENTER
           isAntiAlias = true
       }
       
       val xPos = size / 2f
       val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
       
       canvas.drawText(extension, xPos, yPos, textPaint)
       
       return bitmap
   }
   
   private fun getContrastingTextColor(backgroundColor: Int): Int {
       // Вычисляем яркость
       val red = Color.red(backgroundColor)
       val green = Color.green(backgroundColor)
       val blue = Color.blue(backgroundColor)
       val brightness = (red * 299 + green * 587 + blue * 114) / 1000
       
       // Если фон светлый - темный текст, иначе - светлый
       return if (brightness > 128) {
           Color.parseColor("#424242") // темно-серый
       } else {
           Color.WHITE
       }
   }
   ```

### Шаг 4: Интеграция с существующей системой загрузки
1. Обновить логику в адаптере или image loader:
   ```kotlin
   private fun loadThumbnail(imageView: ImageView, file: MediaFile) {
       val needsGeneratedThumbnail = when (file.type) {
           MediaType.AUDIO -> true
           MediaType.DOCUMENT -> file.extension in listOf("txt", "rtf") // Не PDF/EPUB
           else -> false
       }
       
       if (needsGeneratedThumbnail) {
           val thumbnail = thumbnailGenerator.generateThumbnailForExtension(
               file.name,
               thumbnailSize
           )
           imageView.setImageBitmap(thumbnail)
       } else {
           // Загрузка реальной миниатюры через Glide/Picasso
           loadRealThumbnail(imageView, file)
       }
   }
   ```

### Шаг 5: Кэширование генерируемых миниатюр
1. Добавить кэш для избежания повторной генерации:
   ```kotlin
   object ThumbnailCache {
       private val cache = LruCache<String, Bitmap>(50) // Кэш на 50 элементов
       
       fun get(key: String): Bitmap? = cache.get(key)
       
       fun put(key: String, bitmap: Bitmap) {
           cache.put(key, bitmap)
       }
       
       fun getCacheKey(fileName: String, size: Int): String {
           val extension = fileName.substringAfterLast('.', "")
           return "$extension-$size"
       }
   }
   
   // В генераторе:
   fun generateThumbnailForExtension(fileName: String, size: Int): Bitmap {
       val cacheKey = ThumbnailCache.getCacheKey(fileName, size)
       ThumbnailCache.get(cacheKey)?.let { return it }
       
       val bitmap = createBitmap(...) // генерация
       ThumbnailCache.put(cacheKey, bitmap)
       return bitmap
   }
   ```

### Шаг 6: Тестирование
1. Подготовить папку с файлами разных типов:
   - MP3, FLAC, APE (аудио)
   - TXT, PDF (документы)
   - ZIP, RAR (архивы)
   - EXE, APK (бинарные)

2. Открыть в Browse mode
3. Проверить:
   - Каждый тип файла имеет свой цвет фона
   - Файлы одной категории имеют похожие цвета
   - Текст расширения хорошо читается на всех фонах
   - Цвета визуально приятные

4. Проверить в темной теме

## Критерии приемки
- ✅ Генерируемые миниатюры имеют цветные фоны
- ✅ Цвета соответствуют типу файла (аудио - желтые, документы - синие, и т.д.)
- ✅ Разные форматы в категории имеют градации цвета
- ✅ Текст расширения контрастен и читаем на всех фонах
- ✅ Цвета работают в светлой и темной теме
- ✅ Нет проблем с производительностью (использование кэша)

## Файлы для создания/изменения
- Создать: `ThumbnailColorMapper.kt`
- Изменить: `ThumbnailGenerator.kt` или аналогичный
- Изменить: Image loading логика в адаптере

## Примечания
- Цветовая схема может быть скорректирована для лучшей эстетики
- Рассмотреть добавление иконок для популярных типов файлов вместо/вместе с текстом
- Можно добавить настройку для включения/отключения цветных фонов
