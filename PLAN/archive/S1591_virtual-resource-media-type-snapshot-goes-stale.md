# Стратегическая спецификация: S1591 - снимок типов у виртуального ресурса не пересматривается после смены настроек

**Ticket:** S1591
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-12
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - исправить устаревший фильтр типов виртуальных ресурсов
**Tactical plan:** Primitive path - implementation is covered by the existing filter and regression-test files.
**Implemented date:** 2026-08-12

---

## Inbox: захваченный материал

**Захвачено:** 2026-08-12

**Захвачено во время:** реализации S1584 (расхождение счётчика карточки и списка browse). К объёму S1584
отношения не имеет - запарковано без переключения активной задачи.

**Текст:**

`MediaResource.supportedMediaTypes` у виртуальных ресурсов (`virtual://camera_photos`, `virtual://all_images`
и прочих) заполняется один раз, в момент провижининга, из настроек, действующих на тот момент:
`ProvisionDefaultResourcesUseCase.kt:99-110` (Camera), `:124-134` (All Images), аналогично в
`ScanLocalFoldersUseCase`. Дальше этот набор не пересматривается.

Оба потребителя пересекают его с живыми глобальными настройками. Значит, если пользователь позже изменил
набор поддерживаемых типов так, что он разошёлся со снимком, пересечение схлопывается - вплоть до пустого
множества. Виртуальный ресурс тогда становится демонстративно пустым, хотя файлы на месте, а сам ресурс по
названию («Camera Photos») обещает ровно тот тип, который пользователь только что включил.

**Что известно без расследования.** Механизм подтверждён чтением кода при работе над S1584. Не установлено:
воспроизводится ли это на практике (нужен сценарий смены настроек после провижининга), и какой ремонт верен -
пересматривать снимок при изменении настроек, выводить типы виртуального ресурса живьём вместо снимка, или
запретить пересечение именно для виртуальных агрегатов, у которых тип задан их природой.

**Почему это не входило в S1584.** S1584 добивался того, чтобы счётчик карточки и список browse отвечали
одинаково, и добился: оба теперь читают снимок через один вывод фильтра. Согласованность достигнута, но
сама возможность просроченного снимка осталась - это отдельная причина с отдельным ремонтом.

**Дедуп.** `search.ps1 -Query "supportedMediaTypes"` и `-Query "virtual"` - записей нет.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Delegated by user - /spec-all auto-approval - implementation of the existing S1591 bug.
- **Goal / expected outcome:** Delegated by user - /spec-all auto-approval - predefined aggregate virtual resources resolve their natural media family from current global settings instead of a provisioning-time type snapshot; ordinary resources keep their saved type filter.
- **Local anchor:** Provided by user - S1591 and the captured stale-snapshot symptom; the existing local anchor is the shared scan-filter derivation.
- **Scope boundaries / forbidden areas:** Delegated by user - /spec-all auto-approval - change only the shared filter derivation and its regression tests; no Room schema, resource migration, UI, strings, flavor policy, or read-only directory changes.
- **Done / success signal:** Delegated by user - /spec-all auto-approval - focused unit tests prove current settings drive all aggregate virtual families and saved snapshots still constrain ordinary resources; the standard code check and pipeline audit pass.
- **Autonomy rule:** Delegated by user - /spec-all auto-approval - agent may decide with explicit assumptions when the codebase establishes the contract.
- **UI decisions / delegation:** N/A - no user-visible surface changes.

`Approved` is permitted by `/spec-all` auto-approval because the local behavior, scope, success signal, and reversible implementation choice are all established by the existing code and S1591 capture.

---

## 1. Проблема / симптом

Предопределённые виртуальные агрегаты сохраняют набор типов, рассчитанный при первом создании ресурса. После изменения глобальных настроек этот набор может не содержать тип, который пользователь включил для соответствующего агрегата, поэтому общий фильтр возвращает пустой набор и виртуальный ресурс выглядит пустым при наличии файлов.

---

## 2. Корневая причина

Фильтр применяет пересечение живых глобальных настроек со снимком `supportedMediaTypes` одинаково к обычным ресурсам и к предопределённым агрегатам. Для агрегатов природный тип задаётся самим виртуальным путём, а сканер уже использует этот путь для выбора семейства файлов, поэтому сохранённый снимок не является источником истины.

---

## 3. Исправление

Для пяти предопределённых агрегатов вычислять эффективные типы по текущим настройкам и семейству виртуального пути: аудио, видео, изображения, камера или документы. Сохранять прежнее пересечение со снимком для обычных ресурсов и прежний режим `allFiles` для ресурсов, не являющихся предопределёнными агрегатами. Зафиксировать оба контракта focused unit tests.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1584 - обнаружено при его реализации, общий код (`ResolveScanFilterUseCase`).

### 3.4 Решение по альтернативам

- **Выбрано:** вычислять типы агрегата живьём при разрешении фильтра, не переписывая ресурс в базе.
- **Отклонено:** пересоздавать или синхронизировать сохранённый снимок при каждом изменении настройки - это добавляет запись состояния и новый жизненный цикл для данных, которые уже задаются виртуальным путём.
- **Отклонено:** полностью отменять пересечение с глобальными настройками - отключённый пользователем тип всё равно должен скрываться.

---

## 4. Проверка

Проверить focused unit tests для виртуального агрегата после смены настроек, focused unit test для обычного ресурса с устаревшим снимком, затем выполнить стандартную code validation. После реализации запустить `/spec-check S1591`; ожидаемый итог - `Verified`, если static audit и build gates проходят.

## 5. Риски и ограничения

- Изменение ограничено виртуальными путями из существующего каталога; неизвестные или пользовательские пути не получают специального поведения.
- Отключённые в настройках типы остаются исключёнными даже для агрегатов.
- Схема Room, Hilt-граф, ресурсы и локализация не меняются.

## 6. Research items

1. **Which source owns aggregate media types?**
   - **Status:** Resolved.
   - **Evidence:** the local scanner routes aggregate paths to fixed families, while the shared filter intersects a persisted snapshot with live settings.
2. **Should ordinary resources change semantics?**
   - **Status:** Resolved.
   - **Decision:** no; their saved `supportedMediaTypes` remains an explicit resource-level selection.

## 11. Критерии готовности

- A camera aggregate opened after switching from image support to video support resolves video types instead of an empty set.
- All five aggregate families follow their current enabled settings and remain empty when their family is globally disabled.
- A normal folder resource with a stale saved type snapshot still resolves to the intersection of its snapshot and global settings.
- Focused tests and the standard code gate pass.

## 8. Documentation impact

No `docs/FEATURES*.md` change - this is an internal correctness fix with no new user-facing capability or string.

## 10. Related tickets

- S1584 - shared filter introduced while aligning the card counter and Browse list.

## 9. Архитектурное решение

**ADR-1: Resolve predefined aggregate types from the live settings contract**

- **Решение:** virtual aggregate paths select their natural family from current settings; ordinary resources keep their saved type snapshot.
- **Альтернативы:** rewrite saved resources on settings changes, or disable global-setting intersection for aggregates.
- **Почему:** the scanner already treats the path as the family source, and global settings must still hide disabled types.

## 12. Ссылка на тактическую спецификацию

Primitive path - no tactical folder; implementation and regression tests are complete in the existing shared filter.

## Last Audit

**Date:** 2026-08-12
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [x] No on-device check required - this is an internal filter contract covered by focused unit tests and the standard debug build.
