# Стратегическая спецификация: S0645 - Унификация кнопок перехода во внутренние окна настроек

**Ticket:** S0645
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-23
**Roadmap entry:** Ad-hoc - запрос 2026-06-23
**Tactical plan:** [`S0645_settings-navigation-trigger-unification/INDEX.md`](S0645_settings-navigation-trigger-unification/INDEX.md)

> **Scope:** STRATEGIC.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** owner request `/spec-draft`

**Текст запроса (verbatim):**

унификация всех кнопок для открытия другого окна настроек).
Портрет - Настройки - Медиа- "Перевод, оцифровка" - "Загрузка OCR или перевода". В этой кнопке есть текст наименования, текст подсказки и стрелка право , но не в виде треулгольника, а настоящая. У этой кнопки нужно убрать масгтабирование на всю ширину экрана и стрелку прижать справа от текста. В таком виде она становится эталоном для кнопок для перехода к внутренним настройкам или инжеерным активити (окнам). Нужно обнаружить и задать везде одинаковый эталонный вид (в ландшафте и портрете) с сохраением иконки , если она есть. Например, переход к диалогу Статистика или "Управление и клавиши". Такие нужно найти и унифицировать по всей программе.

**Ключевые примеры из запроса:**

- Portrait -> Settings -> Media -> "Translation, OCR" -> "Download OCR or translation".
- Эталон для navigation rows: заголовок + подсказка + настоящая стрелка вправо, стрелка прижата справа от текста.
- Текстовый блок не должен растягиваться на всю ширину экрана.
- Нужно унифицировать все переходы к внутренним settings screens / engineering activities.
- Иконка, если есть у строки, должна сохраниться.
- Примеры родственных переходов: Statistics dialog, "Controls and keys".

**Что ожидается позже при доработке:**

- Найти все settings-row элементы, которые открывают другой экран, activity, диалог или внутреннее окно настроек.
- Выделить единый эталонный layout/pattern для portrait + landscape.
- Проверить совместимость с subtitle/hint, иконками и правой стрелкой.

**Вложения:** нет.

---

## 1. Контекст

Строки-переходы реализованы разрозненно: `rowControlsKeybindings` (`OperationsSettingsFragment:380`, `setOnClickListener` -> `SettingsActivity.openKeybindingRemap`), переход к Statistics dialog, "Download OCR or translation" (OtherMedia) и др.

Канонического nav-row виджета нет:

- `ui/common/widget/SettingsSelectionRow.kt` - value-строка с шевроном `>` (эталон S0644), не настоящая стрелка.
- `ui/common/widget/ActionHelpRow.kt` (S0567/S0595 Phase 06) - button + help icon, для action-полос (GIF editor), не nav-переход с hint.

Owner отделяет nav-строки (настоящая стрелка `→`) от value-строк (шеврон `>`, S0644) - это сквозное глиф-различие батча. Требование no-stretch совпадает с правилом owner против full-width (S0605).

---

## 2. Цель

Свести все строки-переходы (открывают экран/activity/dialog) к единому эталону: заголовок + опц. hint + настоящая стрелка `→` справа, без full-width растяжки, иконка сохраняется, portrait + landscape.

### 2.1 Не-цели

- Не трогать value-строки выбора значения (S0644) - у них шеврон `>`.
- Не трогать action-полосы `ActionHelpRow`.

---

## 3. Объём работ (scope)

### 3.1 В объёме

- Аудит всех строк-переходов в настройках.
- Привести к эталону nav-row (no-stretch, `→` справа, hint, иконка) в portrait + landscape.
- Канонический виджет - `SettingsSelectionRow` в nav-режиме (переключаемый трейлинг-глиф `→`), не новый класс.

### 3.2 Вне объёма

- Value-строки (S0644), сложные chooser-строки, action-полосы.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0644 (value-строки, сквозное глиф-различие), S0646, S0648, S0649 (settings-UI батч), S0567/S0595 (доставили row-виджеты), S0605 (no-full-width).
- **UI scope:** визуальная унификация строк-переходов на всех экранах настроек, portrait + landscape.

---

## 6. Вопросы owner (решены 2026-06-23)

### 6.1 Канонический nav-row виджет

Готового nav-row виджета нет. Расширить `SettingsSelectionRow` режимом "navigation" (трейлинг-глиф `→` вместо `>`, опц. hint), или создать отдельный `SettingsNavigationRow`?
**Рекомендация:** расширить `SettingsSelectionRow` nav-режимом (один виджет, переключаемый трейлинг-глиф) - меньше дублирования; альтернатива годится, если value/nav семантика должна быть жёстко раздельной.
**Решение (owner, 2026-06-23):** Resolved - расширить `SettingsSelectionRow` nav-режимом (переключаемый трейлинг-глиф `>` -> `→`); общий layout, иконка, hint и inline/no-stretch переиспользуются, новый класс не создаётся.

### 6.2 Глиф-различие nav vs value (сквозное с S0644)

Подтвердить: nav-строки несут настоящую стрелку `→`, value-строки - шеврон `>` (S0644). Это одно сквозное правило для всего батча.
**Рекомендация:** да, зафиксировать различие `→` (nav) / `>` (value) как единое правило S0644+S0645.
**Решение (owner, 2026-06-23):** Resolved - единое сквозное правило батча: `→` = nav (переход к экрану/activity/диалогу), `>` = value (выбор значения); правило привязывает и S0644.

---

## 10. Зависимости и связанные тикеты

- **S0644** - value-строки; делит сквозное глиф-различие (`→` nav / `>` value).
- **S0646** - диалог выбора значения (другой паттерн).
- **S0648 / S0649** - settings-UI батч.
- **S0567 / S0595** - доставили `SettingsSelectionRow`, `ActionHelpRow`.
- **S0605** - правило против full-width элементов.

---

### Quiz decisions (2026-06-23)

- Канонический nav-row виджет → Расширить `SettingsSelectionRow` nav-режимом (трейлинг-глиф `ssr_chevron` уже `ImageView`, inline/no-stretch и icon/hint уже есть - дублирования не требуется).
- Глиф-различие nav vs value → Да, единое сквозное правило S0644+S0645: `→` = nav, `>` = value (привязывает S0644).

---

## Last Audit

### Manual / on-device

Device run 2026-06-24 (`/spec-test-device`, claude-opus-4-8[1m], emulator-5554 Android 17/API 37, standard debug v2.60.6241.447). Each of the four nav rows verified in BOTH portrait and landscape: real forward arrow `→` (not chevron `>`), content hugs the left with arrow right after the text (no full-width stretch), leading icon and hint preserved, tap opens the target.

- [x] Row #1 Other media -> "OCR & translation downloads" (`layoutExtensionsManager`): arrow, no-stretch, no leading icon (by design), hint; opens Extensions Manager - verified on-device 2026-06-24 (portrait + landscape)
- [x] Row #2 Operations -> "Controls & Keybindings" (`rowControlsKeybindings`, in MaterialCardView): arrow, no-stretch, hint; opens keybinding remap - verified on-device 2026-06-24 (portrait + landscape)
- [x] Row #3 General -> "Saved authorizations" (`row_saved_authorizations`): arrow, no-stretch, help icon preserved, hint; opens Saved authorizations screen - verified on-device 2026-06-24 (portrait + landscape)
- [x] Row #4 General -> "Statistics" (`rowOpenStatistics`, gated by Statistics-collection toggle): arrow, no-stretch, leading icon `ic_history` preserved; opens StatisticsActivity - verified on-device 2026-06-24 (portrait + landscape)

Evidence: `temp/S0645_mobile_test_scenario_20260624_1344.md`, screenshots in `temp/S0645_screens/`, logcat `temp/S0645_run_20260624_1344.log` (S0645 debug tags confirm nav-mode + tap handlers; no crashes).

## Revision History

- **2026-06-24** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 17/API 37)
  - Scenario: `temp/S0645_mobile_test_scenario_20260624_1344.md` · PASS/FAIL/SKIPPED 8/0/0 (4 rows x portrait+landscape) · Errors in log: 0
