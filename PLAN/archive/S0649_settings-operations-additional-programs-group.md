# Стратегическая спецификация: S0649 - Новая группа "Additional programs and scenarios" в Operations settings

**Ticket:** S0649
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-23
**Roadmap entry:** Ad-hoc - запрос 2026-06-23
**Tactical plan:** `PLAN/S0649_settings-operations-additional-programs-group/INDEX.md`

> **Scope:** STRATEGIC.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** owner request `/spec-draft`

**Текст запроса (verbatim):**

В насройки - управления - добавить новую сворачиваемую группу "Дополнительные программы и сценарии"
Для наполнение меню "три точки" "прораммы" в основном окне 

Сюда мы переноси:м:
- настройку "Бастрый перевод с камеры" и подчинённую "Только распознавание"
- настройку "Калькулятор"
- настройку "Мини-игра". 

В ландшафте идут в две колонки.

**Ключевые требования из запроса:**

- Экран: Settings -> Operations.
- Добавить новую collapsible group: "Additional programs and scenarios".
- Назначение группы: наполнение меню "three dots" -> "Programs" в основном окне.
- Перенести в эту группу:
- "Fast camera translation" и подчинённую настройку "Recognition only".
- "Calculator".
- "Mini-game".
- Landscape layout: элементы группы должны идти в две колонки.

**Что ожидается позже при доработке:**

- Найти текущие местоположения всех перечисленных настроек в Operations screen.
- Определить точную структуру новой группы и поведение collapse/expand.
- Перенести строки без потери подчинённости "Recognition only" к "Fast camera translation".
- Продумать counterpart layout для portrait и two-column landscape.
- Проверить, как новая группа соотносится с future "Programs" menu population in the main screen.

**Вложения:** нет.

---

## 1. Контекст и текущее состояние

Экран Operations - `OperationsSettingsFragment` + `res/layout/fragment_settings_destinations.xml` (+ `res/layout-land/` counterpart). Сворачиваемые группы реализованы через `CollapsibleSectionsManager` + `CollapsibleSectionHeader` (S0535); каждая регистрируется в `setupCollapsibleSections()` парой header/container + строковый ключ, `defaultExpanded = false`.

Текущие места целевых настроек - все в группе **OtherFeatures** (`containerOtherFeatures`, header `settings_category_other_features`):

- "Fast camera translation" - `rowCameraOcrTranslationEnabled` (layout ~408).
- "Recognition only" (подчинённая) - `rowCameraOcrOnly` внутри `layoutCameraOcrOnly` (вложенный отступ; видимость завязана на `cameraOcrTranslationEnabled`, см. `observeData` + `applyFlavorRestrictions`).
- "Calculator" - `rowEnableCalculator` (layout ~644).
- "Mini-game" - `rowEmbeddedGame` (layout ~654).

Важно: `containerOtherFeatures` содержит ещё и блок Camera Photos (`rowCameraToResourceEnabled` + дочерние), который НЕ переносится. То есть OtherFeatures не опустеет - перенос выборочный, целевые строки чередуются с остающимися.

Подчинённость "Recognition only" уже реализована логикой видимости `layoutCameraOcrOnly` - при переносе она сохраняется автоматически, т.к. id строк не меняются.

---

## 2. Цель и предлагаемое решение

Вынести четыре строки (Fast camera translation + Recognition only, Calculator, Mini-game) из OtherFeatures в новую сворачиваемую группу "Additional programs and scenarios", сохранив поведение и подчинённость; в ландшафте элементы группы идут в две колонки.

Предлагаемое решение:

- Новая строка `settings_category_additional_programs` (EN/RU/UK) для заголовка группы.
- Новые `headerAdditionalPrograms` (`CollapsibleSectionHeader`) + `containerAdditionalPrograms` в portrait и landscape layouts.
- Перенести 4 строки (с вложенным `layoutCameraOcrOnly`) в новый контейнер; id сохранить - вся wiring/observe в `OperationsSettingsFragment` остаётся без изменений.
- Зарегистрировать секцию в `setupCollapsibleSections()` ключом `operations__additional_programs`.
- Регенерировать settings-манифест/референс/аннотации (Rule 22 - позиция настроек изменилась).

### 2.1 Не-цели

- Не трогать "Programs" three-dots menu в командной панели главного окна (см. §6.1) - оно уже существует; новая группа лишь поставляет ему настройки, само меню вне переноса.
- Не трогать остающийся в OtherFeatures блок Camera Photos.
- Не менять логику подчинённости/флейвор-гейтинга OCR-строк.

---

## 3. Объём работ (scope)

### 3.1 В объёме

- Новый заголовок группы (строки EN/RU/UK).
- Layout-правки portrait + landscape (two-column в land).
- Регистрация секции в `setupCollapsibleSections()`.
- Settings docs regen.

### 3.2 Вне объёма

- "Programs" menu в главном окне.
- Camera Photos блок и прочие строки OtherFeatures.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0644, S0645, S0646, S0648, S0650, S0651 (родственный settings-UI батч 2026-06-23).
- **UI scope:** новая сворачиваемая группа + перенос 4 строк на экране Settings -> Operations; новый two-column landscape паттерн (прецедента нет).
- **Flavor scope:** Fast camera translation / Recognition only видимы только при OCR+translation (`applyFlavorRestrictions`); Calculator/Mini-game - общие. Группа должна корректно схлопываться, если OCR-строки скрыты.

---

## 6. Вопросы owner (разрешены quiz 2026-06-23)

### 6.1 Связка с "Programs" меню главного окна

Owner называет назначение группы - наполнение three-dots "Programs" меню в главном окне. **Решение (quiz 2026-06-23):** S0649 ограничен переносом 4 строк. Меню "Programs" уже существует - это кнопка с тремя точками в командной панели главного окна; S0649 его не создаёт и не модифицирует, новая группа лишь поставляет ему настройки.

### 6.2 Механизм two-column landscape

**Решение (quiz 2026-06-23, research-resolved, owner не спрашивался):** две равновесные `LinearLayout`-колонки (`android:layout_weight="1"`) внутри `containerAdditionalPrograms` в `layout-land`. Левая колонка - Fast camera translation + вложенная Recognition only (цельный блок, не разрывать между колонками). Правая - Calculator + Mini-game. Portrait остаётся одной колонкой. Обоснование: механизм - чистая implementation-деталь (двухколоночные паттерны уже есть в `dialog_scheduled_operation`, `activity_calculator`, `activity_game`, welcome), а распределение пиннится вложенностью Recognition only под Fast camera translation.

### 6.3 Порядок новой группы среди существующих

Группы сейчас: Safety, CopyMove, Destinations, Scheduled, Behaviour, OtherFeatures, SystemApps, ScreenGestures. **Решение (quiz 2026-06-23):** разместить сразу после OtherFeatures (RU-метка "Камера, микрофон и прочий функционал", откуда уходят 4 строки). Итог: .. Behaviour -> OtherFeatures -> Additional programs -> SystemApps -> ScreenGestures. В `setupCollapsibleSections()` зарегистрировать между `headerOtherFeatures` и `headerSystemApps`.

---

## 10. Зависимости и связанные тикеты

- **S0644 / S0645 / S0646 / S0648 / S0650 / S0651** - родственный settings-UI батч 2026-06-23; S0649 не пересекается с ними по строкам, но делит общий стиль строк/групп.
- **S0535** - ввёл `CollapsibleSectionsManager`; S0649 переиспользует его без изменений.
- **"Programs" menu** - уже существует (кнопка с тремя точками в командной панели главного окна); новая группа поставляет ему настройки, S0649 само меню не трогает (§6.1).

---

## 11. Решения quiz (2026-06-23)

- **Scope coupling (§6.1)** -> только перенос настроек; меню "Programs" уже существует (три точки в командной панели), S0649 его не трогает.
- **Group order (§6.3)** -> сразу после OtherFeatures ("Камера, микрофон и прочий функционал").
- **Two-column mechanism (§6.2, research-resolved, owner не спрашивался)** -> две равновесные LinearLayout-колонки; слева Fast camera translation + вложенная Recognition only, справа Calculator + Mini-game.

---

## Revision History

- **2026-06-23** - by `/spec-test-device` (emulator-5554, tablet 1600x2560, standard debug)
  - Scenario: temp/S0649_mobile_test_scenario_20260623_2353.md · PASS/FAIL/INCONCLUSIVE 7/0/1 · log errors 0
  - Portrait: new group present + correct order, hosts OCR translation (+ nested Recognition only), Calculator, Mini-game; moved rows gone from Photo/Video/Voice-recorder; subordination + toggle verified; `S0649:` debug tag exercised; no crashes.
  - Landscape two-column INCONCLUSIVE on-device (settings screen portrait-locked on this AVD); verified-by-construction (layout-land two weighted columns, compiled).

---

## Last Audit

**Date:** 2026-06-23
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 14 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Verified: new `headerAdditionalPrograms`/`containerAdditionalPrograms` group present in portrait + landscape (ids unique, identical across orientations); four rows moved out of `containerOtherFeatures` (each id once); landscape rebuilt as two weighted columns with `layoutCameraOcrGroup`/`layoutCalculatorGameGroup` dissolved; section `operations__additional_programs` registered between Other-features and System-apps; `settings_category_additional_programs` present EN/RU/UK; settings manifest + reference regenerated, `headerAdditionalPrograms` annotated, settings-doc-sync gate green; standardDebug assembles. FEATURES EXEMPT (no §8 change - settings regrouping). Debug tag removed on this Verified flip.

### Manual / on-device

- [x] Portrait: group + order + contents + subordination + toggle verified on emulator-5554 (2026-06-23); `S0649:` tag exercised; no crashes.
- [ ] Landscape two-column: confirm on a device/AVD whose settings screen rotates (this AVD portrait-locks the settings screen); code-correct in `layout-land`.
