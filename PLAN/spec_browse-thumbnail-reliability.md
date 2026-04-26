# Стратегическая спецификация: Ad-hoc — Browse Thumbnail Reliability: Cache Hits and Frame Extraction

**Status:** Verified
<!-- auto-approved by /spec-all — 2026-04-26 -->
**Audit:** see `PLAN/spec_browse-thumbnail-reliability__audit_2026-04-26_2.md`
**Date:** 2026-04-26
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — запрос пользователя 2026-04-26 (дополнительный анализ Quest 3 лога: repeated zero disk cache hits и массовые frame extraction failures для video thumbnails)
**Tactical plan:** `PLAN/spec_browse-thumbnail-reliability/INDEX.md`

> **Scope of this document:** STRATEGIC. Цели, ограничения, риски и направление решения. Без детальной пошаговой реализации.

---

## 1. Проблема

В browse-пайплайне видео-миниатюр одновременно видны два симптома деградации. Во-первых, диагностическая статистика Glide несколько раз подряд сообщает `Zero disk cache hits with 12 total loads`, хотя в той же сессии миниатюры загружаются повторно и это уже не выглядит как одноразовый «первый вход в папку». Во-вторых, network video frame extraction массово падает с `No retriever available` для тяжёлых MKV, DV/HDR и части других контейнеров, из-за чего миниатюры просто не появляются.

Эти два симптома лежат в одном functional surface: browse thumbnail pipeline для видео и network sources. Пользователь видит пустые placeholders, нестабильные повторные загрузки и потенциально лишний network/CPU churn вместо устойчивых cached previews.

---

## 2. Цели

1. Повторное открытие папок с видео должно давать ожидаемые disk-cache hits, если cache keys и invalidation не менялись.
2. Ошибки video frame extraction должны быть классифицированы и иметь устойчивый fallback path вместо бесконечных повторов одного и того же retriever failure.
3. Тяжёлые MKV / DV/HDR / problematic network sources не должны ломать весь thumbnail UX: либо thumbnail строится альтернативно, либо failure кэшируется и показывает осмысленный fallback.
4. Диагностика должна различать «реально пустой cache» и «cache есть, но request path не использует совместимый key/strategy».

Non-goals:

- Полная замена Glide или глобальный refactor image pipeline.
- Улучшение full-size video playback; scope ограничен browse thumbnails/previews.
- Решение SMB playback timeout itself — это отдельный network spec.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Disk-cache anomaly и frame extraction failures должны разбираться в одной спеке, потому что пользовательский эффект общий: отсутствующие/нестабильные превью.
2. Повторный failure thumbnail extraction не должен каждый раз заново дёргать тяжёлый retriever path, если уже известно, что источник/problematic codec unsupported.
3. Debug stats должны оставаться полезными и не вводить в заблуждение ложной аномалией.

### 3.2 Жёсткие ограничения

- **Flavor:** затрагивает `standard`, `lite`, `legacy`, `vr`; `photos` может затронуться только косвенно через shared Glide stats, но video path там неактивен.
- **API level:** без жёстких API fork'ов; учитывается поведение `MediaMetadataRetriever` и network media data source.
- **Wear OS:** не затрагивается.
- **Архитектура:** логика extraction/caching должна жить в browse/network/glide pipeline, не в Activity.
- **Логирование:** `Timber` only; repeated known failures должны логироваться с контролируемой частотой.

---

## 4. Контекст текущей архитектуры

### 4.1 Diagnostic stats gap

`GlideCacheStats` (`utils/GlideCacheStats.kt`) вызывает `recordLoad(dataSource)` в `RequestListener.onResourceReady()` и различает `RESOURCE_DISK_CACHE`, `DATA_DISK_CACHE`, `MEMORY_CACHE`, `REMOTE`, `LOCAL`. **Ключевой момент:** `NetworkVideoFrameDecoder` имеет первичный путь через `ThumbnailCacheRepository` (Room + `filesDir/thumbnails/`). Когда thumbnail уже сохранён в этом persistent cache, Glide загружает его как `LOCAL` файл с `DiskCacheStrategy.RESOURCE`, и `GlideCacheStats` записывает это как LOCAL-load, а не как DISK_CACHE_HIT. Это объясняет «нулевые disk hits» даже при активном повторном использовании thumbnail — диагностическая слепая зона, а не реальный cache miss.

Дополнительный фактор: `AdapterThumbnailLoader` использует `DiskCacheStrategy.RESOURCE` для local list-view видео и `DiskCacheStrategy.DATA` для local grid-view видео — это создаёт разные cache entries для одного файла при переключении режимов.

### 4.2 Video frame extraction and failure cache

Сетевые video thumbnails строятся в `NetworkVideoFrameDecoder`: `MediaMetadataRetriever` получает `NetworkMediaDataSource` и пытается извлечь кадр с 10-секундным таймаутом. Для ряда файлов (тяжёлые MKV, DV/HDR) retriever не поднимается. **Failure cache уже существует**: in-memory `LinkedHashMap` (5000 записей, FIFO) в `NetworkFileModelLoader` (`failedVideos`) — предотвращает повторные попытки в рамках сессии. Слабое место: при рестарте приложения failure cache сбрасывается, и все previously-failed файлы повторно дёргают `MediaMetadataRetriever`.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

#### Столп A — Cache-Key Consistency Audit

Проблема: `AdapterThumbnailLoader` использует `DiskCacheStrategy.RESOURCE` для list-view видео и `DiskCacheStrategy.DATA` для grid-view — разные стратегии создают несовместимые cache entries. Нужно выровнять стратегию для local video thumbnails. Отдельно: `NetworkFileData` cache key (path + size) — проверить, что смена `loadFullImage`/`highPriority` не создаёт phantom cache misses.

#### Столп Б — Failure-aware Thumbnail Extraction

In-memory failure cache в `NetworkFileModelLoader` (`failedVideos` LinkedHashMap) работает, но сбрасывается при рестарте. Нужен persistent failure cache с TTL для устойчиво unsupported файлов (MKV/DV/HDR). Реализуется через `ThumbnailCacheRepository` или отдельный lightweight store; TTL гарантирует retry после обновления приложения/кодеков.

#### Столп В — Deterministic Fallback UX

Когда кадр извлечь нельзя и файл помечен как persistent failure, показывать предсказуемый fallback: extension icon или placeholder, без повторных дорогостоящих попыток. `NetworkVideoFrameDecoder` уже умеет останавливать retry; нужно расширить это поведение на cross-session случай.

#### Столп Г — Honest Diagnostics

`GlideCacheStats` должна различать:

- первый вход с пустым кэшем (реальный LOCAL/REMOTE без предшествующего hit);
- cache hit из `ThumbnailCacheRepository` (сейчас отображается как LOCAL — ложная аномалия);
- Glide disk cache hit (RESOURCE_DISK_CACHE / DATA_DISK_CACHE);
- unsupported retriever path (failure cache hit);
- key drift (RESOURCE vs DATA strategy mismatch).

`GlideCacheStats.logStats()` должен учитывать ThumbnailCacheRepository source как отдельную категорию.

### 5.2 Потоки данных и событий

```text
Browse thumbnail request
    ↓
request model + signature + cache strategy
    ↓
Glide / thumbnail repository lookup
    ├─ cache hit → render thumbnail
    ├─ miss → extraction path
    │          ├─ success → store/cache → render
    │          └─ classified failure → failure cache / stable fallback
    └─ diagnostics record actual source and reason
```

### 5.3 Точки расширяемости

- Failure classifier должен быть пригоден для будущих extractor backends, если `MediaMetadataRetriever` придётся обходить.
- Diagnostic model должна быть полезна не только для VR session logs, но и для обычного browse debugging.

---

## 6. Открытые вопросы / Research items

1. **Это реальный cache miss или диагностическая слепая зона?**
   - **Ответ (resolved):** В подавляющем большинстве случаев — слепая зона. `ThumbnailCacheRepository` работает вне Glide disk cache stats; его hits отображаются как LOCAL loads. Реальный cache miss требует дополнительного анализа по key drift (RESOURCE vs DATA strategy switch).
   - **Статус:** Resolved — требует реализации честного учёта ThumbnailCacheRepository hits в диагностике.

2. **Какие retriever failures стоит считать permanent для данного файла?**
   - **Ответ (resolved):** `No retriever available` для MKV/DV/HDR — устойчиво unsupported case (codec incompatibility), а не race condition. In-memory failure cache уже работает в сессии; проблема — сброс при рестарте.
   - **Статус:** Resolved — нужен persistent failure cache (Room или shared prefs) с TTL.

3. **Нужен ли альтернативный backend для тяжёлых MKV/DV?**
   - **Ответ (resolved):** Не для этой спеки. Приоритет — failure cache persistence + stable fallback. Альтернативный extractor backend — отдельный task при наличии конкретной потребности.
   - **Статус:** Resolved — deferred out of scope.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
| ---- | :---------: | ----------- | --------- |
| Исправление cache keys сломает существующую инвалидацию | Средняя | Stale thumbnails | Явно документировать key contract и invalidation triggers |
| Failure cache будет слишком агрессивным | Средняя | Thumbnail не появится там, где повторная попытка могла бы сработать | Ввести TTL или reason-based classification |
| Альтернативный extractor path окажется слишком дорогим для browse | Низкая | UX лаги при прокрутке | Сначала классификация и stable fallback, потом только selective alternative path |

---

## 8. Влияние на пользователя (docs/FEATURES)

No FEATURES doc update required.

Это повышение надёжности browse preview pipeline, а не новая пользовательская возможность.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Cache anomaly и extraction failures рассматриваются как единый thumbnail reliability scope.**

- **Решение:** вести их в одной спеке.
- **Альтернативы:** разнести на две мелкие спеки.
- **Почему так:** оба симптома проявляются в одном UX и, вероятно, пересекаются в request/caching/extraction pipeline.

**ADR-2: Первый deliverable — наблюдаемая классификация, а не мгновенный выбор нового extractor backend.**

- **Решение:** сначала честно различить cache/key/extraction причины.
- **Альтернативы:** сразу добавлять сложный альтернативный extractor.
- **Почему так:** иначе легко перелечить не ту причину и усложнить pipeline без точного понимания дефекта.

---

## 10. Связи с другими спеками

- Пересекается с network SMB/media specs только в части thumbnail source path, но не зависит от уже идущих VR rendering/input работ.
- Может выполняться независимо от `spec_network-smb-pooling`, кроме возможного общего diagnostic материала.

---

## 11. Критерии готовности (strategic-level)

1. Повторное browse-открытие папок с видео: `GlideCacheStats` показывает ThumbnailCacheRepository hits как отдельную категорию, warning «Zero disk cache hits» не срабатывает ложно когда thumbnails реально reused.
2. `No retriever available` для MKV/DV/HDR: failure не повторяется после рестарта приложения (persistent failure cache с TTL).
3. Пользователь получает детерминированный fallback для persistent-failed thumbnail sources вместо случайно пустых превью.
4. `DiskCacheStrategy` для local video thumbnails унифицирована (list и grid используют одну стратегию).

---

## 12. Ссылка на тактическую спецификацию

После утверждения этой страницы — перейти к `/spec-tech browse-thumbnail-reliability`, чтобы создать `PLAN/spec_browse-thumbnail-reliability/` с фазами реализации.
