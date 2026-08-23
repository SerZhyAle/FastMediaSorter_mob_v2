# Спецификация (compact bugfix): S1826 - Прунинг каталога оставляет осиротевшие строки stream_play_outcomes

**Ticket:** S1826
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-20
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-20

**Текст:**

Найдено при сверке контракта каталога трансляций с внешним потребителем (StreamsPlayer, Windows). Потребитель прислал заметку, где среди прочего описал свою семантику слияния каталога (`RemoveMissing: true`) и последствия сегодняшней перезаливки:

> Адрес, пропавший из CSV, удаляет канал у всех. Слияние идёт с RemoveMissing: true: строки, которых нет в новом банке, вычищаются из каталога каждого пользователя вместе с закреплением и членством в коллекциях. Сегодняшняя перезаливка сняла 1 906 каналов - у меня из «Закреплённых» пропал AccuWeather.

Наше приложение делает ровно то же самое (`ImportStreamCatalogUseCase` -> `StreamSourceRepository.mergeCatalog`), и это by design. Дефект в другом: путь прунинга удаляет только строку канала, но не связанную с ней строку исхода воспроизведения.

**Эвиденс (working tree, 2026-08-20):**

- `StreamSourceRepository.remove(source)` (S1502) удаляет обе строки в одной транзакции, и комментарий над ним прямо это фиксирует: `/** S1502: the outcome now lives in its own table, so removing a channel must take its row too. */`
- `StreamSourceRepository.mergeCatalog(entries)` в ветке прунинга вызывает только `dao.deleteCatalogByUrls(it)`; `streamPlayOutcomeDao` в этой транзакции не участвует.
- `StreamSourceDao.deleteCatalogByUrls`: `@Query("DELETE FROM stream_sources WHERE sourceOrigin = 'CATALOG' AND url IN (:urls)")` - каскада нет.
- `StreamPlayOutcomeEntity` - `@PrimaryKey val streamId: String`, без `@ForeignKey`/`onDelete = CASCADE`, то есть БД тоже не подчистит.
- `StreamSourceDao.deleteAllDownloaded()` (S1780) - тот же паттерн, тот же результат.
- Осиротевшая строка невосстановима: `streamId` - это UUID, сгенерированный при импорте; после удаления строки канала связать её обратно не с чем.

**Масштаб (сегодня, 2026-08-20):** перезаливка каталога сняла 1 906 строк (19 534 -> 17 628), значит у каждого пользователя, который обновил каталог, осиротело до 1 906 строк исходов.

**Захвачено во время:** разбора контракта с внешним потребителем каталога (без активного тикета).

---

## 1. Проблема / симптом

- Таблица `stream_play_outcome` копит строки, у которых больше нет владельца: `streamId` указывает на `stream_sources.id`, которого в базе уже нет.
- Симптом невидим в UI: список читает исход по id живого канала, а осиротевшая строка не совпадает ни с одним живым id. Плата - мёртвый вес в базе, растущий на каждой перезаливке каталога.
- Строка невосстановима: `streamId` - это UUID, выданный при импорте; после удаления строки канала связать исход обратно не с чем.
- Масштаб на 2026-08-20: перезаливка каталога сняла 1 906 строк (19 534 -> 17 628), то есть у каждого обновившегося пользователя осиротело до 1 906 строк.
- Флейворы: дефект в общем `src/main`, поэтому одинаков во всех шести.

## 2. Корневая причина

- Удаление канала имеет три пути, и только один из них забирает исход.
- `StreamSourceRepository.remove(source)` (S1502) корректен: одна транзакция, `dao.delete(source)` плюс `streamPlayOutcomeDao.deleteByStreamId(source.id)`.
- `StreamSourceRepository.mergeCatalog(entries)` в ветке прунинга вызывает только `dao.deleteCatalogByUrls(it)`; `streamPlayOutcomeDao` в этой транзакции не участвует.
- `StreamSourceRepository.deleteAllDownloaded()` (S1780) делегирует голому `dao.deleteAllDownloaded()` - ни транзакции, ни исходов.
- Ни база, ни Room не подчищают за ними: `StreamPlayOutcomeEntity` объявлен как `@PrimaryKey val streamId: String` без `@ForeignKey` и без `onDelete = CASCADE`.
- Инвариант, который не был записан ни в одном месте: строка `stream_play_outcome` живёт ровно столько, сколько живёт строка `stream_sources` с тем же id. `remove()` его соблюдает по факту, но нигде не выражает, поэтому два более поздних пути удаления его не унаследовали.

## 3. Исправление

- Добавить в `StreamPlayOutcomeDao` запрос `deleteOrphanedPlayOutcomes(): Int` - `DELETE FROM stream_play_outcome WHERE streamId NOT IN (SELECT id FROM stream_sources)`.
- Вызывать его в конце транзакции `mergeCatalog`, безусловно: это и закрывает новый прунинг, и лечит уже накопленный хвост при первом же импорте.
- Обернуть `deleteAllDownloaded()` в `db.withTransaction` и вызвать ту же чистку после удаления каналов.
- Не заводить `@ForeignKey`/`CASCADE`: это изменение схемы Room с миграцией на версию 52 ради инварианта, который в приложении и так выражается одной строкой SQL в двух местах.

### 3.1 Почему чистка по остатку, а не удаление по списку id

- Прунинг знает удаляемые url, а не id: `deleteCatalogByUrls` бьёт по url, и id удалённых строк в этой ветке в память не поднимаются.
- Поднимать их пришлось бы отдельным `SELECT id` с теми же чанками по 900 - ещё один проход по каталогу ради того же результата.
- Чистка по остатку дополнительно закрывает уже осиротевшие строки, которых ни один список удаляемых url не покрывает.

### 3.2 Почему безусловный вызов безопасен

- Каждая запись исхода сначала резолвит живую строку канала: `PlayerViewModel` берёт `getStreamSourceByUrlUseCase(url)` и выходит на `null`, `AudioPlaybackService` пишет по `source.id`, зонды `StreamHealthProbeManager`/`StreamFrameSnapshotManager` - по id строки списка.
- Значит `streamId` без строки-владельца не может быть ничем, кроме сироты; запрос физически не способен удалить живой исход.
- `DELETE`, не совпавший ни с одной строкой, не дёргает update-hook SQLite, поэтому `observeAll()` не переизлучает и список не перерисовывается - тот же аргумент, что записан над `markPlayOutcome` (S1502).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1502 (вынос исхода в отдельную таблицу), S0821 (chunked prune), S1780 (deleteAllDownloaded)
- **Схема Room:** не меняется, миграция не нужна, версия базы остаётся 51.
- **Видимых пользователю изменений нет:** запись в `docs/ALL_FEATURES.jsonl` не требуется.
- **Строк не добавляется:** локализация не затрагивается.

## 4. Проверка

- Регрессионный юнит-тест в `StreamSourceCatalogMergeTest`: записать исход для канала, вычистить его канал перезаливкой каталога, убедиться что `outcomeFor(id)` вернул `null`.
- Второй тест на уже накопленный хвост: вписать исход по id, которого нет в `stream_sources`, вызвать `mergeCatalog` и убедиться что строка ушла.
- Третий тест на `deleteAllDownloaded`: MANUAL-канал с исходом переживает вызов, CATALOG-канал с исходом уходит вместе со своим исходом.
- `.\a.ps1 fu` - зелёный набор.
- `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` - PASS.

---

## Last Audit

**Date:** 2026-08-20
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 2

- `StreamPlayOutcomeDao.deleteOrphanedPlayOutcomes(): Int` объявлен; запрос бьёт по `streamId NOT IN (SELECT id FROM stream_sources)`.
- `mergeCatalog` вызывает чистку внутри той же транзакции, сразу после чанкованного прунинга.
- `deleteAllDownloaded()` обёрнут в `db.withTransaction` и чистит исходы после удаления каналов.
- Схема Room не тронута: версия базы 51, `@ForeignKey` в `StreamPlayOutcomeEntity` не появился.
- Три регрессионных теста зелёные, и негативный контроль подтвердил, что они настоящие: с закомментированными вызовами чистки падают ровно эти три (`failures="3"`), три прежних теста класса продолжают проходить.
- Отладочных тегов `Timber.d("S1826:` в дереве нет - статус не `BlockNeedUserTest`.
- `post-change.ps1 -ChangeType Kotlin -ScopeToFile` - PASS без advisories; dev-log и каталог классов синхронизированы.

### Manual / on-device

- [ ] Не обязателен для закрытия: на устройстве с уже накопленным хвостом убедиться, что первый импорт каталога пишет `Stream play outcomes: purged N orphaned rows` с ненулевым N. Путь уже покрыт тестом `mergeCatalog_clearsOutcomesStrandedByEarlierImports`.
