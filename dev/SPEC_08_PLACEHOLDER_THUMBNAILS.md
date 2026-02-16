# Спецификация #8: Placeholder миниатюры при загрузке

## Описание задачи
Когда у медиа-файла (изображение, видео, PDF, EPUB) нет миниатюры (она еще загружается или не смогла загрузиться), показывать сгенерированную миниатюру по тем же правилам, что и для файлов без превью, до момента загрузки реальной миниатюры.

## Требования

### Функциональные требования
- Пока миниатюра загружается, показывать placeholder с цветным фоном
- Если миниатюра не смогла загрузиться, placeholder остается
- После успешной загрузки placeholder заменяется на реальную миниатюру
- Placeholder должен соответствовать типу файла (цветовая схема из Спецификации #7)

### Технические требования
- Плавная анимация перехода от placeholder к реальной миниатюре
- Не блокировать UI во время загрузки
- Эффективное использование памяти
- Корректная обработка ошибок загрузки

## Пошаговая реализация

### Шаг 1: Определить состояния миниатюры
1. Создать enum для состояний:
   ```kotlin
   enum class ThumbnailState {
       LOADING,      // Миниатюра загружается
       LOADED,       // Миниатюра успешно загружена
       FAILED,       // Ошибка загрузки
       PLACEHOLDER   // Показывается placeholder
   }
   ```

### Шаг 2: Расширить ThumbnailGenerator для медиа-файлов
1. Обновить `ThumbnailColorMapper.kt` из Спецификации #7:
   ```kotlin
   object ThumbnailColorMapper {
       // ... существующий код ...
       
       fun getColorForMediaType(mediaType: MediaType): Int {
           return when (mediaType) {
               MediaType.IMAGE -> Color.parseColor("#81C784")  // зеленый
               MediaType.VIDEO -> Color.parseColor("#9575CD")  // фиолетовый
               MediaType.DOCUMENT -> Color.parseColor("#42A5F5") // синий
               MediaType.AUDIO -> Color.parseColor("#FFA726")  // оранжевый
               else -> Color.parseColor("#EEEEEE")
           }
       }
   }
   ```

2. Добавить генерацию placeholder для медиа:
   ```kotlin
   // В ThumbnailGenerator.kt
   fun generatePlaceholderForMedia(
       file: MediaFile,
       size: Int
   ): Bitmap {
       val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
       val canvas = Canvas(bitmap)
       
       // Цвет фона по типу файла
       val backgroundColor = ThumbnailColorMapper.getColorForMediaType(file.type)
       val bgPaint = Paint().apply {
           color = backgroundColor
           style = Paint.Style.FILL
       }
       canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
       
       // Иконка типа файла в центре
       val icon = getIconForMediaType(file.type)
       val iconSize = size * 0.4f
       val left = (size - iconSize) / 2
       val top = (size - iconSize) / 2
       
       icon?.let {
           val iconBitmap = getBitmapFromDrawable(it, iconSize.toInt())
           canvas.drawBitmap(iconBitmap, left, top, null)
       }
       
       // Опционально: расширение файла снизу
       drawExtensionLabel(canvas, file.extension, size)
       
       return bitmap
   }
   
   private fun getIconForMediaType(type: MediaType): Drawable? {
       val iconRes = when (type) {
           MediaType.IMAGE -> R.drawable.ic_image_placeholder
           MediaType.VIDEO -> R.drawable.ic_video_placeholder
           MediaType.DOCUMENT -> R.drawable.ic_document_placeholder
           else -> null
       }
       return iconRes?.let { ContextCompat.getDrawable(context, it) }
   }
   
   private fun drawExtensionLabel(canvas: Canvas, extension: String, size: Int) {
       val textPaint = Paint().apply {
           color = Color.WHITE
           textSize = size * 0.12f
           typeface = Typeface.DEFAULT_BOLD
           textAlign = Paint.Align.CENTER
           isAntiAlias = true
       }
       
       val xPos = size / 2f
       val yPos = size * 0.85f
       
       canvas.drawText(extension.uppercase(), xPos, yPos, textPaint)
   }
   ```

### Шаг 3: Интеграция с image loading библиотекой

#### Вариант A: Использование Glide
```kotlin
class MediaImageLoader(
    private val context: Context,
    private val thumbnailGenerator: ThumbnailGenerator
) {
    
    fun loadThumbnail(
        imageView: ImageView,
        file: MediaFile,
        size: Int,
        onStateChange: ((ThumbnailState) -> Unit)? = null
    ) {
        // Генерируем placeholder
        val placeholder = thumbnailGenerator.generatePlaceholderForMedia(file, size)
        
        onStateChange?.invoke(ThumbnailState.PLACEHOLDER)
        
        Glide.with(context)
            .load(file.uri)
            .placeholder(BitmapDrawable(context.resources, placeholder))
            .error(BitmapDrawable(context.resources, placeholder))
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    onStateChange?.invoke(ThumbnailState.FAILED)
                    return false // Позволить Glide установить error placeholder
                }
                
                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    onStateChange?.invoke(ThumbnailState.LOADED)
                    return false
                }
            })
            .into(imageView)
    }
}
```

#### Вариант B: Ручная загрузка с корутинами
```kotlin
suspend fun loadThumbnailAsync(
    imageView: ImageView,
    file: MediaFile,
    size: Int
): ThumbnailState = withContext(Dispatchers.IO) {
    
    // Показываем placeholder сразу
    val placeholder = thumbnailGenerator.generatePlaceholderForMedia(file, size)
    withContext(Dispatchers.Main) {
        imageView.setImageBitmap(placeholder)
    }
    
    try {
        // Загружаем реальную миниатюру
        val thumbnail = loadRealThumbnail(file, size)
        
        withContext(Dispatchers.Main) {
            // Плавный переход
            val drawable = BitmapDrawable(imageView.context.resources, thumbnail)
            imageView.animateImageChange(drawable)
        }
        ThumbnailState.LOADED
        
    } catch (e: Exception) {
        Log.e("ThumbnailLoader", "Failed to load thumbnail", e)
        // Placeholder остается
        ThumbnailState.FAILED
    }
}

private fun ImageView.animateImageChange(newDrawable: Drawable) {
    val fade = TransitionDrawable(arrayOf(drawable, newDrawable))
    setImageDrawable(fade)
    fade.startTransition(200)
}
```

### Шаг 4: Обновление адаптера
1. В адаптере RecyclerView:
   ```kotlin
   override fun onBindViewHolder(holder: ViewHolder, position: Int) {
       val file = items[position]
       
       // Определяем, нужен ли placeholder
       val needsPlaceholder = file.type in listOf(
           MediaType.IMAGE,
           MediaType.VIDEO,
           MediaType.DOCUMENT
       )
       
       if (needsPlaceholder) {
           mediaImageLoader.loadThumbnail(
               holder.thumbnail,
               file,
               thumbnailSize
           ) { state ->
               // Опционально: показать индикатор загрузки
               holder.loadingIndicator.isVisible = (state == ThumbnailState.LOADING)
           }
       } else if (file.type == MediaType.AUDIO) {
           // Для аудио всегда генерируемая миниатюра
           val thumbnail = thumbnailGenerator.generateThumbnailForExtension(
               file.name,
               thumbnailSize
           )
           holder.thumbnail.setImageBitmap(thumbnail)
       }
       
       // ... остальная логика bind
   }
   ```

### Шаг 5: Создание иконок placeholder
1. Создать vector drawable для каждого типа:

`res/drawable/ic_image_placeholder.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"/>
</vector>
```

`res/drawable/ic_video_placeholder.xml`, `ic_document_placeholder.xml` - аналогично

### Шаг 6: Тестирование

#### Сценарий 1: Нормальная загрузка
1. Открыть папку с изображениями/видео
2. Проверить:
   - Сначала показываются placeholder с цветными фонами
   - Постепенно появляются реальные миниатюры
   - Переход плавный (fade in)

#### Сценарий 2: Медленное соединение
1. Ограничить скорость сети (через Developer Options)
2. Открыть сетевой ресурс (FTP) с медиа
3. Проверить:
   - Placeholders показываются сразу
   - При прокрутке нет задержек UI
   - Миниатюры загружаются асинхронно

#### Сценарий 3: Ошибка загрузки
1. Открыть поврежденные/недоступные файлы
2. Проверить:
   - Placeholder остается видимым
   - Нет crash или ANR
   - UI остается отзывчивым

#### Сценарий 4: Разные типы файлов
1. Папка с mix файлов (JPG, MP4, PDF, MP3)
2. Проверить:
   - Каждый тип имеет свой цвет placeholder
   - Для MP3 всегда сгенерированная миниатюра
   - Для JPG/MP4/PDF сначала placeholder, потом реальная

## Критерии приемки
- ✅ При загрузке миниатюр показываются placeholder с цветными фонами
- ✅ Placeholder соответствуют типу файла
- ✅ После загрузки происходит плавная замена на реальную миниатюру
- ✅ При ошибке загрузки placeholder остается видимым
- ✅ Нет задержек или фризов UI
- ✅ Работает для изображений, видео, PDF, EPUB

## Файлы для создания/изменения
- Изменить: `ThumbnailGenerator.kt`
- Изменить: `ThumbnailColorMapper.kt`
- Создать: `MediaImageLoader.kt` (опционально)
- Изменить: Адаптер browse экрана
- Создать: `res/drawable/ic_*_placeholder.xml`

## Зависимости
- Спецификация #7 (цветовая схема)
- Image loading библиотека (Glide/Coil/Picasso)

## Примечания
- Рассмотреть кэширование placeholder для повышения производительности
- Можно добавить анимацию "скелетона" для placeholder
- Учесть размер миниатюр при генерации placeholder
