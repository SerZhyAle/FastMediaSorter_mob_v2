# S0808 research: architecture + reuse map (streams-panel collapse)

**Собрано:** 2026-07-01 (inline code-map). Read-only срез на момент реализации. S0808 - прямое зеркало S0807 (сворачивание панели программ) для панели трансляций.

## Затронутые файлы

- `app_v2/.../ui/main/helpers/MainStreamsPanelManager.kt` (@UnstableApi, ~164 LOC) - рендер панели трансляций; получает свёрнутое состояние + новый пункт меню.
- `app_v2/.../ui/main/helpers/StreamsPanelMenuActions.kt` - группа колбэков меню кнопки-входа. Collapse НЕ требует нового колбэка (логика внутри менеджера, как у S0807).
- `app_v2/.../ui/main/MainActivity.kt` - конструкция менеджера (~839), `setup()` (~876), `setVisible()` в `refreshPanels()` (~696). Добавить только параметр `settingsRepository = settingsRepository` (уже инъецирован, поле ~181).
- `app_v2/src/main/res/layout/view_main_streams_panel.xml` - один файл, без `-land` (ориентация через `R.bool.main_streams_panel_show_labels`).
- `AppSettings.kt` (~215) + `SettingsRepositoryImpl.kt` (ключ ~159, чтение ~441, запись ~652) - новый флаг рядом с `programsPanelCollapsed`.
- `app_v2/src/test/.../MainStreamsPanelManagerTest.kt` (NEW) - зеркало `MainProgramsPanelManagerTest`.

## Карта переиспользования (collapse-ядро S0807/S0781)

- Чистая `resolveVisibility(available, collapsed): Pair<Boolean,Boolean>` = `(available && !collapsed) to (available && collapsed)` - копия из `MainProgramsPanelManager`.
- Персист - один булев флаг DataStore (`booleanPreferencesKey("streams_panel_collapsed")`), read `?: false`, write в save-блоке. Backup/import/preset НЕ трогаются (`programsPanelCollapsed` там отсутствует - collapse-флаги намеренно вне backup).
- Полоска: `@color/main_resource_filter_strip_background` / `..._foreground`, значок `@drawable/ic_double_arrow_down`, `@dimen/text_size_small`, `@drawable/focus_button_background` (всё уже используется полоской программ).
- Строки: `streams_panel_collapse_action` = копия текста `programs_panel_collapse_action` (EN «Collapse panel» / RU «Свернуть панель» / UK «Згорнути панель»); `main_streams_panel_strip` = «трансляции..» по вербатиму (EN «Streams..» / RU «Трансляции..» / UK «Трансляції..»).

## Отличия от S0807 (программы)

- У трансляций нет отдельной ведущей header-кнопки «три точки» - меню висит на long-press кнопки-входа (`showEntryMenu`). Пункт «Свернуть панель» добавляется туда (Configure → Collapse → Hide → Disable).
- У программ есть `available` + `install()`; у трансляций видимость идёт через `setVisible(visible)`. Маршрутизирую `setVisible` через `available` + `applyVisibility()`; загрузку флага + тап полоски кладу в существующий `setup()` (без отдельного `install()`).
- Класс `@UnstableApi` (Media3) - тест `resolveVisibility` требует `@OptIn(UnstableApi::class)` (в отличие от чистого теста программ).

## Порядок пунктов меню кнопки-входа (после изменения)

Открыть → [Открыть в новом окне] → Настроить (S0780) → **Свернуть панель (S0808)** → Спрятать панель (S0782) → Отключить (S0779).

## Тест-прецедент

`MainProgramsPanelManagerTest` тестирует только чистую `resolveVisibility` (4 кейса: expanded / collapsed / unavailable / never-both). Зеркалирую в `MainStreamsPanelManagerTest` с `@OptIn(UnstableApi::class)`.
