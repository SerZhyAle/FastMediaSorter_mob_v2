# Спецификация #6: Специальное поведение аудио-ресурсов

## Описание задачи
Реализовать специальное поведение для ресурсов, содержащих только аудио-файлы: все миниатюры должны быть 48×48 пикселей независимо от настроек, режим сетки должен быть отключен (только список).

## Требования

### Функциональные требования
- Если ресурс помечен как "только аудио" (выбрано только "Аудио"), применяется специальное поведение
- Все миниатюры отображаются размером 48×48 пикселей
- Переключение на режим сетки недоступно (кнопка скрыта/отключена)
- Доступен только режим списка

### Технические требования
- Размер миниатюр не должен зависеть от глобальных настроек
- Логика должна определяться типом контента ресурса
- UI должен четко показывать, что режим сетки недоступен

## Пошаговая реализация

### Шаг 1: Определение аудио-ресурса
1. Найти модель данных ресурса (вероятно `Resource.kt`, `MediaSource.kt` или аналогичный)
2. Добавить метод проверки:
   ```kotlin
   data class MediaSource(
       val id: String,
       val name: String,
       val allowedTypes: Set<MediaType>,
       // другие поля
   ) {
       fun isAudioOnly(): Boolean {
           return allowedTypes.size == 1 && 
                  allowedTypes.contains(MediaType.AUDIO)
       }
   }
   
   enum class MediaType {
       AUDIO, VIDEO, IMAGE, DOCUMENT
   }
   ```

### Шаг 2: Модификация Browse Activity/Fragment
1. Найти файл Browse экрана (вероятно `BrowseActivity.kt` или `BrowseFragment.kt`)
2. Добавить поле для отслеживания типа ресурса:
   ```kotlin
   private var currentSource: MediaSource? = null
   private var isAudioOnlyMode = false
   ```

### Шаг 3: Управление видимостью кнопки режима просмотра
1. Найти кнопку переключения view mode (grid/list):
   ```kotlin
   private fun updateViewModeButton() {
       val source = currentSource ?: return
       isAudioOnlyMode = source.isAudioOnly()
       
       binding.viewModeToggleButton.apply {
           if (isAudioOnlyMode) {
               // Скрыть кнопку или сделать неактивной
               visibility = View.GONE
               // ИЛИ
               isEnabled = false
               alpha = 0.3f
           } else {
               visibility = View.VISIBLE
               isEnabled = true
               alpha = 1.0f
           }
       }
   }
   ```

### Шаг 4: Принудительный режим списка для аудио
1. Модифицировать логику установки view mode:
   ```kotlin
   private fun setViewMode(mode: ViewMode) {
       // Если аудио-ресурс, всегда используем список
       val actualMode = if (isAudioOnlyMode) {
           ViewMode.LIST
       } else {
           mode
       }
       
       currentViewMode = actualMode
       updateRecyclerView(actualMode)
       saveViewModePreference(actualMode)
   }
   
   private fun loadViewModePreference(): ViewMode {
       if (isAudioOnlyMode) {
           return ViewMode.LIST
       }
       // загрузка из SharedPreferences
       return savedViewMode
   }
   ```

### Шаг 5: Фиксированный размер миниатюр для аудио
1. Найти адаптер RecyclerView для browse (например, `MediaBrowseAdapter.kt`)
2. Модифицировать логику размера миниатюр:
   ```kotlin
   class MediaBrowseAdapter(
       private val isAudioOnly: Boolean,
       // другие параметры
   ) : RecyclerView.Adapter<ViewHolder>() {
       
       private val thumbnailSize: Int
           get() = if (isAudioOnly) {
               48.dpToPx() // Фиксированный размер для аудио
           } else {
               // Размер из настроек
               settingsManager.getThumbnailSize()
           }
       
       override fun onBindViewHolder(holder: ViewHolder, position: Int) {
           holder.thumbnail.layoutParams.apply {
               width = thumbnailSize
               height = thumbnailSize
           }
           // остальная логика
       }
   }
   
   private fun Int.dpToPx(): Int {
       return (this * Resources.getSystem().displayMetrics.density).toInt()
   }
   ```

### Шаг 6: Обновление layout для аудио-режима
1. Если используется отдельный layout для списка, обеспечить фиксированный размер:
   `res/layout/item_media_audio_list.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <androidx.constraintlayout.widget.ConstraintLayout
       xmlns:android="http://schemas.android.com/apk/res/android"
       android:layout_width="match_parent"
       android:layout_height="wrap_content">
       
       <ImageView
           android:id="@+id/thumbnail"
           android:layout_width="48dp"
           android:layout_height="48dp"
           android:scaleType="centerCrop"
           ... />
       
       <!-- Остальные элементы -->
   </androidx.constraintlayout.widget.ConstraintLayout>
   ```

### Шаг 7: Индикатор аудио-режима (опционально)
1. Добавить визуальный индикатор того, что ресурс в аудио-режиме:
   ```kotlin
   private fun updateAudioModeIndicator() {
       if (isAudioOnlyMode) {
           binding.audioModeIndicator.apply {
               visibility = View.VISIBLE
               text = getString(R.string.audio_only_mode)
           }
       } else {
           binding.audioModeIndicator.visibility = View.GONE
       }
   }
   ```

### Шаг 8: Тестирование

#### Подготовка
1. Создать тестовый ресурс с типом "только аудио"
2. Создать обычный ресурс с несколькими типами файлов

#### Тестирование аудио-ресурса
1. Открыть аудио-ресурс в Browse
2. Проверить:
   - Все миниатюры имеют размер 48×48 пикселей
   - Кнопка переключения на сетку скрыта или неактивна
   - Отображается только режим списка
   - Настройки размера миниатюр не влияют на отображение

#### Тестирование обычного ресурса
1. Открыть обычный ресурс (с видео/фото)
2. Проверить:
   - Кнопка переключения режима активна и видна
   - Можно переключаться между списком и сеткой
   - Размер миниатюр соответствует настройкам

#### Тестирование переключения между ресурсами
1. Открыть аудио-ресурс → перейти к обычному ресурсу
2. Проверить: UI корректно обновился
3. Открыть обычный ресурс → перейти к аудио-ресурсу
4. Проверить: применились ограничения аудио-режима

## Критерии приемки
- ✅ Для аудио-ресурсов все миниатюры фиксированного размера 48×48 пикселей
- ✅ Настройки размера миниатюр не влияют на аудио-ресурсы
- ✅ Кнопка переключения на сетку скрыта/отключена для аудио-ресурсов
- ✅ Доступен только режим списка
- ✅ Для обычных ресурсов функционал не изменился
- ✅ Переключение между ресурсами работает корректно

## Файлы для изменения
- Модель ресурса: `MediaSource.kt` или аналогичный
- Browse экран: `BrowseActivity.kt` / `BrowseFragment.kt`
- Адаптер: `MediaBrowseAdapter.kt`
- Layout: возможно `item_media_list.xml`

## Зависимости
- Система определения типов ресурсов
- Текущая реализация Browse экрана

## Примечания
- Рассмотреть добавление tooltip при наведении на отключенную кнопку с объяснением
- Можно добавить специальную иконку/бейдж для аудио-ресурсов
