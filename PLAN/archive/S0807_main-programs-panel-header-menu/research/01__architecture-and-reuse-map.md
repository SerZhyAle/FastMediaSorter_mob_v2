# S0807 research: architecture + reuse map (programs-panel header menu)

**Собрано:** 2026-07-01 (code-map subagent). Read-only срез на момент реализации.

## Затронутые файлы

- `app_v2/.../ui/main/helpers/MainProgramsPanelManager.kt` - рендер панели программ; владелец нового header-меню и свёрнутого состояния.
- `app_v2/.../ui/main/helpers/MainPanelItemActionsManager.kt` - запись флагов настроек панелей (Hide).
- `app_v2/.../ui/main/MainActivity.kt` - проводка панели, `refreshPanels()`, коллектор настроек, верхняя кнопка «три точки».
- `app_v2/.../ui/main/helpers/PanelItemContextMenu.kt` - общий билдер popup-меню `Action(@StringRes, () -> Unit)`.
- `app_v2/.../ui/settings/SettingsActivity.kt` - `openProgramsSectionIntent()` (уже существует, S0780).
- `app_v2/src/main/res/layout/view_main_programs_panel.xml` - разметка панели (нет `-land` варианта; ориентация через `R.bool.main_programs_panel_show_labels`).
- `AppSettings.kt` + `SettingsRepositoryImpl.kt` - флаги `showProgramsPanelInMainWindow` (S0755) + новый `programsPanelCollapsed`.

## Карта переиспользования (3 действия меню)

1. **Настроить панель** -> `SettingsActivity.openProgramsSectionIntent(context)` (S0780, уже в проводке как `onConfigure` в `MainProgramsPanelManager`). Ноль нового кода в настройках; блок = `SECTION_ADDITIONAL_PROGRAMS` на вкладке управления.
2. **Свернуть панель** -> паттерн S0781 (`MainResourceTabsCollapseManager`): чистая `resolveVisibility(available, collapsed)`, персист одного булева флага, своп `View.isVisible`. Цвета полоски `main_resource_filter_strip_background/foreground` (`colors.xml:242-243`), значок `ic_double_arrow_down`. Отличие от S0781: сворачивание запускается пунктом меню (не long-press), разворот - тапом по полоске.
3. **Скрыть панель** -> зеркало `MainPanelItemActionsManager.hideStreamsPanelFromPanel()` против `showProgramsPanelInMainWindow`. Коллектор `refreshPanels()` реагирует (`programsPanelChanged`), панель исчезает, `refreshMainWindowDropdownMenuVisibility()` возвращает верхнюю кнопку «три точки» (`!isProgramsPanelEnabled && itemCount > 0`).

## Ключевые точки кода

- `MainProgramsPanelManager` не имел ведущей header-кнопки; трейлинг `btnProgramsPanelOverflow` - иной концерн (overflow набора). Новое: ведущая `btnProgramsPanelMenu`.
- `refreshPanels()` (MainActivity ~692) - единственная точка пересчёта видимости панелей; `programsPanelManager.update(visible = isProgramsPanelEnabled, ..)`.
- Коллектор настроек (~1168-1210) детектит `programsPanelChanged`/`streamsPanelChanged` и дёргает `refreshPanels()` на каждом эмите настроек - запись флага round-trip'ит через DataStore обратно в UI без доп. проводки.
- `MainActivity` ~1467 LOC (у потолка 1500): новая логика - в помощнике панели; в активити только проводка (2 колбэка + `install()`).

## Решения по 4 открытым вопросам тактика

1. Отдельный класс `ProgramsPanelMenuActions` НЕ создавался - у менеджера уже поколбэчный стиль; добавлен один колбэк `onHidePanel` + инфраструктура (`settingsRepository`, `scope`). Configure переиспользует существующий `onConfigure`.
2. Флаг `programsPanelCollapsed: Boolean = false` рядом с `resourceTypeTabCollapsed`; DataStore-ключ `programs_panel_collapsed` (стиль `KEY_RESOURCE_TYPE_TAB_COLLAPSED`).
3. Свёрнутая полоска - внутри `view_main_programs_panel.xml` (сиблинг контента), своп менеджером; single-file, без `-land`.
4. Ведущая кнопка - первый ребёнок `programsPanelContent`, стиль/значок как у `btnProgramsPanelOverflow` (`ic_more_vert`, `main_panel_item_min_width`).

## Тест-прецедент

`MainResourceTabsCollapseManagerTest` тестирует только чистую `resolveVisibility`. Зеркалировано в `MainProgramsPanelManagerTest` (4 кейса: expanded / collapsed / unavailable / never-both).
</content>
