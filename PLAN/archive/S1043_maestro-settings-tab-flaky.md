# Стратегическая спецификация: S1043 - Maestro flow settings_toggle_sweep flaky из-за persisted tab

**Ticket:** S1043
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-13
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-13
**Tactical spec:** `PLAN/S1043_maestro-settings-tab-flaky/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-13

**Текст:**

Maestro flow settings_toggle_sweep.yaml is order-dependent and flaky: it assumes Settings opens on the "Общие" (General) tab and scrolls to find id headerInterface, but SettingsActivity persists the last-active tab across app sessions (by design). When a prior flow or manual session leaves Settings on a different tab (e.g. "Управление"), settings_toggle_sweep.yaml fails with "No visible element found: id: headerInterface" even though the app itself is working correctly.

Evidence: reproduced twice during /spec-prerelease sweep on 2026-07-13 (emulator-5554, standard-debug):
1. First reproduction: after manual mobile-mcp session left Settings on "Управление" tab, `pwsh maestro/run-tests.ps1 -Suite smoke -DeviceId emulator-5554 -Json` failed settings_toggle_sweep.yaml (log: temp/settings_toggle_sweep_maestro_20260713_191057.log). Confirmed root cause by manually resetting the persisted tab back to "Общие" and re-running just that flow -> passed (temp/settings_toggle_sweep_maestro_20260713_191716.log).
2. Second reproduction: full suite run `-Suite all` (16/17 passed) failed the same flow again with the identical "headerInterface not visible" signature (log: temp/settings_toggle_sweep_maestro_20260713_193641.log), most likely because an earlier flow in the suite (screen_tour.yaml or settings_search.yaml, both of which touch Settings) left the persisted tab on something other than "Общие" before settings_toggle_sweep.yaml ran.

Proposed fix: add an explicit step at the start of maestro/smoke/settings_toggle_sweep.yaml (or its shared setup fragment) that navigates to / asserts the "Общие" tab is selected before scrolling for headerInterface, making the flow independent of whatever tab a prior flow or manual session left Settings on. Alternatively add a shared "reset settings to Общие tab" fragment reusable by any flow that depends on a specific default tab.

Not a production app defect - the app's tab-persistence behavior is intentional UX; this is purely a test-flow robustness gap in the Maestro suite that undermines /spec-prerelease's release-gating reliability.

---

## 1. Проблема

Maestro-флоу `settings_toggle_sweep.yaml` жёстко предполагает, что экран Настроек открывается на вкладке "Общие", и падает с ошибкой "No visible element found: id: headerInterface", если предыдущий флоу (в suite) или ручная сессия оставили Настройки на другой вкладке. Это ложный FAIL, маскирующий реальное состояние сборки под /spec-prerelease.

---

## 2. Цели

1. `settings_toggle_sweep.yaml` проходит независимо от того, на какой вкладке Настроек закончился предыдущий шаг/флоу.
2. Другие флоу, зависящие от конкретной вкладки Настроек по умолчанию, получают переиспользуемый механизм сброса вкладки.

**Non-goals:**

- Изменение поведения persisted-tab в самом приложении (это осознанный UX, не баг).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Предпочтителен shared-фрагмент (`_shared/`) для сброса вкладки Настроек, переиспользуемый другими флоу.

### 3.2 Жёсткие ограничения

- **Flavor:** не относится к flavor - тестовая инфраструктура.
- **API level:** не относится.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично.
- **Совместимость данных:** не относится.
- **Локализация:** не относится (YAML-флоу, не UI-строки).
- **Доступность:** не относится.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

`maestro/smoke/settings_toggle_sweep.yaml` открывает экран Настроек через `btnSettings` и сразу скроллит к `headerInterface` без проверки текущей активной вкладки `TabLayout`. Экран Настроек (`SettingsActivity`) хранит последнюю активную вкладку между запусками (persisted preference), что корректно для UX, но делает флоу чувствительным к состоянию, оставленному предыдущими шагами/сессиями.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- Shared Maestro-фрагмент, гарантирующий активную вкладку "Общие" перед тем, как флоу полагается на элементы этой вкладки.

### 5.2 Потоки данных и событий

Флоу → тап по вкладке "Общие" (или assert, что она уже активна) → продолжение существующих шагов без изменений.

### 5.3 Точки расширяемости

Фрагмент должен быть переиспользуем любым флоу, которому нужна конкретная вкладка Настроек по умолчанию.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Другие флоу окажутся зависимы от той же persisted-вкладки | Средняя | Аналогичные ложные FAIL в других suite-прогонах | Аудит остальных Settings-флоу на ту же уязвимость при реализации |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (shared Maestro fragments).

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. `settings_toggle_sweep.yaml` проходит после того, как предыдущий флоу в suite оставляет Настройки на любой другой вкладке.
2. Полный suite (`-Suite all`) проходит 17/17 без ручного вмешательства в состояние вкладки между флоу.

---

## Last Audit

**Дата:** 2026-07-16
**Режим:** review (код опередил спеку) + доделка.
**Вердикт:** BlockNeedUserTest - реализация полна, остался только прогон Maestro.

### Что уже лежало в дереве до этого прохода

Спека была `Draft`, но правка была сделана и закоммичена (`966a431c`, 2026-07-15) - классический «спека написана, фикс поехал, спеку не обновили». Найдено в `maestro/smoke/settings_toggle_sweep.yaml`:

- **Выбор вкладки** (цель §2.1): явный `tapOn "Общие"` перед скроллом к `headerInterface`.
- **Защита секции от схлопывания** - в спеке этого нет вообще. Секция Interface могла быть уже раскрыта предыдущим флоу (`screen_tour` / `settings_search`), и безусловный тап по заголовку её бы **свернул**, спрятав строки. Правка раскрывает только когда `rowEnableFavorites` не виден. Это вторая, независимая order-dependency того же класса; §0/§1 описывают лишь проблему вкладки.

`drift-check` показал «0 code markers» не потому, что маркеров нет, а потому что он сканирует их только в `app_v2/src/`; Maestro-флоу лежат вне. Вердикт DRIFT пришёл от коммита. Считать «0 markers» за «в коде ничего нет» - ошибка, которой я едва не совершил.

### Что доделано в этом проходе

Цель §2.2 и пожелание владельца §3.1 (**переиспользуемый shared-фрагмент**) закрыты не были: правка была инлайновой, притом что `maestro/_shared/` существует и этот же флоу двумя строками выше зовёт `../_shared/dismiss_settings_dialogs.yaml`.

- Создан `maestro/_shared/settings_select_general_tab.yaml` по конвенции соседей (`appId` + `---` + WHY-комментарий + идемпотентные шаги).
- `settings_toggle_sweep.yaml` теперь зовёт его через `runFlow` - инлайнового тапа не осталось, это 4-й `../_shared/` вызов в файле.

**Честная оговорка про ценность фрагмента.** Аудит по §7 (митигация «проверить остальные Settings-флоу») выполнен: Настройки открывают 6 флоу (`critical/settings`, `features/edge/back_from_every_screen`, `features/resource/resource_lifecycle`, `features/settings/settings_search`, `smoke/screen_tour`, `smoke/settings_toggle_sweep`), но от **конкретной вкладки зависит только** `settings_toggle_sweep` - остальные работают с `titleText` или тулбар-поиском, то есть с элементами, не привязанными к вкладке. Значит «другие флоу» из цели §2.2 сегодня - пустое множество, и у фрагмента ровно один потребитель. Сделан всё равно: §3.1 - записанное решение владельца, а не догадка, и конвенция `_shared/` в этом файле уже действует. Риск §7 при этом снимается с доказательством, а не по умолчанию.

**Наблюдение (не дефект, стратегия спеки).** Флоу дальше сам тапает Медиа/Плеер/Управление, то есть оставляет вкладку грязной для следующего флоу. Это не упущение: §5.2 задаёт потребительскую стратегию - каждый флоу обеспечивает свою предпосылку, а не убирает за собой. Фрагмент - ровно её инструмент.

### Проверки

- `maestro/smoke/../_shared/settings_select_general_tab.yaml` - путь разрешается; оба файла читаются.
- Инлайновый `text: "Общие"` в флоу отсутствует - вынесен без остатка.
- Аудит §7 - 6 флоу проверены поимённо (выше).
- `.kt` не тронуто -> зондов `Timber.d("S1043: ..")` нет и быть не может; инвариант CLAUDE.md («тег в `.kt` <=> BlockNeedUserTest») не нарушается, гейт `assert-no-ticket-logs` ловит теги без статуса, а не статус без тегов.

### Остаётся (device-гейт)

Оба критерия §11 - прогон Maestro, статически недоказуемы:

1. `settings_toggle_sweep.yaml` проходит после флоу, оставившего Настройки на другой вкладке.
2. `-Suite all` -> 17/17 без ручного вмешательства.

`DEVICE_ONLINE=false` в этой сессии. Прогон только на эмуляторе: реальное устройство стирает конфиг, а FAIL харнеса - не дефект приложения.
