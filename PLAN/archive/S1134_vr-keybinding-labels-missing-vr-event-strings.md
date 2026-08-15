# Спецификация (draft): S1134 - VR-кейбиндинги показывают сырое VR[type] (нет строк-меток)

**Ticket:** S1134
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-20
**Tier:** 1 - Trivial

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-20 (device-test VR-сценария, Quest 3 noLegal)

**Текст:**

Раздел "VR Only" в Settings -> Controls and Keybindings показывает значения как "VR[15]" и т.п. вместо человеческих названий. Пользователь: "все значения выглядят как VR VR[15] - что это такое?".

**Контекст:**

- Корень: `KeybindingRowLabelFormatter.formatVrEvent(type)` строит имя строкового ресурса `keybinding_input_vr_<type>`; если ресурса нет (id==0) - возвращает fallback `"VR[$type]"`. Строк `keybinding_input_vr_*` для VR-событий нет -> все VR-строки рисуются как `VR[<type>]`.
- Числа (15 и т.п.) = внутренний тип VR-события.
- Отдельно: раздел "VR Only" в кейбиндингах НЕ управляет нативным OpenXR-вводом в шлеме (xr_input.cpp зашит отдельно) - возможная UX-путаница, оценить при работе над тикетом.

## 1. Проблема

VR-строки в кейбиндингах рисуются как `VR[<type>]`, потому что нет ресурсов `keybinding_input_vr_<type>` и `formatVrEvent` (`KeybindingRowLabelFormatter.kt:118-122`) возвращает сырой фолбэк. Симптом шире, чем раздел "VR Only": `vr:N`-триггеры в дефолтах привязаны и к общим командам (`playback.pause_play`->vr:0, `system.exit`->vr:1, `navigation.*`, `audio.volume_*`, `view.zoom_reset`), так что `VR[N]` виден в 7 группах на ВСЕХ флейворах.

## 2. Результаты исследования (2026-07-20, read-only, android-solution-researcher)

- **Источник строк:** `assets/input/default_bindings.json` (25 `vr:N`-записей) -> `DefaultsMapLoader` -> `InputBindingRepository` -> `KeybindingRemapViewModel`. Не из живого XR-ввода.
- **Вердикт: VESTIGIAL** для VR-диспетчеризации. Ни один живой путь не строит `InputTrigger.VrEvent` и не резолвит его через `KeyBindingManager`. `fromXrInputEvent` (`InputTrigger.kt:86`) - без вызовов. Реальный OpenXR-рантайм шлёт 2 жеста хардкод-литералами `1`/`2` (`xr_session.cpp:1480,1486` -> `DiagnosticXrActivity.onNativeInputEvent`), полностью в обход системы кейбиндингов. Экран - рабочий CRUD над Room+JSON, но потребителя биндингов нет.
- **Нет канонического `XrInputEventType` enum** нигде (Kotlin/C++). Коды 0-23 (18 не используется) - произвольные уникальные int на команду в JSON, не семантическая таксономия VR-ввода. Коды 10-23 имеют осмысленные VR-жестовые команды (`vr.recenter`, `vr.swipe_*`, `vr.double_pinch`, `vr.zoom_*`); коды 0-9,16 привязаны к общим командам без жестовой идентичности.
- **Флейвор-скоуп:** раздел НЕ гейтится - виден на standard/lite/photos/legacy/vr/noLegal. Поле `flavor_gate:"vr_only"` в JSON мёртвое (Gson дропает, `DefaultsMapLoader` не читает). Даже на сборках без VR-рантайма (`src/vr/java` монтируется только в noLegal).
- **Тестов нет** у `KeybindingRowLabelFormatter`/`KeybindingRemapViewModel`/`DefaultsMapLoader`.

## 3. Развилка - решение владельца (BlockQuestions)

Минимальный label-фикс упирается в то, что для семантически-неопределённых кодов (0-9,16) нет правдивой человеческой метки без определения таксономии VR-ввода; а честный `VR[15]` лучше правдоподобного-но-произвольного текста. Нужно решение владельца:

1. **Направление фикса:** (a) добавить строки-метки `keybinding_input_vr_*` для всех кодов; (b) скрыть раздел/строки как вестигиальные; (c) заменить рефлексивный `getIdentifier` на явный `when(type)` (устраняет тихий пропуск ресурса + R8-риск) - можно комбинировать с (a). Рекомендация исследования: (a)+(c), отвергнуть (b) как несоразмерное (blast-radius шире VR_ONLY).
2. **Семантика кодов 0-9,16:** что писать в метках кодов без жестовой идентичности? Определить таксономию VR-ввода или оставить эти коды как есть?
3. **Флейвор-гейт:** развести ли мёртвый `flavor_gate:"vr_only"` (скрыть VR_ONLY на не-VR сборках) - продуктовое решение, кодом не определено.
4. **Wire-or-stub:** довести ли вестигиальный раздел до реального (подключить `fromXrInputEvent` в `DiagnosticXrActivity.onNativeInputEvent`) - отдельный крупный тикет, или держать remap-UI как forward-looking stub?

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1135 (дубли VR-кодов в тех же дефолтах)

## 4. Связанные

- **S1135** (Draft, parked) - дубли VR-кодов `vr:19`/`vr:20` в дефолтах; трогает тот же `default_bindings.json`, решить вместе если пойдёт правка JSON.
- Полная таблица кодов 0-23 -> команда -> группа: см. отчёт исследования (в транскрипте сессии 2026-07-20).

## 5. Решения (spec-quiz)

### Quiz decisions (2026-07-20)

- Направление фикса -> **Labels + чистый форматтер (a+c)**: добавить строки `keybinding_input_vr_*` (EN/RU/UK) для именованных жестовых кодов + заменить рефлексивный `getIdentifier` на явный `when(type)`. (Экран становится читаемым; убирает тихий пропуск ресурса и R8-риск; blast-radius по 7 группам закрыт разом.)
- Флейвор-видимость -> **Гейтить на VR-сборки**: развести мёртвое поле `flavor_gate:"vr_only"` так, чтобы VR-раздел кейбиндингов показывался только на vr/noLegal (где есть VR-рантайм). (Убирает путаницу на standard/lite/photos/legacy.)

### Разрешено без вопроса (архитектура/конвенция)

- Семантика кодов без жестовой идентичности (0-9, 16) -> **localized generic-фолбэк с параметром** (напр. `keybinding_input_vr_unknown` = "VR input %d"), по образцу существующих `keybinding_fmt_gamepad_unknown`/`keybinding_fmt_mouse_unknown`. Именованные жесты (10-23) получают явные строки; неопределённые коды - параметризованный generic, а не сырой `VR[$type]`. Определять новую таксономию VR-ввода не требуется.

### Осталось вне объёма (отдельные тикеты)

- Wire-or-stub (сделать раздел функциональным через `fromXrInputEvent`) - не входит в S1134; при желании - отдельный крупный тикет.
- Дубли `vr:19`/`vr:20` - **S1135**.

### Реализовано (2026-07-20)

1. `strings_input.xml` (EN/RU/UK): одна новая строка `keybinding_fmt_vr_unknown` = "VR %d" (по образцу sibling `keybinding_fmt_gamepad_unknown`). Именованные жесты **переиспользуют** уже существующие `keybinding_label_vr_*` (recenter/controller_ray/swipe_*/double_pinch) - новые per-code строки не заводились.
2. `KeybindingRowLabelFormatter.formatVrEvent`: явный `when(type)` -> для 7 подлинных input-жестов (коды 10,17,19-23) `R.string.keybinding_label_vr_*`; для всех прочих кодов (действия/общие команды, без input-идентичности) фолбэк `getString(R.string.keybinding_fmt_vr_unknown, type)` = "VR N". Рефлексивный `getIdentifier` удалён (устраняет тихий пропуск ресурса + R8-риск). Коды - именованные `const` в companion (detekt MagicNumber).
3. Флейвор-гейт: в `KeybindingRemapViewModel` инжектнут `VrMediaSectionContract` (тот же гейт, что у `MediaSettingsFragment`; Rule 14 - без BuildConfig-гардов в src/main); `buildRows` фильтрует группу `VR_ONLY` когда `!vrMediaSection.isAvailable` -> раздел скрыт на standard/lite/photos/legacy, виден на vr/noLegal.
4. `KeybindingRowLabelFormatterTest` (Robolectric, реальные ресурсы): именованные жесты -> gesture-метки; неопределённые коды -> "VR N"; фолбэк без сырых скобок.

### Осознанный объём

- Скрыт **раздел VR_ONLY** (vr.*-команды), как в ответе владельца ("VR-раздел"). `vr:N`-триггеры на общих командах (`pause_play` и т.п.) остаются видимы, но теперь как "VR N", не `VR[N]`. Полное удаление vr-триггеров с общих команд на не-VR сборках (развод самого `flavor_gate` в дефолтах) - потенциальный follow-up, вне объёма симптома.

## Last Audit

### 2026-07-20 (status: Verified)

- **formatVrEvent** покрыт `KeybindingRowLabelFormatterTest` (Robolectric, реальные ресурсы) - PASS: коды 10/17/19-23 -> gesture-метки (`VR Recenter`/`Controller Ray`/`Swipe Left/Right/Up/Down`/`Double Pinch`); коды 0/5/15/99 -> `VR 0`/`VR 5`/`VR 15`/`VR 99`; фолбэк без сырых скобок `[`. Тест проверяет ровно тот текст, что рендерится.
- **Компиляция** standard debug: PASS (форматтер + ViewModel + ресурсы). **String-audit** `keybinding_fmt_vr`: EN/RU/UK parity OK. **detekt** (scoped, 3 тронутых файла): 0 findings.
- **Флейвор-гейт** VR_ONLY: `KeybindingRemapViewModel.buildRows` фильтрует по `vrMediaSection.isAvailable` - тот же контракт, что `MediaSettingsFragment` (уже отгружен/проверен); false на standard/lite/photos/legacy, true на vr/noLegal. Rule 14 соблюдён (интерфейс, не BuildConfig-гард).
- **Device (emulator-5554, v2.60.7201.903-DEBUG):** свежий билд установлен и подтверждён (версия в About). Экран remap на standard зарыт; жестовые метки VR_ONLY по дизайну скрыты на standard (гейт) и видны только на vr/noLegal (Quest) - их визуал покрыт real-resource unit-тестом, отдельный Quest-просмотр был бы повтором. Общекомандные `VR N` эмулятор-верифицируемы (не догнал экран в рамках бюджета навигации).
- Rendering-путь (ViewModel -> adapter -> `formatter.format`) не менялся - изменён только возврат `format()` (unit-tested) + добавлен фильтр.
- Rule 22 (settings-manifest) не применяется: remap-экран не является setting-тумблером; document-registry - нет затронутых записей.
