# Спецификация (compact bugfix): S1001 - Статистика ресурса не обновляется

**Ticket:** S1001
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-11
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11

**Текст:**

захожу в ресурс - вижу много файлов - отурывают ресурс для редакции - внизу свёрнуто "статистика" - число папок есть, а числа файлов нет. Последние отрытие - никогда,  последняя синхронизация - никогда. Похоже эта статистика не обновляется везде нде ей следует

---

## 1. Проблема / симптом

<Что наблюдается, где (flavor/устройство/экран), эвиденс - лог-строки, stack trace, repro. Без имён классов на этапе захвата.>

Наблюдение владельца: при открытии ресурса на редактирование блок "статистика" (внизу, свёрнут) показывает число папок, но не число файлов. Поля "последнее открытие" и "последняя синхронизация" всегда "никогда". Похоже, статистика ресурса не обновляется во всех местах, где должна.

---

## 2. Корневая причина

Lost update (stale full-entity write) в Browse-сессии:

- `BrowseMetadataManager.updateMetadata` после загрузки файлов корректно пишет в БД `fileCount`, `lastBrowseDate`, `lastSyncDate`, `subfolderCount` - но НЕ обновляет in-memory `state.resource` в `BrowseViewModel`. In-memory копия ресурса на весь сеанс остаётся со значениями на момент открытия (0 / null).
- Три писателя копируют `stateFlow.value.resource` (стейл) и пишут ПОЛНУЮ entity через `UpdateResourceUseCase` -> `ResourceDao.update`:
  - `BrowseResourceStateManager.saveScrollPosition` - срабатывает на КАЖДОМ `onPause` BrowseActivity;
  - `BrowseResourceStateManager.saveLastViewedFile`;
  - `BrowseFileOpenManager.openFile` - на каждом открытии файла.
  - (`BrowseSortFilterManager` - те же стейл-копии при смене sort/display mode.)
- Итог: свежая статистика записана -> на выходе из browse (или при открытии файла) затёрта обратно в 0/null. Порядок асинхронных записей недетерминирован - иногда статистика «выживает» (эмулятор: 2 ресурса из 27 с данными), обычно затирается.
- Почему «число папок есть»: `ResourceEditorUseCase.getResourceStatistics` выводит subfolderCount из КЭША списка файлов (`inferSubfolderCount`, отдельная таблица, не затирается), а fileCount берёт только из затёртого поля ресурса.
- Смежный пробел: `BrowseMetadataManager` обновляет `lastSyncDate` только для SMB/SFTP/FTP, а рендерер статистики показывает «Последняя синхронизация» для всех не-LOCAL - для CLOUD всегда «Никогда». В домене уже есть `ResourceType.isNetworkResource` (включает CLOUD).

Эвиденс: дамп Room БД эмулятора (temp/S1001/fms.db) - 25/27 ресурсов с fileCount=0, lastBrowseDate=NULL при существующем кэше файлов; код-путь по цепочке `BrowseActivity.onPause` -> `BrowseStateManager.saveScrollPosition` -> `BrowseResourceStateManager.saveScrollPosition` -> full-entity `dao.update`.

---

## 3. Исправление

1. **State-когерентность (корневой фикс):** `BrowseMetadataManager.updateMetadata` возвращает записанный `MediaResource?` (null при неудаче); колбэк `updateResourceMetadata` в `BrowseViewModel` атомарно мёржит 4 stats-поля (fileCount, subfolderCount, lastBrowseDate, lastSyncDate) в ТЕКУЩИЙ `state.resource` (guard по id). После этого все стейл-копировщики (scroll, lastViewed, openFile, sort/display) несут свежие значения - класс клоббера закрыт целиком.
2. **Partial-update для одиночных полей (hardening):**
   - `ResourceDao`: `@Query UPDATE` `updateLastViewedFile(id, path)`, `updateLastScrollPosition(id, position)` (прецедент: `updateDisplayOrder`, `updateIcon`; FTS не затрагивается - индексирует name/path ресурса).
   - `ResourceRepository` + Impl: пробросить оба метода.
   - `UpdateResourceUseCase`: `saveLastViewedFile(resourceId, path)`, `saveScrollPosition(resourceId, position)`.
   - `BrowseResourceStateManager.saveLastViewedFile` / `saveScrollPosition`, `BrowseFileOpenManager.openFile`: перевести на partial-update (in-memory `updateState`-копия остаётся).
3. **lastSyncDate для CLOUD:** в `BrowseMetadataManager` заменить локальную проверку SMB/SFTP/FTP на `resource.type.isNetworkResource` (включает CLOUD) - выравнивание с рендерером статистики.
4. Тестовые фейки: `FakeResourceRepository` (testing/fakes + private в SendResourcesToWatchUseCaseTest) дополнить двумя методами.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

1. `.\a.ps1 fc` - компиляция + ресурсы PASS (expected: BUILD SUCCESSFUL | actual: PASS, 46s).
2. Targeted unit tests `UpdateResourceUseCaseTest` (4, вкл. 2 новых clobber-guard) + `SendResourcesToWatchUseCaseTest` - PASS (expected: exit 0 | actual: exit 0, 55s).
3. Эмулятор emulator-5556, ресурс id=27 «Все файлы» (до: fileCount=0, даты NULL): browse -> скролл -> выход -> дамп БД: `(49, 10, 1783788799853, None, scroll=10)` - fileCount и lastBrowseDate записаны, scroll-save их НЕ затёр. Повторный вход-выход: `(49, 10, 1783788861778, None, 10)` - значения стабильны, lastBrowseDate обновилась. PASS.
4. Редактор ресурса, блок «Статистика»: «Файлов: 49  Подпапок: 10», «Последнее открытие: 2026-07-11 18:54» (было «Никогда»). Карточка на главном: «49 файлов» (было «0 файлов»). PASS (скриншоты temp/scratch/emulator-5556_20260711_1855*.png).

---

## Last Audit

- **Date:** 2026-07-11
- **Verdict:** Verified
- **Scope:** lost-update клоббер статистики ресурса в Browse-сессии.
- **Changed:** `ResourceDao` (+2 partial UPDATE), `ResourceRepository`+Impl (+2 метода), `UpdateResourceUseCase` (+saveLastViewedFile/saveScrollPosition), `BrowseMetadataManager` (возврат persisted-ресурса, `isNetworkResource` вкл. CLOUD для lastSyncDate), `BrowseViewModel` (мёрж свежих stats в state.resource), `BrowseResourceStateManager`/`BrowseFileOpenManager` (targeted-записи вместо full-entity), тестовые фейки x2, +2 unit-теста.
- **Evidence:** build PASS, unit tests PASS, on-device двухцикловый DB-дамп + UI редактора (см. §4). P1 (data race / lost update) устранён двумя слоями: state-мёрж закрывает весь класс stale-copy писателей; partial-update убирает механизм для двух самых частых.
- **Residual:** пустой скан (`files.isEmpty()`) по-прежнему не пишет метаданные (сознательно - нечего писать); full-entity писатели sort/display в `BrowseSortFilterManager` остаются, но после state-мёржа несут свежие stats.
