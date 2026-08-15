# Стратегическая спецификация: S0646 - Унификация простых диалогов выбора значения в настройках

**Ticket:** S0646
**Status:** Archived
**Priority:** 60
**Date:** 2026-06-23
**Roadmap entry:** Ad-hoc - запрос 2026-06-23
**Tactical plan:** `PLAN/S0646_settings-simple-list-dialog-unification/INDEX.md`

> **Scope:** STRATEGIC.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** owner request `/spec-draft`

**Текст запроса (verbatim):**

Унифицировать диалог выбор значения, там где это просто список. Есть сложние крсасивые диалоги выбора с логикой (например выбор профиля устройства)  или такие в которых логика и картинки (выбор языка перевода)- такие мы не трогаем. Но есть примитивный выбор, например "Визуализатор при отсутствия обложки" или "Размер шрифта для OCR" или "Параллелизм сети". Все они выполнены по разному по всей программе. Мне нравится минималистичный листинг дизайн . Нужно найти их все по программе и унифицировать

**Ключевые примеры из запроса:**

- Трогаем только примитивные list dialogs без сложной логики и без графических карточек.
- Не трогаем сложные/специальные диалоги вроде device profile chooser или translation language chooser.
- Примеры кандидатов: "Visualizer when cover art is absent", "OCR font size", "Network parallelism".
- Целевой стиль: minimalistic listing design.
- Нужно найти все такие места по всей программе и унифицировать.

**Что ожидается позже при доработке:**

- Отделить simple list dialogs от custom logic dialogs.
- Выявить все разрозненные реализации по приложению.
- Зафиксировать единый visual/interaction pattern для простого выбора значения.

**Вложения:** нет.

---

## 1. Контекст и проблема

В настройках выбор одного значения из короткого списка реализован минимум тремя разными механизмами, что и создаёт визуальную разнородность, на которую жалуется owner:

- ad-hoc `AlertDialog.Builder` / `MaterialAlertDialogBuilder` с `setItems` / `setSingleChoiceItems` (вызывается инлайн из хелперов);
- сырой `android.widget.Spinner` (OCR-вкладка, не мигрирован после S0567);
- сырой `AutoCompleteTextView` (визуализатор аудио).

Сводный список мест - в [research/01](S0646_settings-simple-list-dialog-unification/research/01__value-selection-dialog-inventory.md).

### 1.1 Уже существующий канонический компонент

S0567 Phase 04 уже доставил минималистичный list-диалог и не должен переизобретаться:

- `ui/dialog/ListSelectionDialog.kt` - `open class ListSelectionDialog<T>(context, config: ListSelectionConfig<T>)`. Ширина 85% экрана, высота по контенту, `RecyclerView` + темизированные item-вью, без рантайм-цветов; тап по строке выбирает и закрывает; `isSelected` рисует `ic_check` у текущего значения; `allowClear` показывает кнопку очистки.
- `ui/dialog/ListSelectionAdapter.kt` - `ListSelectionAdapter<T>` + `interface ItemFormatter<T>` (`getDisplayName`, `getIcon`).
- `res/layout/item_list_selection.xml`, `res/layout/dialog_list_selection.xml`.
- Уже мигрированы на него: `ResourcePickerDialog`, `DestinationPickerDialog`.

S0567 был заархивирован после фаз 01-04; остаток S0595 взял только фазы 05-07. Широкий проход "перевести все оставшиеся ad-hoc диалоги выбора значения на `ListSelectionDialog`" так и не был запланирован - это и есть содержание S0646.

### 1.2 Канонический trigger-row уже существует

`ui/common/widget/SettingsSelectionRow.kt` (S0567 Phase 01) - кликабельная строка с заголовком, опц. иконкой, текущим значением и шевроном `>`. Это эталон строки-триггера, который owner отдельно фиксирует в родственном тикете S0644.

### 1.3 Связанный батч одного дня

S0646 - часть батча настроечного UI от 2026-06-23. S0644 нормирует ROW-триггер list-значения, S0646 - сам ДИАЛОГ, который этот триггер открывает; это две половины одного паттерна (см. §10).

---

## 2. Цель и предлагаемое решение

Свести простой выбор одного значения из короткого списка к единому минималистичному паттерну: строка-триггер `SettingsSelectionRow` (эталон S0644), открывающая существующий `ListSelectionDialog<T>` (S0567 Phase 04). Это миграция разрозненных реализаций на уже готовый компонент, а не новый компонент.

Предлагаемое решение:

- Мигрировать ad-hoc диалоги (`setItems`/`setSingleChoiceItems`) на `ListSelectionDialog<T>` с подходящим `ItemFormatter`.
- Мигрировать сырые `Spinner` OCR-вкладки и `AutoCompleteTextView` визуализатора на тот же паттерн (триггер-строка + диалог), сохраняя их побочные эффекты (видимость зависимых строк, delivery-gate).
- Сохранить иконку строки, если она есть, и паритет portrait/landscape.

### 2.1 Не-цели

- Не переизобретать компонент - использовать существующий `ListSelectionDialog<T>`.
- Не трогать сложные/графические диалоги: device profile chooser, translation language chooser, cloud auth provider, resource-editor profile selector.
- Не откатывать уже консистентные инлайн-`SettingsDropdownRow` селекторы из S0567 (язык/тема/сортировка) - они не относятся к "выполнены по разному".

---

## 3. Объём работ (scope)

### 3.1 Кандидаты в объём (точный список - после §6)

- Ad-hoc диалоги: screenshot gesture action, add destination, destination picker, import method, widget type, document viewer type.
- Сырые селекторы: OCR font size, OCR font family, OCR engine type, PaddleOCR model, visualizer when cover art absent.

### 3.2 Вне объёма

- Device profile chooser, translation language chooser, cloud auth provider list, resource-editor profile selector (сложные/специальные).
- Инлайн-`SettingsDropdownRow` селекторы из S0567 (язык/тема/сортировка) - уже консистентны.
- Network parallelism - уже numeric `SettingsInputRow` (S0567 Phase 03), не список (см. §6.2).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0644, S0645, S0648 (родственный батч), S0567 (archived, доставил компонент), S0595 (archived, остаток S0567).
- **UI scope:** изменяется внешний вид и механизм выбора значения в нескольких группах настроек (Audio, Operations, Other/OCR, General). Решения по объёму и паттерну - в §6.
- **Flavor scope:** OCR engine/model строки только noLegal; gesture-action только standard+noLegal. Миграция не должна ломать no-op путь на остальных флейворах.

---

## 6. Решённые вопросы (разблокируют /spec-tech)

Все ниже разрешены: §6.1/6.3/6.4/6.5 - quiz-решением owner (2026-06-23), §6.2 - архитектурой (S0567 уже сделал поле numeric). /spec-tech планирует от этих решений.

### 6.1 Dialog vs inline для коротких списков

**Status: Resolved** - tap-row + `ListSelectionDialog` (следовать эталону S0644). Инлайн-селекторы (OCR font size, визуализатор) переводятся в строку-триггер `SettingsSelectionRow` + диалог ради единого вида со строкой-триггером. Это связывает S0646 с row-работой S0644 (см. §6.3).

### 6.2 Network parallelism - стейл-пример

**Status: Resolved** - исключён (архитектурно). S0567 Phase 03 уже перевёл "Параллелизм сети" в numeric `SettingsInputRow`; это не список, поэтому вне объёма унификации list-диалогов (зафиксировано в §3.2). Возврат к списку был бы регрессией намеренного решения S0567 - не делаем.

### 6.3 Sequencing с S0644

**Status: Resolved** - планировать совместно, без жёсткого блока. Для общих строк S0644 идёт первым (доставляет row-эталон), затем S0646 подключает диалог. S0646 НЕ помечается `BlockByOtherTask` - tactical-план S0646 учитывает порядок на уровне фаз.

### 6.4 Delivery-gate визуализатора

**Status: Resolved** - включить визуализатор (K) в миграцию, сохранив on-demand delivery-проверку в обёртке над `onSelected` (выбор VISUALIZATION запускает проверку доставки и откатывается при отказе). Полное покрытие одним тикетом.

### 6.5 Объём action-pickers

**Status: Resolved** - включить командные пикеры (import method, add destination, widget type, document viewer type). Они уже диалоги; миграция на `ListSelectionDialog` дешёвая и убирает appcompat-vs-Material разнобой. Объём §3.1 подтверждён полностью.

---

### Quiz decisions (2026-06-23)

- §6.1 Dialog vs inline -> tap-row + `ListSelectionDialog` (единый вид со строкой-триггером S0644; инлайн-селекторы мигрируют в tap-row + диалог).
- §6.3 Sequencing с S0644 -> совместно, S0644 первым для общих строк; без `BlockByOtherTask` (порядок на уровне фаз tactical-плана).
- §6.4 Delivery-gate визуализатора -> включить, gate в обёртке над `onSelected` (выбор откатывается при отказе доставки).
- §6.5 Action-pickers -> включить (дешёвая миграция уже-диалогов, убирает appcompat-vs-Material разнобой).
- §6.2 Network parallelism -> исключён без вопроса (архитектура: S0567 уже сделал поле numeric `SettingsInputRow`, не список).

---

## 10. Зависимости и связанные тикеты

- **S0644** settings-list-value-trigger-unification (Draft) - эталон ROW-триггера list-значения (`>` chevron, без растяжки, иконка). Две половины одного паттерна с S0646; для общих строк идёт первым.
- **S0645** settings-navigation-trigger-unification (Draft) - ROW-триггер перехода в другое окно. Соседний паттерн, не пересекается с диалогами выбора значения.
- **S0648** settings-upload-target-rows-unification (Draft) - две конкретные upload-target строки. Узкий row-визуал, координируется с S0644.
- **S0567** ui-settings-forms-dialogs-unification (Archived) - доставил `ListSelectionDialog<T>`, `ListSelectionAdapter<T>`, `SettingsSelectionRow`, `SettingsDropdownRow`, `SettingsInputRow`. Базис S0646.
- **S0595** forms-dialogs-unification-remainder (Archived) - остаток S0567 (фазы 05-07), не включал широкую миграцию диалогов.
