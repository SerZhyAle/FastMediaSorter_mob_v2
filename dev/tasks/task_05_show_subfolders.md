# Задача 5: Режим "Показывать подпапки отдельно"

## Описание
Новый флаг в настройках "показывать подпапки отдельно". Работает только для ресурсов с включенным "сканировать подпапки". Если включено - в списке отображаются папки, клик открывает папку. Если выключено (по умолчанию) - подпапки не видны, все файлы в общем списке.

## Приоритет
🟢 Низкий (улучшение UX)

## Требования
- Папки всегда сверху, всегда по алфавиту
- Режим сорти ровки только для файлов
- Глобальная настройка с возможностью override для каждого ресурса
- ⚠️ Требуется БД миграция

## Затронутые файлы

**Модель:**
- `data/model/MediaFile.kt` - добавить `isDirectory`
- `data/model/MediaResource.kt` - добавить `showSubfoldersAsItems`
- `data/model/BrowseState.kt` - навигация по папкам

**Repository/UseCase:**
- `MediaFileRepository.kt`
- `LocalResourceManager.kt`, `SmbResourceManager.kt`

**ViewModel:**
- `BrowseViewModel.kt`

**UI:**
- `BrowseActivity.kt`
- `MediaFileAdapter.kt`
- `activity_browse.xml`
- Settings (fragment)

---

## Промпты для разработки

### Промпт 1: Расширение моделей данных
```
Добавь поддержку директорий в модель данных:

1. **MediaFile.kt:**
```kotlin
data class MediaFile(
    // ... existing fields
    val isDirectory: Boolean = false,
    val directoryPath: String? = null,
    val fileCount: Int? = null  // кол-во файлов в папке (optional)
)
```

2. **MediaResource.kt:**
```kotlin
data class MediaResource(
    // ... existing fields
    val showSubfoldersAsItems: Boolean? = null  // null = use global setting
)
```

3. **BrowseState.kt:**
```kotlin
data class BrowseState(
    // ... existing fields
    val currentDirectory: String? = null,  // текущая открытая папка
    val directoryStack: List<String> = emptyList(),  // для навигации назад
    val showSubfoldersAsItems: Boolean = false
)
```

Объясни как эти поля будут использоваться.
```

### Промпт 2: БД миграция
```
Создай миграцию БД для добавления нового поля:

1. Найди текущую версию БД
2. Создай миграцию X → X+1:
   - ALTER TABLE resources ADD COLUMN show_subfolders_as_items INTEGER (nullable)
3. В DAO добавь поддержку нового поля
4. Обнови entity class MediaResourceEntity

Покажи код миграции и проверь что она работает.
```

### Промпт 3: Settings UI
```
Добавь глобальную настройку и per-resource override:

**Глобальная настройка (Settings fragment):**
- Title: "Показывать подпапки отдельно"
- Summary: "Папки будут отображаться как отдельные элементы"
- Default: OFF
- Key: "show_subfolders_as_items"

**Per-resource override (Resource Edit screen):**
- Checkbox: "Показывать подпапки отдельно"
- Enabled только если `scanSubdirectories == true`
- 3 состояния:
  - Null (use global) - показать текущее global значение
  - True (override to ON)
  - False (override to OFF)

Реализуй UI для обоих экранов.
```

### Промпт 4: Логика загрузки с папками
```
Модифицируй BrowseViewModel.loadMediaFilesStandard():

Если `showSubfoldersAsItems == true`:

1. Загружать только из текущей директории (не рекурсивно)
2. Получить список подпапок: `getSubdirectories(currentDirectory)`
3. Для каждой подпапки создать MediaFile с `isDirectory = true`
4. Получить файлы из текущей директории (без подпапок)
5. Объединить: сначала папки (sorted alphabetically), потом файлы (sorted по sortMode)

Если `showSubfoldersAsItems == false`:
- Текущее поведение (рекурсивно все файлы)

Покажи diff для метода loadMediaFilesStandard.
```

### Промпт 5: Сортировка папок и файлов
```
Реализуй правило: папки всегда сверху по алфавиту:

В BrowseViewModel где происходит сортировка файлов:

```kotlin
fun sortMediaFiles(files: List<MediaFile>, sortMode: SortMode): List<MediaFile> {
    // Разделить на folders и files
    val folders = files.filter { it.isDirectory }.sortedBy { it.name }
    val regularFiles = files.filter { !it.isDirectory }
    
    // Применить sortMode только к файлам
    val sortedFiles = when (sortMode) {
        SortMode.NAME_ASC -> regularFiles.sortedBy { it.name }
        SortMode.DATE_DESC -> regularFiles.sortedByDescending { it.createdDate }
        // ... other modes
    }
    
    // Folders всегда сверху
    return folders + sortedFiles
}
```

Интегрируй эту логику в существующий код сортировки.
```

### Промпт 6: Навигация по папкам
```
Реализуй навигацию (открытие папок):

**В MediaFileAdapter:**
```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val item = getItem(position)
    
    if (item.isDirectory) {
        // Показать иконку папки
        holder.thumbnail.setImageResource(R.drawable.ic_folder)
        holder.itemView.setOnClickListener {
            onFolderClickListener?.invoke(item.directoryPath!!)
        }
    } else {
        // Обычный файл - текущая логика
    }
}
```

**В BrowseActivity:**
```kotlin
adapter.onFolderClickListener = { folderPath ->
    viewModel.navigateToDirectory(folderPath)
}
```

**В BrowseViewModel:**
```kotlin
fun navigateToDirectory(path: String) {
    val currentStack = state.value.directoryStack
    updateState {
        it.copy(
            currentDirectory = path,
            directoryStack = currentStack + path
        )
    }
    loadMediaFiles()  // reload для новой директории
}
```

Реализуй полную цепочку навигации.
```

### Промпт 7: Breadcrumb и кнопка "Назад"
```
Добавь UI для навигации в BrowseActivity:

1. **Breadcrumb (TextView в toolbar):**
   - Показывать путь: "Resource / Folder1 / Folder2"
   - Или просто название текущей папки
   - Обновлять при навигации

2. **Кнопка "Назад":**
   - Переопределить onBackPressed():
   ```kotlin
   override fun onBackPressed() {
       if (viewModel.canNavigateUp()) {
           viewModel.navigateUp()
       } else {
           super.onBackPressed()  // выйти из Activity
       }
   }
   ```

3. **В ViewModel:**
   ```kotlin
   fun canNavigateUp() = state.value.directoryStack.isNotEmpty()
   
   fun navigateUp() {
       val stack = state.value.directoryStack
       if (stack.isEmpty()) return
       
       val newStack = stack.dropLast(1)
       val newDirectory = newStack.lastOrNull()
       
       updateState {
           it.copy(
               currentDirectory = newDirectory,
               directoryStack = newStack
           )
       }
       loadMediaFiles()
   }
   ```

Реализуй breadcrumb и navigation back.
```

### Промпт 8: Repository support для директорий
```
Добавь методы для работы с директориями в каждом Manager:

**LocalResourceManager:**
```kotlin
suspend fun getSubdirectories(parentPath: String): List<File> {
    return File(parentPath).listFiles { file -> file.isDirectory }
        ?.toList() ?: emptyList()
}

suspend fun getFilesInDirectory(path: String, includeSubfolders: Boolean): List<MediaFile> {
    // Scan только из этой папки
}
```

**SmbResourceManager:**
- Аналогично для SMB shares

**CloudManagers:**
- Для Google Drive / OneDrive

Реализуй для каждого типа ресурса.
```

### Промпт 9: Тестирование
```
Протестируй режим показа подпапок:

**Тест 1: Включить режим**
- Открой ресурс с подпапками
- Включи "показывать подпапки отдельно"
- Проверь:
  - ✅ Папки отображаются сверху
  - ✅ Папки отсортированы по алфавиту
  - ✅ Файлы ниже папок
  - ✅ sortMode влияет только на файлы

**Тест 2: Навигация**
- Кликни на папку
- Проверь:
  - ✅ Открылась папка (показаны её файлы)
  - ✅ Breadcrumb обновился
  - ✅ Back button возвращает наверх

**Тест 3: Глубокая навигация**
- Зайди на 3-4 уровня вглубь
- Проверь навигацию назад работает корректно

**Тест 4: Выключить режим**
- Выключи "показывать подпапки отдельно"
- Проверь:
  - ✅ Папки исчезли
  - ✅ Все файлы из всех подпапок в одном списке

Запиши результаты.
```

## Критерии готовности
- ✅ БД миграция создана и работает
- ✅ Глобальная настройка работает
- ✅ Per-resource override работает
- ✅ Папки отображаются сверху по алфавиту
- ✅ Клик по папке открывает её
- ✅ Навигация назад работает
- ✅ Breadcrumb показывает текущий путь
- ✅ Режим сортировки влияет только на файлы
- ✅ Работает для Local, SMB, Cloud
