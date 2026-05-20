# S0258 — Дизайн решения

**Статус:** На реализацию
**Тип:** UI infrastructure + phased migration

## 1. Архитектурная идея

Вместо ручной сборки switch/title/subtitle/help в каждом XML вводится один общий compound view для toggle-строки. Компонент живёт в общем UI-слое `app_v2` и инкапсулирует:

- внутреннюю разметку строки;
- canonical spacing/typography;
- optional help-button с `TooltipDialog`;
- API для checked/enabled/contentDescription/listener;
- optional trailing action slot для редких строк с дополнительной кнопкой.

## 2. Канонический layout компонента

- корневой horizontal row;
- switch слева;
- по центру weighted text block;
- внутри text block:
  - верхняя horizontal line: title + helper icon;
  - нижняя line: subtitle;
- справа optional action slot для специальных случаев типа clear/reset.

Так helper остаётся рядом с названием, а не «уплывает» на широких экранах.

## 3. Контракт компонента

Компонент должен уметь:

- принимать title / subtitle / help-title / help-message из XML attrs и из Kotlin API;
- показывать/скрывать subtitle;
- показывать/скрывать helper по наличию payload;
- читать и менять checked-state;
- прокидывать `setOnCheckedChangeListener`;
- управлять enabled-state строки и внутреннего switch;
- задавать contentDescription для switch по title, если отдельное описание не передано;
- держать optional trailing action view.

## 4. Стратегия миграции

### Волна 1

- обновить canonical rules в `docs/ARCHITECTURE.md`, `CLAUDE.md`, `.github/copilot-instructions.md`;
- добавить `SettingsToggleRow`;
- перевести `DocumentsSettingsFragment` и его layout на новый компонент.

### Волна 2

- пакетно перевести settings fragments с наибольшим числом ручных toggle rows;
- сохранить existing IDs на уровне container-row, а логику биндинга перевести на API компонента.

### Волна 3

- перевести add/edit resource forms;
- закрыть оставшиеся ad-hoc toggle rows в `app_v2`.

## 5. Риски и митигация

- ViewBinding changes: при замене `SwitchMaterial` на custom view ломаются прямые обращения `binding.switchX`.
  - Митигация: пилотить на небольшом фрагменте, добавить helper-методы в `BaseSettingsFragment`.
- Missing help payload for existing rows.
  - Митигация: компонент допускает скрытый helper до появления текстов, но layout-структура остаётся единой.
- Landscape drift.
  - Митигация: каждую миграцию выполнять парой с `layout-land`, если он существует.

## 6. Проверка

- `DocumentsSettingsFragment` компилируется с новым компонентом.
- Pilot screen визуально следует новому шаблону.
- Tooltip existing row продолжает открываться.
- Internal repo rules больше не конфликтуют с новым canonical layout.
