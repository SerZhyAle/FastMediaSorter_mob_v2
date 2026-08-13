# Стратегическая спецификация: S0136 — Glide disk cache не сохраняется между сессиями

**Ticket:** S0136
**Status:** Tactical
**Field log:** `logs/fastmediasorter_20260510_201249.log` + `logs/fastmediasorter_20260510_203412.log` — Samsung SM-S731B (Galaxy S25), Android 16 / API 36, v2.60.5102.002-DEBUG
**Priority:** 85
**Date:** 2026-05-10
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — полевая сессия 2026-05-10, лог `logs/fastmediasorter_20260510_012252.log`
**Tactical plan:** `PLAN/S0136_bugfix-glide-disk-cache-not-persisting/INDEX.md`

> **Scope:** STRATEGIC + RESEARCH. Цели и гипотезы по причине, почему `image_cache` пустой на старте и почему за полную сессию диск-кеш не накапливает hit'ов. Тактическая фаза — после Phase R.

---

## 1. Проблема

В сессии 2026-05-10 01:22..01:57 (≈34 мин) встроенная диагностика зафиксировала:

- На старте `CacheStatusHelper.logGlideDiskCacheStatus`: `Cache directory does NOT exist: /data/user/0/com.sza.fastmediasorter/cache/image_cache`. Сообщение «no thumbnails were cached from previous sessions».
- В конце сессии `GlideCacheStats.logStats`: `WARNING: Zero disk cache hits with 21 total loads`.

То есть:

- При старте каталог Glide диск-кеша физически отсутствует (хотя в `GlideAppModule.applyOptions` есть код `cacheDir.mkdirs()` — значит, до первой загрузки он не вызывался либо каталог удаляется между сессиями).
- За время активного просмотра 21 загрузка не дала ни одного hit'а ни в `RESOURCE_DISK_CACHE`, ни в `DATA_DISK_CACHE` (счётчик `diskCacheHits == 0`).

### 1.1 Что неизвестно

- Какой `DataSource` фактически возвращает Glide для этих 21 загрузок (REMOTE / LOCAL / MEMORY_CACHE).
- Меняются ли cache key между запусками (зависят ли они от `refreshVersion`, `createdDate`, `size` полей `NetworkFileData`).
- Удаляет ли приложение `image_cache` где-то явно (на logout, смене языка, очистке настроек).
- Почему `cacheDir.mkdirs()` в `GlideAppModule.applyOptions` ([di/GlideAppModule.kt:69-75](app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt#L69-L75)) не приводит к существованию каталога к моменту `CacheStatusHelper.logGlideDiskCacheStatus`. Возможные причины: `applyOptions` вызывается лениво при первом обращении к Glide; диагностика читает каталог раньше первой загрузки; либо каталог создаётся, но тут же затирается перед первой загрузкой.
- Влияет ли `BuildType=release` (без proguard правил для Glide) на регистрацию `@GlideModule`.

### 1.2 Влияние на пользователя

- Каждый просмотр сетевой папки скачивает миниатюры заново — **трафик, батарея, задержка отрисовки**.
- На медленных SMB/SFTP/cloud резервах повторное открытие той же папки выглядит как «приложение тормозит», хотя файлы уже однажды скачивались.
- Конфигурация `cacheSizeMb=2048MB` фактически бесполезна — резерв занимает место в `Internal Free`, но ничего не кеширует.

---

## 2. Цели

1. Понять, **почему** `image_cache` пуст к началу новой сессии: каталог не создаётся, удаляется, либо создаётся в другом месте.
2. Понять, **почему** за активную сессию диск-кеш не накапливает hit'ов: Glide пишет, но ключи не совпадают; Glide не пишет; путь загрузки идёт мимо диск-кеша.
3. После Research: одна точечная правка, восстанавливающая работу диск-кеша между сессиями. Critical success metric: после двух запусков подряд по той же сетевой папке, во второй сессии `diskCacheHits > 0`.

**Non-goals:**

- Не менять стратегию диск-кеша (`DiskCacheStrategy.ALL` сохранять).
- Не менять размер `memoryCacheSize` или политику memory cache.
- Не трогать `ThumbnailCacheRepository` (отдельный filesDir-backed уровень) — это параллельная подсистема.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Сначала research, потом фикс — гипотез слишком много, чтобы угадывать.
2. Диагностика после фикса должна давать однозначный сигнал в `GlideCacheStats.logStats`: ratio diskCacheHits / total ≥ 30 % при повторном просмотре уже виденных папок.
3. Не плодить новые лог-каналы — расширить существующие (`CacheStatusHelper`, `GlideCacheStats`).

### 3.2 Жёсткие ограничения

- **Flavor:** все, где Glide активен (все).
- **API level:** без изменений (minSdk 26 / 23).
- **Совместимость данных:** изменений Room-схемы не требуется.
- **Производительность:** диагностика не должна добавлять более 1 % overhead к загрузке миниатюр.

---

## 4. Контекст текущей архитектуры

`GlideAppModule` ([di/GlideAppModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt)) — единая точка конфигурации Glide:

- Memory cache: `LruResourceCache(min(heap*10%, 64MB))`.
- Disk cache: `InternalCacheDiskCacheFactory(context, "image_cache", diskCacheSize)`, размер из `glide_config` SharedPreferences (default 2048MB).
- `setDefaultRequestOptions(diskCacheStrategy = DiskCacheStrategy.RESOURCE)`.
- `cacheDir.mkdirs()` вызывается в `applyOptions` перед регистрацией `setDiskCache`.

`AdapterThumbnailLoader` ([ui/browse/AdapterThumbnailLoader.kt:451-487](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt#L451-L487)) — основной потребитель для сетевых миниатюр:

- `Glide.with(context).load(NetworkFileData(...))`
- `.diskCacheStrategy(DiskCacheStrategy.ALL)` — переопределяет default.
- `.override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE).centerCrop()`
- `.onlyRetrieveFromCache(isScrolling)` — при скролле не идёт в сеть.
- `RequestListener.onResourceReady` → `GlideCacheStats.recordLoad(dataSource)`.

`NetworkFileData` ([data/network/glide/NetworkFileData.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileData.kt)) — модель ключа Glide для SMB/SFTP/FTP. Её `equals`/`hashCode` определяют cache key. Если в составе ключа есть нестабильное поле (например, текущий `size` файла, который меняется при сканировании, или `createdDate` с миллисекундами), то ключ при каждом scan другой и hit невозможен.

`CacheStatusHelper` ([core/util/CacheStatusHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/CacheStatusHelper.kt)) — стартовая диагностика. Вызывается из `FastMediaSorterApp.onCreate()` (предположительно, проверим).

`GlideCacheStats` ([utils/GlideCacheStats.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt)) — runtime-счётчики hit/miss; печатает `WARNING: Zero disk cache hits` при `disk == 0 && repo == 0 && total > 10`.

---

## 5. Предлагаемый подход

### 5.1 Этапы работы

**Phase R (Research, обязательная первая):**

1. **R1 — точка вызова `CacheStatusHelper`:** убедиться, что диагностика стартует **после** Glide initialisation, а не до. Если до — она и должна показывать «отсутствует», это нормально, и гипотеза «каталог не создаётся» опровергается.
2. **R2 — `cacheDir` после первой загрузки:** добавить `Timber.i` сразу после первого успешного `onResourceReady` с дамп `image_cache.exists() && files.size`.
3. **R3 — стабильность cache key:** лог `Timber.v` в `NetworkFileData.equals`/`hashCode` или вывод `model.toString()` в `RequestListener.onResourceReady` для одной и той же миниатюры в двух сессиях → сравнить.
4. **R4 — `DataSource` распределение для тех 21 загрузок:** уже логируется через `GlideCacheStats.recordLoad`, но в сводке не выводится количество REMOTE/LOCAL/MEMORY_CACHE — расширить `logStats` до per-source breakdown, чтобы понять, идёт ли путь через memory cache (тогда диск не нужен) или весь идёт сети (тогда диск-кеш не пишется или пишется не туда).
5. **R5 — Reproducibility:** запустить приложение, открыть SMB-папку → `Force stop` → запустить снова, открыть ту же папку → проверить `image_cache` на существование и наличие файлов; собрать новый лог.
6. **R6 — Поиск явных удалений:** grep по `cacheDir`, `image_cache`, `deleteRecursively`, `Glide.get(context).clearDiskCache()` — найти все места, которые могут стирать диск-кеш приложения.

**Phase D (Decide):**

По итогам R принять одно из:

- **D1.** Cache key нестабилен (R3 показал разные хэши) → исправить `NetworkFileData.equals/hashCode`, исключив нестабильные поля.
- **D2.** Диск-кеш стирается явно (R6 нашёл вызов) → удалить лишний вызов или ограничить сценарием «пользователь явно очищает кэш».
- **D3.** `setDiskCache` не применяется (Glide использует default location) → проверить логи Glide в `setLogLevel(VERBOSE)` или регистрацию `@GlideModule` в манифесте.
- **D4.** Все hit идут через `MEMORY_CACHE` (R4 показал memory ≫ 0) → не баг, а ожидание; `WARNING: Zero disk cache hits` нужно понизить до D-уровня для случая `total == memory`.

**Phase F (Fix):** одна правка по итогам D.

### 5.2 Точки расширяемости

- Per-source breakdown в `GlideCacheStats.logStats` остаётся в коде после исследования и помогает в любых будущих regression.

---

## 6. Открытые вопросы / Research items

1. **Когда вызывается `CacheStatusHelper.logGlideDiskCacheStatus`?** До или после первой загрузки Glide?
   - **Статус:** **Resolved 2026-05-10.** Вызывается в `GlideAppModule.applyOptions` — т.е. при инициализации Glide, до первой загрузки. Показывает состояние `image_cache` на момент запуска: сессия 1 → `exists=true fileCount=0`; сессия 2 → `exists=true fileCount=5`. Каталог создан `mkdirs()` в `applyOptions`, существует к моменту чтения.
2. **Меняются ли `NetworkFileData.equals`/`hashCode` от сессии к сессии для одного и того же файла?**
   - Поля модели: `path`, `credentialsId`, `loadFullImage`, `size`, `createdDate`. Из них `size` и `createdDate` могут меняться, если SMB-сервер обновляет timestamp. `path` стабилен. `credentialsId` стабилен после initial setup.
   - **Статус:** **Partially resolved 2026-05-10.** Размер конкретных файлов стабилен в логах (например, `size=36411` для одного и того же `.jpg` в обеих сессиях). Однако `createdDate` не логируется — остаётся гипотетическим источником нестабильности ключа. Это **главный оставшийся research item** для Phase 02.
3. **Какое распределение `DataSource` для загрузок?**
   - **Статус:** **Resolved 2026-05-10.** Сессия 2 (51 загрузка): `disk=0, memory=41, network=10, local=0, repo=0`. Пояснение: первые 10 уникальных миниатюр загружены из сети (холодный старт сессии, disk miss), затем 41 hit из памяти (тот же процесс, повторные загрузки тех же ячеек при скролле). Ни один из первых 10 network-загрузок не дал disk hit — несмотря на то, что `fileCount=5` при старте сессии.
4. **Воспроизводится ли (R5)?**
   - **Статус:** **Resolved 2026-05-10.** Да, воспроизводится. Сессия 1 → 0 файлов в кеше → активный просмотр → конец сессии. Сессия 2 → 5 файлов в кеше при старте → `disk=0` при загрузке тех же миниатюр. Файлы пишутся, но при следующем запуске **не читаются** (`disk=0`).
5. **Где приложение явно стирает `image_cache` (R6)?**
   - **Статус:** **Partially resolved 2026-05-10.** Два явных вызова `clearDiskCache()` найдены: (a) `GeneralSettingsCacheHelper` — при нажатии «Очистить кеш» в настройках (ожидаемо); (b) `PlayerMediaLoaderManager` — после сохранения отредактированного изображения (целевое поведение). Ни один из них не вызывался в полевых сессиях 2026-05-10. Лог также не содержит `S0136: ... clearDiskCache` — значит, явное удаление не является причиной `fileCount=0` в сессии 1 (кеш просто не успел вырасти за одну сессию). **Причина disk=0 в сессии 2 — не явное удаление, а невозможность прочитать ранее записанные файлы.**

---

## 7. Риски

- **Phase R-инструментирование искажает измерение.** Низкая вероятность: добавляются только Timber.v/i и Atomic counters.
- **Cache key fix меняет поведение всех существующих кешей** (старые ключи становятся «sticky» — никогда не попадут в hit). Митигация: одна invalidation-сессия на стороне пользователя — приемлемо.
- **D3 (setDiskCache не применяется) подразумевает регрессию `@GlideModule` discovery.** Если фикс — добавить запись в `AndroidManifest`, нужна проверка для всех flavor.

---

## 8. Влияние на пользователя (docs/FEATURES)

- Скрытое улучшение производительности; внешне функционал не меняется.
- В `docs/FEATURES.md` фичи не появляются. В release notes — упомянуть «ускорение повторного открытия сетевых папок».

---

## 9. Архитектурные решения (ADR)

**ADR-1: Research-first.**

- **Решение:** не предлагать конкретный fix до завершения Phase R. Гипотез D1..D4 четыре, у каждой радикально разная правка.
- **Альтернативы:** сразу попытаться стабилизировать cache key (D1) — это самая «частая» причина в Glide-проектах.
- **Почему:** если причина D2 или D3, фикс D1 ничего не даст и съест бюджет.

---

## 10. Связи с другими спеками

- **S0084** (bugfix-cache-subfolder-mismatch-restore, Verified) — другая сторона той же подсистемы (path-based mismatch); может содержать релевантные patterns.
- **S0087** (bugfix-cover-art-glide-404-log-spam, Verified) — соседний Glide-bugfix.
- **S0138** (bugfix-glide-cancellation-log-noise) — параллельная косметическая чистка W-логов Glide; не блокирует.

---

## 11. Критерии готовности (strategic-level)

1. По итогам Phase R известна причина (D1/D2/D3/D4) с конкретным указанием места в коде.
2. Phase F: после фикса в свежем тесте «открыть SMB-папку → Force stop → открыть снова» вторая сессия даёт `diskCacheHits ≥ 30 %` от total в `GlideCacheStats.logStats`.
3. `CacheStatusHelper` после первой полной сессии показывает `image_cache` существующим с `file count > 0`.
4. `GlideCacheStats.logStats` расширен: per-source breakdown (REMOTE / LOCAL / MEMORY / DISK / REPO) выводится всегда (а не только в WARNING-ветке).

---

## 12. Тактическая спецификация

После Phase R: `/spec-tech glide-disk-cache-not-persisting` → `PLAN/S0136_bugfix-glide-disk-cache-not-persisting/` с фазами R1..R6 → D → F.
