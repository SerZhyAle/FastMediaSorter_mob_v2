# Статус реализации файловых операций - FastMediaSorter v2

## 🎯 Общая картина

| Тип ресурса | Copy | Move | Delete | Rename |
|-------------|------|------|--------|--------|
| **Local**   | ✅   | ✅   | ✅     | ✅     |
| **SMB**     | ✅   | ✅   | ✅     | ❌     |
| **SFTP**    | ❌   | ❌   | ❌     | ❌     |

## 📊 Детальный статус

### Local (локальные файлы) - 100% ✅
- ✅ Copy: реализовано в `FileOperationUseCase.executeCopy()`
- ✅ Move: реализовано в `FileOperationUseCase.executeMove()`
- ✅ Delete: реализовано в `FileOperationUseCase.executeDelete()`
- ✅ Rename: реализовано в `FileOperationUseCase.executeRename()`

### SMB (Network Share) - 75% частично ✅
- ✅ Copy: SMB→Local, Local→SMB, SMB→SMB - `SmbFileOperationHandler.executeCopy()`
- ✅ Move: SMB→Local, Local→SMB, SMB→SMB - `SmbFileOperationHandler.executeMove()`
- ✅ Delete: SMB files - `SmbFileOperationHandler.executeDelete()`
- ❌ **Rename: НЕ РЕАЛИЗОВАНО**
  - Файл: `SmbClient.kt` - нет метода `renameFile()`
  - Файл: `SmbFileOperationHandler.kt` - нет метода `executeRename()`
  - Файл: `FileOperationUseCase.kt:76` - возвращает `Failure("Rename not supported for SMB")`

### SFTP (SSH File Transfer) - 0% ❌
- ❌ **Copy: НЕ РЕАЛИЗОВАНО**
  - Файл: `SftpClient.kt` - нет методов `downloadFile()` / `uploadFile()`
  - Отсутствует: `SftpFileOperationHandler.kt` (весь класс не создан)
  
- ❌ **Move: НЕ РЕАЛИЗОВАНО**
  - Зависит от Copy + Delete
  
- ❌ **Delete: НЕ РЕАЛИЗОВАНО**
  - Файл: `SftpClient.kt` - нет метода `deleteFile()`
  
- ❌ **Rename: НЕ РЕАЛИЗОВАНО**
  - Файл: `SftpClient.kt` - нет метода `renameFile()`

## 🔧 Что нужно реализовать

### Приоритет 1: SMB Rename (1-2 часа работы)

**Файлы для изменения:**

1. `app_v2/src/main/java/com/sza/fastmediasorter_v2/data/network/SmbClient.kt`
   ```kotlin
   suspend fun renameFile(
       connectionInfo: SmbConnectionInfo,
       oldPath: String,
       newName: String
   ): SmbResult<Unit>
   ```

2. `app_v2/src/main/java/com/sza/fastmediasorter_v2/data/network/SmbFileOperationHandler.kt`
   ```kotlin
   suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult {
       // Parse SMB path
       // Get credentials
       // Call smbClient.renameFile()
       // Return result
   }
   ```

3. `app_v2/src/main/java/com/sza/fastmediasorter_v2/domain/usecase/FileOperationUseCase.kt`
   ```kotlin
   // Строка 73-76: заменить
   is FileOperation.Rename -> {
       Timber.w("FileOperation.Rename: Not supported for SMB")
       FileOperationResult.Failure("Rename not supported for SMB resources")
   }
   // НА:
   is FileOperation.Rename -> smbFileOperationHandler.executeRename(operation)
   ```

4. `app_v2/src/main/java/com/sza/fastmediasorter_v2/ui/player/PlayerActivity.kt`
   ```kotlin
   // Строка 426-434: убрать проверку
   // Check if this is a network resource (SMB/SFTP)
   if (resource != null && (resource.type == ResourceType.SMB || resource.type == ResourceType.SFTP)) {
       Toast.makeText(...)
       return
   }
   ```

### Приоритет 2: SFTP Operations (4-6 часов работы)

**Новые файлы для создания:**

1. `app_v2/src/main/java/com/sza/fastmediasorter_v2/data/remote/sftp/SftpFileOperationHandler.kt`
   - Структура аналогична `SmbFileOperationHandler.kt`
   - Методы: `executeCopy()`, `executeMove()`, `executeDelete()`, `executeRename()`

**Файлы для изменения:**

1. `app_v2/src/main/java/com/sza/fastmediasorter_v2/data/remote/sftp/SftpClient.kt`
   - Добавить: `downloadFile(remotePath, outputStream)`
   - Добавить: `uploadFile(remotePath, inputStream)`
   - Добавить: `deleteFile(remotePath)`
   - Добавить: `renameFile(oldPath, newName)`

2. `app_v2/src/main/java/com/sza/fastmediasorter_v2/domain/usecase/FileOperationUseCase.kt`
   - Добавить dependency: `private val sftpFileOperationHandler: SftpFileOperationHandler`
   - Добавить проверку SFTP путей (аналогично SMB)
   - Добавить routing для SFTP операций

3. `app_v2/src/main/java/com/sza/fastmediasorter_v2/di/AppModule.kt` (или аналог)
   - Добавить `@Provides` для `SftpFileOperationHandler`

## 🧪 План тестирования

### SMB Rename
1. Открыть SMB ресурс в PlayerActivity
2. Выбрать файл
3. Нажать touch zone "Rename" (или жест вверх)
4. Ввести новое имя
5. Проверить: файл переименован на SMB share
6. Проверить: список обновился с новым именем

### SFTP Operations
1. **Copy SFTP→Local:**
   - Открыть SFTP ресурс
   - Скопировать файл в локальную папку
   - Проверить: файл появился локально
   
2. **Copy Local→SFTP:**
   - Открыть локальную папку
   - Скопировать файл в SFTP ресурс
   - Проверить: файл появился на SFTP сервере
   
3. **Move, Delete, Rename:**
   - Аналогично для каждой операции

## 📝 Дополнительные улучшения (опционально)

1. **Прогресс-бары для длительных операций**
   - Показывать прогресс при копировании больших файлов
   - Отображать скорость передачи (MB/s)
   
2. **Retry механизм**
   - Автоматический retry при сетевых ошибках
   - Configurable количество попыток
   
3. **Batch операции**
   - Копирование/перемещение нескольких файлов одновременно
   - Параллельная загрузка (для разных файлов)

## 🎓 Справочная информация

### Текущая архитектура обработки операций

```
PlayerActivity (UI)
    ↓
FileOperation (sealed class)
    ↓
FileOperationUseCase.execute()
    ↓
[определяет тип: Local/SMB/SFTP]
    ↓
├─ Local → FileOperationUseCase.executeXxx()
├─ SMB → SmbFileOperationHandler.executeXxx()
└─ SFTP → SftpFileOperationHandler.executeXxx() [НЕ РЕАЛИЗОВАНО]
    ↓
[low-level API]
├─ File() API (для Local)
├─ SmbClient (для SMB)
└─ SftpClient (для SFTP)
```

### Формат путей

- **Local:** `/storage/emulated/0/Download/file.jpg`
- **SMB:** `smb://192.168.1.100:445/share/folder/file.jpg`
- **SFTP:** `sftp://192.168.1.100:22/home/user/file.jpg`

### Проблема с File() API

```kotlin
val smbPath = "smb://192.168.1.100/share/file.jpg"
val file = File(smbPath)
println(file.absolutePath)  // "/smb:/192.168.1.100/share/file.jpg" ❌

// FileOperationUseCase обрабатывает оба варианта:
file.absolutePath.startsWith("smb://")   // false
file.absolutePath.startsWith("/smb:")    // true ✅
```

---

**Последнее обновление:** 2025-11-08  
**Статус:** SMB частично работает, SFTP не реализовано  
**Следующий шаг:** Реализовать SMB Rename (приоритет HIGH)
