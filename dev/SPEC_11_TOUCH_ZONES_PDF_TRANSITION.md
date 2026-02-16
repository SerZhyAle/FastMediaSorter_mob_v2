# Спецификация #11: Тач-зоны после навигации из PDF

## Описание задачи
В режиме с командной панелью при переходе на изображение после документа (PDF) не работают тач-зоны предыдущий/следующий. При этом центральная область (зум) работает, что может указывать на то, что она общая для документов и изображений, а боковые зоны не инициализируются корректно.

## Требования

### Функциональные требования
- Тач-зоны навигации (предыдущий/следующий) должны работать после перехода с PDF на изображение
- Все зоны должны корректно переинициализироваться при смене типа контента
- Поведение должно быть одинаковым независимо от предыдущего файла

### Технические требования
- Правильная очистка и переинициализация touch listeners при смене типа медиа
- Не должно быть утечек памяти от старых listeners
- Touch zones должны соответствовать текущему типу контента

## Возможные причины
1. PDF viewer использует свои touch zones, которые не очищаются при переходе на изображение
2. Touch zones для изображений не инициализируются, если предыдущий файл был PDF
3. Listeners регистрируются только один раз и не обновляются
4. View hierarchy не обновляется при смене типа контента
5. Z-order или visibility проблемы

## Пошаговая реализация

### Шаг 1: Исследование текущей реализации

#### 1.1: Найти код переключения между типами медиа
```kotlin
// Поиск в PlayerActivity или аналогичном
fun loadMedia(mediaFile: MediaFile) {
    when (mediaFile.type) {
        MediaType.IMAGE -> loadImage(mediaFile)
        MediaType.DOCUMENT -> loadDocument(mediaFile)
        MediaType.VIDEO -> loadVideo(mediaFile)
        MediaType.AUDIO -> loadAudio(mediaFile)
    }
}
```

#### 1.2: Добавить логирование
```kotlin
private fun loadImage(file: MediaFile) {
    Log.d("MediaSwitch", "Loading image, previous type: $currentMediaType")
    // Текущий код загрузки
}

private fun loadDocument(file: MediaFile) {
    Log.d("MediaSwitch", "Loading document, previous type: $currentMediaType")
    // Текущий код загрузки
}
```

### Шаг 2: Диагностика touch zones

#### 2.1: Проверить инициализацию touch zones
```kotlin
private fun setupImageTouchZones() {
    Log.d("TouchZones", "Setting up IMAGE touch zones")
    
    binding.leftZone?.setOnClickListener {
        Log.d("TouchZones", "Left zone clicked (previous)")
        navigateToPrevious()
    }
    
    binding.rightZone?.setOnClickListener {
        Log.d("TouchZones", "Right zone clicked (next)")
        navigateToNext()
    }
    
    binding.centerZone?.setOnClickListener {
        Log.d("TouchZones", "Center zone clicked (zoom)")
        toggleZoom()
    }
}

private fun setupDocumentTouchZones() {
    Log.d("TouchZones", "Setting up DOCUMENT touch zones")
    // PDF может использовать свои зоны
}
```

#### 2.2: Проверить, вызывается ли cleanup
```kotlin
private fun cleanupPreviousTouchZones() {
    Log.d("TouchZones", "Cleaning up previous touch zones")
    
    binding.leftZone?.setOnClickListener(null)
    binding.rightZone?.setOnClickListener(null)
    binding.centerZone?.setOnClickListener(null)
    
    // Если используется GestureDetector
    currentGestureDetector = null
}
```

### Шаг 3: Реализация исправления

#### 3.1: Создать Touch Zone Manager
```kotlin
class TouchZoneManager(
    private val activity: PlayerActivity,
    private val binding: ActivityPlayerBinding
) {
    
    private var currentMediaType: MediaType? = null
    private var imageGestureDetector: GestureDetector? = null
    private var documentGestureDetector: GestureDetector? = null
    
    fun setupForMediaType(mediaType: MediaType) {
        // Важно: сначала очистить предыдущие
        cleanup()
        
        currentMediaType = mediaType
        
        when (mediaType) {
            MediaType.IMAGE -> setupImageTouchZones()
            MediaType.DOCUMENT -> setupDocumentTouchZones()
            MediaType.VIDEO -> setupVideoTouchZones()
            MediaType.AUDIO -> setupAudioTouchZones()
        }
    }
    
    private fun cleanup() {
        Log.d("TouchZoneManager", "Cleaning up for type: $currentMediaType")
        
        // Удалить все listeners
        binding.touchZoneLeft?.setOnClickListener(null)
        binding.touchZoneRight?.setOnClickListener(null)
        binding.touchZoneCenter?.setOnClickListener(null)
        
        // Удалить touch listeners
        binding.mediaContainer?.setOnTouchListener(null)
        
        // Очистить gesture detectors
        imageGestureDetector = null
        documentGestureDetector = null
    }
    
    private fun setupImageTouchZones() {
        Log.d("TouchZoneManager", "Setting up IMAGE touch zones")
        
        // Убедиться, что зоны видимы
        binding.touchZoneLeft?.visibility = View.VISIBLE
        binding.touchZoneRight?.visibility = View.VISIBLE
        binding.touchZoneCenter?.visibility = View.VISIBLE
        
        // Установить clickable
        binding.touchZoneLeft?.isClickable = true
        binding.touchZoneRight?.isClickable = true
        binding.touchZoneCenter?.isClickable = true
        
        // Установить listeners
        binding.touchZoneLeft?.setOnClickListener {
            Log.d("TouchZoneManager", "IMAGE: Previous clicked")
            activity.navigateToPrevious()
        }
        
        binding.touchZoneRight?.setOnClickListener {
            Log.d("TouchZoneManager", "IMAGE: Next clicked")
            activity.navigateToNext()
        }
        
        binding.touchZoneCenter?.setOnClickListener {
            Log.d("TouchZoneManager", "IMAGE: Center clicked")
            activity.toggleZoom()
        }
        
        // Альтернативно: gesture detector для всего контейнера
        imageGestureDetector = GestureDetector(
            activity,
            ImageTouchZoneGestureListener(activity)
        )
        
        binding.mediaContainer?.setOnTouchListener { v, event ->
            imageGestureDetector?.onTouchEvent(event) ?: false
        }
    }
    
    private fun setupDocumentTouchZones() {
        Log.d("TouchZoneManager", "Setting up DOCUMENT touch zones")
        
        // Документы могут иметь другую логику
        // Но боковые зоны должны работать аналогично
        
        binding.touchZoneLeft?.visibility = View.VISIBLE
        binding.touchZoneRight?.visibility = View.VISIBLE
        binding.touchZoneCenter?.visibility = View.VISIBLE
        
        binding.touchZoneLeft?.setOnClickListener {
            Log.d("TouchZoneManager", "DOCUMENT: Previous clicked")
            activity.navigateToPrevious()
        }
        
        binding.touchZoneRight?.setOnClickListener {
            Log.d("TouchZoneManager", "DOCUMENT: Next clicked")
            activity.navigateToNext()
        }
        
        // Центральная зона для документов может иметь другую функцию
        binding.touchZoneCenter?.setOnClickListener {
            Log.d("TouchZoneManager", "DOCUMENT: Center clicked")
            activity.toggleCommandPanel()
        }
    }
}
```

#### 3.2: Интегрировать в PlayerActivity
```kotlin
class PlayerActivity : BaseActivity<ActivityPlayerBinding>() {
    
    private lateinit var touchZoneManager: TouchZoneManager
    private var currentMediaType: MediaType? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        touchZoneManager = TouchZoneManager(this, binding)
    }
    
    private fun loadMedia(mediaFile: MediaFile) {
        Log.d("PlayerActivity", "Loading ${mediaFile.type} (was: $currentMediaType)")
        
        // Сохранить предыдущий тип
        val previousType = currentMediaType
        currentMediaType = mediaFile.type
        
        // ВАЖНО: Настроить touch zones ДО загрузки медиа
        // или сразу ПОСЛЕ, но обязательно при каждой смене
        touchZoneManager.setupForMediaType(mediaFile.type)
        
        when (mediaFile.type) {
            MediaType.IMAGE -> {
                loadImageContent(mediaFile)
            }
            MediaType.DOCUMENT -> {
                loadDocumentContent(mediaFile)
            }
            // и т.д.
        }
    }
    
    private fun loadImageContent(file: MediaFile) {
        // Загрузка изображения
        // ...
        
        // Убедиться, что touch zones настроены
        // (на случай, если были настроены до загрузки view)
        binding.root.post {
            if (currentMediaType == MediaType.IMAGE) {
                touchZoneManager.setupForMediaType(MediaType.IMAGE)
            }
        }
    }
}
```

#### 3.3: Проверить layout структуру
Убедиться, что touch zones есть в layout:

```xml
<!-- activity_player.xml -->
<FrameLayout
    android:id="@+id/mediaContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <!-- Контент (image view, document view, etc.) -->
    <FrameLayout
        android:id="@+id/contentContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
    
    <!-- Touch zones overlay -->
    <LinearLayout
        android:id="@+id/touchZonesContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="horizontal"
        android:baselineAligned="false">
        
        <!-- Left zone (previous) -->
        <FrameLayout
            android:id="@+id/touchZoneLeft"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@android:color/transparent"
            android:clickable="true"
            android:focusable="true" />
        
        <!-- Center zone (zoom/action) -->
        <FrameLayout
            android:id="@+id/touchZoneCenter"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@android:color/transparent"
            android:clickable="true"
            android:focusable="true" />
        
        <!-- Right zone (next) -->
        <FrameLayout
            android:id="@+id/touchZoneRight"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@android:color/transparent"
            android:clickable="true"
            android:focusable="true" />
    </LinearLayout>
    
    <!-- Command panel и другие элементы поверх -->
    
</FrameLayout>
```

### Шаг 4: Убедиться в правильном Z-order

```kotlin
private fun ensureTouchZonesOnTop() {
    // Поднять touch zones наверх
    binding.touchZonesContainer?.bringToFront()
    binding.root.invalidate()
}
```

### Шаг 5: Тестирование

#### Сценарий 1: PDF → Изображение
1. Открыть PDF документ
2. Проверить: боковые зоны работают (навигация)
3. Перейти к следующему файлу (изображение)
4. Проверить: боковые зоны работают для изображения
5. Проверить: центральная зона работает (zoom)

#### Сценарий 2: Изображение → PDF → Изображение
1. Открыть изображение
2. Проверить зоны
3. Перейти к PDF
4. Проверить зоны
5. Перейти к другому изображению
6. Проверить зоны (критический тест!)

#### Сценарий 3: Смешанная последовательность
1. Открыть папку с mix: JPG, PDF, JPG, PDF, JPG
2. Навигировать через все файлы с помощью touch zones
3. Проверить: зоны работают на каждом переходе

#### Debug проверка
1. Включить логирование
2. Следить за сообщениями:
   - "Setting up IMAGE touch zones"
   - "Cleaning up for type: DOCUMENT"
   - "IMAGE: Next clicked"
3. Убедиться, что cleanup вызывается перед setup

## Критерии приемки
- ✅ Touch zones работают при переходе с PDF на изображение
- ✅ Touch zones работают при переходе с изображения на PDF
- ✅ Touch zones работают в любой последовательности типов файлов
- ✅ Нет утечек памяти от старых listeners
- ✅ Центральная и боковые зоны работают корректно для каждого типа

## Файлы для создания/изменения
- Создать: `TouchZoneManager.kt`
- Изменить: `PlayerActivity.kt`
- Проверить: `activity_player.xml` (touch zones layout)
- Возможно изменить: PDF viewer integration код

## Связанные спецификации
- Спецификация #10: Touch zones в fullscreen

## Примечания
- Проблема скорее всего в отсутствии переинициализации при смене типа
- Убедиться, что PDF viewer не блокирует touch events
- Рассмотреть единую систему touch zones для всех типов медиа
