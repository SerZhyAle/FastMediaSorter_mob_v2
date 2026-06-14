## Задача

- Исследовать предупреждения Google Play для релиза `2.60.6141.930`, связанные с Android 15 edge-to-edge и deprecated API системных баров.
- Подтвердить текущие точки входа edge-to-edge в `app_v2`.
- Внести минимально-рискованные правки для:
- корректного edge-to-edge поведения на Android 15+;
- backward-compatible поведения на Android 14 и ниже;
- снижения вероятности вызовов `Window.setStatusBarColor()` / `Window.setNavigationBarColor()` из библиотечного кода.

## Ограничения

- Без изменения пользовательских сценариев и размещения элементов UI.
- Без добавления Activity-бизнес-логики.
- Проверка должна быть дешёвой и достаточной для config/resource правки.

## UI Clarification Status
Status: READY

### Approved Decisions
- Портрет/ландшафт: существующая компоновка экранов не меняется.
- Overflow/direct actions: новые кнопки, меню и точки входа не добавляются.
- Visibility rules: текущее поведение экранов сохраняется.
- Fallback/error behavior: меняется только обработка системных баров и библиотечных edge-to-edge веток.

### Delegated Assumptions
- Для BottomSheet на API 35+ допустимо включить автоматическую inset-защиту через тему без изменения содержимого самих sheet-экранов.
