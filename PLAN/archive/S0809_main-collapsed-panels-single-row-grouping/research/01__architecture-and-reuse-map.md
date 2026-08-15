# S0809 research: architecture + reuse map (collapsed-panels single-row grouping)

**Собрано:** 2026-07-01 (inline code-map). Read-only срез. Компоновка - Вариант A (чипы в общей строке), UI-clarify 2026-07-01.

## Три помощника сворачивания (идентичный паттерн)

- `MainResourceTabsCollapseManager` (фильтр, S0781): `collapsedStrip: View` param; `setCollapsed`/`applyVisibility`/focus-handoff/persist(`resourceTypeTabCollapsed`). Разворот = `collapsedStrip.setOnClickListener { expand() }`.
- `MainProgramsPanelManager` (S0807): `panel.programsPanelCollapsedStrip` (include binding); `install()`/`setCollapsed`/`applyVisibility`; persist `programsPanelCollapsed`.
- `MainStreamsPanelManager` (S0808): `panel.streamsPanelCollapsedStrip` (include binding); `setup()`/`setCollapsed`/`applyVisibility`; persist `streamsPanelCollapsed`.

Все три: свёрнутое представление = один `View`, показывается когда `available && collapsed`; тап разворачивает; фокус передаётся при своп-переходах. Меняется ТОЛЬКО куда указывает эта `View` (чип в общем ряду вместо полоски на месте).

## Текущее расположение полосок (activity_main, портрет)

```
[command bar]
<include mainProgramsPanel>   -> programsPanelCollapsedStrip (внутри)
<include mainStreamsPanel>    -> streamsPanelCollapsedStrip (внутри)
<TabLayout tabResourceTypes>
<LinearLayout resourceTabsCollapsedStrip>  (после вкладок)
[resource list]
```

Три варианта: `layout/`, `layout-land/`, `layout-w600dp/` (`activity_main.xml`). Полоски `match_parent`. `resourceTabsCollapsedStrip` строится напрямую в activity_main; `MainActivity` передаёт `binding.resourceTabsCollapsedStrip` в `MainResourceTabsCollapseManager` (~1384).

## Целевой дизайн (Вариант A)

- Новый общий ряд `mainCollapsedPanelsRow` (horizontal LinearLayout, wrap_content h, без фона/верт. отступов -> 0 высоты при пустоте) сразу после командной панели / перед `mainProgramsPanel`, в трёх вариантах.
- Три чипа-ребёнка: `chipProgramsCollapsed` / `chipStreamsCollapsed` / `chipFilterCollapsed`. Каждый - стиль текущей полоски (bg `main_resource_filter_strip_background`, fg `..._foreground`, `ic_double_arrow_down`, `text_size_small`, focus_button_background), но `wrap_content` + `marginEnd` + подпись из существующего строкового ключа:
  - programs: `@string/main_programs_panel_strip`
  - streams: `@string/main_streams_panel_strip`
  - filter: `@string/main_resource_type_filter_strip`
- In-place полоски удаляются из своих разметок.

## Переиспользование помощников (минимальная правка)

- Фильтр: 0 правок кода. `MainActivity` меняет `collapsedStrip = binding.resourceTabsCollapsedStrip` -> `binding.chipFilterCollapsed`.
- Программы/трансляции: добавить конструкторный параметр `collapsedChip: View`; заменить все `panel.programsPanelCollapsedStrip` / `panel.streamsPanelCollapsedStrip` на `collapsedChip`. Логика `applyVisibility`/`setCollapsed`/focus не меняется (то же `isVisible`/`hasFocus`/`requestFocus`).

## Видимость ряда

Ряд прозрачен, без вертикальных отступов и фона -> когда все три чипа `GONE`, высота ряда = 0 (места не занимает). Явная координация видимости ряда между тремя помощниками НЕ нужна. Цвет несёт каждый чип.

## Флаги (без изменений)

`resourceTypeTabCollapsed` / `programsPanelCollapsed` / `streamsPanelCollapsed` - как есть. Фильтр round-trip'ится в backup/import (`ImportSettingsUseCase`/`BackupMapper`/`BackupData`); программы/трансляции - нет. S0809 флаги не трогает.

## Затронутые файлы

- `res/layout/activity_main.xml` + `res/layout-land/activity_main.xml` + `res/layout-w600dp/activity_main.xml` - ряд + чипы; удалить `resourceTabsCollapsedStrip`.
- `res/layout/view_main_programs_panel.xml` - удалить `programsPanelCollapsedStrip`.
- `res/layout/view_main_streams_panel.xml` - удалить `streamsPanelCollapsedStrip`.
- `MainProgramsPanelManager.kt` / `MainStreamsPanelManager.kt` - параметр `collapsedChip`.
- `MainActivity.kt` - передать чипы; фильтр -> `binding.chipFilterCollapsed`.
