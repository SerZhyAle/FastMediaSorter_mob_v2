# Стратегическая спецификация: S0138 — `CancellationException` от video-priority засоряет W-лог как «Network image load failed»

**Ticket:** S0138
**Status:** Implemented
**Priority:** 25
**Date:** 2026-05-10
**Tier:** 1 — Trivial
**Tactical plan:** `PLAN/S0138_bugfix-glide-cancellation-log-noise/INDEX.md`
**Implemented date:** 2026-05-10
**Roadmap entry:** Ad-hoc — полевая сессия 2026-05-10, лог `logs/fastmediasorter_20260510_012252.log`

> **Scope:** STRATEGIC. Косметика логов: ожидаемое поведение `ConnectionThrottleManager` (suspend thumbnail loading во время видео-плеера) логируется как W/Failed to load. При анализе field-логов даёт ложный positive «сетевые ошибки».

---

## 1. Проблема

В сессии 2026-05-10 01:38:24 за одну секунду в W/App ушло 9 сообщений вида:

```
W/App: Network image load failed: 20260325_195701.jpg, Failed to load resource
There was 1 root cause:
java.util.concurrent.CancellationException(Video player priority - thumbnail loading suspended)
 call GlideException#logRootCauses(String) for more detail
```

Это **штатное** поведение: `ConnectionThrottleManager` ([data/network/ConnectionThrottleManager.kt:367-373](app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt#L367-L373)) при активном видео-плеере **намеренно** бросает `CancellationException("Video player priority - thumbnail loading suspended")` для low-priority thumbnail-запросов, чтобы освободить пропускную способность сети для стриминга видео. Это feature, не bug.

`AdapterThumbnailLoader.onLoadFailed` ([ui/browse/AdapterThumbnailLoader.kt:469-477](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt#L469-L477)) логирует **любой** `GlideException` как W:

```kotlin
override fun onLoadFailed(e: GlideException?, ...): Boolean {
    if (e != null) {
        Timber.w("Network image load failed: ${file.name}, ${e.message}")
        NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
    }
    ...
}
```

Помимо засорения лога, есть второй side-effect: `markThumbnailAsFailed` помечает миниатюру как failed, и **повторный** запрос той же миниатюры может пойти по failed-pathway вместо нормальной загрузки после того, как видео-плеер закроется.

### 1.1 Что неизвестно

- Вызывает ли `markThumbnailAsFailed` для cancellation реальное «зависание» миниатюры в failed-state до конца сессии, или есть retry-policy.
- Сколько таких ложных «failed» в день у среднего пользователя — лог за 30 минут показал 9, экстраполяция на день даёт сотни.

### 1.2 Влияние на пользователя

- Пользователь не видит — это чисто внутренний лог.
- Влияние **на разработчика**: при анализе field-логов реальные сетевые ошибки тонут в шуме «failed» от cancellation. Это уже мешало в текущем анализе лога 2026-05-10.

---

## 2. Цели

1. `CancellationException` с message «Video player priority - thumbnail loading suspended» **не логируется** на W-уровне в `AdapterThumbnailLoader.onLoadFailed`.
2. Ту же причину `NetworkFileDataFetcher.markThumbnailAsFailed` **не вызывает** — миниатюра остаётся в normal state и подгрузится при следующей попытке (например, после закрытия плеера).
3. Реальные сетевые ошибки (IOException, SocketException, FileNotFoundException) продолжают логироваться как W.
4. Опционально: счётчик suspended thumbnail в `ConnectionThrottleManager` для диагностики (сколько запросов было заблокировано за время видео — полезно для tuning).

**Non-goals:**

- Не менять политику suspension (продолжать блокировать low-priority при видео).
- Не переписывать `markThumbnailAsFailed` — только не вызывать его на cancellation.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Минимально-инвазивная правка — изменение в одном-двух местах.
2. Не вводить новые исключения / новые типы.

### 3.2 Жёсткие ограничения

- **Flavor:** все.
- **API level:** без изменений.
- **Производительность:** правка не должна добавлять overhead к hot path.

---

## 4. Контекст текущей архитектуры

`ConnectionThrottleManager.withThrottle` ([data/network/ConnectionThrottleManager.kt:355-374](app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt#L355-L374)) — единая обёртка для сетевых операций. Логика:

```kotlin
if (!highPriority && videoPlayerActive) {
    if (videoPlayerResources.contains(resourceKey)) {
        throw kotlinx.coroutines.CancellationException("Video player priority - thumbnail loading suspended")
    }
}
```

`AdapterThumbnailLoader` ([ui/browse/AdapterThumbnailLoader.kt:469-477](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt#L469-L477)) — RequestListener Glide; `onLoadFailed` вызывается на любой неуспех загрузки, включая cancellation.

`NetworkFileDataFetcher.markThumbnailAsFailed` — глобальный реестр failed-миниатюр (предположительно, для предотвращения retry при reattach view).

`GlideException` — обёртка Glide, в которую заворачивается root cause. Можно проверить root cause через `e.rootCauses` или `e.causes`.

---

## 5. Предлагаемый подход

### 5.1 Этапы работы

**Phase F1 — фильтр в onLoadFailed:**

В `AdapterThumbnailLoader.onLoadFailed` определить, является ли root cause `CancellationException` с конкретным message. Если да:

- логировать на `v` или `d` уровне (или не логировать вовсе),
- **не вызывать** `markThumbnailAsFailed`,
- продолжать применять placeholder.

Реализация: проход по `e.rootCauses` (Glide API) с проверкой `it is CancellationException && it.message?.contains("Video player priority") == true`.

**Phase F2 (опциональная) — счётчик suspended:**

В `ConnectionThrottleManager` AtomicInteger `suspendedThumbnailCount`; в `videoPlayerActive=false` обработчике вывод одной строки `Timber.i("ConnectionThrottle: video session ended, suspended N thumbnail loads"). Дальше — диагностика, нужна ли политика менее агрессивной suspension.

### 5.2 Точки расширяемости

- Тот же фильтр применим везде, где Glide bridges throttle (например, `MediaFileAdapter`, `PagingMediaFileAdapter` — проверить, используют ли они тот же `RequestListener`-подход; если нет — extract helper-функцию `isThrottleSuspendCancellation(GlideException)` в `utils/`).

---

## 6. Открытые вопросы / Research items

1. **`markThumbnailAsFailed` — есть ли auto-retry?** Проверено: companion `NetworkFileDataFetcher` в `NetworkFileModelLoader.kt` и персистит failure, и использует `isThumbnailFailed` для short-circuit последующих попыток. Для expected cancellation этот pathway надо обходить.
    - **Статус:** resolved
2. **Используют ли `MediaFileAdapter` / `PagingMediaFileAdapter` ту же ветку `onLoadFailed`?** Проверено: network EPUB/PDF/image/video listeners для этого кейса централизованы в `AdapterThumbnailLoader`, так что локальный helper в этом классе покрывает весь browse-flow.
    - **Статус:** resolved
3. **Сколько таких suspended за типичный сеанс воспроизведения видео?**
    - **Статус:** deferred (опциональная F2 вне текущего tactical scope)

---

## 7. Риски

- **Регрессия — пропустить реальную ошибку,** если её root cause тоже `CancellationException`. Митигация: проверка по точному message-substring «Video player priority», а не по типу.
- **Тонкая зависимость от Glide API** (`e.rootCauses` или `e.causes`). Митигация: try/catch fallback на полный лог.

---

## 8. Влияние на пользователя (docs/FEATURES)

- Внешне ничего не меняется.
- В release notes — не упоминать (внутреннее улучшение логов).

---

## 9. Архитектурные решения (ADR)

**ADR-1: Match по message, не по типу.**

- **Решение:** различать suspension от других CancellationException по конкретной message-substring.
- **Альтернативы:** ввести специализированный `ThumbnailSuspendedException : CancellationException`.
- **Почему:** message-based проверка минимально-инвазивна; новый тип потребует протаскивать его через всю throttle chain без выгоды.

---

## 10. Связи с другими спеками

- **S0087** (bugfix-cover-art-glide-404-log-spam, Verified) — родственная косметика логов Glide; шаблон фильтрации того же типа.
- **S0136** (bugfix-glide-disk-cache-not-persisting) — соседний Glide-расследование; не блокирует.
- **S0110** (bugfix-thumbnails-during-scroll, Verified) — упоминается в коде `AdapterThumbnailLoader` как причина skip listener при scrolling; учесть, чтобы не сломать его инвариант.

---

## 11. Критерии готовности (strategic-level)

1. После фикса при активном видео-плеере в W/App нет «Network image load failed: ... CancellationException(Video player priority...)».
2. Реальные сетевые ошибки (например, отсоединение от SMB-сервера) продолжают появляться в W/App без изменений.
3. После закрытия видео-плеера миниатюры, для которых был suspend, подгружаются при следующем взаимодействии (scroll / reattach), а не висят в failed-state.

---

## 12. Тактическая спецификация

`/spec-tech glide-cancellation-log-noise` → `PLAN/S0138_bugfix-glide-cancellation-log-noise/` с фазами F1 + опциональная F2.
