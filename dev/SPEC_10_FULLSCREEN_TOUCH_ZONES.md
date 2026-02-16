# Спецификация #10: Тач-зоны в полноэкранном режиме изображения

## Описание задачи
При проигрывании изображения в полном экране не работают 9 тач-зон (3×3 сетка для навигации и управления). Необходимо исследовать и исправить проблему.

## Требования

### Функциональные требования
- 9 тач-зон должны работать в полноэкранном режиме изображения
- Зоны должны соответствовать функционалу (например: предыдущий файл, следующий файл, зум, настройки и т.д.)
- Визуальная обратная связь при касании (опционально)
- Работа как в портретной, так и в ландшафтной ориентации

### Технические требования
- Корректная обработка touch events
- Приоритет touch zones над другими gesture detectors (если применимо)
- Не конфликтовать с zoom/pan функционалом

## Возможные причины проблемы
1. Touch events перехватываются другим view (PhotoView, ZoomableImageView)
2. Touch zones не добавлены в полноэкранный режим
3. Z-order проблема (зоны находятся под другими view)
4. Touch zones не обновляются при переходе в fullscreen
5. Конфликт с gesture detectors

## Пошаговая реализация

### Шаг 1: Исследование текущей реализации

#### 1.1: Найти код изображения viewerа
1. Найти Activity/Fragment для просмотра изображений
   - Вероятные файлы: `ImageViewerFragment.kt`, `PlayerActivity.kt`
2. Найти layout файлы:
   - `fragment_image_viewer.xml` или аналогичный

#### 1.2: Найти реализацию тач-зон
1. Поиск файлов с "TouchZone", "TouchArea", "GestureZone"
   ```powershell
   Get-ChildItem -Recurse -Filter "*.kt" | Select-String -Pattern "TouchZone|TouchArea|GestureZone"
   ```

2. Проверить существование класса управления зонами

#### 1.3: Проверка layout в полноэкранном режиме
1. Найти как переключается fullscreen mode
2. Проверить, добавлены ли touch zones в fullscreen layout

### Шаг 2: Диагностика проблемы

#### 2.1: Добавить логирование touch events
```kotlin
// В ImageViewerFragment или аналогичном
override fun onTouchEvent(event: MotionEvent): Boolean {
    Log.d("TouchDebug", "Image viewer touch: ${event.action} at (${event.x}, ${event.y})")
    return super.onTouchEvent(event)
}

// В PhotoView или ZoomableImageView
photoView.setOnTouchListener { v, event ->
    Log.d("TouchDebug", "PhotoView touch: ${event.action} at (${event.x}, ${event.y})")
    false // Не перехватывать
}
```

#### 2.2: Проверить иерархию view
```kotlin
// Debug метод для вывода hierarchy
fun printViewHierarchy(view: View, indent: String = "") {
    Log.d("ViewHierarchy", "$indent${view::class.simpleName} - clickable:${view.isClickable}")
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            printViewHierarchy(view.getChildAt(i), "$indent  ")
        }
    }
}
```

### Шаг 3: Реализация решения

#### Решение A: Overlay с тач-зонами поверх PhotoView

1. Создать layout для touch zones overlay:
   `res/layout/overlay_touch_zones.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
       android:id="@+id/touchZonesOverlay"
       android:layout_width="match_parent"
       android:layout_height="match_parent">
       
       <!-- 3x3 Grid of zones -->
       <GridLayout
           android:layout_width="match_parent"
           android:layout_height="match_parent"
           android:columnCount="3"
           android:rowCount="3">
           
           <!-- Top-left -->
           <View
               android:id="@+id/zone_top_left"
               android:layout_width="0dp"
               android:layout_height="0dp"
               android:layout_columnWeight="1"
               android:layout_rowWeight="1"
               android:background="@android:color/transparent" />
           
           <!-- Top-center -->
           <View
               android:id="@+id/zone_top_center"
               android:layout_width="0dp"
               android:layout_height="0dp"
               android:layout_columnWeight="1"
               android:layout_rowWeight="1"
               android:background="@android:color/transparent" />
           
           <!-- Top-right -->
           <View
               android:id="@+id/zone_top_right"
               android:layout_width="0dp"
               android:layout_height="0dp"
               android:layout_columnWeight="1"
               android:layout_rowWeight="1"
               android:background="@android:color/transparent" />
           
           <!-- Middle row... -->
           <!-- Аналогично для остальных 6 зон -->
           
       </GridLayout>
   </FrameLayout>
   ```

2. Добавить overlay в layout изображения:
   ```xml
   <FrameLayout>
       <!-- PhotoView или другой image view -->
       <com.github.chrisbanes.photoview.PhotoView
           android:id="@+id/photoView"
           ... />
       
       <!-- Touch zones overlay -->
       <include layout="@layout/overlay_touch_zones" />
   </FrameLayout>
   ```

3. Настроить touch zones:
   ```kotlin
   class ImageViewerFragment : Fragment() {
       
       private fun setupTouchZones() {
           binding.zoneTopLeft.setOnClickListener {
               // Например, предыдущий файл
               navigateToPrevious()
           }
           
           binding.zoneTopCenter.setOnClickListener {
               // Например, toggle UI
               toggleUI()
           }
           
           binding.zoneTopRight.setOnClickListener {
               // Например, следующий файл
               navigateToNext()
           }
           
           binding.zoneMiddleLeft.setOnClickListener {
               // Например, brightness down
               adjustBrightness(-10)
           }
           
           binding.zoneMiddleCenter.setOnClickListener {
               // Например, toggle zoom
               toggleZoom()
           }
           
           // И так далее для всех зон
       }
       
       private fun updateTouchZonesVisibility(fullscreen: Boolean) {
           // В полноэкранном режиме зоны должны быть активны
           binding.touchZonesOverlay.visibility = if (fullscreen) {
               View.VISIBLE
           } else {
               View.VISIBLE // или GONE если только в fullscreen
           }
       }
   }
   ```

#### Решение B: GestureDetector с разделением на зоны

Если overlay создает проблемы с zoom:

```kotlin
class TouchZoneGestureListener(
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val onZoneClick: (TouchZone) -> Unit
) : GestureDetector.SimpleOnGestureListener() {
    
    enum class TouchZone {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }
    
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val zone = determineZone(e.x, e.y)
        onZoneClick(zone)
        return true
    }
    
    private fun determineZone(x: Float, y: Float): TouchZone {
        val col = (x / viewWidth * 3).toInt().coerceIn(0, 2)
        val row = (y / viewHeight * 3).toInt().coerceIn(0, 2)
        
        return when (row * 3 + col) {
            0 -> TouchZone.TOP_LEFT
            1 -> TouchZone.TOP_CENTER
            2 -> TouchZone.TOP_RIGHT
            3 -> TouchZone.MIDDLE_LEFT
            4 -> TouchZone.MIDDLE_CENTER
            5 -> TouchZone.MIDDLE_RIGHT
            6 -> TouchZone.BOTTOM_LEFT
            7 -> TouchZone.BOTTOM_CENTER
            8 -> TouchZone.BOTTOM_RIGHT
            else -> TouchZone.MIDDLE_CENTER
        }
    }
}

// Использование:
class ImageViewerFragment : Fragment() {
    
    private lateinit var gestureDetector: GestureDetector
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        gestureDetector = GestureDetector(
            requireContext(),
            TouchZoneGestureListener(
                binding.photoView.width,
                binding.photoView.height
            ) { zone ->
                handleZoneClick(zone)
            }
        )
        
        binding.photoView.setOnTouchListener { v, event ->
            // Сначала проверяем зоны, потом zoom
            if (isFullscreen && gestureDetector.onTouchEvent(event)) {
                true
            } else {
                // Позволить PhotoView обрабатывать zoom/pan
                false
            }
        }
    }
    
    private fun handleZoneClick(zone: TouchZone) {
        when (zone) {
            TOP_LEFT -> navigateToPrevious()
            TOP_RIGHT -> navigateToNext()
            MIDDLE_CENTER -> toggleZoom()
            // и т.д.
        }
    }
}
```

### Шаг 4: Визуальная обратная связь (опционально)

```kotlin
// Показать временно зоны при первом входе в fullscreen
private fun showTouchZonesHint() {
    binding.touchZonesOverlay.alpha = 0.3f
    binding.touchZonesOverlay.setBackgroundResource(R.drawable.touch_zones_grid)
    
    binding.touchZonesOverlay.animate()
        .alpha(0f)
        .setDuration(2000)
        .setStartDelay(1000)
        .withEndAction {
            binding.touchZonesOverlay.background = null
        }
        .start()
}
```

### Шаг 5: Тестирование

#### Тест 1: Базовая функциональность
1. Открыть изображение
2. Перейти в полноэкранный режим
3. Тапнуть по каждой из 9 зон
4. Проверить: каждая зона выполняет свою функцию

#### Тест 2: Zoom не конфликтует
1. Открыть изображение в fullscreen
2. Выполнить pinch-to-zoom
3. Проверить: zoom работает
4. Выполнить pan (перемещение)
5. Проверить: pan работает
6. Тапнуть по зоне
7. Проверить: зона сработала

#### Тест 3: Ориентация
1. Открыть изображение в портретном режиме
2. Проверить зоны
3. Повернуть в ландшафтный режим
4. Проверить зоны снова

#### Тест 4: Разные типы изображений
1. Маленькое изображение
2. Большое изображение (требует zoom)
3. Панорама
4. Проверить зоны на всех

## Критерии приемки
- ✅ 9 тач-зон работают в полноэкранном режиме изображения
- ✅ Зоны не конфликтуют с zoom/pan функционалом
- ✅ Работает в портретной и ландшафтной ориентации
- ✅ Работает на разных размерах экранов
- ✅ Touch events обрабатываются корректно

## Файлы для изменения
- `ImageViewerFragment.kt` (или аналогичный)
- Layout файлы image viewer
- Возможно создать: `TouchZoneManager.kt`
- Возможно создать: `overlay_touch_zones.xml`

## Зависимости
- PhotoView или другая библиотека для zoom
- Текущая система fullscreen mode

## Примечания
- Убедиться, что логика зон соответствует ожиданиям пользователя
- Рассмотреть добавление настройки для кастомизации зон
- Можно добавить haptic feedback при нажатии на зоны
