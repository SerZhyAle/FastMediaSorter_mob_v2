# Спецификация #12: Toast при "Выбрать все"

## Описание задачи
В Browse режиме при нажатии кнопки "Выбрать все" показывать toast-сообщение с количеством выбранных файлов (например, "Выбрано 42 файла").

## Требования

### Функциональные требования
- При нажатии "Выбрать все" показывается toast с количеством файлов
- Формат сообщения: "Выбрано N файлов" с правильным склонением
- Toast должен быть коротким (Toast.LENGTH_SHORT)
- Сообщение должно быть локализовано (EN, RU, UK)

### Технические требования
- Правильное склонение числительных для русского и украинского языков
- Не показывать toast если файлов 0
- Toast не должен блокировать UI

## Пошаговая реализация

### Шаг 1: Найти кнопку "Выбрать все"
1. Найти BrowseActivity/Fragment
2. Найти обработчик кнопки "Select All":
   ```kotlin
   binding.selectAllButton.setOnClickListener {
       selectAllFiles()
   }
   ```

### Шаг 2: Создать функцию склонения
1. Создать файл `PluralHelper.kt`:
   ```kotlin
   object PluralHelper {
       
       /**
        * Получить правильное склонение для русского языка
        * @param count количество
        * @param singular единственное число (файл)
        * @param few несколько (файла)
        * @param many множество (файлов)
        */
       fun getRussianPlural(
           count: Int,
           singular: String,
           few: String,
           many: String
       ): String {
           val lastDigit = count % 10
           val lastTwoDigits = count % 100
           
           return when {
               lastTwoDigits in 11..14 -> many
               lastDigit == 1 -> singular
               lastDigit in 2..4 -> few
               else -> many
           }
       }
       
       /**
        * Украинское склонение (аналогично русскому)
        */
       fun getUkrainianPlural(
           count: Int,
           singular: String,
           few: String,
           many: String
       ): String {
           return getRussianPlural(count, singular, few, many)
       }
       
       /**
        * Английское склонение
        */
       fun getEnglishPlural(count: Int, singular: String, plural: String): String {
           return if (count == 1) singular else plural
       }
   }
   ```

### Шаг 3: Добавить строковые ресурсы

#### values/strings.xml (English)
```xml
<string name="selected_files">Selected %d file</string>
<string name="selected_files_plural">Selected %d files</string>
```

#### values-ru/strings.xml (Russian)
```xml
<string name="selected_files_one">Выбран %d файл</string>
<string name="selected_files_few">Выбрано %d файла</string>
<string name="selected_files_many">Выбрано %d файлов</string>
```

#### values-uk/strings.xml (Ukrainian)
```xml
<string name="selected_files_one">Вибрано %d файл</string>
<string name="selected_files_few">Вибрано %d файли</string>
<string name="selected_files_many">Вибрано %d файлів</string>
```

### Шаг 4: Создать утилиту для форматирования сообщения
```kotlin
object SelectionMessageFormatter {
    
    fun getSelectedFilesMessage(context: Context, count: Int): String {
        if (count == 0) return ""
        
        return when (Locale.getDefault().language) {
            "ru" -> formatRussian(context, count)
            "uk" -> formatUkrainian(context, count)
            else -> formatEnglish(context, count)
        }
    }
    
    private fun formatRussian(context: Context, count: Int): String {
        val lastDigit = count % 10
        val lastTwoDigits = count % 100
        
        val stringRes = when {
            lastTwoDigits in 11..14 -> R.string.selected_files_many
            lastDigit == 1 -> R.string.selected_files_one
            lastDigit in 2..4 -> R.string.selected_files_few
            else -> R.string.selected_files_many
        }
        
        return context.getString(stringRes, count)
    }
    
    private fun formatUkrainian(context: Context, count: Int): String {
        // Аналогично русскому
        return formatRussian(context, count)
    }
    
    private fun formatEnglish(context: Context, count: Int): String {
        val stringRes = if (count == 1) {
            R.string.selected_files
        } else {
            R.string.selected_files_plural
        }
        return context.getString(stringRes, count)
    }
}
```

### Шаг 5: Альтернативный подход - использовать Quantity Strings (рекомендуется)

#### values/strings.xml
```xml
<plurals name="selected_files_count">
    <item quantity="one">Selected %d file</item>
    <item quantity="other">Selected %d files</item>
</plurals>
```

#### values-ru/strings.xml
```xml
<plurals name="selected_files_count">
    <item quantity="one">Выбран %d файл</item>
    <item quantity="few">Выбрано %d файла</item>
    <item quantity="many">Выбрано %d файлов</item>
    <item quantity="other">Выбрано %d файлов</item>
</plurals>
```

#### values-uk/strings.xml
```xml
<plurals name="selected_files_count">
    <item quantity="one">Вибрано %d файл</item>
    <item quantity="few">Вибрано %d файли</item>
    <item quantity="many">Вибрано %d файлів</item>
    <item quantity="other">Вибрано %d файлів</item>
</plurals>
```

#### Использование:
```kotlin
val message = resources.getQuantityString(
    R.plurals.selected_files_count,
    count,
    count
)
```

### Шаг 6: Обновить обработчик "Выбрать все"
```kotlin
private fun selectAllFiles() {
    // Выбрать все файлы
    adapter.selectAll()
    
    // Получить количество
    val selectedCount = adapter.getSelectedCount()
    // ИЛИ
    val selectedCount = currentFiles.size
    
    // Показать toast
    if (selectedCount > 0) {
        showSelectionToast(selectedCount)
    }
    
    // Обновить UI
    updateSelectionUI()
}

private fun showSelectionToast(count: Int) {
    val message = resources.getQuantityString(
        R.plurals.selected_files_count,
        count,
        count
    )
    
    Toast.makeText(
        requireContext(),
        message,
        Toast.LENGTH_SHORT
    ).show()
}
```

### Шаг 7: Расширение (опционально)
Также показывать toast при других операциях с выбором:

```kotlin
private fun deselectAll() {
    adapter.deselectAll()
    Toast.makeText(
        requireContext(),
        R.string.selection_cleared,
        Toast.LENGTH_SHORT
    ).show()
}

private fun invertSelection() {
    adapter.invertSelection()
    val selectedCount = adapter.getSelectedCount()
    showSelectionToast(selectedCount)
}
```

Строки:
```xml
<string name="selection_cleared">Выбор снят</string>
```

### Шаг 8: Тестирование

#### Тест 1: Основной функционал
1. Открыть папку с файлами в Browse
2. Нажать "Выбрать все"
3. Проверить:
   - Появился toast
   - Показано правильное количество файлов
   - Склонение правильное

#### Тест 2: Различные количества (русский/украинский)
- 1 файл: "Выбран 1 файл"
- 2 файла: "Выбрано 2 файла"
- 5 файлов: "Выбрано 5 файлов"
- 11 файлов: "Выбрано 11 файлов"
- 21 файл: "Выбран 21 файл"
- 22 файла: "Выбрано 22 файла"
- 100 файлов: "Выбрано 100 файлов"

#### Тест 3: Локализация
1. Переключить язык на English
2. Проверить: "Selected N file(s)"
3. Переключить на русский
4. Проверить: правильное склонение
5. Переключить на украинский
6. Проверить: правильное склонение

#### Тест 4: Пустая папка
1. Открыть пустую папку
2. Нажать "Выбрать все"
3. Проверить: toast не показывается (или показывается "Нет файлов")

#### Тест 5: Быстрое нажатие
1. Быстро нажать "Выбрать все" несколько раз
2. Проверить: toast не накладываются друг на друга некорректно

## Критерии приемки
- ✅ При клике "Выбрать все" показывается toast с количеством файлов
- ✅ Склонение правильное для всех языков (EN, RU, UK)
- ✅ Склонение корректно для различных чисел (1, 2-4, 5+, 11-14)
- ✅ Toast короткий (LENGTH_SHORT)
- ✅ Не показывается при 0 файлов

## Файлы для создания/изменения
- Изменить: `BrowseActivity.kt` / `BrowseFragment.kt`
- Изменить: `res/values/strings.xml` (добавить plurals)
- Изменить: `res/values-ru/strings.xml` (добавить plurals)
- Изменить: `res/values-uk/strings.xml` (добавить plurals)

## Примечания
- Рекомендуется использовать встроенный механизм plurals Android
- Убедиться, что toast не перекрывает важные элементы UI
- Можно использовать Snackbar вместо Toast для лучшего UX
- Рассмотреть добавление звукового или вибро-отклика
