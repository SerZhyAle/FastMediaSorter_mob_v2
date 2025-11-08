# Улучшения логирования операций копирования/переноса

## Дата: 2025-11-08

## Проблема
При ошибках копирования/переноса файлов:
- Всплывало общее сообщение "ошибка копирования"
- В логах отсутствовала информация о деталях операции
- Невозможно было понять причину сбоя

## Внесенные изменения

### 1. FileOperationUseCase.kt
**Добавлено детальное логирование:**
- Начало каждой операции с указанием типа и количества файлов
- Прогресс обработки каждого файла (индекс/всего)
- Размер файлов, пути источника/назначения
- Время выполнения каждой операции
- Тип ошибок с полным stack trace
- Итоговый статус: SUCCESS/PARTIAL SUCCESS/FAILURE

**Теги логов:** `FileOperation`, `executeCopy`, `executeMove`

**Примеры:**
```
D FileOperation: Starting operation: Copy
D FileOperation.Copy: sources=0/3 SMB, dest=Local
D executeCopy: Starting local copy of 3 files to /storage/emulated/0/Pictures
D executeCopy: [1/3] Processing image001.jpg
D executeCopy: Target: /storage/emulated/0/Pictures/image001.jpg, size=2145678 bytes
I executeCopy: SUCCESS - image001.jpg copied in 234ms
E executeCopy: ERROR - Failed to copy image002.jpg: IOException - No space left on device
```

### 2. SmbFileOperationHandler.kt
**Добавлено детальное логирование SMB операций:**
- Тип операции (SMB→Local, Local→SMB, SMB→SMB)
- Парсинг SMB путей (server, share, remote path)
- Размер передаваемых данных
- Время загрузки/выгрузки
- Двухэтапные операции (copy+delete для move)
- Детали ошибок на каждом этапе

**Теги логов:** `SMB executeCopy`, `SMB executeMove`, `downloadFromSmb`, `uploadToSmb`, `deleteFromSmb`, `copySmbToSmb`

**Примеры:**
```
D SMB executeMove: Starting move of 1 files to smb://192.168.1.10/share/folder
D SMB executeMove: [1/1] Processing video.mp4
D SMB executeMove: Source=Local, Dest=SMB
D uploadToSmb: /storage/emulated/0/DCIM/video.mp4 → smb://192.168.1.10/share/folder/video.mp4
D uploadToSmb: Local file size=15234567 bytes
D uploadToSmb: Parsed - server=192.168.1.10, share=share, path=folder/video.mp4
I uploadToSmb: SUCCESS - uploaded video.mp4
D SMB executeMove: Uploaded in 3456ms, attempting local delete
I SMB executeMove: SUCCESS - moved video.mp4 in 3478ms
```

### 3. CopyToDialog.kt & MoveToDialog.kt
**Улучшенный показ ошибок пользователю:**
- При `PartialSuccess`: показывается ErrorDialog со списком ошибок (до 5 штук)
- При `Failure`: показывается ErrorDialog с основной ошибкой и ссылкой на logcat
- При `Exception`: показывается ErrorDialog с полным stack trace
- Все диалоги поддерживают копирование в буфер обмена

**Было:**
```kotlin
Toast.makeText(context, "Copy failed: ${result.error}", Toast.LENGTH_LONG).show()
```

**Стало:**
```kotlin
ErrorDialog.show(
    context,
    "Copy Failed",
    getString(R.string.copy_failed, result.error),
    "Check logcat for detailed information (tag: FileOperation)"
)
```

## Как использовать

### Для отладки в Android Studio:
1. Открыть Logcat
2. Фильтр: `FileOperation OR executeCopy OR executeMove OR SMB`
3. При ошибке копирования искать последовательность:
   ```
   FileOperation: Starting operation
   → executeCopy/executeMove: Starting...
   → Прогресс по файлам
   → ERROR/FAILURE с деталями
   ```

### Для пользователя:
- При ошибке появляется диалог с деталями
- Кнопка "Copy to Clipboard" копирует текст ошибки
- Можно отправить разработчику для анализа

## Примеры логов при разных сценариях

### Успешная локальная копия:
```
D FileOperation: Starting operation: Copy
D executeCopy: Starting local copy of 2 files to /storage/emulated/0/Download
D executeCopy: [1/2] Processing photo.jpg
I executeCopy: SUCCESS - photo.jpg copied in 156ms
D executeCopy: [2/2] Processing video.mp4
I executeCopy: SUCCESS - video.mp4 copied in 2345ms
I executeCopy: All 2 files copied successfully
I FileOperation: SUCCESS - processed 2 files
```

### Частичный успех:
```
D executeCopy: Starting local copy of 3 files
D executeCopy: [1/3] Processing file1.jpg
I executeCopy: SUCCESS - file1.jpg copied in 123ms
D executeCopy: [2/3] Processing file2.jpg
E executeCopy: ERROR - Failed to copy file2.jpg: FileAlreadyExistsException - File already exists
D executeCopy: [3/3] Processing file3.jpg
I executeCopy: SUCCESS - file3.jpg copied in 234ms
W executeCopy: Partial success - 2/3 files copied. Errors: [Failed to copy file2.jpg: ...]
W FileOperation: PARTIAL SUCCESS - 2 ok, 1 failed
```

### Ошибка SMB:
```
D SMB executeMove: Local→SMB - uploading file.pdf
D uploadToSmb: /storage/file.pdf → smb://server/share/file.pdf
D uploadToSmb: Parsed - server=server, share=share, path=file.pdf
E uploadToSmb: FAILED - Connection timed out
E SMB executeMove: Failed to upload file.pdf to SMB
E SMB executeMove: All move operations failed. Errors: [Failed to upload...]
E FileOperation: FAILURE - All move operations failed: Failed to upload file.pdf to SMB
```

## Тестирование
1. Запустить приложение в debug-режиме
2. Выполнить операции копирования/переноса
3. Проверить наличие логов в Logcat
4. При ошибке проверить детальность диалога

## Release сборка
Timber автоматически отключает DEBUG/VERBOSE логи в release,
но ERROR/WARN логи сохраняются для отладки через logcat после установки.
