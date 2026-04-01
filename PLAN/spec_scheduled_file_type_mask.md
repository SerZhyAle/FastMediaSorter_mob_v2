# Specification: II.7 — Scheduled Operations: Multi-Flag File Type Filter

**Status:** Implemented  
**Date:** 2026-04-01  
**Tier:** 2 — Small-Medium (2–5 h, low risk)  
**Roadmap entry:** Extend scheduled operations with multi-select file type filter (bitmask)

---

## 1. Problem Statement

Текущий фильтр типов файлов в расписаниях (`FileTypeFilter`) реализован как `enum` с единственным выбором: ALL, AUDIO, IMAGES, VIDEO, DOCUMENTS. Пользователь не может выбрать, например, «Изображения + Видео» — только один тип или «все файлы».

Кроме того, значение `ALL` на деле означает «все файлы, которые вернул `GetMediaFilesUseCase`» — то есть только известные медиатипы. Пользователь не может перенести/удалить **немедиафайлы** (бинарные архивы, `.apk`, неизвестные форматы и т.п.) без ручной операции.

Необходимо:
1. Заменить одиночный выбор типа файла на набор независимых флагов (bitmask).
2. Ввести флаг `ALL_FILES`, который означает «обрабатывать все файлы в источнике, включая немедиафайлы».
3. Позволить любую комбинацию флагов (например, IMAGES + VIDEO, или AUDIO + DOCUMENTS).

---

## 2. Goals

1. **Множественный выбор типов**: пользователь может установить любую комбинацию флагов IMAGES, AUDIO, VIDEO, DOCUMENTS.
2. **ALL_FILES** как особый флаг: включает все вышеперечисленные + немедиафайлы (бинарные, архивы, `.apk`, неизвестные форматы). При выборе ALL_FILES остальные флаги не имеют значения.
3. **UI**: вместо dropdown — контейнер с 5 чекбоксами прямо в диалоге.
4. **DB migration**: Room 21 → 22, колонка `file_type_filter TEXT` → `file_type_mask INTEGER`.
5. **Backwards-compatible import**: XML-backup версии 3 парсит старое строковое значение enum и конвертирует в bitmask.
6. **Execution**: `ExecuteScheduledOperationUseCase` корректно фильтрует файлы по маски, в т.ч. для ALL_FILES запрашивает расширенный список (включая немедиафайлы).

**Non-goals:**
- Изменение других фильтров (TimeFilter, OperationType).
- Добавление новых типов файлов (например, ARCHIVES) — расширение через будущий ADR.
- Перевод UI диалога на Compose.
- Изменения в Wear OS модуле.
- Изменение логики `GetMediaFilesUseCase` для других экранов (Browse, Player).

---

## 3. Flavor & API Level Scope

### 3.1 Flavor Impact

| Flavor    | Задет? | Примечания |
|-----------|:------:|------------|
| `standard`| ✅ | Все 5 флагов доступны |
| `lite`    | ✅ | DOCUMENTS недоступен (PDF/EPUB/TEXT исключены); чекбокс «Документы» скрывается через `BuildConfig.FEATURE_DOCUMENTS` |
| `photos`  | ✅ | AUDIO, VIDEO, DOCUMENTS недоступны; только IMAGES + ALL_FILES |
| `legacy`  | ✅ | Все 5 флагов доступны (minSdk 23; DB migration безопасна для API 23+) |

### 3.2 Android API Level Forks

| API level | Поведение |
|-----------|-----------|
| 23+ (`legacy`) | Room migration совместима (SQL ALTER TABLE). Нет API-level-специфичного кода в этой задаче. |
| 26+ (стандартный minSdk) | Без изменений. |

### 3.3 Wear OS Impact

Нет. `wear/` модуль не использует `FileTypeFilter` и `ScheduledOperation`.

---

## 4. Текущая архитектура (AS-IS)

### 4.1 Модель данных

```
domain/model/FileTypeFilter.kt
  enum class FileTypeFilter { ALL, AUDIO, IMAGES, VIDEO, DOCUMENTS }

domain/model/ScheduledOperation.kt
  data class ScheduledOperation(
      ...
      val fileTypeFilter: FileTypeFilter,   // одиночный выбор
      ...
  )

data/local/db/ScheduledOperationEntity.kt
  @ColumnInfo(name = "file_type_filter")
  val fileTypeFilter: String,               // FileTypeFilter.name(), хранится как TEXT
```

### 4.2 UI

`ui/dialog/ScheduledOperationDialog.kt` — `actvFileTypeFilter: AutoCompleteTextView` содержит выпадающий список из 5 строк; одиночный выбор.

Лайаут: `res/layout/dialog_scheduled_operation.xml` — `TextInputLayout` + `AutoCompleteTextView` для фильтра.

### 4.3 Execution

```kotlin
// ExecuteScheduledOperationUseCase.kt
private fun matchesTypeFilter(file: MediaFile, filter: FileTypeFilter): Boolean = when (filter) {
    FileTypeFilter.ALL       -> true
    FileTypeFilter.AUDIO     -> file.type == MediaType.AUDIO
    FileTypeFilter.IMAGES    -> file.type == MediaType.IMAGE || file.type == MediaType.GIF
    FileTypeFilter.VIDEO     -> file.type == MediaType.VIDEO
    FileTypeFilter.DOCUMENTS -> file.type in setOf(MediaType.PDF, MediaType.EPUB, MediaType.TEXT)
}
```

`ALL` фактически пропускает **только** то, что вернул `GetMediaFilesUseCase` — немедиафайлы не включаются.

### 4.4 Room DB

- **Таблица**: `scheduled_operations`
- **Текущая версия БД**: 21
- **Колонка**: `file_type_filter TEXT NOT NULL` (хранит enum name)

### 4.5 Backup / Import

- `ExportSettingsUseCase.kt`: сериализует `<FileTypeFilter>` как строку enum name.
- `ImportSettingsUseCase.kt`: парсит строку и маппит обратно в `FileTypeFilter`.

---

## 5. Предлагаемый дизайн (TO-BE)

### 5.1 Новая модель флагов

**Удалить** старый `enum class FileTypeFilter`.  
**Создать** `domain/model/FileTypeFilter.kt` с флагами и bitmask-хелперами:

```kotlin
// domain/model/FileTypeFilter.kt

/**
 * Bitmask-флаги типов файлов для расписания.
 * Хранится в БД как Integer (биты).
 *
 * ALL_FILES (bit 0) — особый флаг: обработать ВСЕ файлы источника,
 *   включая немедиафайлы (бинарные, архивы, неизвестные).
 *   Когда ALL_FILES установлен — остальные флаги игнорируются при выполнении.
 *
 * IMAGES    (bit 1) — IMAGE + GIF
 * AUDIO     (bit 2) — AUDIO
 * VIDEO     (bit 3) — VIDEO
 * DOCUMENTS (bit 4) — PDF + EPUB + TEXT
 */
object FileTypeFlags {
    const val ALL_FILES: Int = 1 shl 0   // 1
    const val IMAGES:    Int = 1 shl 1   // 2
    const val AUDIO:     Int = 1 shl 2   // 4
    const val VIDEO:     Int = 1 shl 3   // 8
    const val DOCUMENTS: Int = 1 shl 4   // 16

    /** Все медиафлаги без ALL_FILES */
    const val ALL_MEDIA: Int = IMAGES or AUDIO or VIDEO or DOCUMENTS  // 30

    /** Дефолт для новых операций — все типы = 31 */
    const val DEFAULT: Int = ALL_FILES or ALL_MEDIA  // 31

    fun isAllFiles(mask: Int): Boolean = (mask and ALL_FILES) != 0
    fun hasImages(mask: Int): Boolean  = (mask and IMAGES)    != 0
    fun hasAudio(mask: Int): Boolean   = (mask and AUDIO)     != 0
    fun hasVideo(mask: Int): Boolean   = (mask and VIDEO)     != 0
    fun hasDocuments(mask: Int): Boolean = (mask and DOCUMENTS) != 0
}
```

> **ADR-001**: Выбран `Int` bitmask, а не `Set<Enum>`, потому что:  
> (a) Room нативно хранит `INTEGER` без конверторов,  
> (b) SQL-миграция тривиальна (`ALTER + UPDATE`),  
> (c) будущие флаги добавляются добавлением новой константы без schema-change.

### 5.2 Изменения в `ScheduledOperation`

```kotlin
// domain/model/ScheduledOperation.kt (было fileTypeFilter: FileTypeFilter)
data class ScheduledOperation(
    ...
    val fileTypeMask: Int = FileTypeFlags.DEFAULT,  // bitmask; замена fileTypeFilter
    ...
)
```

### 5.3 Изменения в `ScheduledOperationEntity`

```kotlin
// data/local/db/ScheduledOperationEntity.kt
//   БЫЛО: val fileTypeFilter: String
//   СТАЛО:
@ColumnInfo(name = "file_type_mask")
val fileTypeMask: Int = FileTypeFlags.DEFAULT,
```

Колонка переименовывается: `file_type_filter` (TEXT) → `file_type_mask` (INTEGER).

### 5.4 Room Migration (версия 21 → 22)

```kotlin
// data/local/db/AppDatabase.kt
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Добавляем новую INTEGER-колонку (DEFAULT = 31 = ALL_FILES | ALL_MEDIA)
        db.execSQL(
            "ALTER TABLE scheduled_operations ADD COLUMN file_type_mask INTEGER NOT NULL DEFAULT 31"
        )
        // Конвертируем старые TEXT-значения в bitmask
        db.execSQL("UPDATE scheduled_operations SET file_type_mask = 31  WHERE file_type_filter = 'ALL'")
        db.execSQL("UPDATE scheduled_operations SET file_type_mask = 2   WHERE file_type_filter = 'IMAGES'")
        db.execSQL("UPDATE scheduled_operations SET file_type_mask = 4   WHERE file_type_filter = 'AUDIO'")
        db.execSQL("UPDATE scheduled_operations SET file_type_mask = 8   WHERE file_type_filter = 'VIDEO'")
        db.execSQL("UPDATE scheduled_operations SET file_type_mask = 16  WHERE file_type_filter = 'DOCUMENTS'")
        // Удаляем старую колонку (Room не поддерживает DROP COLUMN до API 35 без пересоздания)
        // — Оставляем file_type_filter как orphan-колонку; она не читается кодом и не мешает.
        // АЛЬТЕРНАТИВА: если minSdk 26, пересоздать таблицу — см. ADR-002.
    }
}
```

> **ADR-002**: `DROP COLUMN` в SQLite доступен с версии 3.35 (Android API ~35). Для legacy flavor (minSdk 23) оставляем orphan-колонку. Это не нарушает Room schema hash (Room читает структуру через `PRAGMA table_info`). Альтернатива — пересоздание таблицы с `INSERT INTO ... SELECT` — более безопасна для проверки хэша, но сложнее. **Рекомендация**: пересоздание таблицы (безопаснее для Room schema validation).

**Пересоздание таблицы (рекомендуется):**
```sql
-- Шаг 1: Создать новую таблицу с правильной схемой
CREATE TABLE scheduled_operations_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    is_enabled INTEGER NOT NULL DEFAULT 1,
    source_resource_id INTEGER NOT NULL,
    operation_type TEXT NOT NULL,
    target_resource_id INTEGER,
    file_type_mask INTEGER NOT NULL DEFAULT 31,
    time_filter TEXT NOT NULL,
    start_time_hour INTEGER NOT NULL,
    start_time_minute INTEGER NOT NULL,
    interval_hours INTEGER NOT NULL,
    interval_minutes INTEGER NOT NULL,
    overwrite INTEGER NOT NULL DEFAULT 0,
    silent_mode INTEGER NOT NULL DEFAULT 0,
    last_run_at INTEGER,
    next_run_at INTEGER,
    last_run_status TEXT,
    worker_id TEXT,
    FOREIGN KEY(source_resource_id) REFERENCES resources(id) ON DELETE CASCADE,
    FOREIGN KEY(target_resource_id) REFERENCES resources(id) ON DELETE CASCADE
);

-- Шаг 2: Перенести данные с конвертацией file_type_filter → file_type_mask
INSERT INTO scheduled_operations_new
SELECT
    id, is_enabled, source_resource_id, operation_type, target_resource_id,
    CASE file_type_filter
        WHEN 'ALL'       THEN 31
        WHEN 'IMAGES'    THEN 2
        WHEN 'AUDIO'     THEN 4
        WHEN 'VIDEO'     THEN 8
        WHEN 'DOCUMENTS' THEN 16
        ELSE 31
    END AS file_type_mask,
    time_filter, start_time_hour, start_time_minute,
    interval_hours, interval_minutes,
    overwrite, silent_mode, last_run_at, next_run_at, last_run_status, worker_id
FROM scheduled_operations;

-- Шаг 3: Удалить старую таблицу, переименовать новую
DROP TABLE scheduled_operations;
ALTER TABLE scheduled_operations_new RENAME TO scheduled_operations;

-- Шаг 4: Пересоздать индексы
CREATE INDEX idx_sched_ops_source   ON scheduled_operations(source_resource_id);
CREATE INDEX idx_sched_ops_target   ON scheduled_operations(target_resource_id);
CREATE INDEX idx_sched_ops_enabled  ON scheduled_operations(is_enabled);
```

### 5.5 UI Design

**Текущий UI (удаляется):**
```xml
<!-- TextInputLayout + AutoCompleteTextView actvFileTypeFilter -->
```

**Новый UI (добавляется в `dialog_scheduled_operation.xml`):**
```xml
<LinearLayout
    android:id="@+id/containerFileTypeMask"
    android:orientation="vertical"
    ...>

    <TextView android:text="@string/scheduled_ops_filter_type_label" ... />

    <CheckBox android:id="@+id/cbFileTypeAllFiles"
              android:text="@string/scheduled_ops_filter_all_files" />
    <CheckBox android:id="@+id/cbFileTypeImages"
              android:text="@string/scheduled_ops_filter_images" />
    <CheckBox android:id="@+id/cbFileTypeAudio"
              android:text="@string/scheduled_ops_filter_audio" />
    <CheckBox android:id="@+id/cbFileTypeVideo"
              android:text="@string/scheduled_ops_filter_video" />
    <CheckBox android:id="@+id/cbFileTypeDocs"
              android:text="@string/scheduled_ops_filter_documents" />

</LinearLayout>
```

**Логика чекбоксов:**
- Каждый чекбокс независим.
- Когда пользователь ставит **ALL_FILES** → остальные 4 чекбокса визуально отключаются (disabled) и снимаются, так как ALL_FILES включает всё.  
  > **Примечание**: это только visually-disabled; bitmask пишется как `ALL_FILES` (= 1). При снятии ALL_FILES — чекбоксы снова становятся enabled (пустыми), пользователь выбирает нужные.
- Если ни один флаг не выбран при нажатии «Сохранить» → показывать `Toast` с ошибкой «Выберите хотя бы один тип файла».
- Флаг `DOCUMENTS` скрывается (`View.GONE`) для flavors без `BuildConfig.FEATURE_DOCUMENTS` (lite, photos).

**Строки ресурсов** (добавить/актуализировать в `strings.xml`):
```xml
<string name="scheduled_ops_filter_type_label">Типы файлов</string>
<string name="scheduled_ops_filter_all_files">Все файлы (включая немедиафайлы)</string>
<string name="scheduled_ops_filter_images">Изображения (включая GIF)</string>
<string name="scheduled_ops_filter_audio">Аудио</string>
<string name="scheduled_ops_filter_video">Видео</string>
<string name="scheduled_ops_filter_documents">Документы (PDF, EPUB, текст)</string>
```

Аналогичные строки для `strings-ru.xml` и `strings-uk.xml`.

### 5.6 Mapping в репозитории / DTO

`ScheduledOperationRepositoryImpl.kt` — изменить маппинг `entity → domain` и `domain → entity`:

```kotlin
// entity → domain
val fileTypeMask = entity.fileTypeMask

// domain → entity
val entity = ScheduledOperationEntity(
    ...
    fileTypeMask = operation.fileTypeMask,
    ...
)
```

Удалить старый TypeConverter/маппинг строки → enum, если он есть.

### 5.7 Execution Logic (ExecuteScheduledOperationUseCase)

#### Режим медиатипов (IMAGES / AUDIO / VIDEO / DOCUMENTS и комбинации)

`GetMediaFilesUseCase` вызывается как сейчас, но вместо `resource.supportedMediaTypes` передаётся вычисленный из маски набор `MediaType`:

```kotlin
private fun buildSupportedTypesFromMask(mask: Int): Set<MediaType>? {
    // null = специальный режим ALL_FILES (см. ниже)
    if (FileTypeFlags.isAllFiles(mask)) return null
    val types = mutableSetOf<MediaType>()
    if (FileTypeFlags.hasImages(mask))    types += setOf(MediaType.IMAGE, MediaType.GIF)
    if (FileTypeFlags.hasAudio(mask))     types += MediaType.AUDIO
    if (FileTypeFlags.hasVideo(mask))     types += MediaType.VIDEO
    if (FileTypeFlags.hasDocuments(mask)) types += setOf(MediaType.PDF, MediaType.EPUB, MediaType.TEXT)
    return types
}
```

После фильтрации по типам применяется `matchesTimeFilter()` как сейчас.

#### Режим ALL_FILES

Вместо вызова `GetMediaFilesUseCase` используется путь через `MediaScanner` напрямую с механизмом `allFiles` ресурса.

`MediaScanner.scanFolder()` уже поддерживает режим «все файлы включая немедиафайлы» — он активируется когда `supportedTypes` содержит все 7 типов. В `SmbMediaScanner` это реализовано:
```kotlin
val isAllFilesMode = supportedTypes.containsAll(MediaType.entries.toSet())
val extensions = if (isAllFilesMode) null else MediaTypeUtils.buildExtensionsSet(supportedTypes)
```
То есть `null` в поле расширений = «не фильтровать, взять все файлы».

**Реализация в `ExecuteScheduledOperationUseCase`:**

При `FileTypeFlags.isAllFiles(mask)` — выполнять сканирование с `supportedTypes = MediaType.entries.toSet()`, что активирует существующий `isAllFilesMode` в сканерах. Рекурсия управляется существующим полем `resource.scanSubdirectories` — никаких изменений в ресурсе не нужно.

```kotlin
private suspend fun loadFilesForOperation(
    sourceResource: MediaResource,
    mask: Int
): List<MediaFile> {
    val effectiveResource = if (FileTypeFlags.isAllFiles(mask)) {
        // Передать все типы → активирует isAllFilesMode в сканерах (null extensions)
        // scanSubdirectories берётся из настроек ресурса как обычно
        sourceResource.copy(
            supportedMediaTypes = MediaType.entries.toSet(),
            allFiles = true
        )
    } else {
        val types = buildSupportedTypesFromMask(mask)!!
        sourceResource.copy(supportedMediaTypes = types)
    }
    return getMediaFilesUseCase(
        resource = effectiveResource,
        sortMode = SortMode.NAME_ASC,
        forceFullScan = true
    ).first()
}
```

И в `applyFilters()`:

```kotlin
private fun applyFilters(files: List<MediaFile>, op: ScheduledOperation): List<MediaFile> {
    val now = System.currentTimeMillis()
    return files
        // Если ALL_FILES — type-фильтр уже применён на уровне сканера; здесь не фильтруем
        .filter { if (FileTypeFlags.isAllFiles(op.fileTypeMask)) true else matchesTypeMask(it, op.fileTypeMask) }
        .filter { matchesTimeFilter(it, op.timeFilter, op.lastRunAt, now) }
}

private fun matchesTypeMask(file: MediaFile, mask: Int): Boolean {
    if (FileTypeFlags.hasImages(mask)    && (file.type == MediaType.IMAGE || file.type == MediaType.GIF)) return true
    if (FileTypeFlags.hasAudio(mask)     && file.type == MediaType.AUDIO)  return true
    if (FileTypeFlags.hasVideo(mask)     && file.type == MediaType.VIDEO)  return true
    if (FileTypeFlags.hasDocuments(mask) && file.type in setOf(MediaType.PDF, MediaType.EPUB, MediaType.TEXT)) return true
    return false
}
```

> **ADR-003 (РЕШЕНО)**: Семантика `ALL_FILES` — **все файлы ресурса**, включая немедиафайлы.  
> При включённом `scanSubdirectories` у ресурса — включаются все файлы из подкаталогов.  
> Реализация: передать `MediaType.entries.toSet()` в сканер, что активирует существующий `isAllFilesMode` (null-extensions) в `SmbMediaScanner`, `CloudMediaScanner` и других. Новых методов в `ResourceRepository` не требуется. Поведение определяется флагом `scanSubdirectories` самого ресурса, не операции расписания.

### 5.8 Backup / Import (XML формат)

#### ExportSettingsUseCase

```xml
<!-- Было: -->
<FileTypeFilter>ALL</FileTypeFilter>

<!-- Стало: -->
<FileTypeMask>31</FileTypeMask>
```

#### ImportSettingsUseCase

При парсинге XML backup v3:
- Если встречается тег `<FileTypeMask>` → парсить как Int
- Если встречается тег `<FileTypeFilter>` (старый backup v3) → конвертировать:
  ```
  "ALL"       → 31
  "IMAGES"    → 2
  "AUDIO"     → 4
  "VIDEO"     → 8
  "DOCUMENTS" → 16
  иначе       → 31
  ```

Номер версии XML backup остаётся `3` (обратная совместимость сохраняется через fallback).

---

## 6. Затрагиваемые файлы

| # | Файл | Изменение |
|---|------|-----------|
| 1 | `domain/model/FileTypeFilter.kt` | Заменить `enum` на `object FileTypeFlags` с bitmask-константами |
| 2 | `domain/model/ScheduledOperation.kt` | `fileTypeFilter: FileTypeFilter` → `fileTypeMask: Int` |
| 3 | `data/local/db/ScheduledOperationEntity.kt` | Колонка `fileTypeFilter: String` → `fileTypeMask: Int` |
| 4 | `data/local/db/AppDatabase.kt` | version 21→22, добавить `MIGRATION_21_22` |
| 5 | `data/repository/ScheduledOperationRepositoryImpl.kt` | Обновить маппинг |
| 6 | `domain/usecase/ExecuteScheduledOperationUseCase.kt` | Заменить `matchesTypeFilter` на `matchesTypeMask` |
| 7 | `ui/dialog/ScheduledOperationDialog.kt` | Dropdown → 5 CheckBox; логика interlock для ALL_FILES |
| 8 | `res/layout/dialog_scheduled_operation.xml` | Удалить `tilFileTypeFilter`/`actvFileTypeFilter`; добавить CheckBox-контейнер |
| 9 | `res/values/strings.xml` | Обновить/добавить строки |
| 10 | `res/values-ru/strings.xml`, `res/values-uk/strings.xml` | То же для RU/UK (если файлы разделены; иначе через `strings.xml` + translatable) |
| 11 | `domain/usecase/ExportSettingsUseCase.kt` | `<FileTypeMask>` вместо `<FileTypeFilter>` |
| 12 | `domain/usecase/ImportSettingsUseCase.kt` | Парсинг нового и старого тегов |
| 13 | `ui/settings/ScheduledOperationsAdapter.kt` | Если отображает тип фильтра — обновить отображение маски в читаемый текст |
| 14 | `ui/settings/ScheduledOperationsViewModel.kt` | Проверить, есть ли mapping/displayName; обновить при необходимости |

---

## 7. Testing Plan

### Unit Tests

| Тест | Цель |
|------|------|
| `FileTypeFlagsTest` | Проверить все 5 флагов, комбинации, `isAllFiles()`, `hasImages()` и т.д. |
| `ExecuteScheduledOperationUseCaseTest` — `matchesTypeMask` | mask=1 пропускает все, mask=2 пропускает IMAGE/GIF, mask=4 → только AUDIO, mask=6 → IMAGE+AUDIO, mask=0 → ничего |
| `ScheduledOperationRepositoryImplTest` | Маппинг `Int ↔ entity` без потери данных |
| `ScheduledOperationDialogTest` | Установка ALL_FILES отключает остальные чекбоксы; сохранение пустой маски → Toast |
| Room Migration test 21→22 | Старые записи с `ALL`/`IMAGES`/`AUDIO`/`VIDEO`/`DOCUMENTS` конвертируются в корректные bitmask значения |
| Import XML с `<FileTypeFilter>ALL</FileTypeFilter>` | Парсится в mask=31 |
| Import XML с `<FileTypeMask>6</FileTypeMask>` | Парсится в mask=6 |

### Manual / Smoke Tests

- Создать операцию с «Изображения + Видео»; сохранить; переоткрыть диалог → чекбоксы верно заполнены.
- Запустить операцию вручную; убедиться, что обрабатываются только файлы нужных типов.
- Вариант «Все файлы»: проверить обработку по сценарию ADR-003 (A или B в зависимости от выбора).
- Обновить приложение с DB version 21 → 22; убедиться, что существующие операции не сломались.
- Импорт старого XML backup v3 с `<FileTypeFilter>` → операции восстанавливаются с корректным mask.

---

## 8. Accessibility

- Все `CheckBox` должны иметь `contentDescription` (или `android:text` достаточен — `CheckBox` автоматически использует `text` как label для TalkBack). Отдельного `contentDescription` не нужно.
- Disabled чекбоксы при выборе ALL_FILES: добавить `android:importantForAccessibility="no"` или аналогичное объяснение для TalkBack (disabled state озвучивается Android автоматически).
- Все текстовые строки — локализованы (strings.xml).

---

## 9. ADR Summary

| # | Решение | Выбор | Обоснование |
|---|---------|-------|-------------|
| ADR-001 | Представление маски | `Int` bitmask вместо `Set<Enum>` | Нативный Room INTEGER, тривиальная SQL-миграция, расширяемость |
| ADR-002 | DB migration strategy | Пересоздание таблицы | Избегает orphan-коммуны и проблем с Room schema hash |
| ADR-003 | Семантика ALL_FILES | `MediaType.entries.toSet()` → использует существующий `isAllFilesMode` в сканерах (null-extensions). Рекурсия — по `resource.scanSubdirectories`. Новых методов не нужно. | Пользователь подтвердил: «все файлы ресурса (включая подкаталоги если включена соответствующая настройка)» |

---

## 10. Open Questions

1. **`photos` flavor**: показывать ли чекбоксы `AUDIO` и `VIDEO` в виде disabled, или полностью скрыть? (Рекомендация: скрыть через `BuildConfig`.)
2. **Отображение маски в списке операций** (`ScheduledOperationsAdapter`): как отображать комбинацию флагов в столбце «Тип файлов»? Варианты: «Изображ. + Видео», «3 типа», иконки. (Рекомендация: short comma-separated аббревиатуры.)
3. **Backup version**: стоит ли поднять версию XML backup с `3` до `4` для явного указания нового формата?

---

## 11. Estimation

| Компонент | Оценка |
|-----------|--------|
| Domain model + FileTypeFlags | 0.5 h |
| Room migration (21→22) + Entity | 1 h |
| RepositoryImpl mapping | 0.25 h |
| ExecuteScheduledOperationUseCase (loadFilesForOperation + matchesTypeMask) | 0.75 h |
| ScheduledOperationDialog (UI: CheckBox + interlock) | 1 h |
| XML layout changes | 0.5 h |
| Strings (EN/RU/UK) | 0.25 h |
| Export/Import (backup compat) | 0.5 h |
| ScheduledOperationsAdapter display | 0.25 h |
| Unit tests | 1 h |
| QA / manual testing | 0.5 h |
| **Итого** | **~6.5 h** |
