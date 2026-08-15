# Research 01: Candidates for a "Main window interface" Settings group

**Спека:** S0911
**Дата:** 2026-07-03
**Метод:** codebase research (android-solution-researcher), read-only.

## Текущая таксономия настроек

`SettingsActivity` хостит `ViewPager2` с 4 статичными вкладками (`SettingsPagerAdapter`): General, Media, Playback, Operations (+ flavor-специфичные вкладки поверх, например Wear-синхронизация). Внутри каждой вкладки - раскрывающиеся секции (`CollapsibleSectionsManager`). General уже содержит секцию "Interface" (`headerInterface`), но она не специализирована под главное окно - в ней смешаны разнородные UI-настройки.

Специализированной "Интерфейс главного окна" секции/вкладки сегодня нет - настройки, реально влияющие на главный экран, разбросаны по темам (Operations > Other Features, Media > Streams, General > Interface).

## Именованный тоглер владельца

`showProgramsPanelInMainWindow` (`AppSettings.kt`, default false) - сегодня живёт в Operations > "Other Features" (`OperationsSettingsFragment`, строка `rowShowProgramsPanel`). Строки: `setting_show_programs_panel_title/_summary`. Доставлен спекой S0755 (`BlockNeedUserTest`).

## Кандидаты на перенос

1. **`showProgramsPanelInMainWindow`** - однозначный кандидат (явный пример владельца), переименовать в «Панель программ».
2. **`showStreamsPanelInMainWindow`** - тот же нейминг-паттерн ("Show X panel in main window"), сегодня в Media > Streams, вложен под мастер-тоглер `enableStreams` и flavor-гейт (`isStreamsAvailable()`). Перенос физического расположения строки в UI не должен потерять существующий гейт видимости/доступности - тоглер остаётся видимым/включаемым только когда Streams доступны и включены, независимо от того, в какой секции он визуально расположен.
3. **`resourceOpsInOverflowMenu`** - уже в General > Interface, уже помечен в коде как "UI-настройка главного окна, не браузера" - хороший кандидат на перегруппировку внутри той же вкладки.
4. **`enableFavorites`** - НЕ переносить как есть: это сквозной мастер-тоглер фичи «Избранное» (влияет и на Browse, не только на кнопку главного окна) - вне узкого фрейминга «размещение элемента на главном окне». Остаётся в General > Interface.
5. **`isResourceGridMode`** - нет соответствующей строки в настройках вообще (переключается только кнопкой на главном экране) - нечего переносить; вне объёма первой итерации.
6. Мастер-тоглеры фич (калькулятор, мини-игра, OCR) - их единственная поверхность - панель/меню главного окна, но сами тоглеры это переключатели ФУНКЦИЙ, а не настройки размещения UI - вне объёма.

## Архитектурное решение по размещению новой группы

Два варианта:

- **(A) Новая 5-я вкладка верхнего уровня** - требует правки ordinal-зависимого `SettingsSearchDestination` enum, `SettingsPagerAdapter`, новую разметку/фрагмент, записи в `SettingsSearchLayoutCatalog`/`SettingsSearchTabMapping`. Дорого, многофайлово.
- **(B) Новая секция внутри уже существующей вкладки** (General) - переиспользует существующую запись `SettingsSearchTabMapping` для General, не трогает enum/adapter. Дешевле, соответствует существующему паттерну (General уже содержит тематические секции).

Рекомендация: (B) - новая раскрываемая секция «Интерфейс главного окна» внутри вкладки General, рядом с существующей общей секцией "Interface".

## Синхронизация документации настроек (Rule 22)

- `docs/settings/settings-manifest.json` - автогенерируется тестом `SettingsManifestExportTest.kt` (Gradle-свойство `-Dsettings.manifest.generate=true`).
- `docs/settings/settings-annotations.json` - вручную поддерживаемый EN/RU/UK файл описаний, ключ - `android:id` строки; правка нужна только если id меняется или появляется новый id.
- `docs/SETTINGS_REFERENCE*.md` - автогенерируются `scripts/docs/render-settings-reference.ps1`.
- Composite-гейт: `scripts/quality/assert-settings-doc-sync.ps1` (в `post-change.ps1`).
- Практический вывод: при переносе строки с сохранением её `android:id` ручная правка `settings-annotations.json` не требуется - только регенерация манифеста/референса.

## Влияние на поиск по настройкам

`SettingsSearchLayoutCatalog` - список layout-ресурсов, сканируемых на строки (уже содержит `fragment_settings_general.xml`, `fragment_settings_destinations.xml`, `fragment_settings_streams.xml` - новых записей не нужно, если группа размещается внутри уже каталогизированных layout-файлов). `SettingsSearchTabMapping` мапит layout -> (вкладка, секция); при варианте (B) нужна только одна новая запись секции внутри существующей вкладки General, а не новая вкладка.

## Вывод по решённым вопросам (см. §6 стратегической спеки)

Решения о размещении новой группы (вариант B) и о составе переносимых элементов (3 подтверждённых, 1 явно исключён, 1 вне объёма) приняты по принципу "исследовать и рекомендовать" (см. `.claude/agent-memory/android-rd-specialist/feedback_research_over_owner_question.md`) - оба вопроса решаются best-practice-анализом стоимости и существующих паттернов проекта, без необходимости уточнения у владельца.
