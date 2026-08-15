# Спецификация (compact bugfix): S1159 - ручная FTS-синхронизация в ResourceDao дублирует триггеры Room

**Ticket:** S1159
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-24
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-24

**Захвачено во время:** S1009 (research по visibility-filter / orphan-cleanup)

**Текст:**

Обнаружено при аудите S1009 (orphan-cleanup targets). `ResourceRepository.deleteResource(resourceId)` (`data/repository/ResourceRepositoryImpl.kt:277-279`) вызывает нетранзакционный `ResourceDao.deleteById(id)` (`data/local/db/ResourceDao.kt:23-24`, без чистки FTS) ВМЕСТО транзакционного `deleteByIdWithFts(id)` (`data/local/db/ResourceDao.kt:92-96`, который дополнительно зовёт `deleteFts(id)`). Из-за этого каждый вызов удаления ресурса оставляет висячую строку в `resources_fts` (stale FTS row), app-wide.

Подтверждённый вызывающий: `domain/usecase/DeleteResourceUseCase.kt:30`.

Severity по аудиту: Low (data hygiene, не видно пользователю напрямую как краш, но засоряет FTS-индекс и может давать фантомные результаты поиска по удалённым ресурсам).

Нетривиально: помимо однострочной замены вызова, нужна проверка/бэкфилл уже накопленных stale-строк в `resources_fts` в существующих БД (миграция-очистка), иначе старые висячие строки останутся.

Вне scope S1009: S1009 сознательно НЕ копирует этот баговый путь - его orphan-cleanup удаляет скрытый ресурс FTS-safe способом.

---

## 1. Проблема / симптом

Исходная гипотеза §0 **опровергнута замером**: `deleteById()` не оставляет висячих строк в `resources_fts`, бэкфилл существующих БД не нужен.

Реальный дефект - обратный: `ResourceDao` вручную поддерживает индекс `resources_fts` (`insertFts`/`updateFts`/`deleteFts`/`deleteAllFts`) поверх content-sync триггеров, которые Room генерирует сам. Ручные вызовы избыточны, а два из них выполняются в порядке, который SQLite документирует как undefined для external-content FTS4: `DELETE FROM resources_fts WHERE docid=X` после того, как строка-контент уже удалена.

Наблюдаемого пользователем сбоя сегодня нет - индекс спасает триггер, который отрабатывает раньше. Это латентный путь порчи индекса, а не активный баг: любое изменение порядка операций или новый путь удаления в обход триггера начнёт оставлять мусор в индексе, и симптом будет ровно тот, что описан в §0.

---

## 2. Корневая причина

`resources_fts` - это **external-content FTS4** (`@Fts4(contentEntity = ResourceEntity::class)`, `CREATE VIRTUAL TABLE ... USING FTS4(name, path, content=resources)`), а не самостоятельная таблица.

Для таких таблиц Room генерирует четыре content-sync триггера на `resources` (подтверждено в `app_v2/schemas/.../44.json` и в сгенерированном `AppDatabase_Impl`):

- `BEFORE UPDATE` / `BEFORE DELETE` -> `DELETE FROM resources_fts WHERE docid = OLD.rowid`
- `AFTER UPDATE` / `AFTER INSERT` -> `INSERT INTO resources_fts(docid, name, path) VALUES (NEW.rowid, ...)`

Триггеры сбрасываются перед миграциями и пересоздаются в `onPostMigrate`, то есть присутствуют и в обновлённых БД, не только в свежесозданных. Индекс синхронизирует сама БД.

Отсюда два следствия:

- `deleteById()` FTS-безопасен: `BEFORE DELETE` удаляет запись индекса, пока строка-контент ещё на месте, - именно тот порядок, которого требует документация SQLite. Гипотеза §0 о накоплении stale-строк неверна.
- Ручные `deleteFts(id)` в `delete()`/`deleteByIdWithFts()` выполняются уже ПОСЛЕ удаления строки-контента. Документация SQLite для external content: если строка-контент не найдена, «the results can be difficult to predict, the FTS index may be left containing entries corresponding to the deleted row». Аналогично `updateFts()` в `update()` вызывается после обновления контента, то есть delete-половина этого UPDATE читает уже новые значения.

Замер на реальном SQLite (Robolectric, in-memory Room, схема 44) - одноразовый probe-тест, замененный постоянным `ResourceFtsSyncTest` (§4):

- `triggers=4` - триггеры в живой БД есть
- `plainDeleteById -> index=0` - обычный `deleteById` чистит индекс сам
- `deleteByIdWithFts -> ok, index=0` - лишний `deleteFts` сегодня безвреден, но это тот самый undefined-вызов
- `update -> oldIndex=0, newIndex=1` - переименование не оставляет старых токенов
- `replaceInsert -> oldIndex=0, newIndex=1` - REPLACE-конфликт по существующему id тоже не оставляет мусора

---

## 3. Исправление

Убрать ручную FTS-поддержку из `ResourceDao` и оставить индекс на триггерах Room.

- Удалить `insertFts`, `updateFts`, `deleteFts`, `deleteAllFts` - вся четвёрка дублирует триггеры (Rule 20, dead weight).
- `insert()` / `update()` / `delete()` / `deleteAll()` - оставить публичные имена, убрать из тел FTS-вызовы; убрать `@Transaction` там, где тело стало одним стейтментом.
- Удалить `deleteByIdWithFts(id)`; единственный вызывающий - `ResourceRepositoryImpl.deleteResourceIfHidden()` (S1009) - перевести на `deleteById(id)` и поправить его комментарий, ссылающийся на несуществующий баг.
- `deleteResource()` не трогать: `deleteById(resourceId)` уже корректен.
- Схему БД не менять: версия остаётся 44, миграция и бэкфилл не нужны - накопленного мусора нет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1009 (обнаружен при его research; S1009 не зависит от этого фикса)

---

## 4. Проверка

`ResourceFtsSyncProbeTest` (probe) заменяется постоянным регрессионным тестом `ResourceFtsSyncTest` на in-memory Room, фиксирующим инварианты индекса после снятия ручной синхронизации:

- после `insert()` термин ресурса встречается в индексе ровно один раз (нет дубля запись-триггер)
- `deleteById()` убирает запись из индекса и из результатов поиска
- `update()` с переименованием убирает старый термин и добавляет новый
- `deleteAll()` очищает индекс полностью
- повторный `insert()` с существующим id (REPLACE) не оставляет старого термина

Плюс существующий `ResourceDaoTest` должен остаться зелёным без правок логики.

Команда: `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*ResourceFts*"`.

---

## Last Audit

**Date:** 2026-07-24
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

- `insertFts` / `updateFts` / `deleteFts` / `deleteAllFts` / `deleteByIdWithFts` - 0 ссылок в `app_v2/src`.
- `insert` / `update` / `delete` / `deleteAll` сохранили публичные имена и пишут только в content-таблицу.
- `deleteResource()` и `deleteResourceIfHidden()` оба на `deleteById()`.
- `@Database(version = 44)` без изменений, новых миграций нет.
- `ResourceFtsSyncTest` - 7 тестов, `ResourceDaoTest` зелёный без правок логики: `check-standard-fast.ps1 -Mode Unit` BUILD SUCCESSFUL.
- `post-change.ps1 -ChangeType Kotlin -ScopeToFile`: PASS; detekt diff-scoped PASS (0 новых находок в изменённых файлах).
- Debug-тегов `Timber.d("S1159:` в `.kt` нет (статус не `BlockNeedUserTest`).
- FEATURES trilingual - EXEMPT: изменение внутреннее, пользовательского поведения не меняет.

### Manual / on-device

- Ничего: слой БД полностью покрыт JVM-тестами на реальном SQLite (Robolectric), устройство не требуется.
