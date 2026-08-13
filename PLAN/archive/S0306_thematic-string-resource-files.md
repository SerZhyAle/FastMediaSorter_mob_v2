# Стратегическая спецификация: S0306 - Тематическая организация строковых ресурсов

**Ticket:** S0306
**Status:** Archived
**Implemented date:** 2026-05-30
**Priority:** 50
**Date:** 2026-05-30
**Tier:** 2 - Easy
**Roadmap entry:** Ad-hoc - запрос 2026-05-30
**Tactical plan:** `PLAN/S0306_thematic-string-resource-files/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - создать спецификацию на замену S-ticket строковых resource-файлов тематическими файлами `strings_link_auth.xml`, `strings_google_account.xml`, `strings_vr.xml` вместо объединения в общий монолит.
- **Local anchor:** Provided by user - текущие S-ticket string resource groups: `strings_s0140`, `strings_s0155`, `strings_s0157`, `strings_s0160`, `strings_s0200`, `strings_s0292`, `strings_s0294`.
- **Scope boundaries / forbidden areas:** Provided by user - не объединять эти строки в общий `strings.xml`; предпочесть тематические resource groups.
- **Done / success signal:** Provided by user - стратегическая спецификация создана и готова к review перед `/spec-tech`.
- **Autonomy rule:** Provided by user - update/refine the spec; agent may apply non-structural `/spec-update` fixes and record structural product/process choices as DISCUSS proposals.
- **UI decisions / delegation:** N/A - задача меняет организацию ресурсов, а не поведение UI.

`Approved` remains blocked while open owner decisions in §6 are unresolved or not explicitly deferred.

---

## 1. Проблема

Сейчас часть строковых ресурсов разложена по файлам, названным по S-ticket id. Такой формат удобен во время реализации спеки, но после закрытия или частичного закрытия задач оставляет постоянную структуру, привязанную к истории разработки, а не к продуктовой области.

Полное объединение в общий `strings.xml` тоже не решает проблему: файл уже большой, даёт шумные diff при изменениях и повышает риск merge-конфликтов. Нужна стабильная тематическая группировка, которая сохраняет пользу разделения и убирает зависимость от временных ticket names.

---

## 2. Цели

1. Строки из S-ticket resource groups должны быть перенесены в тематические группы по продуктовой области.
2. Пользовательский текст, resource key names и runtime-поведение должны остаться неизменными.
3. EN/RU/UK варианты должны сохранить одинаковый набор ключей после переноса.
4. Новая структура должна помочь будущим изменениям искать строки по домену, а не по историческому номеру спеки.
5. `strings.xml` не должен становиться местом для механического слива этих групп.

**Non-goals:**

- Переименование resource keys.
- Переписывание пользовательских текстов.
- Изменение экранов, навигации, настроек или cloud/VR/auth поведения.
- Удаление или переписывание исторических spec-документов.
- Рефакторинг Kotlin-кода сверх необходимой проверки ссылок на существующие resource keys.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Использовать тематические файлы для link auth, Google account и VR.
2. Не делать один общий монолитный файл для этих строк.
3. Сохранить понятную структуру для будущих строковых групп.

### 3.2 Жёсткие ограничения

- **Flavor:** main app resources; структура должна оставаться совместимой со всеми app_v2 flavors, которые используют общий набор ресурсов.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** runtime-бюджет не меняется; Android resource merge должен остаться эквивалентным.
- **Совместимость данных:** без миграций и пользовательских данных.
- **Локализация:** EN/RU/UK обязательны для каждого перенесённого key.
- **Доступность:** без изменений, потому что UI copy и UI structure не меняются.
- **Communication policy:** если на тактическом этапе появится необходимость изменить сам текст строк, изменение должно пройти `docs/COMMUNICATION_POLICY.md`, включая tone checklist §6. Базовый путь - перенос без copy edit.

### 3.3 Owner inputs (Approval gate)

- **Resource scope:** app_v2 main resources, EN/RU/UK, current groups `strings_s0140`, `strings_s0155`, `strings_s0157`, `strings_s0160`, `strings_s0200`, `strings_s0292`, `strings_s0294`.
- **Target grouping:** `strings_link_auth.xml`, `strings_google_account.xml`, `strings_vr.xml`, and owner-approved `strings_resource_operations.xml`.
- **S0160 decision:** P-1 accepted - resource operations strings move into their own thematic group.
- **Future S-ticket policy:** P-2 deferred outside S0306 implementation; it does not block this resource move.
- **Preservation contract:** resource keys and user-visible string values stay unchanged; no copy edit and no key rename.
- **Validation level:** locale parity checks for affected key prefixes plus standard debug build.
- **Feature docs:** no `docs/FEATURES*.md` update, because there is no new user-visible behavior.
- **Related tickets:** S0140, S0155, S0157, S0160, S0200, S0234, S0292, S0294.

---

## 4. Контекст текущей архитектуры

Android resource pipeline объединяет все XML внутри одного values-набора в единый compiled resource table. Поэтому имя XML-файла не является runtime-контрактом, пока resource key names остаются прежними и нет duplicate definitions.

Текущие S-ticket файлы полезны как временный след реализации, но постоянная структура ресурсов должна отражать продуктовые домены: вход по ссылкам, Google account/cloud auth, VR player entry. Это снижает когнитивную нагрузку при поиске строк и уменьшает риск случайно смешать unrelated copy в большом общем файле.

Текущий inventory перед тактическим переносом:

- В EN/RU/UK присутствуют одинаковые S-ticket groups: `strings_s0140`, `strings_s0155`, `strings_s0157`, `strings_s0160`, `strings_s0200`, `strings_s0292`, `strings_s0294`.
- Количество string keys совпадает по локалям: S0140 - 3, S0155 - 13, S0157 - 5, S0160 - 4, S0200 - 30, S0292 - 8, S0294 - 3.
- S0234 не имеет отдельной resource group; ключи `s0234_*` находятся внутри группы S0200 и относятся к Google Drive sign-in error surfacing.
- Целевые thematic groups ещё не созданы.

---

## 5. Предлагаемый подход

Перевести постоянную организацию строковых ресурсов с ticket-based grouping на domain-based grouping. Перенос должен быть механическим: ключи и значения остаются стабильными, меняется только контейнер, в котором они лежат.

### 5.1 Основные столпы / модули

#### Link authentication resources

Группа содержит строки, связанные с приёмом ссылок, WebView/CCT авторизацией сторонних сайтов, именованием аккаунтов, выбором аккаунта и постоянным отказом от предложения авторизации. Источники переноса: S0140, S0155, S0157.

#### Google account and Google Drive auth resources

Группа содержит строки центрального Google account, Google Drive sign-in, browser/CCT/AppAuth отказов, ошибок входа и статусов Drive resources. Источники переноса: S0200, S0234-prefixed keys внутри S0200, S0294.

#### VR player entry resources

Группа содержит строки плоского player entry для VR: badge, loading/error states, settings prompt, return snackbars и overflow fallback. Источник переноса: S0292.

#### Resource operations follow-up

Строки resource operations overflow не относятся напрямую к link auth, Google account или VR. Их нужно явно классифицировать перед тактическим переносом.

### 5.2 Потоки данных и событий

Runtime-поток не меняется. UI и application layers продолжают обращаться к тем же resource keys, Android build объединяет тематические XML в тот же resource table, а локализация выбирается тем же механизмом values-qualified resources.

### 5.3 Точки расширяемости

Новые постоянные string groups должны называться по продуктовому домену. S-ticket resource groups допустимы только как временная staging-зона во время активной спеки и должны либо исчезать при закрытии, либо получать явный follow-up.

---

## 6. Открытые вопросы / Research items

1. **Куда перенести resource operations strings**
   - **Вопрос:** что делать со строками S0160, которые относятся к resource card actions, а не к link auth, Google account или VR?
   - **Варианты:** создать отдельную thematic group для resource actions; вернуть в существующую общую settings/resource область; оставить S0160 до отдельного cleanup.
   - **Решение:** перенести S0160 в отдельную thematic group для resource operations/resource actions.
   - **Статус:** Resolved - owner accepted P-1 on 2026-05-30.

2. **Политика будущих S-ticket string files**
   - **Вопрос:** должны ли новые `strings_sNNNN` файлы быть запрещены после закрытия спеки или разрешены как временный staging pattern?
   - **Варианты:** запретить полностью; разрешить только для активных specs; разрешить без правила.
   - **Нужно выяснить:** насколько жёстким должен быть процесс cleanup.
   - **Статус:** Deferred - не блокирует S0306 implementation; P-2 остаётся отдельным proposed process cleanup.

3. **Переименование legacy S-prefixed keys**
   - **Вопрос:** нужно ли когда-либо переименовывать ключи вида `s0140_*`, `s0200_*`, `s0294_*` в domain-based key names?
   - **Варианты:** не делать в этой спецификации; запланировать отдельный low-priority cleanup; делать вместе с переносом.
   - **Нужно выяснить:** допустим ли дополнительный Kotlin/XML diff ради косметики ключей.
   - **Статус:** Resolved - не делать в этой спецификации.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Потеря локализационной парности | Средняя | Одна локаль собирается с fallback или missing translation | Переносить EN/RU/UK синхронно и запускать string locale audit |
| Duplicate resource names | Низкая | AAPT build failure | Перед переносом проверять уникальность ключей и удалять старую definition вместе с новой |
| Шумный diff из-за форматирования | Средняя | Review сложнее, blame по строкам менее полезен | Делать чистый mechanical move без copy edits |
| Неправильная классификация S0160 | Средняя | Строки resource actions попадут в неподходящий домен | Закрыть open question перед `/spec-tech` |
| Случайное изменение UI copy | Низкая | Пользователь видит изменённый текст без review | Считать copy edit вне scope и сравнить значения до/после |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`: это внутренняя организация resources без нового user-facing поведения.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Domain-based resource files вместо S-ticket files**

- **Решение:** постоянные string resource files группируются по продуктовым доменам, а не по номеру спеки.
- **Альтернативы:** оставить S-ticket files; перенести всё в общий `strings.xml`.
- **Почему:** domain-based grouping сохраняет компактные diff и улучшает поиск, не создавая монолит.

**ADR-2: Resource key names сохраняются**

- **Решение:** первый этап меняет только физическое расположение строк, не resource names.
- **Альтернативы:** переименовать ключи под новые домены.
- **Почему:** переименование ключей добавляет Kotlin/XML churn без пользовательской пользы.

**ADR-3: Copy text remains byte-equivalent**

- **Решение:** значения строк не меняются, кроме обязательного XML-equivalent escaping при переносе.
- **Альтернативы:** совместить cleanup с copy review.
- **Почему:** смешивание move и copy edit делает review хуже и повышает риск незаметного UX-изменения.

---

## 10. Связи с другими спеками

- S0140 - incoming links / market URL coverage.
- S0155 - link auth multi-account.
- S0157 - link auth offer and dismissal UX.
- S0160 - resource operations overflow toggle.
- S0200 - central Google account binding.
- S0234 - Google Drive sign-in error surfacing.
- S0292 - VR content launch UI.
- S0294 - Google Drive browser auth for Quest 3.

---

## 11. Критерии готовности (strategic-level)

1. Для EN/RU/UK существует одинаковая тематическая структура для link auth, Google account, VR и owner-approved resource operations строк.
2. Старые S-ticket string files для перенесённых доменов удалены после миграции ключей или явно оставлены только для unresolved follow-up.
3. Ни один resource key не переименован.
4. Значения перенесённых строк XML-normalized эквивалентны исходным.
5. Android resource merge проходит без duplicate definitions.
6. Locale audit по затронутым key prefixes проходит без missing keys.
7. `strings.xml` не получает механический слив строк из этих S-ticket groups.
8. Для S0160 принято явное решение перед `/spec-tech`.
9. Тактическая валидация фиксирует expected vs actual key counts для каждой перенесённой группы и локали.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0306` - создаст `PLAN/S0306_thematic-string-resource-files/` с фазами.

---

## Implementation Handoff

- S-ticket string files moved into thematic groups: link auth, Google account, VR, and resource operations.
- Resource keys were not renamed.
- User-visible string values were not intentionally changed; validation compared XML-normalized values against the original S-ticket files.
- No docs/FEATURES update is required because S0306 changes internal resource organization only.
- No Kotlin catalog sync is required because S0306 does not edit Kotlin or Java public API.

---

## Proposed Structural Changes

### Proposal P-1 - Move S0160 to a resource operations thematic group  (proposed 2026-05-30 by GPT-5)

**Status:** Accepted 2026-05-30 - owner selected P-1.
**Affected:** §5.1 Resource operations follow-up; §6 Research item #1; §11 Criteria #1/#8
**Rationale:** S0160 strings describe resource-card actions and overflow settings. They do not belong to link auth, Google account or VR, but leaving the S-ticket group behind keeps the same historical naming problem S0306 is meant to remove.
**Suggested edit:**
> §6.1 `Статус: Open` → `Resolved - перенести S0160 в отдельную thematic group для resource operations/resource actions`.

### Proposal P-2 - Allow S-ticket string groups only as active-spec staging  (proposed 2026-05-30 by GPT-5)

**Status:** Proposed
**Affected:** §5.3 Точки расширяемости; §6 Research item #2
**Rationale:** A full ban would slow active specs, while unrestricted use recreates permanent historical resource groups. A staging-only rule preserves implementation convenience and makes cleanup part of closure.
**Suggested edit:**
> §6.2 `Статус: Open` → `Resolved - разрешить strings_sNNNN только для активных specs; при закрытии спеки строки переносятся в thematic group или фиксируется explicit follow-up`.

---

## Revision History

- **2026-05-30** - by `/spec-update` (`GPT-5`, focus: language, structure, verifiability, consistency, completeness, style)
  - Applied: 4. Proposed (DISCUSS): 2.
- **2026-05-30** - by owner decision + `/spec-update` (`GPT-5`, focus: implementation gate)
  - Applied: P-1 accepted; P-2 deferred outside S0306 implementation.
- **2026-05-30** - by `/spec-tech` (`GPT-5`)
  - Created tactical plan: 2 phases. Status moved to Tactical.
- **2026-05-30** - implemented by `/spec-dev` (`GPT-5`)
  - Moved S-ticket string resource files into thematic EN/RU/UK groups and completed standard debug build validation.

## Last Audit

**Date:** 2026-05-30
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 38 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 2

### Manual / on-device

- None required; S0306 is a resource-only regroup.
- Standard debug build passed: `temp/build_debug_20260530_175324.log`.
