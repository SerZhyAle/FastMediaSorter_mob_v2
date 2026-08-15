# Стратегическая спецификация: S1451 - Player command shortTitleResId dead weight

**Ticket:** S1451
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-07
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при ресёрче S1365, 2026-08-07
**Tactical spec:** `PLAN/S1451_player-command-shorttitle-dead-weight/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Текст:**

CommandPanelLayoutPlanner.PlayerCommand объявляет свойство `shortTitleResId` ("Short label for Big Buttons Mode top-panel display"), и ~25 записей перечисления передают в него ключи `R.string.big_btn_short_*`. Grep по всему репозиторию (*.kt) находит ровно одно вхождение имени - саму декларацию в CommandPanelLayoutPlanner.kt:46. Свойство не читается нигде: ни в CommandPanelController, ни в BigButtonsModeManager, ни в тестах. Значит режим больших кнопок короткие подписи не показывает, а два десятка строковых ключей big_btn_short_* в трёх локалях висят мёртвым грузом (CLAUDE.md Rule 20). Нужно: подтвердить, что Big Buttons Mode действительно не рисует текстовые подписи, затем удалить свойство, все аргументы и осиротевшие ключи строк EN/RU/UK. Обнаружено при ресёрче S1365.

**Захвачено во время:** S1365

---

## 1. Проблема

Перечисление команд плеера несёт свойство короткой подписи, которое никто не читает. Вместе с ним живут два десятка строковых ключей в трёх локалях, которые никогда не попадают на экран.

Мёртвый груз стоит не только места в сборке. Он вводит в заблуждение: свойство подписано как «короткая подпись для режима больших кнопок», и следующий читатель кода решит, что режим больших кнопок такие подписи рисует, хотя это не так. Ключи с префиксом `big_btn_short_` при этом проходят все проверки локализации и требуют перевода на каждый новый язык.

**Посылка перепроверена 2026-08-08, до правки:**

- `shortTitleResId` встречается в `app_v2` ровно один раз - объявление в `CommandPanelLayoutPlanner.kt:57`. Читателей нет.
- `big_btn_short_` - 35 упоминаний в `CommandPanelLayoutPlanner.kt` и по 22 ключа в каждом из `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`. Упоминаний больше, чем ключей, потому что часть ключей переиспользуется несколькими командами.
- Те же ключи упоминаются в `config/detekt/baseline-app_v2.xml` (32) и `app_v2/lint-baseline.xml` (2) - удаление затронет обе базовые линии.

---

## 2. Цели

1. Перечисление команд плеера не несёт свойства, которого никто не читает.
2. Строковые ключи `big_btn_short_*` не требуют перевода на новые языки.
3. Читатель кода больше не выводит из объявления, будто режим больших кнопок рисует короткие подписи.

**Non-goals:**

- Введение коротких подписей в режиме больших кнопок. Тикет удаляет неиспользуемое, а не реализует задуманное. Если подписи понадобятся, это отдельный тикет со своим решением по размещению.
- Любое изменение поведения плеера или состава команд.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Особых пожеланий нет - чистка по Rule 20.

### 3.2 Жёсткие ограничения

- **Flavor:** затрагиваются все, машинерия панели команд лежит в `src/main`.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не затрагивается.
- **Совместимость данных:** миграций нет.
- **Локализация:** ключи удаляются во всех трёх локалях одновременно, иначе аудит строк упадёт на расхождении.
- **Доступность:** без изменений - удаляемые строки нигде не произносятся и не отображаются.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1365 - ресёрч, при котором находка сделана. Дедуп по `search.ps1` («shortTitle», «big_btn_short») других тикетов не дал.

---

## 4. Контекст текущей архитектуры

Перечисление команд плеера описывает каждую команду набором свойств: приоритет в адаптивной раскладке, идентификатор пункта меню, признак наличия собственной кнопки на панели, ключ заголовка и ключ иконки. Шестым свойством к ним добавлена короткая подпись со значением по умолчанию, и именно оно оказалось лишним: значения передаются, но ни один потребитель их не спрашивает.

Свойство объявлено с умолчанием, поэтому его удаление не ломает записи, которые его не передают, и требует правки только там, где аргумент передан явно.

---

## 5. Предлагаемый подход

Удаление, а не сохранение про запас. Сохранённое «на будущее» неиспользуемое свойство уже один раз ввело читателя в заблуждение - это и есть содержание тикета.

Порядок обязателен: сперва снимаются аргументы у записей перечисления, затем само свойство, и только потом строковые ключи. Обратный порядок оставляет ссылки на удалённые ресурсы и роняет сборку ресурсов вместо компиляции, где ошибка читается хуже.

### 5.1 Основные столпы / модули

- Перечисление команд плеера - снятие свойства и всех переданных аргументов.
- Ресурсы строк трёх локалей - удаление осиротевших ключей.
- Базовые линии статических анализаторов - обе содержат записи по удаляемым именам и должны сойтись после правки.

### 5.2 Потоки данных и событий

Не меняются: удаляемое свойство ни во что не втекает.

### 5.3 Точки расширяемости

Не требуются.

---

## 6. Открытые вопросы / Research items

1. **Действительно ли режим больших кнопок не рисует текстовых подписей**
   - **Статус:** Resolved 2026-08-08. Свойство не читается ни одним классом, поэтому короткая подпись физически не может дойти до экрана. Отдельная проверка на устройстве не нужна: отсутствие читателя доказывается grep-ом сильнее, чем наблюдением.

2. **Затронуты ли базовые линии анализаторов**
   - **Статус:** Resolved 2026-08-08. Затронуты обе: `config/detekt/baseline-app_v2.xml` (32 упоминания) и `app_v2/lint-baseline.xml` (2). Тактический план обязан включить шаг сверки после удаления.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Ключ удалён не во всех локалях | Средняя | Аудит строк падает на расхождении | Удалять ключ инструментом, работающим сразу по трём локалям, и прогнать аудит по префиксу |
| Часть ключей используется вне перечисления | Низкая | Сборка ресурсов падает | Перед удалением каждого ключа проверить его отсутствие в `res/` и в остальных `.kt` |
| Устаревшие записи базовых линий | Высокая | Гейты падают после удаления | Шаг сверки обеих базовых линий в конце |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - пользователь не видел удаляемого ни разу.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшемуся правилу Rule 20.

---

## 10. Связи с другими спеками

- **S1365** - ресёрч, при котором находка сделана.

---

## 11. Критерии готовности (strategic-level)

1. Имя `shortTitleResId` не встречается в репозитории.
2. Ни один ключ с префиксом `big_btn_short_` не остаётся ни в одной из трёх локалей.
3. Компиляция и сборка ресурсов проходят.
4. Аудит строк по префиксу проходит.
5. Обе базовые линии анализаторов сходятся.

---

## Last Audit

**Date:** 2026-08-08
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 21 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

All five §11 criteria hold on fresh evidence: `shortTitleResId` has zero occurrences outside `PLAN/`, `dev/CHANGELOG.md` and a `temp/` backup; no `big_btn_short_` key survives in any locale on disk; `.\a.ps1 fk` passed with `mergeStandardDebugResources` and `generateStandardDebugRFile` executing, which proves the resource build too; the string audit reports the prefix gone from every strict locale; and `assert-detekt-baseline-absorption -Gate` passes at 12247 = 12247.

Integrity check beyond the criteria: the enum declares 58 entries both before and after, compared against the pre-edit backup, so the argument removal took no entry with it. §8 is EXEMPT - it reads "Без изменений", and nothing removed was ever rendered.

Two pieces of work the tactical plan did not foresee, both recorded as steps 01.5 and 01.6:

- Dropping the sixth argument left ~29 entries in the one shape `ArgumentListWrapping` rejects, under signatures the old baseline no longer matched. Fixed in the code per Rule 19 rather than re-baselined: 22 entries joined to a single line, 7 expanded to one argument per line, 4 trailing comments lifted above their entry with the blank line `SpacingBetweenDeclarationsWithComments` requires.
- The absorption gate failed on a **pre-existing** divergence: S1406 froze `MagicNumber:..OFFICE_TEXT_SETTINGS$393` in the detekt baseline without re-seeding the ID snapshot. That ID names neither removed symbol, so this ticket's pruning cannot have caused it. Resolved by naming the priority a constant the way S0995/S1364/S1474 did, dropping the stale entry, and re-seeding the snapshot through the script so the reason is recorded - the `.ids` header forbids hand-editing.

### Manual / on-device

- None. §6 item 1 resolved this statically: with no reader of the property, the short label could never reach the screen, which grep proves more strongly than observation would.
