# Задача 7: Операции с папками (Будущая функциональность)

## Описание
Расширение функциональности работы с папками при включенном режиме "Показывать подпапки отдельно". Добавление возможности выбора, копирования, перемещения, переименования и добавления папок в избранное.

## Приоритет
🔵 Будущее (зависит от Задачи 5)

## Примечания
⚠️ **Эта задача будет реализована ПОЗЖЕ, после Задачи 5**  
⚠️ **Не включать в текущий спринт**

## Затронутые файлы

**UseCase (новые):**
- `domain/usecase/CopyFolderUseCase.kt`
- `domain/usecase/MoveFolderUseCase.kt`
- `domain/usecase/CreateFolderUseCase.kt`
- `domain/usecase/RenameFolderUseCase.kt`
- `domain/usecase/DeleteFolderUseCase.kt`

**UI:**
- `BrowseActivity.kt`
- `MediaFileAdapter.kt`
- Dialogs: CreateFolderDialog, RenameFolderDialog

---

## Промпты для разработки

### Промпт 1: Выбор папок (Selection)
```
Добавь возможность выбора папок checkbox:

В MediaFileAdapter:

```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val item = getItem(position)
    
    // Show checkbox для папок тоже
    holder.checkbox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
    holder.checkbox.isChecked = selectedItems.contains(item.path)
    
    if (item.isDirectory) {
        // Folder can be selected
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            onFolderSelectionChanged(item, isChecked)
        }
    }
}
```

В BrowseActivity:
- Отслеживать количество выбранных folders vs files
- Action bar: "Выбрано: 3 папки, 5 файлов"
```

### Промпт 2: Избранное для папок
```
Расширь функцию Favorites для папок:

**БД изменения:**
- В таблице favorites добавить поле `is_directory BOOLEAN`
- При добавлении папки в избранное сохранять как directory

**UI:**
- В меню папки добавить "Добавить в избранное"
- В Favorites показывать папки отдельно от файлов
- При клике на папку в Favorites - открывать её содержимое

Реализуй полный flow добавления папки в Favorites.
```

### Промпт 3: Копирование папки
```
Создай CopyFolderUseCase для рекурсивного копирования:

Файл: `domain/usecase/CopyFolderUseCase.kt`

```kotlin
class CopyFolderUseCase(
    private val resourceManager: ResourceManager
) {
    suspend fun execute(
        sourcePath: String,
        destinationPath: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Get all files recursively
            val allFiles = getAllFilesRecursive(sourcePath)
            val totalFiles = allFiles.size
            
            // 2. Create destination folder
            resourceManager.createDirectory(destinationPath)
            
            // 3. Copy each file
            allFiles.forEachIndexed { index, file ->
                val relativePath = file.path.removePrefix(sourcePath)
                val destFile = destinationPath + relativePath
                
                // Create subdirectories if needed
                val parentDir = destFile.substringBeforeLast('/')
                resourceManager.createDirectory(parentDir)
                
                // Copy file
                resourceManager.copyFile(file.path, destFile)
                
                onProgress(index + 1, totalFiles)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

Добавь progress dialog при копировании.
```

### Промпт 4: Перемещение папки
```
Создай MoveFolderUseCase:

Аналогично CopyFolderUseCase, но:
1. Копировать все файлы в destination
2. Удалить source folder полностью
3. Если ошибка при копировании - НЕ удалять source (rollback)

Сделать atomic operation где возможно (для Local можно использовать File.renameTo).
```

### Промпт 5: Создание папки
```
Реализуй функцию создания новой папки:

**UI (BrowseActivity):**
- FAB button или пункт меню "Создать папку"
- Диалог с:
  - EditText для имени папки
  - Caption: "Будет создана в [current directory]"
  - Buttons: Cancel, Create

**UseCase:**
```kotlin
class CreateFolderUseCase {
    suspend fun execute(parentPath: String, folderName: String): Result<Unit> {
        // 1. Validate name (no special chars, not exists)
        if (!isValidFolderName(folderName)) {
            return Result.failure(Exception("Invalid folder name"))
        }
        
        val fullPath = "$parentPath/$folderName"
        if (resourceManager.exists(fullPath)) {
            return Result.failure(Exception("Folder already exists"))
        }
        
        // 2. Create folder
        resourceManager.createDirectory(fullPath)
        
        return Result.success(Unit)
    }
}
```

Протестируй создание на Local, SMB, Cloud.
```

### Промпт 6: Переименование папки
```
Реализуй переименование папки:

**Challenges:**
- Папка может содержать тысячи файлов
- Пути к файлам должны обновиться
- Если папка открыта - нужно обновить navigation stack

**Logic:**
```kotlin
class RenameFolderUseCase {
    suspend fun execute(folderPath: String, newName: String): Result<Unit> {
        // 1. Validate new name
        // 2. Check не существует ли уже папка с таким именем
        // 3. Переименовать на уровне file system
        // 4. Если файлы из этой папки в favorites - обновить paths
        
        val parentPath = folderPath.substringBeforeLast('/')
        val newPath = "$parentPath/$newName"
        
        resourceManager.renameDirectory(folderPath, newPath)
        
        // Update favorites paths if needed
        favoritesRepository.updatePaths(folderPath, newPath)
        
        return Result.success(Unit)
    }
}
```

Тестируй на папке с файлами в Favorites.
```

### Промпт 7: Удаление папки
```
Реализуй безопасное удаление папки:

**UI:**
```kotlin
private fun confirmDeleteFolder(folder: MediaFile) {
    // Count files recursively
    val fileCount = countFilesInFolder(folder.path)
    
    val message = """
        Удалить папку "${folder.name}"?
        
        Будет удалено:
        - $fileCount файлов
        - Все подпапки
        
        Это действие нельзя отменить!
    """.trimIndent()
    
    AlertDialog.Builder(this)
        .setTitle("Удаление папки")
        .setMessage(message)
        .setPositiveButton("Удалить") { _, _ ->
            viewModel.deleteFolder(folder)
        }
        .setNegativeButton("Отмена", null)
        .show()
}
```

**UseCase:**
- Рекурсивное удаление всех файлов
- Progress dialog если >100 файлов
- Интеграция с .trash (если Task 4 реализована)

Протестируй на папке с 50+ файлами.
```

### Промпт 8: Batch operations для папок
```
Разреши batch операции на выбранных папках:

Если выбрано:
- 2 папки + 3 файла

Доступные операции:
- ✅ Copy all → рекурсивно копировать папки и файлы
- ✅ Move all → аналогично
- ✅ Delete all → с подтверждением общего кол-ва файлов
- ❌ Rename - недоступно для multiple selection
- ❌ Add to Favorites - пока не поддерживается для batch

Long-press menu должен корректно отображать доступные операции.
```

### Промпт 9: Интеграция и тестирование
```
Финальная интеграция и тестирование:

**Тест 1: Selection**
- ✅ Выбрать 2 папки и 3 файла
- ✅ Action bar показывает правильные числа
- ✅ Операции применяются ко всем выбранным

**Тест 2: Favorites**
- ✅ Добавить папку в Favorites
- ✅ Открыть Favorites
- ✅ Кликнуть на папку - открывается её содержимое

**Тест 3: Copy/Move**
- ✅ Копировать папку с 20 файлами
- ✅ Progress отображается
- ✅ Все файлы скопированы корректно
- ✅ Move работает аналогично

**Тест 4: Create/Rename/Delete**
- ✅ Создать новую папку
- ✅ Переименовать её
- ✅ Удалить с подтверждением

**Тест 5: Разные ресурсы**
- ✅ Local resource
- ✅ SMB share
- ❓ Google Drive (если поддерживается)

Запиши результаты всех тестов.
```

## Критерии готовности
- ✅ Папки можно выбирать checkbox
- ✅ Папки можно добавлять в Favorites
- ✅ Copy folder работает рекурсивно с progress
- ✅ Move folder работает
- ✅ Create новой папки работает
- ✅ Rename папки работает
- ✅ Delete папки с подтверждением работает
- ✅ Batch operations на папки+файлы работают
- ✅ Все операции работают для Local и SMB
