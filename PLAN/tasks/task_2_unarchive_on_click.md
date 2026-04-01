# Детализированное техзадание: Разархивация по клику на архив

> **Статус**: готово к реализации  
> **Дата**: 2026-04-01  
> **Ревизия**: 2 (исправлены имена классов, точки входа и архитектурные детали по реальному коду)

---

## 1. Цель задачи

Добавить возможность распаковки ZIP-архивов в один клик внутри экрана Browse.  
При нажатии на файл типа `BINARY_ARCHIVE` вместо открытия бинарного меню показывается диалог подтверждения → распаковка с прогрессом → Snackbar с кнопкой «Открыть» для перехода в созданную папку.

**Что НЕ входит в задачу**: поддержка RAR/7z/TAR (только ZIP через `ZipInputStream`); распаковка во внешние источники (SMB/Cloud — только локально и SD-карта).

---

## 2. Область изменений в UI

| Элемент | Класс | Путь |
|---------|-------|------|
| Главный экран | `BrowseActivity` | `ui/browse/BrowseActivity.kt` |
| ViewModel | `BrowseViewModel` | `ui/browse/BrowseViewModel.kt` |
| Адаптер списка | `MediaFileAdapter` + `PagingMediaFileAdapter` | `ui/browse/MediaFileAdapter.kt`, `PagingMediaFileAdapter.kt` |
| Диалог подтверждения | `UnarchiveConfirmDialog` (новый) | `ui/browse/managers/` |
| Прогресс | `FileOperationProgressDialog` (уже есть) | `ui/dialog/FileOperationProgressDialog.kt` |

**Точка входа**: `onBinaryFileClick` в `BrowseActivity.setupViews()`.  
Сейчас все `BINARY_*` файлы идут туда и показывают `showBinaryFileMenu()`.  
Нужно в `showBinaryFileMenu()` добавить специальную ветку для `BINARY_ARCHIVE`.

---

## 3. Типы файлов

`MediaType.BINARY_ARCHIVE` уже определён в `domain/model/Models.kt`:

```
BINARY_ARCHIVE  →  .zip, .rar, .7z, .tar, .gz, .bz2, .xz
```

**Реализация через `ZipInputStream` охватывает только `.zip`**.  
Для первой версии поддерживаем только `.zip`. Детектор — `MediaTypeUtils.getMediaType(fileName)` из `data/common/MediaTypeUtils.kt` (использовать как есть, ничего не менять в `MediaType`).

> ⚠️ `.jar` и `.apk` — это `BINARY_EXECUTABLE`, а не `BINARY_ARCHIVE`. Не включать в фичу.

---

## 4. Domain Layer

### 4.1 `ExtractArchiveUseCase` (новый)

**Путь**: `domain/usecase/ExtractArchiveUseCase.kt`

```kotlin
class ExtractArchiveUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun invoke(
        archivePath: String,
        targetDirPath: String,
        onCancel: () -> Boolean          // возвращает true — прерваться
    ): Flow<ExtractProgress>
}

sealed class ExtractProgress {
    data class Started(val totalEntries: Int) : ExtractProgress()
    data class EntryDone(val entryName: String, val done: Int, val total: Int) : ExtractProgress()
    data class Success(val extractedCount: Int, val targetPath: String) : ExtractProgress()
    data class Failure(val error: String) : ExtractProgress()
}
```

**Требования к реализации**:
- `Dispatchers.IO` внутри (caller во ViewModel запускает через `viewModelScope.launch(Dispatchers.IO)`).
- Потоковое извлечение через `ZipInputStream` — не накапливать в памяти.
- Сохранять иерархию папок из `ZipEntry.name`.
- **Zip Bomb защита**: лимит `MAX_UNCOMPRESSED_SIZE = 2 GB`, `MAX_ENTRIES = 100 000`, максимальная глубина вложенности `MAX_DEPTH = 10`. При превышении — `ExtractProgress.Failure`.
- **Path traversal защита**: проверять, что `ZipEntry.name` после нормализации находится внутри `targetDirPath`. Если нет — пропустить запись с `Timber.w(...)`.
- Кодировка имён: попытаться UTF-8, при ошибке — `Charset.forName("CP866")`. Использовать `ZipInputStream(inputStream, Charsets.UTF_8)` с fallback-логикой.
- При `onCancel()` == true — прервать цикл, выбросить `ExtractProgress.Failure("cancelled")`.

**Аналог для ориентира**: `ArchiveFilesUseCase.kt` в `domain/usecase/` (паттерн `Flow<Progress>` + `ZipOutputStream`).

### 4.2 Папка назначения

**Не выносить в отдельный UseCase**. Логику определения целевой папки реализовать приватным extension-методом в `ExtractArchiveUseCase`:

```
"photos.zip"        →  "photos/"
"photos/" уже есть  →  "photos_1/", "photos_2/", ...  (до _99, затем ошибка)
```

Использовать `CreateDirectoryUseCase` (уже существует в `domain/usecase/CreateDirectoryUseCase.kt`) для физического создания папки.

---

## 5. ViewModel Layer

**Файл**: `ui/browse/BrowseViewModel.kt`

### 5.1 Новые методы

```kotlin
// Запуск распаковки
fun extractArchive(file: MediaFile)

// Отмена текущей распаковки
fun cancelExtraction()
```

### 5.2 Расширение `BrowseState`

Добавить поля в `BrowseState` (или вынести в отдельный `ExtractionState` как вложенный data class):

```kotlin
data class ExtractionState(
    val isExtracting: Boolean = false,
    val currentEntry: String = "",
    val progress: Int = 0,          // 0..100
    val totalEntries: Int = 0,
    val doneEntries: Int = 0,
    val targetPath: String = ""     // заполняется после Success
)
```

### 5.3 Новые события `BrowseEvent`

```kotlin
data class ShowExtractConfirmDialog(val file: MediaFile, val targetDirName: String) : BrowseEvent()
object ExtractionSuccess : BrowseEvent()    // Snackbar + предложение открыть папку
data class ExtractionFailed(val error: String) : BrowseEvent()
```

### 5.4 Поток данных

```
BrowseActivity.showBinaryFileMenu(file)
  └─ if file.mediaType == BINARY_ARCHIVE
       → viewModel.prepareExtraction(file)    // вычислить targetDirName
         → sendEvent(ShowExtractConfirmDialog)
           → BrowseActivity показывает UnarchiveConfirmDialog
             → on confirm: viewModel.extractArchive(file)
               → collectFlow(ExtractArchiveUseCase)
                 → обновляет state.extractionState
                   → FileOperationProgressDialog.update(...)
                 → on Success: sendEvent(ExtractionSuccess)
                   → Snackbar "Архив распакован" + кнопка "Открыть"
                     → viewModel.navigateToFolder(state.extractionState.targetPath)
```

---

## 6. UI Layer — `BrowseActivity`

### 6.1 Изменить `showBinaryFileMenu(file: MediaFile)`

Добавить ветку перед отображением меню:

```kotlin
private fun showBinaryFileMenu(file: MediaFile) {
    if (file.mediaType == MediaType.BINARY_ARCHIVE) {
        // новая ветка → обработка через ViewModel
        viewModel.prepareExtraction(file)
        return
    }
    // существующий код меню...
}
```

### 6.2 Наблюдатели в `BrowseActivity.observeEvents()`

- `ShowExtractConfirmDialog` → показать `UnarchiveConfirmDialog`
- `ExtractionSuccess` → скрыть `progressDialog`; показать Snackbar
- `ExtractionFailed("cancelled")` → скрыть `progressDialog` тихо
- `ExtractionFailed(error)` → скрыть `progressDialog`; показать `Toast` с ошибкой

### 6.3 `UnarchiveConfirmDialog`

**Путь**: `ui/browse/managers/UnarchiveConfirmDialog.kt` (или `ui/dialog/`)  
Реализовать как `AlertDialog` через `MaterialAlertDialogBuilder`:

```
Заголовок : "Распаковать архив?"
Сообщение : "«{имя_файла}» → папка «{целевая_папка}»"
Positive   : "Распаковать"
Negative   : "Отмена"
```

### 6.4 Прогресс

Использовать **существующий** `FileOperationProgressDialog` (`ui/dialog/`).  
Создать `fun update(entry: String, done: Int, total: Int)` или адаптировать существующий `update(FileOperationProgress)` — проверить совместимость.  
Показывать с задержкой 1.5 с (аналог существующей логики в диалоге).

### 6.5 Snackbar после успеха

```kotlin
Snackbar.make(binding.root, R.string.unarchive_success, Snackbar.LENGTH_LONG)
    .setAction(R.string.action_open) {
        viewModel.navigateToFolder(viewModel.state.value.extractionState.targetPath)
    }
    .show()
```

---

## 7. Строковые ресурсы

Добавить во все три локали:

| Ключ | EN | RU | UK |
|------|----|----|----|
| `unarchive_dialog_title` | `"Extract archive?"` | `"Распаковать архив?"` | `"Розпакувати архів?"` |
| `unarchive_dialog_message` | `""%1$s" → folder "%2$s""` | `"«%1$s» → папка «%2$s»"` | `"«%1$s» → тека «%2$s»"` |
| `unarchive_progress_entry` | `"Extracting: %s"` | `"Извлечение: %s"` | `"Вилучення: %s"` |
| `unarchive_success` | `"Archive extracted"` | `"Архив распакован"` | `"Архів розпакований"` |
| `unarchive_error_zip_bomb` | `"Archive too large or too deep"` | `"Архив слишком большой или глубокий"` | `"Архів занадто великий або глибокий"` |
| `unarchive_error_no_space` | `"Not enough storage space"` | `"Недостаточно места"` | `"Недостатньо місця"` |
| `action_open` | `"Open"` | `"Открыть"` | `"Відкрити"` |

**Файлы**:
- `app_v2/src/main/res/values/strings.xml` (EN, canonical)
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

> Проверить: если `action_open` уже существует — не дублировать.

---

## 8. Пошаговый чеклист имплементации

### Шаг 1: Domain — `ExtractArchiveUseCase`
- [ ] Создать `domain/usecase/ExtractArchiveUseCase.kt` с `ExtractProgress` sealed class
- [ ] Реализовать `ZipInputStream`-цикл с Zip Bomb защитой и path traversal защитой
- [ ] Реализовать логику определения целевой папки (приватный метод, использовать `CreateDirectoryUseCase`)
- [ ] Unit-тест: успешная распаковка, Zip Bomb, path traversal, отмена, CP866-имена

### Шаг 2: ViewModel — `BrowseViewModel`
- [ ] Добавить `ExtractionState` в `BrowseState`
- [ ] Добавить новые события в `BrowseEvent`
- [ ] Реализовать `prepareExtraction(file)`, `extractArchive(file)`, `cancelExtraction()`
- [ ] Подключить `Flow<ExtractProgress>` к `state.extractionState` через `collectIn(viewModelScope)`

### Шаг 3: UI — `BrowseActivity`
- [ ] Изменить `showBinaryFileMenu()` — добавить ветку `BINARY_ARCHIVE`
- [ ] Создать `UnarchiveConfirmDialog` (`MaterialAlertDialogBuilder`)
- [ ] В `observeEvents()` добавить обработчики новых событий
- [ ] Показывать / обновлять / скрывать `FileOperationProgressDialog`
- [ ] Snackbar с действием «Открыть» → `navigateToFolder`

### Шаг 4: Строки
- [ ] Добавить 7 ключей в `values/strings.xml` (EN)
- [ ] Добавить 7 ключей в `values-ru/strings.xml`
- [ ] Добавить 7 ключей в `values-uk/strings.xml`

### Шаг 5: Проверка и линт
- [ ] Сборка: `.\gradlew.bat assembleStandardDebug` — 0 ошибок
- [ ] Линт: `.\gradlew.bat lintStandardDebug` — 0 новых предупреждений
- [ ] Unit-тесты: `.\gradlew.bat testStandardDebugUnitTest`
- [ ] Ручная проверка: ZIP-файл на локальном хранилище → распаковка, кириллические имена
- [ ] Ручная проверка: Кнопка «Открыть» открывает созданную папку
- [ ] Лог изменений: `.\scripts\add_to_dev_log.ps1` для каждого изменённого файла

---

## 9. Риски и ограничения

| Риск | Последствие | Митигация |
|------|------------|-----------|
| **Encoding (CP866)** | Кракозябры в именах файлов | Авто-определение charset: UTF-8 fallback → CP866 |
| **Zip Bomb** | OOM / зависание | Лимиты: 2 GB, 100 000 записей, глубина 10 |
| **Path traversal** | Запись за пределами targetDir | Нормализация пути + проверка prefix |
| **Нет места** | IOException без контекста | Перехватить, показать `unarchive_error_no_space` |
| **SMB/сетевые источники** | Медленная запись, таймауты | **Фича работает только для LOCAL и SD-карта (ResourceType.LOCAL)**. Для сетевых ресурсов — показать Toast «Недоступно для сетевых источников» |
| **Конфликт папки** | Тихая перезапись | Индексация `_1`, `_2`... |
| **Большой архив (>100 МБ)** | ANR при `Dispatchers.IO` | IO-диспетчер в корутине ViewModel достаточен; WorkManager — опционально для v2 |

---

## 10. Что НЕ делать (явные ограничения)

- **Не трогать** `MediaType` enum и `MediaTypeUtils` — `BINARY_ARCHIVE` уже есть.
- **Не создавать** `CreateTargetExtractionDirUseCase` — логика папки встроена в `ExtractArchiveUseCase`.
- **Не использовать** `Log.d()` — только `Timber`.
- **Не писать** файлы в корень проекта — только в `temp/`.
- **Не добавлять** поддержку RAR/7z в этой задаче.
- **Не изменять** `FileExtensions.kt` — там только `networkPath` extension property.
