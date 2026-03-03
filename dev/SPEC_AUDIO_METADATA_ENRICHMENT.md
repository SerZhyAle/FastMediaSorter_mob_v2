# Спецификация: Обогащение аудиометаданными при загрузке списка файлов

**Дата**: 2026-03-03  
**Статус**: DRAFT — ожидает ревью  
**Модуль**: `app_v2`  
**Вход**: Запрос пользователя — при загрузке файлового списка с включённым "Запоминать список файлов" загружать метаданные (artist, title, duration) в БД, отображать в Browse и мгновенно показывать в Player.

---

## 1. Анализ текущего состояния (AS-IS)

### 1.1 Pipeline загрузки файлов

```
BrowseViewModel → GetMediaFilesUseCase → scanner.scanFolder()
    → tagFavorites → metadataExtractor.enrichBatch() → sortFiles → emit(files)
    → saveCachedFiles() (GZIP JSON BLOB в cached_file_lists)
```

### 1.2 Metadata Enrichment (CachedMediaMetadataExtractor)

| Шаг | Описание | Файл |
|-----|----------|------|
| 1 | `enrichBatch()` загружает все `FileMetadataCacheEntity` для resourceId одним query | `CachedMediaMetadataExtractor.kt:L39` |
| 2 | Проверяет `isLocalPath(file.path)` — если сетевой → **skip** | `CachedMediaMetadataExtractor.kt:L76-L82` |
| 3 | Cache hit: `lastModified == createdDate && fileSize == size` → копирует **только** `duration`, `width`, `height`, `videoRotation`, `exifDateTime` | `CachedMediaMetadataExtractor.kt:L52-L57` |
| 4 | Cache miss: вызывает `enrichAudio()` → `MediaMetadataRetriever` → `file.copy(artist, album, title, duration)` | `CachedMediaMetadataExtractor.kt:L93-L107` |
| 5 | Сохраняет в `FileMetadataCacheEntity` через `upsertAll()` | `CachedMediaMetadataExtractor.kt:L68` |

### 1.3 Выявленные проблемы

#### Баг 1: `FileMetadataCacheEntity` НЕ ХРАНИТ `artist`, `album`, `title`

**Таблица `file_metadata_cache`** содержит:
```
id, resourceId, filePath, provider, credentialsId,
lastModified, fileSize, cachedAt,
thumbnailPath, durationMs, width, height, videoRotation, exifDateTime, exifJson
```

**Отсутствуют колонки**: `artist`, `album`, `title`.

**Последствие**: При **cache hit** (файл не изменился) → `enrichBatch()` берёт данные из Entity и делает `file.copy(duration=..., width=..., ...)` — поля `artist`, `album`, `title` остаются `null`. Аудиометаданные **теряются при каждом повторном открытии ресурса**, хотя `enrichAudio()` их однажды извлёк.

**Единственное место**, где artist/album/title сохраняются: внутри GZIP JSON BLOB в `CachedFileListEntity.compressedData` (как часть сериализованного `MediaFile`). Но при горячем scan (не из кеша) — данные пропадают.

#### Баг 2: Сетевые файлы (SMB/SFTP/FTP/Cloud) — полный пропуск

`isLocalPath()` отсеивает все `smb://`, `sftp://`, `ftp://`, `cloud://`, `content://` пути. Для сетевых файлов **нет никакого механизма** извлечения метаданных в Browse-контексте.

Обходной путь существует только в Player: `ImageLoadingManager.loadAudioCoverArt()` ждёт 1.5 сек, пытается получить artwork от ExoPlayer, затем ищет через iTunes API. Но это **не заполняет** `MediaFile.artist/title/duration`.

#### Баг 3: Player показывает метаданные с задержкой

При открытии аудиофайла в Player:
1. Сначала показывается `audioFileName` = filename без расширения (`ImageLoadingManager.showAudioFileInfo()`)
2. `audioMetadata` **скрыт** (visibility GONE)
3. Через 1-2 сек приходит callback `onAudioMetadataLoaded()` из iTunes API/ExoPlayer
4. Только тогда появляется "Artist - Track • Album (Year)"

Если `MediaFile` уже содержит `artist`/`title` из enrichment — Player **НЕ использует** эти данные (они присутствуют в модели, но UI их игнорирует при инициализации).

### 1.4 Сводная таблица проблем

| Сценарий | Локальный файл | Сетевой файл (SMB/FTP) |
|----------|---------------|------------------------|
| Browse: artist/title при первом scan | ✅ Работает (enrichAudio) | ❌ Пропускается (isLocalPath) |
| Browse: artist/title при повторном scan (cache hit) | ❌ **Теряются** (Entity без полей) | ❌ Не было изначально |
| Browse: duration | ✅ cache hit сохраняет `durationMs` | ❌ Пропускается |
| Player: мгновенное отображение metadata | ❌ Задержка 1-2 сек | ❌ Задержка 1.5-3 сек |
| Player: используются готовые MediaFile.artist/title | ❌ Не проверяются | ❌ Не проверяются |
| Persistent cache: audio metadata | ❌ Не персистятся | ❌ N/A |

---

## 2. Требования (TO-BE)

### FR-1: Персистентное хранение аудиометаданных
- Поля `artist`, `album`, `title` должны сохраняться в `FileMetadataCacheEntity` и восстанавливаться при cache hit.

### FR-2: Восстановление метаданных при cache hit
- При cache hit в `enrichBatch()` → `file.copy()` должен включать `artist`, `album`, `title` из Entity.

### FR-3: Извлечение метаданных для сетевых аудиофайлов
- Для `smb://`, `sftp://`, `ftp://` — извлекать artist/album/title/duration через stream-based `MediaMetadataRetriever` или аналогичный подход.
- Для `cloud://` — использовать API провайдера (если доступно) или аналогичный stream подход.

### FR-4: Отображение метаданных в Browse
- Уже реализовано в `MediaFileAdapter`: `buildAudioDisplayName()` выводит "Artist - Title", `buildFileInfo()` добавляет duration.
- **Требуется**: гарантировать, что данные доступны (FR-1, FR-2, FR-3).

### FR-5: Мгновенное отображение в Player
- При открытии аудиофайла в Player: если `MediaFile.artist` и/или `MediaFile.title` уже заполнены — отображать их немедленно в `audioMetadata` TextView, не дожидаясь iTunes API/ExoPlayer callback.
- Online-поиск обложки и метаданных может дополнить/обновить данные позже (например, обложка, год выпуска).

### FR-6: Сортировка и фильтрация (уже реализовано)
- `SortMode.ARTIST_ASC/DESC`, `TITLE_ASC/DESC`, `DURATION_ASC/DESC` уже существуют.
- `needsMetadataForSort()` уже триггерит `enrichBatch()` при этих режимах.
- **Требуется**: гарантировать корректную работу после исправления FR-1/FR-2/FR-3.

---

## 3. Дизайн решения

### 3.1 DB Migration: Добавление колонок в `file_metadata_cache`

**Migration 17→18**: добавить колонки

```sql
ALTER TABLE file_metadata_cache ADD COLUMN artist TEXT DEFAULT NULL;
ALTER TABLE file_metadata_cache ADD COLUMN album TEXT DEFAULT NULL;
ALTER TABLE file_metadata_cache ADD COLUMN title TEXT DEFAULT NULL;
```

**Файлы**:
- `FileMetadataCacheEntity.kt` — добавить поля `artist: String?`, `album: String?`, `title: String?`
- `AppDatabase.kt` — version 17→18, добавить `MIGRATION_17_18`

### 3.2 Fix: Cache hit восстановление artist/album/title

**Файл**: `CachedMediaMetadataExtractor.kt`

В блоке cache hit (L52-L57) добавить поля:

```kotlin
// БЫЛО:
file.copy(
    duration = cached.durationMs,
    width = cached.width,
    height = cached.height,
    videoRotation = cached.videoRotation,
    exifDateTime = cached.exifDateTime
)

// СТАЛО:
file.copy(
    duration = cached.durationMs,
    width = cached.width,
    height = cached.height,
    videoRotation = cached.videoRotation,
    exifDateTime = cached.exifDateTime,
    artist = cached.artist,
    album = cached.album,
    title = cached.title
)
```

В `mapToEntity()` — включить `artist`, `album`, `title` в сохраняемую Entity.

### 3.3 Сетевые файлы: Stream-based metadata extraction

#### Подход: `MediaMetadataRetriever.setDataSource(FileDescriptor)` + temp file / pipe

`MediaMetadataRetriever` не поддерживает сетевые URI напрямую. Варианты:

| Подход | Плюсы | Минусы |
|--------|-------|--------|
| **A) Partial download → temp file** | Надёжно, MMR работает с файлами | Требует скачивания (первые ~256KB достаточно для ID3), расход трафика |
| **B) `setDataSource(context, Uri)` с content:// proxy** | Нативный Android API | Требует ContentProvider, сложная реализация |
| **C) ExoPlayer `MetadataRetriever`** (Media3) | Поддерживает http/custom DataSource | Experimental API, тяжёлый dependency для batch |
| **D) Чтение ID3/Vorbis тегов напрямую из InputStream** | Лёгкий, минимальный трафик (~32KB) | Нужна библиотека (JAudioTagger, или свой парсер ID3v2) |

**Рекомендация**: Подход **A (partial download)** как наименее рискованный:
1. Скачать первые 256KB файла во временный файл через существующий `TransferAccessor`
2. Вызвать `MediaMetadataRetriever.setDataSource(tempFile.path)`
3. Извлечь artist/album/title/duration
4. Удалить temp файл

**Альтернатива** (подход D): Использовать `JAudioTagger` для парсинга ID3v2/Vorbis тегов из `InputStream` без скачивания полного файла. Более экономичный по трафику, но добавляет зависимость.

#### Место интеграции

**Файл**: `CachedMediaMetadataExtractor.kt`

```kotlin
// enrichBatch() — убрать полный skip для сетевых, оставить skip для non-audio:
if (file.isDirectory) return@map file
if (!isLocalPath(file.path) && file.type != MediaType.AUDIO) return@map file

// Для сетевых audio:
if (!isLocalPath(file.path) && file.type == MediaType.AUDIO) {
    return@map enrichNetworkAudio(file, resourceId, credentialsId)
}
```

Новый метод `enrichNetworkAudio()`:
- Получает InputStream через существующий `TransferAccessor` / `DataSourceFactory` (`data/transfer/`)
- Скачивает header (256KB) → temp file → MMR → extract → delete temp
- Или: ID3 парсер из InputStream (если выбран подход D)

#### Batch-ограничения для сетевых файлов

- **Concurrency**: max 3-4 параллельных запроса (не перегружать сетевое соединение)
- **Timeout**: 5 сек на файл (network download + extraction)
- **Graceful degradation**: если extraction не удалась — оставить MediaFile без метаданных, не прерывать batch
- **Cache**: результат сохраняется в `FileMetadataCacheEntity` — при следующем cache hit метаданные берутся из БД

### 3.4 Player: Мгновенное отображение embedded metadata

**Файл**: `ImageLoadingManager.kt`, метод `showAudioFileInfo()` (~L1282)

```kotlin
// ТЕКУЩЕЕ ПОВЕДЕНИЕ:
// 1. audioMetadata.visibility = GONE
// 2. audioFileName.text = file.name.substringBeforeLast('.')
// 3. audioFileInfo.text = "${size} • ${duration}"
// 4. Позже → onAudioMetadataLoaded() показывает artist/title

// ЦЕЛЕВОЕ ПОВЕДЕНИЕ:
// 1. Проверить MediaFile.artist / MediaFile.title
// 2. Если есть → сразу показать в audioMetadata, скрыть audioFileName
// 3. Если нет → текущий flow (filename + ожидание онлайн-поиска)
```

Изменение:
```kotlin
fun showAudioFileInfo(file: MediaFile, ...) {
    // ... existing size/duration calc ...

    val hasEmbeddedMetadata = !file.artist.isNullOrBlank() || !file.title.isNullOrBlank()
    
    if (hasEmbeddedMetadata) {
        // Мгновенное отображение из уже извлечённых данных
        val lines = mutableListOf<String>()
        val artistTitle = listOfNotNull(
            file.artist?.takeIf { it.isNotBlank() },
            file.title?.takeIf { it.isNotBlank() }
        ).joinToString(" - ")
        if (artistTitle.isNotBlank()) lines.add(artistTitle)
        file.album?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        
        safeViews.audioMetadata.text = lines.joinToString("\n")
        safeViews.audioMetadata.visibility = View.VISIBLE
        safeViews.audioFileName.visibility = View.GONE
    } else {
        // Fallback: показать filename, ждать онлайн-поиск
        safeViews.audioMetadata.visibility = View.GONE
        safeViews.audioFileName.text = file.name.substringBeforeLast('.')
        safeViews.audioFileName.visibility = View.VISIBLE
    }
    
    // Обложку и дополнительные данные по-прежнему загружаем async
    // onAudioMetadataLoaded() может дополнить/обновить данные (release year, cover)
}
```

**Файл**: `PlayerActivity.kt`, `onAudioMetadataLoaded()`

Дополнить: если embedded metadata уже отображены — online callback может добавить `releaseYear`, обновить обложку, но не затирать уже показанные artist/title (если online-данные пустые).

---

## 4. Затрагиваемые файлы

| Файл | Изменение | Приоритет |
|------|-----------|-----------|
| `data/local/db/FileMetadataCacheEntity.kt` | +3 nullable колонки (artist, album, title) | P0 |
| `data/local/db/AppDatabase.kt` | version 18, MIGRATION_17_18 | P0 |
| `core/util/CachedMediaMetadataExtractor.kt` | Cache hit: +artist/album/title. mapToEntity: +поля. Сетевые аудио: enrichNetworkAudio() | P0 |
| `ui/player/ImageLoadingManager.kt` | showAudioFileInfo(): instant metadata display | P1 |
| `ui/player/PlayerActivity.kt` | onAudioMetadataLoaded(): не затирать embedded данные | P1 |
| `data/transfer/` (TransferAccessor/strategy) | Публичный API для partial download header (если подход A) | P2 |
| Новый: `core/util/NetworkAudioMetadataExtractor.kt` (опционально) | Отдельный extractor для сетевых файлов | P2 |

---

## 5. Порядок реализации

### Фаза 1 — Fix: Персистентный кеш аудиометаданных (локальные файлы)

1. **DB Migration 17→18**: добавить `artist`, `album`, `title` в `file_metadata_cache`
2. **FileMetadataCacheEntity**: добавить 3 поля
3. **CachedMediaMetadataExtractor**: 
   - `mapToEntity()` — включить artist/album/title
   - Cache hit блок — восстанавливать artist/album/title
4. **Тестирование**: открыть ресурс с аудио → проверить что при повторном открытии metadata сохраняется

### Фаза 2 — Player: мгновенный показ метаданных

5. **ImageLoadingManager.showAudioFileInfo()** — проверять embedded metadata, показывать сразу
6. **PlayerActivity.onAudioMetadataLoaded()** — не перезаписывать embedded данные пустыми online-данными
7. **Тестирование**: открыть аудио в Player → artist/title видны мгновенно (без 1-2 сек задержки)

### Фаза 3 — Сетевые файлы: metadata extraction

8. Выбрать подход (A: partial download или D: ID3 parser)
9. Реализовать `enrichNetworkAudio()` в CachedMediaMetadataExtractor
10. Интегрировать с TransferAccessor для получения InputStream/partial data
11. Добавить batch-ограничения (concurrency, timeout, graceful degradation)
12. **Тестирование**: SMB/SFTP ресурс с аудио → Browse показывает artist/title

---

## 6. Риски и ограничения

| Риск | Вероятность | Митигация |
|------|------------|-----------|
| Сетевой extraction медленный для большого числа файлов | Высокая | Batch-лимиты (max 3 concurrent), background extraction после первичного отображения списка, progressive enrichment |
| `MediaMetadataRetriever` падает на повреждённых файлах | Средняя | `runCatching`, graceful degradation (уже реализовано) |
| Partial download (256KB) недостаточен для некоторых форматов | Низкая | ID3v2 header обычно < 32KB; для FLAC/OGG Vorbis comment тоже в начале файла |
| DB migration на больших базах | Низкая | ALTER TABLE ADD COLUMN — O(1), без rebuild таблицы |
| JAudioTagger (подход D) добавляет dependency | Средняя | Опционально — можно начать с подхода A |

---

## 7. Вопросы для решения перед реализацией

1. **Подход для сетевых файлов**: A (partial download + MMR) или D (ID3 parser из InputStream)? Подход A проще, подход D экономнее по трафику.
2. **Progressive enrichment для сетевых**: показывать список сразу (без metadata) и обогащать в фоне (с обновлением UI) — или блокировать до завершения enrichment?
3. **Cloud providers** (Google Drive, Dropbox): извлекать metadata через API провайдера (у Google Drive есть metadata в API response) или unified подход через stream?
4. **Scope Фазы 3**: включать только SMB/SFTP/FTP или также cloud providers?

---

## 8. Связанные файлы (для reference)

- `app_v2/src/main/java/com/sza/fastmediasorter/core/util/CachedMediaMetadataExtractor.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheEntity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheDao.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` (MediaFile)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/cache/MediaFilesCacheManager.kt`
