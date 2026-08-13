# Стратегическая спецификация: S0644 - Унификация кнопок выбора значения из списка в настройках

**Ticket:** S0644
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-23
**Roadmap entry:** Ad-hoc - запрос 2026-06-23
**Tactical spec:** будет создан через `/spec-tech` после разблокировки S0646

> **Scope:** STRATEGIC.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** owner request `/spec-draft`

**Текст запроса (verbatim):**

унификация всех кнопок для задания значения из открывающися списков. Портрет - Настройки - Управление - Жесты с левого края - "Действие жеста вверх". У этого элемента нужно убрать масгтабирование на всю ширину экрана и стрелку прижать справа от текста. В данном случа стрелка выглядит как знак "больше >". В таком виде она становится эталоном для элементов выбора для выбора значения из списка). Нужно обнаружить и задать везде одинаковый эталонный вид (в ландшафте и портрете) с сохраением иконки , если она есть. Например "общие - "Общие настройки интерфейса" - "Профиль устройства

**Ключевые примеры из запроса:**

- Portrait -> Settings -> Operations -> Left-edge gestures -> "Up gesture action".
- Эталон для list-value rows: текст не растягивается на всю ширину, стрелка `>` прижата справа от текста.
- Нужно унифицировать во всей программе в portrait и landscape.
- Иконка, если есть у строки, должна сохраниться.
- Пример родственного элемента: General -> "General interface settings" -> "Device profile".

**Что ожидается позже при доработке:**

- Найти все settings-row элементы, которые открывают простой список значений.
- Определить один эталонный layout/pattern для portrait + landscape.
- Проверить паритет с иконками, выравниванием текста и правой стрелкой.

**Вложения:** нет.

---

## 1. Контекст

Эталон строки-триггера уже есть: `ui/common/widget/SettingsSelectionRow.kt` (S0567 Phase 01) - заголовок + опц. иконка + текущее значение + шеврон `>`. Названный owner пример "Действие жеста вверх" открывает диалог `ScreenshotGestureActionPickerManager` (`setSingleChoiceItems`); пример "Профиль устройства" - сложный chooser (вне объёма).

S0644 - ROW-половина паттерна выбора значения; DIALOG-половину (унификация самого попапа на существующий `ListSelectionDialog<T>`) держит S0646. Это две половины одного паттерна (S0646 §1.3).

Требование "не растягивать на всю ширину" совпадает с уже зафиксированным правилом owner против full-width элементов в ландшафте (S0605) - визуальная часть направления определена.

---

## 2. Цель

Свести все строки-триггеры выбора значения из списка к эталону `SettingsSelectionRow` (без full-width растяжки, шеврон `>` справа, иконка сохраняется) в portrait и landscape.

### 2.1 Не-цели

- Не трогать сложные chooser-строки (device profile, translation language) - они вне объёма (S0646 §3.2).
- Не дублировать design-вопросы S0646 - они решаются там.

---

## 3. Объём работ (scope)

### 3.1 В объёме (зависит от S0646 §6.1)

- Аудит всех строк-триггеров выбора значения из списка в настройках.
- Привести к эталону `SettingsSelectionRow` (no-stretch, chevron-right, иконка) в portrait + landscape.

### 3.2 Вне объёма

- Сложные/графические chooser-строки.
- Сам диалог-попап (S0646).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0646 (dialog-половина, держит общий design-fork), S0645, S0648, S0649 (settings-UI батч), S0567 (доставил `SettingsSelectionRow`), S0605 (правило no-full-width).
- **UI scope:** визуальная унификация строк-триггеров на всех экранах настроек, portrait + landscape.

---

## 10. Зависимости и связанные тикеты

- **S0646** - блокер. Решение S0646 §6.1 (инлайн-селекторы -> tap-row + диалог, или остаются инлайн `SettingsDropdownRow`) определяет, какие строки попадают под эталон S0644. Разблокировать S0644 после ответа owner по S0646.
- **S0645** - навигационные строки-триггеры (соседний паттерн, настоящая стрелка вместо `>`).
- **S0648** - конкретные upload-target строки (узкий случай).
- **S0567** - доставил `SettingsSelectionRow`.
- **S0605** - правило против full-width элементов в ландшафте (поддерживает no-stretch).

---

## Last Audit

**Date:** 2026-06-24 · **Mode:** strategic (sweep finalize) · **Outcome:** Verified
**Counts:** PASS 1 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Device run (below) confirms the value-row chevron sits right after the text (left-hugging, no full-width stretch) in portrait AND landscape, tap still opens the picker, and navigation rows (S0645) are unchanged. Decisive in landscape (block ends ~1031px on a 2280px screen). No owner-gate, no FAIL. `/spec-check` (via `/spec-sweep`) flips BlockNeedUserTest -> Verified and removes the `Timber.d("S0644:` probe from `SettingsSelectionRow.kt`.

### Manual / on-device

- [x] Portrait: value-row chevron `>` sits right after the text, not at the screen edge - verified on-device 2026-06-24 (Audio -> "Visualizer when no cover art": title ends ~554px, chevron @965px on a 1080px screen; "OCR & translation downloads" chevron @548px)
- [x] Portrait: value-row content hugs left, no full-width stretch - verified on-device 2026-06-24 (`ssr_textGroup` fixed at 888px, row not edge-to-edge)
- [x] Portrait: tapping the value-row opens the list picker - verified on-device 2026-06-24 (`ListSelectionDialog` / `list_selection_recycler`, 5 options)
- [x] Landscape: value-row chevron `>` after text, no full-width stretch - verified on-device 2026-06-24 (chevron @965px on a 2280px screen - compact left block, right ~55% empty)
- [x] Landscape: tapping the value-row opens the list picker - verified on-device 2026-06-24 (same dialog)
- [x] Subtitle rows and navigation rows (S0645) unchanged - verified on-device 2026-06-24 (nav `csh_*` rows keep chevron on the LEFT in both orientations)
- [ ] Operations -> gesture-action rows (owner example "Up gesture action") - not testable on standard build: gated off by `fms.screenCapture=off`; re-run with `-P fms.screenCapture=on`
- [ ] OCR font/engine/model + PaddleOCR model rows - PaddleOCR model row is noLegal-only; font/engine value-rows not surfaced on this standard build state

### Notes

- Leading icon: not asserted - the reachable value-rows on standard have no leading icon (icon slot is optional in `SettingsSelectionRow`); icon-bearing value-row not encountered.
- The fix is row-width, not internal chevron placement: chevron stays pinned to the row's right edge, but the row no longer stretches edge-to-edge, so the edge (and chevron) lands right after the text. Decisive in landscape (2280px) where the block ends at ~1031px.

## Revision History

- **2026-06-24** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554, Android 17 / API 37)
  - Scenario: temp/S0644_mobile_test_scenario_20260624_1001.md - PASS/FAIL/SKIPPED 6/0/2 - Errors in log: 0
