# Стратегическая спецификация: S0258 — Единый шаблон toggle-строки

**Ticket:** S0258
**Status:** Verified
**Implemented date:** 2026-05-19
**Verified date:** 2026-05-19
**Priority:** 50
**Date:** 2026-05-19
**Tier:** 3 — Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc — запрос 2026-05-19
**Tactical plan:** `PLAN/S0258_settings-toggle-row-template/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

В приложении накопилось много toggle/switch-строк в настройках и формах, собранных вручную из примитивов. У одних строк есть subtitle, у других нет; у одних help-иконка стоит справа в хвосте строки, у других отсутствует совсем; часть строк имеет собственный порядок элементов и собственные отступы. Из-за этого новые toggle-элементы продолжают появляться в ad-hoc формате, а любое визуальное выравнивание требует массового ручного обхода XML.

Пользовательский запрос формулирует новый единый шаблон явно: toggle слева, текст названия справа, helper-кнопка прижата сразу к названию, а под названием расположен короткий справочный subtitle меньшим шрифтом. Сейчас этот шаблон не закреплён как обязательная инфраструктура и даже конфликтует с текущим repo-каноном, где help-иконка описана как правый крайний элемент строки.

---

## 2. Цели

1. В `app_v2` появляется один обязательный переиспользуемый шаблон toggle-строки для настроек и форм.
2. Новый шаблон визуально фиксирует порядок: switch слева, title справа, helper рядом с title, subtitle под title.
3. Новые toggle-строки создаются только через общий компонент или шаблон, без ручной сборки из `SwitchMaterial + TextView + ImageButton`.
4. Существующие toggle-строки мигрируются поэтапно без массовой одномоментной ломки всех экранов.
5. Канонические repo-правила синхронизируются с новым шаблоном и больше не требуют rightmost help-icon для switch-row.
6. Компонент поддерживает optional trailing-action для редких строк со спецкнопкой, не ломая основной шаблон.
7. Компонент одинаково работает в portrait и landscape, а также для keyboard / D-pad / mouse.
8. Pilot migration в рамках текущей задачи подтверждает, что новый шаблон реально применим к существующему экрану настроек.

**Non-goals:**

- Полная миграция всех toggle-строк приложения в одном заходе.
- Переработка checkbox-паттерна.
- Изменение бизнес-логики настроек.
- Изменение Wear-модуля.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Helper-кнопка должна быть визуально прижата к названию toggle, а не уезжать в правый край строки.
2. Под названием toggle должен располагаться короткий subtitle чуть меньшим шрифтом.
3. Новые toggle-строки должны создаваться по одному шаблону без повторной ручной вёрстки.
4. Внешний вид должен быть единым между settings-экранами и формами.

### 3.2 Жёсткие ограничения

- **Flavor:** все flavor `app_v2`; при этом объём фактической миграции по экранам может отличаться.
- **API level:** без API-специфичных решений; компонент должен работать в рамках текущего `minSdk`.
- **Wear OS:** не затрагивается.
- **Производительность:** без лишней runtime-логики; обычный inflate + лёгкое обновление state.
- **Совместимость данных:** существующие ключи настроек и поведение переключателей не меняются.
- **Локализация:** EN/RU/UK обязательны для новых subtitle/help-строк, если они добавляются.
- **Доступность:** keyboard / D-pad / mouse / touch; contentDescription, focus-state и touch target обязательны.
- **Communication policy:** все новые user-visible help/subtitle strings должны проходить `docs/COMMUNICATION_POLICY.md` §6 до интеграции.

---

## 4. Контекст текущей архитектуры

Сейчас toggle-строки живут как ручные XML-конструкции в settings fragments и формах ресурса. Несмотря на наличие описанного `Trigger Row` паттерна, проект не имеет одного общего UI-компонента, через который создаётся такая строка. Поэтому даже экраны внутри одной области настроек расходятся по расположению help-иконки, наличию subtitle и структуре контейнеров.

В проекте уже есть два полезных инфраструктурных якоря: переиспользуемый tooltip-диалог и пример общего styleable-компонента для UI-шапки. Это означает, что задача не требует нового архитектурного слоя; нужен именно единый reusable view для toggle-строки плюс поэтапная миграция экранов.

---

## 5. Предлагаемый подход

Ввести один compound view для switch/toggle row. Компонент инкапсулирует canonical layout, типографику, spacing, visibility helper-иконки и открытие tooltip-диалога. Внешний экран управляет только checked-state, enabled-state, обработчиком изменения и при необходимости optional trailing-action.

### 5.1 Основные столпы / модули

1. **Toggle-row component:** общий UI-компонент для switch/title/helper/subtitle.
2. **Canonical repo rules update:** синхронизация load-bearing правил проекта с новым форматом helper рядом с title.
3. **Pilot migration:** перевод одного компактного settings-экрана на новый компонент для проверки binding/API-подхода.
4. **Phased rollout:** дальнейшая пакетная миграция остальных settings/form экранов без big-bang.

### 5.2 Потоки данных и событий

- Экран задаёт title/subtitle/help payload и начальный checked-state.
- Компонент сам рисует canonical row и открывает helper-tooltip при нажатии на help.
- Компонент наружу эмитит изменение checked-state.
- Экран сохраняет setting и управляет зависимыми sibling-контейнерами, как и раньше.

### 5.3 Точки расширяемости

- Optional trailing action slot для специальных строк с дополнительным действием.
- Возможность скрыть helper, если payload ещё не подготовлен, не ломая сам шаблон строки.
- Программная и XML-конфигурация title/subtitle/help payload.

---

## 6. Открытые вопросы / Research items

1. **Scope первой волны**
   - **Вопрос:** включать ли в первую волну только settings fragments или сразу add/edit resource forms тоже.
   - **Решение:** foundation + pilot screen сейчас; формы идут следующей фазой этой же спеки.
   - **Статус:** Resolved.

2. **Положение helper-иконки**
   - **Вопрос:** сохранять ли старый rightmost-канон.
   - **Решение:** нет; helper переезжает вплотную к title по прямому запросу владельца.
   - **Статус:** Resolved.

3. **Обязательность tooltip для каждой строки**
   - **Вопрос:** блокировать ли миграцию строк без готового help-payload.
   - **Решение:** нет; слот helper остаётся частью шаблона, но иконка может быть временно скрыта до появления текста.
   - **Статус:** Resolved.

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Замена прямых `SwitchMaterial` на custom view ломает существующий ViewBinding-код | Средняя | Фрагменты не компилируются до адаптации биндингов | Начать с пилотного компактного фрагмента и сразу ввести row-level API |
| Массовая миграция экранов растянется и оставит смешанный визуал | Средняя | Часть UI останется ad-hoc дольше ожидаемого | Зафиксировать phased rollout и canonical rule для всех новых строк уже с первой фазы |
| Helper payload для ряда строк отсутствует | Высокая | Часть строк будет без видимой help-иконки | Разрешить временно скрытый helper при сохранении общего layout-контракта |
| Repo rules останутся в старом состоянии и начнут конфликтовать с кодом | Средняя | Авто-агенты и ревью будут требовать старый rightmost-формат | Обновить `docs/ARCHITECTURE.md`, `CLAUDE.md`, `.github/copilot-instructions.md` в первой фазе |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Это инфраструктурная унификация существующих toggle-элементов и улучшение консистентности UI, а не новая самостоятельная пользовательская функция.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Toggle row становится compound view**

- **Решение:** ручная XML-сборка заменяется единым переиспользуемым UI-компонентом.
- **Альтернативы:** `include`-шаблоны; продолжение ручной сборки с code-review discipline.
- **Почему:** `include` не закрывает проблему API и биндингов, а дисциплина без компонента уже не сработала.

**ADR-2: Helper располагается рядом с title**

- **Решение:** help-иконка встроена в верхнюю title-line и не прижимается к правому краю строки.
- **Альтернативы:** старый rightmost-формат.
- **Почему:** это прямое требование владельца и более устойчивое поведение на широких экранах.

**ADR-3: Миграция по фазам, не одним пакетом**

- **Решение:** сначала foundation + pilot, затем пакетные волны.
- **Альтернативы:** big-bang миграция всех toggle-строк.
- **Почему:** слишком много XML и биндингов; поэтапный rollout снижает риск регрессий.

---

## 10. Связи с другими спеками

- S0254, S0255, S0256 затрагивают настройки и общие UI-паттерны, но не блокируют эту спецификацию.
- Эта спека обновляет canonical toggle-row baseline, который будет использоваться будущими settings/form задачами.
- **Carve-out:** миграция `fragment_settings_general` и `fragment_settings_destinations` (+ их Kotlin-цепочки helper'ов и `OperationsSettingsFragment`) вынесена в [`S0259_settings-toggle-row-general-destinations.md`](S0259_settings-toggle-row-general-destinations.md). Причина — каскад в 4 helper-класса и пересечение с S0254 (revised settings refactor) делает миграцию небезопасной для текущего pipeline. S0259 ждёт финализации S0254.

---

## 11. Критерии готовности (strategic-level)

1. В проекте существует один общий reusable toggle-row component.
2. Repo rules и `docs/ARCHITECTURE.md` описывают helper рядом с title как новый canonical pattern.
3. Новая toggle-строка может быть добавлена без ручной сборки switch/title/help/subtitle из примитивов.
4. Pilot screen использует новый компонент и сохраняет исходное поведение переключателей.
5. Subtitle отображается под title единообразно, меньшим шрифтом.
6. Helper-иконка на migrated rows располагается рядом с title и открывает tooltip там, где payload задан.
7. Компонент доступен для keyboard / D-pad / mouse / touch.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0258` — создаст `PLAN/S0258_settings-toggle-row-template/` с фазами.

---

## Last Audit

**Date:** 2026-05-19
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 69 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 1

Static checks all green. Component `SettingsToggleRow` exists with the `view_settings_toggle_row.xml` layout and a `declare-styleable name="SettingsToggleRow"` entry. `BaseSettingsFragment` carries the `bindSwitch(row: SettingsToggleRow, ...)` overload at line 48. Six layouts migrated (documents/video/audio/images/other/playback) with `SwitchMaterial` count = 0 in each; landscape parity preserved. Repo canonical rules updated (`docs/ARCHITECTURE.md` Pattern A restructure, `.github/copilot-instructions.md` UI_TRIGGER_ROW constraint). No `Log.d` introduced in `ui/common/widget/`. Locale parity for new key `setting_show_pdf_thumbnails_desc` in EN/RU/UK. Dev log records 36 entries against S0258. Catalog scanned + rendered after the last code change. Zero `Timber.d("S0258:` tags (spec is not in `BlockNeedUserTest`).

§8 FEATURES is EXEMPT — strategic spec explicitly states "Без изменений в `docs/FEATURES.md`" since this is infrastructural unification, not a new user-visible capability.

§2 Goal 4 (phased migration of existing rows) is met via documents pilot + video/audio/images/other/playback rollout. The remaining general+destinations migration is **carved out to [`S0259_settings-toggle-row-general-destinations.md`](S0259_settings-toggle-row-general-destinations.md)** per §10, which is consistent with ADR-3 (phased rollout, not big-bang) and with the explicit non-goal "Полная миграция всех toggle-строк приложения в одном заходе".

### Manual / on-device

- [ ] Visual verification: migrated rows render with switch left / title+helper inline / subtitle below (sample one screen per fragment: documents, video, audio, images, other, playback).
- [ ] Helper-icon click opens the tooltip dialog on rows that declare `app:str_helpTitle` / `app:str_helpMessage`.
- [ ] Keyboard / D-pad focus traversal across migrated rows behaves identically to pre-migration switches (tap-anywhere toggles, helper button focusable separately).
