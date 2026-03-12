# Спецификация: Обогащение аудиометаданными при загрузке списка файлов

**Дата**: 2026-03-03  
**Переоценка**: 2026-03-11  
**Статус**: RE-ESTIMATED — Фазы 1 и 2 уже реализованы; осталась только Фаза 3  
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
| 3 | Cache hit: `lastModified == createdDate && fileSize == size` → копирует `duration`, `width`, `height`, `videoRotation`, `exifDateTime`, **`artist`, `album`, `title`** | `CachedMediaMetadataExtractor.kt:L52-L62` |
| 4 | Cache miss: вызывает `enrichAudio()` → `MediaMetadataRetriever` → `file.copy(artist, album, title, duration)` | `CachedMediaMetadataExtractor.kt:L93-L107` |
| 5 | Сохраняет в `FileMetadataCacheEntity` через `upsertAll()` | `CachedMediaMetadataExtractor.kt:L68` |

### 1.3 Выявленные проблемы

#### ~~Баг 1: `FileMetadataCacheEntity` НЕ ХРАНИТ `artist`, `album`, `title`~~ ✅ ИСПРАВЛЕНО

> **Статус**: ✅ Исправлено. `MIGRATION_17_18` добавила колонки `artist`, `album`, `title` в таблицу `file_metadata_cache`. `enrichBatch()` восстанавливает их при cache hit. `mapToEntity()` сохраняет при cache miss.

~~**Таблица `file_metadata_cache`** содержит:~~
~~**Отсутствуют колонки**: `artist`, `album`, `title`.~~

#### Баг 2: Сетевые файлы (SMB/SFTP/FTP/Cloud) — полный пропуск

`isLocalPath()` отсеивает все `smb://`, `sftp://`, `ftp://`, `cloud://`, `content://` пути. Для сетевых файлов **нет никакого механизма** извлечения метаданных в Browse-контексте.

Обходной путь существует только в Player: `ImageLoadingManager.loadAudioCoverArt()` ждёт 1.5 сек, пытается получить artwork от ExoPlayer, затем ищет через iTunes API. Но это **не заполняет** `MediaFile.artist/title/duration`.

#### ~~Баг 3: Player показывает метаданные с задержкой~~ ✅ ИСПРАВЛЕНО

> **Статус**: ✅ Исправлено. `showAudioFileInfo()` (~L1342-1392) теперь проверяет `MediaFile.artist/album/title` и отображает их мгновенно, с fallback на filename. `onAudioMetadataLoaded()` (~L3177-3205) — не-деструктивный: не затирает embedded данные пустыми online-данными.

### 1.4 Сводная таблица проблем

| Сценарий | Локальный файл | Сетевой файл (SMB/FTP) |
|----------|---------------|------------------------|
| Browse: artist/title при первом scan | ✅ Работает (enrichAudio) | ❌ Пропускается (isLocalPath) |
| Browse: artist/title при повторном scan (cache hit) | ✅ **ИСПРАВЛЕНО** (Entity + cache hit) | ❌ Не было изначально |
| Browse: duration | ✅ cache hit сохраняет `durationMs` | ❌ Пропускается |
| Player: мгновенное отображение metadata | ✅ **ИСПРАВЛЕНО** (instant display) | ❌ Задержка 1.5-3 сек |
| Player: используются готовые MediaFile.artist/title | ✅ **ИСПРАВЛЕНО** (showAudioFileInfo) | ❌ Не проверяются |
| Persistent cache: audio metadata | ✅ **ИСПРАВЛЕНО** (DB v18) | ❌ N/A |

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

### 3.3 Сетевые файлы: Viewport-based metadata extraction (ПЕРЕСМОТРЕННЫЙ ДИЗАЙН)

> **Обновлено 2026-03-11**: Оригинальный подход с batch-enrichment заменён на viewport-based lazy-loading по аналогии с существующим механизмом загрузки thumbnails. Это снижает трафик в ~100× и исключает блокировку UI.

#### 3.3.1 Проблема batch-подхода

Оригинальный дизайн предполагал `enrichBatch()` для **всех** файлов при загрузке списка. Для сетевого ресурса с 10,000 MP3 файлами это означает:
- **Batch (partial download 256KB)**: 10,000 × 256KB = **~2.5 GB** трафика
- **Batch (Media3 stream 32KB)**: 10,000 × 32KB = **~312 MB** трафика
- **Блокировка UI**: список файлов не отображается, пока enrichment не завершён (минуты)

#### 3.3.2 Решение: Viewport-based lazy loading (как thumbnails)

В проекте **уже существует** зрелый механизм lazy-loading для thumbnails, который грузит данные только для видимых элементов. Мы переиспользуем эту инфраструктуру.

**Существующий механизм thumbnails (в коде)**:

```
BrowseActivity.addOnScrollListener()            // уже реализован
    → DRAGGING/SETTLING: adapter.setScrolling(true)  // пауза загрузок
    → IDLE:
        1. layoutManager.findFirstVisibleItemPosition()
        2. layoutManager.findLastVisibleItemPosition()
        3. adapter.loadVisibleThumbnails(first, last)
           → notifyItemRangeChanged(range, "LOAD_THUMBNAILS")  ← payload
           → onBindViewHolder(payloads) → loadThumbnailOnly()  ← partial bind
```

**Ключевые свойства**, которые мы переиспользуем:
- `isScrolling` flag — пауза загрузок во время скролла (MediaFileAdapter)
- Payload-based partial bind — обновляет только нужные view, без полного rebind
- Prefetch distance = 15 items ahead
- Glide auto-cancel для off-screen items
- Failed-cache (FIFO 5000) — предотвращает retry bombing

**Трафик с viewport-подходом (10,000 файлов)**:

| Сценарий | Файлов загружается | Трафик (32KB/файл) |
|----------|--------------------|--------------------|
| Открыл папку (50 visible + 15 prefetch) | 65 | **~2 MB** |
| Пролистал 500 файлов за сессию | 500 | **~16 MB** |
| Пролистал все 10,000 | 10,000 | ~312 MB |
| **Batch (старый подход)** | **10,000 сразу** | **~312 MB сразу** |

#### 3.3.3 Выбор метода extraction: сравнение библиотек

**Текущие зависимости проекта** (из `app_v2/build.gradle.kts`):
- `androidx.media3:media3-exoplayer:1.2.1` — ✅ уже подключён
- `com.hierynomus:smbj:0.12.1` — SMB, поддерживает random access read
- `com.github.mwiede:jsch:0.2.16` — SFTP, поддерживает offset read
- `commons-net:commons-net:3.10.0` — FTP, поддерживает `setRestartOffset()`

**Сравнение подходов**:

| Подход | Новая зависимость | Трафик на файл | Форматы | Temp файлы | Риск |
|--------|-------------------|----------------|---------|------------|------|
| **A) Partial download (256KB) → temp file → MediaMetadataRetriever** | Нет | 256 KB | Все | Да | Средний — MMR может упасть (SIGSEGV) на truncated файле |
| **B) Media3 `MetadataRetriever` + custom `ByteArrayDataSource`** | **Нет** (media3 уже есть) | **~32 KB** | **Все** (MP3/FLAC/OGG/M4A) | **Нет** | Низкий — API стабилен с media3 1.2+ |
| C) `mp3agic:0.9.1` — lightweight ID3 parser | +80 KB | ~20 KB | **Только MP3** | Нет | Низкий, но ограничен форматами |
| D) `JAudioTagger:3.0.1` — full-featured tag library | +1.2 MB | ~32 KB | Все | Да (нужен `RandomAccessFile`) | Средний — тяжёлая зависимость |

**✅ РЕКОМЕНДАЦИЯ: Подход B — Media3 MetadataRetriever** (zero new dependencies)

Преимущества:
- Зависимость `media3-exoplayer:1.2.1` **уже подключена** → 0 байт новых библиотек
- Работает с custom `DataSource` → можно обернуть byte[] буфер из network stream
- Поддерживает ВСЕ audio форматы (MP3 ID3v1/v2, FLAC Vorbis Comment, OGG, M4A/AAC, WAV)
- Нет temp файлов — парсит из памяти
- Thread-safe, cancellable

Flow:
```
SFTP/SMB InputStream (first 32-64KB)
    → byte[] buffer (in-memory)
    → Media3 ByteArrayDataSource
    → Media3 MetadataRetriever.retrieveMetadata()
    → artist, album, title, duration
    → save to FileMetadataCacheEntity (DB)
```

#### 3.3.4 Partial read API в сетевых провайдерах

Все три сетевые библиотеки **нативно поддерживают partial read** (не нужно скачивать полный файл):

| Протокол | Библиотека | API для partial read |
|----------|------------|---------------------|
| **SMB** | `smbj:0.12.1` | `file.read(buffer, offset, length)` — random access, читаем первые 64KB |
| **SFTP** | `jsch:0.2.16` | `channelSftp.get(path, monitor, offset)` — поддерживает offset; или просто close stream после 64KB |
| **FTP** | `commons-net:3.10.0` | `ftpClient.setRestartOffset(0)` + close `InputStream` после чтения 64KB |

Необходимо добавить в `FileTransferProvider` интерфейс:
```kotlin
suspend fun readPartial(path: String, maxBytes: Int): ByteArray
```

#### 3.3.5 Архитектура: полная схема viewport-based loading

```
┌─────────────────────────────────────────────────────────────┐
│ BrowseActivity (scroll listener — УЖЕ СУЩЕСТВУЕТ)           │
│   → onScrollStateChanged(IDLE)                              │
│   → firstVisible = layoutManager.findFirstVisibleItemPos()  │
│   → lastVisible  = layoutManager.findLastVisibleItemPos()   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ MediaFileAdapter                                            │
│   → loadVisibleAudioMetadata(first, last)       [НОВОЕ]     │
│   → notifyItemRangeChanged(range, "LOAD_AUDIO_METADATA")    │
│   → onBindViewHolder(payloads):                             │
│       if "LOAD_AUDIO_METADATA" && file.type == AUDIO        │
│       && file.artist == null:                               │
│           → AudioMetadataLoader.loadIfNeeded(file, callback)│
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ AudioMetadataLoader (НОВЫЙ класс)                           │
│   1. Check DB cache (FileMetadataCacheDao) → cache hit?     │
│      → YES: update MediaFile, bind to ViewHolder, DONE      │
│   2. Cache miss → check failedCache (FIFO 5000) → known?    │
│      → YES: skip, DONE                                      │
│   3. Enqueue network fetch (Dispatchers.IO, max 3 concurrent)│
│      → readPartial(file.path, 64KB)                         │
│      → Media3 MetadataRetriever(ByteArrayDataSource)        │
│      → Extract artist/album/title/duration                  │
│      → Save to FileMetadataCacheEntity                      │
│      → Callback: update adapter item + partial bind         │
│   4. On failure → markFailed(path) in failedCache           │
└────────────────────────┬────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
    ┌──────────┐  ┌───────────┐  ┌──────────┐
    │ SMB      │  │ SFTP      │  │ FTP      │
    │ smbj     │  │ jsch      │  │ commons  │
    │ .read()  │  │ .get()    │  │ .retr()  │
    │ 64KB     │  │ 64KB      │  │ 64KB     │
    └──────────┘  └───────────┘  └──────────┘
```

#### 3.3.6 Интеграция с существующим scroll listener

**Минимальные изменения** в `BrowseActivity.kt` (scroll listener ~L370-405):

```kotlin
// ТЕКУЩИЙ КОД (уже работает для thumbnails):
override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
    when (newState) {
        RecyclerView.SCROLL_STATE_IDLE -> {
            adapter.setScrolling(false)
            val first = layoutManager.findFirstVisibleItemPosition()
            val last = layoutManager.findLastVisibleItemPosition()
            adapter.loadVisibleThumbnails(first, last)
            // ↓ ДОБАВИТЬ ОДНУ СТРОКУ:
            adapter.loadVisibleAudioMetadata(first, last)
        }
        else -> adapter.setScrolling(true)
    }
}
```

**В `MediaFileAdapter.kt`** — новый payload handler:

```kotlin
companion object {
    const val PAYLOAD_LOAD_THUMBNAILS = "LOAD_THUMBNAILS"  // существующий
    const val PAYLOAD_AUDIO_METADATA = "LOAD_AUDIO_METADATA"  // НОВЫЙ
}

fun loadVisibleAudioMetadata(firstVisible: Int, lastVisible: Int) {
    if (firstVisible < 0 || lastVisible < 0) return
    notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1, PAYLOAD_AUDIO_METADATA)
}

override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
    if (payloads.contains(PAYLOAD_AUDIO_METADATA)) {
        val file = getItem(position)
        if (file.type == MediaType.AUDIO && file.artist == null && !isLocalPath(file.path)) {
            audioMetadataLoader.loadIfNeeded(file, position) { enrichedFile ->
                updateItem(position, enrichedFile)
                notifyItemChanged(position, PAYLOAD_AUDIO_METADATA)
            }
        }
        return
    }
    // ... existing payload handling ...
}
```

### 3.4 Player: Мгновенное отображение embedded metadata

> **Статус**: ✅ УЖЕ РЕАЛИЗОВАНО (см. раздел 9.1 Переоценка)

`ImageLoadingManager.showAudioFileInfo()` (~L1342-1392) уже отображает `artist – album – title` из `MediaFile` мгновенно, с fallback на filename. `PlayerActivity.onAudioMetadataLoaded()` (~L3177-3205) — не-деструктивное обновление: обновляет только если online-поиск вернул непустые данные.

---

## 4. Затрагиваемые файлы (ПЕРЕСМОТРЕНО 2026-03-11)

### ✅ Фазы 1 и 2 — уже реализованы, изменения не требуются

| Файл | Статус |
|------|--------|
| `data/local/db/FileMetadataCacheEntity.kt` | ✅ DONE — поля `artist`, `album`, `title` присутствуют |
| `data/local/db/AppDatabase.kt` | ✅ DONE — version 18, `MIGRATION_17_18` добавляет колонки |
| `core/util/CachedMediaMetadataExtractor.kt` | ✅ DONE — cache hit восстанавливает audio fields, `mapToEntity()` сохраняет, `enrichAudio()` извлекает |
| `ui/player/ImageLoadingManager.kt` | ✅ DONE — `showAudioFileInfo()` мгновенно отображает metadata |
| `ui/player/PlayerActivity.kt` | ✅ DONE — `onAudioMetadataLoaded()` не затирает embedded данные |
| `domain/model/Models.kt` | ✅ DONE — `MediaFile` содержит `artist`, `album`, `title` |

### ⚠️ Фаза 3 — требует реализации

| Файл | Изменение | Приоритет |
|------|-----------|-----------|
| `data/transfer/FileTransferProvider.kt` | Добавить `suspend fun readPartial(path: String, maxBytes: Int): ByteArray` | P0 |
| `data/transfer/SmbTransferProvider.kt` | Реализация `readPartial()` через `smbj` file.read() | P0 |
| `data/transfer/SftpTransferProvider.kt` (или аналог) | Реализация `readPartial()` через `jsch` channel.get() | P0 |
| `data/transfer/FtpTransferProvider.kt` (или аналог) | Реализация `readPartial()` через `commons-net` InputStream + close | P0 |
| **Новый**: `core/util/AudioMetadataLoader.kt` | Viewport-triggered loader: DB cache check → network fetch → Media3 parse → save | P0 |
| `ui/browse/MediaFileAdapter.kt` | Новый payload `LOAD_AUDIO_METADATA`, метод `loadVisibleAudioMetadata()`, handler в `onBindViewHolder` | P0 |
| `ui/browse/BrowseActivity.kt` | +1 строка в scroll listener IDLE: `adapter.loadVisibleAudioMetadata(first, last)` | P0 |
| `di/` (Hilt module) | Подключить `AudioMetadataLoader` через DI | P1 |

---

## 5. Порядок реализации (ПЕРЕСМОТРЕНО 2026-03-11)

### ~~Фаза 1 — Fix: Персистентный кеш аудиометаданных (локальные файлы)~~ ✅ DONE
### ~~Фаза 2 — Player: мгновенный показ метаданных~~ ✅ DONE

### Фаза 3 — Сетевые файлы: Viewport-based metadata extraction

**Шаг 3.1**: Partial read API
1. Добавить `readPartial(path, maxBytes): ByteArray` в `FileTransferProvider` interface
2. Реализовать в `SmbTransferProvider` (smbj random access read)
3. Реализовать в SFTP provider (jsch channel.get + close after N bytes)
4. Реализовать в FTP provider (commons-net InputStream + close after N bytes)

**Шаг 3.2**: AudioMetadataLoader
5. Создать `AudioMetadataLoader` — основной класс:
   - DB cache check (`FileMetadataCacheDao`)
   - Failed cache (FIFO LinkedHashMap, max 5000 entries)
   - Network fetch with concurrency limiter (`Semaphore(3)`)
   - Media3 `MetadataRetriever` + `ByteArrayDataSource` для parsing
   - Save result to DB, callback to adapter

**Шаг 3.3**: Adapter integration
6. Добавить `PAYLOAD_AUDIO_METADATA` в `MediaFileAdapter`
7. Метод `loadVisibleAudioMetadata(first, last)` → `notifyItemRangeChanged`
8. Handler в `onBindViewHolder(payloads)` → вызов `AudioMetadataLoader`

**Шаг 3.4**: Scroll listener integration
9. +1 строка в `BrowseActivity.onScrollStateChanged(IDLE)`: `adapter.loadVisibleAudioMetadata(first, last)`

**Шаг 3.5**: Тестирование
10. Unit tests: AudioMetadataLoader с mock DAO + mock TransferProvider
11. Integration: SMB ресурс с MP3/FLAC → Browse показывает artist/title
12. Integration: SFTP ресурс с MP3 → Browse показывает artist/title
13. Stress: 10,000 файлов → проверить что UI не блокируется, трафик минимален

---

## 6. Анализ рисков Фазы 3 (обновлено 2026-03-11)

### Высокий риск

| Риск | Влияние | Митигация |
|------|---------|-----------|
| **Нет partial read API** в `FileTransferProvider` | Текущий интерфейс имеет только `downloadFile()` для полных файлов. Добавление `readPartial()` затрагивает 3 провайдера (SMB/SFTP/FTP) с разными streaming API | Каждый провайдер реализуется отдельно, протокольные edge cases тестируются изолированно |
| **SMB connection pool exhaustion** | Пакетное извлечение metadata из 100+ аудиофайлов = 100+ concurrent SMB соединений. `ConnectionThrottleManager` существует, но enrichment с ним не интегрирован | `Semaphore(3)` в `AudioMetadataLoader` + интеграция с `ConnectionThrottleManager` |
| **Скрытый расход трафика на мобильных сетях** | 64KB × 500 файлов = ~32MB при неосознанном скролле. Пользователь на metered connection получает неожиданный расход | Опциональная настройка "Extract metadata for network files" (off by default на metered); viewport-loading вместо batch радикально снижает проблему |

### Средний риск

| Риск | Влияние | Митигация |
|------|---------|-----------|
| **MediaMetadataRetriever crash на truncated файле** | MMR (подход A) ожидает валидную файловую структуру. Truncated 256KB файл может вызвать native crash (SIGSEGV) на некоторых Android версиях — не перехватывается `runCatching` | Использовать Media3 MetadataRetriever (подход B) вместо MMR — парсит из byte[], не вызывает native crashes |
| **FTP/SFTP partial read несовместимость** | FTP `RETR` с range и SFTP partial read имеют непредсказуемую поддержку на некоторых серверах | Graceful fallback: если partial read не удался → skip файл, записать в failedCache |
| **Temp file cleanup при crash** | (Только для подхода A) Если приложение падает during extraction — orphaned temp файлы накапливаются | Подход B (Media3) не создаёт temp файлов вообще. Если A — `try/finally` delete + periodic cleanup |
| **`MediaMetadataRetriever` падает на повреждённых файлах** | Средняя | `runCatching`, graceful degradation (уже реализовано для локальных файлов) |

### Низкий риск

| Риск | Влияние | Митигация |
|------|---------|-----------|
| ID3 теги за пределами 64KB | Редко, но возможно: album art embedded в ID3v2 header может вытолкнуть теги за 64KB | Увеличить до 128KB; ID3v2 header без artwork обычно < 32KB |
| Cloud API rate limits | Google Drive/Dropbox имеют per-minute лимиты; batch извлечение может триггерить throttling | Cloud scope в отдельной итерации; retry с exponential backoff |
| DB migration на больших базах | ALTER TABLE ADD COLUMN — O(1) | ✅ Уже реализовано (MIGRATION_17_18), подтверждённо |

---

## 7. Сравнение подходов: batch vs viewport (РЕШЕНИЕ)

| Характеристика | Batch enrichment (старый дизайн) | Viewport-based (новый дизайн) |
|----------------|----------------------------------|-------------------------------|
| **Трафик на 10,000 MP3** | ~312 MB сразу | ~2 MB (visible) + по запросу |
| **Блокировка UI** | Да — список не появляется до завершения | Нет — список появляется мгновенно |
| **Нагрузка на сеть** | Взрывная (все файлы сразу) | Плавная (3 concurrent max) |
| **Отмена при уходе** | Не реализована | Автоматическая (off-screen = cancel) |
| **Повторный вход** | Повторный batch (если нет cache hit) | Cache hit из DB, нет повторных запросов |
| **Инфраструктура** | Новая (batch queue, progress) | Переиспользует scroll listener + payload |
| **Сложность** | Высокая | Средняя |

**✅ РЕШЕНИЕ: Viewport-based подход с Media3 MetadataRetriever.**

---

## 8. Связанные файлы (для reference)

### Существующие (будут переиспользованы / расширены)
- `app_v2/src/main/java/com/sza/fastmediasorter/core/util/CachedMediaMetadataExtractor.kt` — текущий extractor (локальные файлы)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheEntity.kt` — DB entity (поля ready)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheDao.kt` — DAO (query/upsert ready)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` — DB v18 (migration ready)
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` — MediaFile (поля ready)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt` — payload-based binding infra
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` — scroll listener (~L370-405)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileTransferProvider.kt` — interface для partial read
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/SmbTransferProvider.kt` — SMB implementation
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileDataFetcher.kt` — failed cache pattern (reuse)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` — audio info display (done)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` — metadata callback (done)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt` — throttling infra

### Новые (будут созданы)
- `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt` — viewport-triggered loader

---

## 9. Переоценка (RE-ESTIMATION) — 2026-03-11

### 9.1 Уже реализовано (не требует работы)

Проверка кодовой базы показала, что **Фаза 1 и Фаза 2 из оригинальной спецификации полностью реализованы**:

| Компонент | Статус | Детали |
|-----------|--------|--------|
| **DB Schema (v18)** | ✅ DONE | `MIGRATION_17_18` добавляет `artist`, `album`, `title` в `file_metadata_cache`. Идемпотентна. |
| **FileMetadataCacheEntity** | ✅ DONE | Поля `artist: String?`, `album: String?`, `title: String?` присутствуют (~L21). |
| **CachedMediaMetadataExtractor — cache hit** | ✅ DONE | `enrichBatch()` восстанавливает `artist`, `album`, `title` из Entity при cache hit (~L60-62). |
| **CachedMediaMetadataExtractor — cache miss** | ✅ DONE | `enrichAudio()` извлекает через `MediaMetadataRetriever`. `mapToEntity()` сохраняет artist/album/title в DB (~L176-178). |
| **ImageLoadingManager.showAudioFileInfo()** | ✅ DONE | Мгновенное отображение `artist – album – title` из `MediaFile`, с fallback на filename (~L1342-1392). |
| **PlayerActivity.onAudioMetadataLoaded()** | ✅ DONE | Не-деструктивное обновление: обновляет только если online-поиск вернул непустые данные (~L3177-3205). |
| **MediaFile data class** | ✅ DONE | Поля `artist`, `album`, `title` присутствуют в domain model. |

### 9.2 Финальная оценка — Фаза 3 (viewport-based, Media3)

| # | Задача | Сложность | Оценка |
|---|--------|-----------|--------|
| 3.1 | **Partial read API**: `readPartial()` в FileTransferProvider + реализации для SMB/SFTP/FTP | Средняя | 2-3 SP |
| 3.2 | **AudioMetadataLoader**: DB cache check, failed cache, Media3 parsing, save to DB, callback | Средняя | 2-3 SP |
| 3.3 | **Adapter payload**: `LOAD_AUDIO_METADATA` + handler + `loadVisibleAudioMetadata()` | Низкая | 1 SP |
| 3.4 | **Scroll listener**: +1 строка в BrowseActivity IDLE handler | Тривиальная | 0.5 SP |
| 3.5 | **Concurrency/throttling**: Semaphore(3), timeout 5s/file, integration с ConnectionThrottleManager | Низкая | 1 SP |
| 3.6 | **Тестирование**: unit + integration SMB/SFTP/FTP | Средняя | 2-3 SP |
| | **ИТОГО Фаза 3** | | **9-11 SP** |

### 9.3 Сравнение с предыдущими оценками

| | Оригинал (2026-03-03) | Переоценка v1 (batch) | **Переоценка v2 (viewport)** |
|---|---|---|---|
| Фаза 1 (DB + cache fix) | ~3-5 SP | ✅ 0 SP | ✅ 0 SP |
| Фаза 2 (Player instant) | ~3-5 SP | ✅ 0 SP | ✅ 0 SP |
| Фаза 3 (Сетевые файлы) | ~8-12 SP | 12-17 SP | **9-11 SP** |
| **ИТОГО** | **~14-22 SP** | **12-17 SP** | **9-11 SP** |

Снижение оценки Фазы 3 благодаря:
- Переиспользование scroll/payload инфраструктуры (вместо нового batch pipeline)
- Media3 вместо partial download + temp files (проще, меньше кода)
- Нет новых зависимостей

### 9.4 Открытые вопросы

1. **Cloud providers** (Google Drive, Dropbox, OneDrive): отдельная итерация? Google Drive API возвращает metadata в ответе listing, что может исключить необходимость stream extraction.
2. **Настройка пользователя**: добавить toggle "Extract audio metadata for network files" в Settings? По умолчанию: ON для Wi-Fi, OFF для metered?
3. **Sort by artist/title для сетевых**: при viewport-loading metadata приходит постепенно. Sort by artist невозможен, пока не загружены все metadata. Решение: показать warning "Sort by artist requires loading metadata for all files" или trigger batch-load при выборе этого sort mode?
