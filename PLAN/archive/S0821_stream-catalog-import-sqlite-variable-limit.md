# Стратегическая спецификация: S0821 - Import stream catalog hits SQLite variable limit

**Ticket:** S0821
**Status:** Archived
**Priority:** 85
**Date:** 2026-06-30
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - log analysis 2026-06-30

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-30

**Источник:** пакет release/debug логов пользователя с двух устройств.

**Ключевые наблюдения:**

- `logs/fastmediasorter_20260625_183235.log:235`
- `logs/fastmediasorter_20260626_102100.log:255`

Оба лога фиксируют один и тот же сбой:

`SQLiteException: too many SQL variables .. DELETE FROM stream_sources WHERE sourceOrigin = 'CATALOG' AND url NOT IN (...)`

Падение всплывает в рамках `Stream catalog import failed: merge`, то есть не в playback path, а именно в merge/prune curated catalog.

---

## 1. Проблема

Импорт curated stream catalog перестаёт быть масштабируемым: как только размер каталога становится достаточно большим, prune-этап merge формирует один SQL-запрос с огромным `NOT IN` списком URL и упирается в лимит SQLite по числу bind-переменных. В результате импорт завершается ошибкой и каталог не обновляется полностью.

Это уже не теоретический риск. Ошибка дважды подтверждена в release-логах на устройстве `ums512_1h10_Natv / Android 14 / API 29`, значит дефект затрагивает реальное shipped behavior, а не только debug tooling.

---

## 2. Цели

1. Убрать зависимость импорта stream catalog от лимита SQLite по количеству bind-переменных.
2. Сохранить текущее свойство merge: существующие catalog rows обновляются, исчезнувшие удаляются, manual/imported rows не трогаются.
3. Сохранить atomicity catalog sync, уже введённую отдельным тикетом по Room write atomicity.
4. Дать решение, которое останется корректным при дальнейшем росте curated catalog.

**Non-goals:**

- Не перерабатывать сам формат stream catalog.
- Не менять UI/UX раздела Streams.
- Не менять semantics pinned/manual rows.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исправление должно быть прозрачным для пользователя - import просто перестаёт ломаться на больших каталогах.
2. Желательно не вводить хрупкую магию "порог N под текущую SQLite", которая снова сломается при росте списка.

### 3.2 Жёсткие ограничения

- **Flavor:** минимум standard, где curated stream catalog уже shipped; не допустить divergence для legacy/noLegal, если path общий.
- **API level:** без platform-specific веток.
- **Wear OS:** не затрагивается.
- **Производительность:** импорт остаётся приемлемым по времени даже на большом списке.
- **Совместимость данных:** pinned/manual/imported rows сохраняют текущий смысл.
- **Локализация:** новых пользовательских строк не требуется.
- **Доступность:** не относится.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0570 (curated stream catalog import), S0583 (catalog import timeout), S0732 (room write atomicity).

---

## 4. Контекст текущей архитектуры

Curated catalog импортируется как список catalog-origin rows, после чего merge-слой синхронизирует его с локальной БД: обновляет уже существующие записи, добавляет новые и удаляет исчезнувшие. Проблема сосредоточена не в загрузке/парсинге архива, а в prune-шаге удаления старых catalog rows по условию "все URL, которых нет в новом списке".

Пока этот шаг выражается через один SQL-запрос с большим `NOT IN`, масштаб решения ограничен SQLite bind limit. Чем больше curated catalog, тем выше вероятность, что одна обычная поставка данных сломает import целиком.

---

## 5. Предлагаемый подход

Нужен масштабируемый prune-path, который не зависит от длины одного bind-списка. Варианты - батчирование, временная таблица/таблица ключей для текущего импорта, либо другой способ вычислить delta без giant `NOT IN`.

### 5.1 Основные столпы / модули

- Безопасный и масштабируемый prune missing catalog rows.
- Сохранение transaction boundary для всего merge.
- Явная регрессионная проверка на large catalog input.

### 5.2 Потоки данных и событий

- Download/unzip -> parse CSV -> merge existing/new rows -> scalable prune missing rows -> success toast/result.

### 5.3 Точки расширяемости

- Решение должно выдерживать дальнейший рост catalog size без повторного redesign.

---

## 6. Открытые вопросы / Research items

1. ~~Какой способ prune лучше всего сочетается с Room transaction и текущим DAO contract.~~ **Resolved (2026-06-30):** in-memory delta. Merge уже держит в памяти оба набора url (`existingCatalogUrls`, `newUrls`), поэтому удаляем дельту `existing - new` через batched `IN (:urls)` чанками по 900 - канонический паттерн проекта (`FavoritesRepositoryImpl.SQLITE_IN_CLAUSE_LIMIT`). Temp table не нужна: ни одного гигантского bind-списка не остаётся, всё в той же `withTransaction`.
2. ~~Нужен ли отдельный regression test на synthetic large catalog.~~ **Resolved (2026-06-30):** да, добавлен `StreamSourceCatalogMergeTest` (1500/1100 строк > 999-лимита) - покрывает large import, large prune и сохранение manual-row.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Исправление prune path нарушит atomic merge | Средняя | Частично обновлённый catalog | Держать весь sync в одной транзакции |
| Неверный delta algorithm удалит живые catalog rows | Средняя | Потеря части списка каналов | Сравнивать только catalog-origin rows и покрыть тестом |
| Батчирование будет слишком медленным на больших списках | Низкая | Медленный import | Выбрать алгоритм с предсказуемой сложностью и измерить на synthetic large input |

---

## 8. Связи с другими спеками

- S0570 - базовый curated stream catalog import.
- S0583 - import timeout path; этот тикет про merge/prune phase после успешной загрузки.
- S0732 - atomicity already handled; новый fix не должен сломать это свойство.

---

## 9. Критерии готовности (strategic-level)

1. Импорт большого curated catalog больше не падает с `SQLiteException: too many SQL variables`.
2. Existing catalog rows корректно обновляются и удаляются при исчезновении из нового списка.
3. Manual/imported/pinned semantics сохраняются.
4. Решение доказано либо тестом, либо воспроизводимым large-catalog scenario.

---

## Implementation (2026-06-30)

**Root cause:** prune-шаг merge выполнял один `DELETE .. WHERE sourceOrigin='CATALOG' AND url NOT IN (:keepUrls)`, биндя весь новый каталог в host-переменные. На каталоге > 999 каналов запрос упирался в SQLite bind limit (release crash, API 29).

**Fix:**

- `StreamSourceDao.kt` - `deleteCatalogNotIn(keepUrls)` заменён на `deleteCatalogByUrls(urls)` (`IN`, вызывается чанками).
- `StreamSourceRepository.mergeCatalog` - вычисляет дельту `existingCatalogUrls - newUrls` в памяти и удаляет её чанками по `SQLITE_IN_CLAUSE_LIMIT = 900` внутри той же `withTransaction`; гигантский bind-список устранён. Семантика merge не изменилась.
- `StreamSourceCatalogMergeTest.kt` - регрессия на 1500/1100 строк: large import без bind-limit, large prune дельты, сохранение manual-row при коллизии url.

**Verification:** `testStandardDebugUnitTest --tests *StreamSourceCatalogMergeTest*` - 3/3 PASS. Чисто data-layer, device-test не требуется.

**Last Audit (2026-06-30):** Verified. Все 4 strategic-критерия покрыты проходящим юнит-тестом; atomicity (S0732) сохранена единой транзакцией; reuse канонического chunk-паттерна (`FavoritesRepositoryImpl`).
