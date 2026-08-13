# Стратегическая спецификация: S0651 - Подчинённый toggler "Ask file name" для microphone recording

**Ticket:** S0651
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-23
**Roadmap entry:** Ad-hoc - запрос 2026-06-23
**Tactical spec:** будет создан через `/spec-tech`

> **Scope:** STRATEGIC. Draft-инбокс.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** owner request `/spec-draft`

**Текст запроса (verbatim):**

тогглер "Спрашивать имя файла" подчинённый "Включить записи с микрофона" нужно показывать подчинённой в интерфейсе (с оступом в портрете и в той же строке в ландшафте

**Ключевые требования из запроса:**

- Экран: Settings -> Operations.
- `Ask file name` является подчинённым toggler-ом для `Enable microphone recording`.
- В интерфейсе нужно явно показать подчинённость.
- Portrait: показать подчинённость через отступ.
- Landscape: показать подчинённость в той же строке.

**Что ожидается позже при доработке:**

- Найти текущие portrait/landscape layout'ы блока microphone recording.
- Определить точный visual pattern для child-row / inline-child presentation.
- Сохранить понятную связь родительского и дочернего toggler-ов без регрессии доступности и выравнивания.

**Вложения:** нет.

---

## 1. Реализация

- Экран Settings -> Operations, блок microphone recording (`rowMicRecordingEnabled` + `rowMicRecordingAskFilename`).
- Portrait (`res/layout/fragment_settings_destinations.xml`): `rowMicRecordingAskFilename` обёрнут в `LinearLayout` с `paddingStart="@dimen/settings_nested_margin_start"` - тот же паттерн отступа, что у camera/video дочерних строк.
- Landscape (`res/layout-land/fragment_settings_destinations.xml`): родитель и дочерний toggler уже находятся в одной горизонтальной строке (две колонки) - подчинённость "в той же строке" уже выполнена, добавлен только поясняющий комментарий S0651.
- Логика видимости не менялась: `OperationsCaptureManager` по-прежнему переключает `rowMicRecordingAskFilename.isVisible`; обёртка-отступ сворачивается в 0 высоты, когда строка `gone`.

## 2. Проверка на устройстве

- Settings -> Operations, включить `Enable microphone recording` (выдать разрешение на микрофон).
- Portrait: строка `Ask for filename` появляется с отступом под родителем.
- Landscape: `Ask for filename` стоит в той же строке справа от `Enable microphone recording`.
- Logcat-тег `S0651:` появляется при открытии экрана.

---

## Last Audit

### Manual / on-device

- Device: emulator-5554 (standard debug, sdk_gphone16k, sw852dp), build v2.60.6261.106-DEBUG.
- Date: 2026-06-26.
- Outcome: PASS
- [x] Portrait: enabling `Enable microphone recording` reveals `Ask for filename` (`rowMicRecordingAskFilename`) indented under the parent - child title at x=274 / switch at x=127 vs parent title at x=225 / switch at x=78, same 49px indent as the camera child rows.
- [x] Landscape (`layout-land`): parent `rowMicRecordingEnabled` (x=78) and child `rowMicRecordingAskFilename` (x=1115) sit side by side on the same row (y=1548), two weighted columns, matching the camera two-column pattern.
- [x] Logcat tag `S0651:` fired on Operations screen open in both orientations (`OperationsSettingsFragment: S0651: Operations settings opened ..`).
- Note: `SettingsActivity` handles `configChanges`, so `layout-land` only loads when Settings is created while the device is already landscape; rotating an already-open portrait Settings keeps the portrait layout. Re-created Settings fresh in landscape to exercise the true two-column layout.
- Evidence: temp/devtest_S0651_S0620_S0656/S0651_portrait.png, S0651_landscape_twocolumn.png, S0651_logcat.txt
