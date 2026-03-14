# SPEC: Виртуальные агрегирующие папки (Вся музыка / Все видео / Все документы)

**Дата**: 2026-03-14  
**Статус**: Design Phase (решения зафиксированы, реализация не начата)  
**Приоритет**: Medium  
**Сложность**: Medium  
**Целевой модуль**: `app_v2/src/main/java/com/sza/fastmediasorter/`  
**Flavors**: только `standard` и `legacy`. В `lite` и `photos` не реализуется.

---

## 1. ОБЗОР

### Контекст

В приложении уже существует одна виртуальная папка — **«Недавние»** (`virtual://recent`).  
Она не является реальной директорией на диске — это специальный ресурс, при открытии которого сканер запрашивает у MediaStore последние N файлов всех разрешённых типов.

### Задача

Добавить три аналогичных виртуальных ресурса и превратить все четыре в **стандартный набор** приложения, который автоматически создаётся при первом запуске.

| Имя (RU)      | Имя (EN)      | Virtual path          | Типы файлов                                        |
|---------------|---------------|-----------------------|----------------------------------------------------|
| Недавние      | Recent        | `virtual://recent`    | все включённые в настройках                         |
| Вся музыка    | All Music     | `virtual://all_audio` | `AUDIO`                                            |
| Все видео     | All Videos    | `virtual://all_video` | `VIDEO`                                            |
| Все документы | All Documents | `virtual://all_docs`  | все документальные типы, поддерживаемые программой |

### Типы файлов в «Все документы»

При установке программы в «Все документы» **по умолчанию включены все документальные типы**, которые программа умеет открывать: `TEXT` (.txt, .md и другие текстовые), `PDF`, `EPUB`. Если в будущем добавится поддержка `RTF`, `ODT` и пр. — они также войдут в этот ресурс. Форматы, которые программа не открывает (DOCX, XLSX), — не включаются.

Пользователь может **отключить** отдельные типы в настройках программы. После ресканирования ресурса файлы отключённых типов пропадают из списка — это корректное и ожидаемое поведение (пользователь сам управляет настройками).

> **Реализационное замечание**: `supportedMediaTypes` для `virtual://all_docs` формируется **динамически** при каждом сканировании — как объединение всех документальных `MediaType`, которые **включены в текущих настройках** (`settings.supportText`, `settings.supportPdf`, `settings.supportEpub` и т.д.). Не хардкодить фиксированный набор, чтобы автоматически поддерживать новые типы в будущем.

### Поведение (общее для всех виртуальных ресурсов)

- Не привязан к конкретной директории на диске.
- При сканировании обходит **все доступные накопители** (внутренняя память + SD-карта + подключённые внешние диски) через MediaStore, при наличии соответствующих permissions.
- Возвращает **плоский список** файлов нужного типа без подпапок, с лимитом **10 000 файлов**. Порядок отсечения — как вернул MediaStore (без специальной сортировки при выборке). Итоговый порядок отображения определяется `ResourceEntity.sortMode`.
- Список кешируется в БД (`CachedFileListEntity`), аналогично обычным ресурсам.
- При повторном открытии отображается кешированный список без автоматического ресканирования.
- Пользователь может вручную запустить ресканирование (pull-to-refresh или кнопка).
- Поддерживается: воспроизведение, сортировка (сохраняется в ресурсе), избранное, PIN-защита.
- Сортировка по умолчанию: **NAME_ASC**. Пользователь может изменить — выбор сохраняется в `ResourceEntity.sortMode` как у любого ресурса.
- `displayMode` (list/grid) по умолчанию: наследует глобальную настройку программы. У «Вся музыка» недоступен grid (как у всех аудиоресурсов).
- **Редактирование** — ограниченное: можно переименовать, задать PIN, задать интервал слайдшоу. Нельзя изменить path и перечень поддерживаемых типов. У виртуального ресурса отображается специальный экран редактирования без этих полей.
- **Не является Destination**: `isDestination = false`, нельзя назначить получателем операции копирования/перемещения.
- **Является Source**: файлы из виртуального ресурса можно копировать, перемещать, переименовывать, удалять — как источник.
- PIN-защита (`accessPin`) — допускается, технических ограничений нет.

---

## 2. ИНИЦИАЛИЗАЦИЯ ПРИ ПЕРВОМ ЗАПУСКЕ

При **первой установке приложения** (БД пустая, нет ни одного ресурса) все четыре виртуальных ресурса создаются **автоматически** и сразу отображаются на экране ресурсов. Пользователь не видит пустого экрана.

### 2.1 Точка инициализации

Логика размещается в компоненте первоначальной настройки приложения (предположительно: `AppInitializer`, `MainViewModel`, или отдельный `ProvisionDefaultResourcesUseCase`). Точное место определяется на этапе реализации после изучения существующего flow первого запуска.

**Условие срабатывания**: первый запуск определяется как отсутствие каких-либо ресурсов в БД (`resourceRepository.getAllResources().first().isEmpty()`).

### 2.2 Порядок создания и отображения

Виртуальные ресурсы создаются в фиксированном порядке и занимают первые позиции `displayOrder`:

| displayOrder | Ресурс        |
|--------------|---------------|
| 0            | Недавние      |
| 1            | Вся музыка    |
| 2            | Все видео     |
| 3            | Все документы |

### 2.3 Момент сканирования виртуального ресурса

**Сканирование запускается немедленно в момент создания ресурса — не лениво.**

- **При провизионировании** (первый запуск, `ProvisionDefaultResourcesUseCase`): все 4 ресурса создаются и **полностью сканируются** (`scanFolder`) до того, как пользователь попадает на главный экран. Прогресс отображается на экране инициализации.
- **При добавлении вручную** (кнопка «Добавить вручную» или «Сканировать»): после сохранения `ResourceEntity` немедленно запускается сканирование с отображением прогресса — так же, как для обычного нового ресурса.

Результат записывается в кеш (`CachedFileListEntity`). Таким образом, пользователь никогда не открывает виртуальный ресурс пустым.

### 2.4 Пользователь может удалить виртуальный ресурс

Удаление полностью аналогично обычному ресурсу:
- Удаляется `ResourceEntity`.
- Очищается `CachedFileListEntity` для данного `resourceId`.

После удаления ресурс можно восстановить:
- Через кнопку **«Сканировать»** на экране добавления ресурсов — виртуальная папка снова появляется в списке результатов, если её `path` отсутствует в `existingPaths`.
- Через кнопку **«Добавить вручную»** — диалог содержит секцию «Специальные папки».

---

## 3. МЕСТА ПОЯВЛЕНИЯ В UI

### 3.1 Сканирование локальных папок (кнопка «Сканировать»)

`ScanLocalFoldersUseCase` — добавляет виртуальные ресурсы в начало списка результатов сканирования, **если они ещё не добавлены** (path отсутствует в `existingPaths`).

Порядок в результатах:
1. Недавние
2. Вся музыка
3. Все видео
4. Все документы
5. … реальные папки (как сейчас)

Условие видимости по flavor и настройкам:

| Ресурс        | Условие включения                                               | Flavors          |
|---------------|-----------------------------------------------------------------|------------------|
| Недавние      | всегда (как сейчас)                                             | standard, legacy |
| Вся музыка    | `settings.supportAudio == true`                                 | standard, legacy |
| Все видео     | `settings.supportVideos == true`                                | standard, legacy |
| Все документы | `settings.supportText || settings.supportPdf || settings.supportEpub || ...` | standard, legacy |

### 3.2 Диалог «Добавить вручную»

`AddResourceActivity.showFolderSelectionDialog()` — в диалоге `dialog_folder_selection.xml` добавляется секция **«Специальные папки»** с четырьмя кнопками.

Кнопки для виртуальных ресурсов, которые уже добавлены, отображаются как **заблокированные** (disabled / grayed out) с текстом «Уже добавлен».

При нажатии на доступную кнопку — `viewModel.addVirtualResource(virtualPath)`.

---

## 4. АРХИТЕКТУРА ИЗМЕНЕНИЙ

### 4.1 Новые константы

**`LocalMediaScanner.kt`** — добавить рядом с `VIRTUAL_PATH_RECENT`:

```kotlin
const val VIRTUAL_PATH_ALL_AUDIO  = "virtual://all_audio"
const val VIRTUAL_PATH_ALL_VIDEO  = "virtual://all_video"
const val VIRTUAL_PATH_ALL_DOCS   = "virtual://all_docs"
const val VIRTUAL_ALL_FILES_LIMIT = 10_000
```

### 4.2 `LocalMediaScanner.scanFolder()`

Добавить ветки в начало функции (аналогично `VIRTUAL_PATH_RECENT`):

```kotlin
VIRTUAL_PATH_ALL_AUDIO -> return@withContext scanAllByTypes(
    setOf(MediaType.AUDIO), sizeFilter, showHiddenFiles, onProgress
)
VIRTUAL_PATH_ALL_VIDEO -> return@withContext scanAllByTypes(
    setOf(MediaType.VIDEO), sizeFilter, showHiddenFiles, onProgress
)
VIRTUAL_PATH_ALL_DOCS  -> return@withContext scanAllByTypes(
    docTypesFromSupportedTypes(supportedTypes), sizeFilter, showHiddenFiles, onProgress
)
```

Новый приватный метод `scanAllByTypes()`:
- Делегирует вызов `mediaStoreRepository.getAllFilesByTypes(allowedTypes, showHiddenFiles)`.
- Применяет `sizeFilter`.
- Применяет лимит `VIRTUAL_ALL_FILES_LIMIT = 10_000`.
- Вызывает `onProgress?.onComplete(...)`.

### 4.3 `LocalMediaScanner.getFileCount()`

Добавить аналогичные ветки рядом с проверкой `VIRTUAL_PATH_RECENT`:

```kotlin
if (path == VIRTUAL_PATH_ALL_AUDIO) return@withContext scanAllByTypes(setOf(MediaType.AUDIO), ...).size
// и т.д. для VIDEO, DOCS
```

### 4.4 `LocalMediaScanner.isWritable()`

```kotlin
if (path.startsWith("virtual://")) return@withContext false
```

### 4.5 `MediaStoreRepository` (интерфейс)

```kotlin
suspend fun getAllFilesByTypes(
    allowedTypes: Set<MediaType>,
    showHiddenFiles: Boolean = false
): List<MediaFile>
```

### 4.6 `MediaStoreRepositoryImpl`

Реализация `getAllFilesByTypes()`:
- Запрос к MediaStore **без фильтрации по `bucket_id`** — охватывает все накопители (внутренняя память, SD-карта, внешние диски).
- Все доступные volumes (`MediaStore.getExternalVolumeNames()` на API 29+, fallback на EXTERNAL_CONTENT_URI).
- Фильтрует скрытые файлы (имена с `.`), если `showHiddenFiles == false`.
- Порядок файлов в запросе **не специфицирован**: берутся первые `VIRTUAL_ALL_FILES_LIMIT` (10 000) в том порядке, как вернул MediaStore. Итоговая сортировка для отображения — по `ResourceEntity.sortMode`.

### 4.7 `ScanLocalFoldersUseCase`

После блока добавления «Недавние» — три аналогичных блока. Пример для аудио:

```kotlin
if (VIRTUAL_PATH_ALL_AUDIO !in existingPaths && settings.supportAudio) {
    resources.add(
        MediaResource(
            id = 0,
            name = context.getString(R.string.virtual_all_music),
            path = VIRTUAL_PATH_ALL_AUDIO,
            type = ResourceType.LOCAL,
            fileCount = 0,
            isWritable = false,
            isDestination = false,
            destinationOrder = null,
            scanSubdirectories = false,
            supportedMediaTypes = setOf(MediaType.AUDIO),
            sortMode = SortMode.NAME_ASC,
            profile = ResourceProfile.AUDIO_LIBRARY, // автовключение Slideshow
            slideshowInterval = settings.slideshowInterval,
            allFiles = false
        )
    )
}
```

Для VIDEO: `profile = ResourceProfile.VIDEO_LIBRARY`.  
Для DOCS: `profile = ResourceProfile.DEFAULT`.

`supportedMediaTypes` для DOCS — динамически:
```kotlin
val docTypes = buildSet {
    if (settings.supportText) add(MediaType.TEXT)
    if (settings.supportPdf)  add(MediaType.PDF)
    if (settings.supportEpub) add(MediaType.EPUB)
    // добавить новые типы по мере поддержки
}
if (docTypes.isNotEmpty() && VIRTUAL_PATH_ALL_DOCS !in existingPaths) { ... }
```

### 4.8 `ProvisionDefaultResourcesUseCase` (новый)

Отдельный UseCase для создания четырёх виртуальных ресурсов при первом запуске.  
Вызывается из точки инициализации приложения (определяется при реализации).  
Внутри — та же логика, что в `ScanLocalFoldersUseCase`, но без проверки `existingPaths` (БД гарантированно пустая) и с фиксированными `displayOrder` 0–3.

### 4.9 `AddResourceViewModel`

Новый метод `addVirtualResource(virtualPath: String)`:
- Проверяет дубликат через `existingPaths`.
- Создаёт `MediaResource` с корректными `profile`, `sortMode`, `supportedMediaTypes`.
- Сохраняет через `resourceRepository.addResource()`.
- Публикует `existingVirtualPaths: LiveData<Set<String>>` для управления состоянием кнопок в диалоге.

### 4.10 UI: `dialog_folder_selection.xml`

Добавить секцию **«Специальные папки»** в начало диалога:

```xml
<!-- Секция специальных виртуальных папок -->
<TextView style="@style/SectionHeader"
    android:text="@string/special_virtual_folders" />

<MaterialButton android:id="@+id/btnVirtualRecent"
    app:icon="@drawable/ic_virtual_recent" ... />
<MaterialButton android:id="@+id/btnVirtualAllMusic"
    app:icon="@drawable/ic_virtual_music" ... />
<MaterialButton android:id="@+id/btnVirtualAllVideo"
    app:icon="@drawable/ic_virtual_video" ... />
<MaterialButton android:id="@+id/btnVirtualAllDocs"
    app:icon="@drawable/ic_virtual_docs" ... />
```

Кнопки уже добавленных ресурсов: `isEnabled = false`, текст «Уже добавлен».

### 4.11 Иконки виртуальных ресурсов

Требуются два назначения иконок:
- **Список ресурсов (главный экран)**: уникальная иконка, визуально отличающая виртуальный ресурс от обычных папок.
- **Диалог «Добавить вручную»**: кнопка с иконкой.

Концепция — Material-стиль, векторные `VectorDrawable`:

| Ресурс        | Концепция иконки                                        | Drawable name      |
|---------------|---------------------------------------------------------|--------------------|
| Недавние      | Часы + стрелка истории (уже есть, возможно переиспользуется) | `ic_virtual_recent` |
| Вся музыка    | Нота внутри стопки горизонтальных линий (библиотека) | `ic_virtual_music`  |
| Все видео     | Кинолента / видеокамера со звёздочкой «всё»          | `ic_virtual_video`  |
| Все документы | Стопка документов с буквой «A» или символом «∞»      | `ic_virtual_docs`   |

Визуальное отличие в списке ресурсов: добавить специальный **tint** или небольшой **badge ∞** поверх иконки для виртуальных ресурсов. Конкретный подход — решается на этапе UI-дизайна.

### 4.12 `AddResourceActivity.showFolderSelectionDialog()`

```kotlin
viewModel.existingVirtualPaths.observe(this) { existingPaths ->
    listOf(
        R.id.btnVirtualRecent   to VIRTUAL_PATH_RECENT,
        R.id.btnVirtualAllMusic to VIRTUAL_PATH_ALL_AUDIO,
        R.id.btnVirtualAllVideo to VIRTUAL_PATH_ALL_VIDEO,
        R.id.btnVirtualAllDocs  to VIRTUAL_PATH_ALL_DOCS
    ).forEach { (btnId, path) ->
        dialogView.findViewById<MaterialButton>(btnId)?.apply {
            isEnabled = path !in existingPaths
            text = if (isEnabled) originalLabel else getString(R.string.virtual_resource_already_added)
        }
    }
}

dialogView.findViewById<MaterialButton>(R.id.btnVirtualAllMusic)?.setOnClickListener {
    viewModel.addVirtualResource(VIRTUAL_PATH_ALL_AUDIO)
    dialog.dismiss()
}
// аналогично для остальных
```

### 4.13 `ResourceScanCoordinator` — диалог предупреждения

Перед стартом `scanAllResources()` — проверить наличие виртуальных агрегирующих ресурсов:

```kotlin
val hasAggregateVirtuals = resources.any { it.path.startsWith("virtual://all_") }
if (hasAggregateVirtuals) {
    // Показать ConfirmationDialog с предупреждением о времени
    // Продолжить только после нажатия «Продолжить»
}
```

---

## 5. СТРОКОВЫЕ РЕСУРСЫ

Добавить в `res/values/strings.xml`:

```xml
<string name="virtual_all_music">Вся музыка</string>
<string name="virtual_all_video">Все видео</string>
<string name="virtual_all_docs">Все документы</string>
<string name="special_virtual_folders">Специальные папки</string>
<string name="virtual_resource_already_added">Уже добавлен</string>
<string name="virtual_resource_added">Ресурс «%1$s» добавлен</string>
<string name="rescan_all_virtual_warning_title">Ресканирование всех ресурсов</string>
<string name="rescan_all_virtual_warning_message">Список включает виртуальные агрегирующие папки (Вся музыка, Все видео, Все документы). Их полное ресканирование может занять значительное время. Продолжить?</string>
```

Аналогично для `strings-ru.xml` и `strings-uk.xml`.

---

## 6. БД / МИГРАЦИЯ

Schema изменений **не требуется**:
- `ResourceEntity.path` — обычная строка, примет значения `virtual://all_*`.
- `CachedFileListEntity` кеширует по `resourceId` — работает без изменений.
- Поля `isWritable = false`, `isDestination = false`, `sortMode`, `profile` уже существуют.

Миграция Room **не нужна**.

При удалении ресурса — `CachedFileListEntity` для данного `resourceId` очищается. Проверить наличие cascade delete или явного вызова при `resourceRepository.deleteResource()`.

---

## 7. КЕШИРОВАНИЕ И РЕСКАНИРОВАНИЕ

Виртуальные ресурсы работают с кешем идентично обычным локальным ресурсам:

- `rememberFileList` — включён по умолчанию (или по настройке пользователя).
- `IncrementalScanStrategy`:
  - `lastModifiedFolder` = 0 (нет папки для проверки mtime) → кеш считается устаревшим при явном ресканировании.
  - При pull-to-refresh / ручном ресканировании — полный re-scan через MediaStore.
  - При обычном открытии — отдаётся кеш.

### 7.1 Массовое ресканирование

Виртуальные агрегирующие папки **участвуют в массовом ресканировании** (обновление `fileCount`, перестройка кеша).

Перед стартом — диалог подтверждения с предупреждением о времени (п.4.13). Диалог показывается только если в списке есть хотя бы один ресурс с `path.startsWith("virtual://all_")`.

### 7.2 Guard в `IncrementalScanStrategy`

```kotlin
fun currentFolderMtime(path: String): Long {
    if (path.startsWith("virtual://")) return 0L
    return try { File(path).lastModified() } catch (e: Exception) { 0L }
}
```

---

## 8. ОГРАНИЧЕНИЯ И EDGE CASES

| Ситуация | Поведение |
|----------|-----------|
| `supportAudio = false` | «Вся музыка» не создаётся при первом запуске и не появляется в scan; кнопка в диалоге скрыта |
| Устройство не имеет файлов данного типа | Список пустой, fileCount = 0; ресурс создаётся без ошибок |
| Дубликат при ручном добавлении | Кнопка disabled; `addVirtualResource()` защищён проверкой |
| Назначение как Destination | Запрещено: `isDestination = false`; UI скрывает эту опцию |
| Операции над файлами | Copy / Move / Rename / Delete — разрешены как **SOURCE**; ресурс сам Destination-ом не является |
| `isWritable` | Всегда `false` → нельзя скопировать файл ВНУТРЬ ресурса |
| Flavor `lite` или `photos` | Виртуальные ресурсы не создаются при первом запуске; не появляются в scan; кнопок в диалоге нет |
| SD-карта / внешний диск | Включаются при наличии `READ_MEDIA_*` / `READ_EXTERNAL_STORAGE` |
| SD-карта отключена после кеша | При следующем ресканировании файлы с отключённого тома не вернутся; кеш обновится |
| Удаление ресурса | Удаляется `ResourceEntity` + `CachedFileListEntity`; восстанавливается через «Сканировать» или «Добавить вручную» |
| PIN на виртуальном ресурсе | Разрешён технически |
| `isAvailable` при массовом сканировании | Всегда `true` (устройство всегда доступно если есть permission) |
| Избранное при удалении ресурса | Сохраняется: избранное хранится по URI файла, не по `resourceId`. После удаления и повторного добавления ресурса помеченные файлы снова будут отмечены — ожидаемое поведение |
| `displayMode` | «Вся музыка» — только список (grid отсутствует, как для всех аудиоресурсов). «Все видео» и «Все документы» — следуют глобальной настройке приложения |
| Wear OS | Виртуальные агрегирующие ресурсы не поддерживаются и не синхронизируются |

---

## 9. ТЕСТИРОВАНИЕ

### Unit-тесты

- `LocalMediaScannerTest`: тесты для каждого виртуального пути; проверка лимита 10 000; `isWritable` возвращает `false`.
- `ScanLocalFoldersUseCaseTest`: три ресурса добавляются при пустом `existingPaths`; не добавляются при `supportAudio = false`; не дублируются при повторном вызове.
- `ProvisionDefaultResourcesUseCaseTest`: первый запуск — 4 ресурса; повторный вызов — дубликаты не создаются.
- `AddResourceViewModelTest`: `addVirtualResource()` — успех; дубликат — тост, нет повторной записи в БД.
- `IncrementalScanStrategyTest`: `currentFolderMtime("virtual://all_audio")` возвращает `0L`.

### Ручные проверки

1. **Первый запуск**: свежая установка → экран инициализации с прогрессом → все 4 виртуальных ресурса уже содержат файлы (не пустые) до попадания на главный экран.
2. **Добавление вручную**: удалить один виртуальный ресурс → «Добавить вручную» → добавить его → немедленно запускается видимый прогресс сканирования → открыть ресурс → он не пустой.
3. Открыть «Вся музыка» → плоский список всех треков (в т.ч. с SD-карты).
4. Pull-to-refresh → перезагрузка из MediaStore.
5. Изменить сортировку → закрыть ресурс → открыть снова → сортировка сохранена.
6. Открыть «Вся музыка» → нажать Play → Slideshow включён автоматически.
7. Открыть «Все видео» → нажать Play → Slideshow включён автоматически.
8. **Редактирование виртуального ресурса**: открыть Edit → присутствуют поля «Название», PIN, «Интервал слайдшоу»; отсутствуют поля «Путь» и «Поддерживаемые типы».
9. **displayMode**: «Вся музыка» — опция переключения list/grid отсутствует (только список). «Все видео» и «Все документы» — `displayMode` следует глобальной настройке.
10. Удалить «Вся музыка» → «Сканировать» → снова предлагается в результатах.
11. «Добавить вручную» → секция «Специальные папки» видна; уже добавленные — disabled.
12. Запустить «Ресканировать все» → диалог-предупреждение → после подтверждения выполняется.
13. Ресурс не отображается как Destination при копировании файлов.
14. Выбрать файл из «Все документы» → скопировать в реальную папку → успешно.
15. Flavor `lite`: виртуальных ресурсов нет, кнопок нет.

---

## 10. ПЛАН РЕАЛИЗАЦИИ (ФАЗЫ)

### Фаза 1 — Backend
- [ ] Константы + `VIRTUAL_ALL_FILES_LIMIT` в `LocalMediaScanner`
- [ ] `getAllFilesByTypes()` в `MediaStoreRepository` интерфейс
- [ ] Реализация в `MediaStoreRepositoryImpl` (все volumes, фильтрация, лимит)
- [ ] Ветки в `scanFolder()`, `getFileCount()`, `isWritable()` в `LocalMediaScanner`
- [ ] Guard в `IncrementalScanStrategy.currentFolderMtime()`
- [ ] `ProvisionDefaultResourcesUseCase` (первый запуск) + подключение к flow инициализации
- [ ] Обновить `ScanLocalFoldersUseCase` (3 ресурса, `ResourceProfile`, динамический `docTypes`)
- [ ] Unit-тесты (п.9)

### Фаза 2 — UI
- [ ] Строковые ресурсы (strings.xml, ru, uk) + строки диалога предупреждения
- [ ] 4 иконки: `ic_virtual_recent`, `ic_virtual_music`, `ic_virtual_video`, `ic_virtual_docs`
- [ ] Обновить `dialog_folder_selection.xml` — секция «Специальные папки»
- [ ] `addVirtualResource()` + `existingVirtualPaths` LiveData в `AddResourceViewModel`
- [ ] Ограниченный экран редактирования для виртуальных ресурсов: `EditResourceActivity` / фрагмент без полей «Путь» и «Тип файлов» при `path.startsWith("virtual://")`
- [ ] Обработчики в `AddResourceActivity.showFolderSelectionDialog()` с disabled-логикой
- [ ] Диалог предупреждения в `ResourceScanCoordinator`
- [ ] Визуальное отличие виртуальных ресурсов в списке на главном экране

### Фаза 3 — Верификация
- [ ] Ручное тестирование по чеклисту п.9
- [ ] Lint pass
- [ ] Changelog + Feature docs (EN / RU / UK)

---

## 11. ФАЙЛЫ К ИЗМЕНЕНИЮ

| Файл | Тип изменения |
|------|---------------|
| `data/local/LocalMediaScanner.kt` | Новые константы, лимит, ветки в scanFolder/getFileCount/isWritable, `scanAllByTypes()` |
| `domain/repository/MediaStoreRepository.kt` | Новый метод `getAllFilesByTypes()` |
| `data/local/MediaStoreRepositoryImpl.kt` | Реализация `getAllFilesByTypes()` (все volumes) |
| `domain/usecase/scan/IncrementalScanStrategy.kt` | Guard `virtual://` в `currentFolderMtime()` |
| `domain/usecase/ScanLocalFoldersUseCase.kt` | 3 новых ресурса, динамический docTypes, ResourceProfile |
| `domain/usecase/ProvisionDefaultResourcesUseCase.kt` | **Новый файл**: создание 4 ресурсов при первом запуске |
| Точка инициализации (определить при реализации) | Вызов `ProvisionDefaultResourcesUseCase` |
| `ui/addresource/AddResourceViewModel.kt` | `addVirtualResource()`, `existingVirtualPaths` LiveData |
| `ui/addresource/AddResourceActivity.kt` | Обработчики + disabled-логика в `showFolderSelectionDialog()` |
| `ui/main/helpers/ResourceScanCoordinator.kt` | Диалог предупреждения перед массовым ресканированием |
| `res/layout/dialog_folder_selection.xml` | Секция «Специальные папки» с 4 кнопками |
| `res/drawable/ic_virtual_*.xml` | **4 новых файла**: иконки виртуальных ресурсов |
| `res/values/strings.xml` (+ ru, uk) | Строки для названий, лейблов, диалогов |
| `data/local/LocalMediaScannerTest.kt` | Новые unit-тесты |
| `domain/usecase/ScanLocalFoldersUseCaseTest.kt` | Новые unit-тесты |
