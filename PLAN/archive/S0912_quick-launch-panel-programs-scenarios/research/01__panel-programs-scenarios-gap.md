# Research 01: Quick-launch panel vs "Programs and Scenarios" - architecture gap

**Спека:** S0912
**Дата:** 2026-07-03
**Метод:** codebase research (android-solution-researcher), read-only.

## Найденные подсистемы

**A. Панель быстрого запуска = "App Launch Panel"** (edge-gesture / QS tile / widget), фиксированная сетка из 15 слотов:

- `AppLaunchPanelTileType` (`domain/model/AppLaunchPanelTileType.kt`) - enum `OWN_APP, EXTERNAL_APP, INTERNAL_ROUTE, RESERVED`.
- `AppLaunchPanelRouteTarget` (`domain/model/panel/AppLaunchPanelRouteTarget.kt`) - sealed `Feature/Resource/OsShortcut`, кодируется в одну TEXT-колонку, без версии схемы.
- `InternalRouteCatalog` (`core/panel/InternalRouteCatalog.kt`) - статический реестр "наших функций", сегодня 5 записей: `calculator, game, ocr, streams, favorites`.
- `AppLaunchPanelRouteIntents` (`core/panel/AppLaunchPanelRouteIntents.kt`) - билдеры `Intent` на маршрут.
- `ResolvePanelRouteAvailabilityUseCase` (`domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt`) - ручной `when` по ключу маршрута, определяющий доступность.
- `LaunchAppLaunchPanelTileUseCase` - резолвит плитку в `Intent`, запускает через `@ApplicationContext` (не Activity).
- `InternalRoutePickerDialogFragment` (`ui/applaunchpanel/edit/...`) - диалог "добавить функцию FastMediaSorter", список = `InternalRouteCatalog.all()`.

**B. "Программы и сценарии"** - строка на главном экране + выпадающее меню "...", единый источник истины:

- `MainProgramsMenuCoordinator` (`ui/main/helpers/MainProgramsMenuCoordinator.kt`) - строит состав/порядок для меню и строки; `populate()` даёт 9 пунктов: Streams, панель быстрого запуска (сама по себе), быстрая камера, быстрый голос, калькулятор, OCR-перевод камеры, запись экрана, загрузка по ссылке, мини-игра.
- `MainProgramsPanelManager` - зеркалит тот же список в виде строки.
- `MainQuickCaptureMenuManager`, `MainLinkDownloadMenuManager`+`MainLinkDownloadManager`, `MainScreenRecordingMenuManager`+`MainScreenRecordingManager`, `MainMiniGameMenuManager` - отдельные обработчики пунктов.

## Ключевой разрыв

Сопоставление списков: в реестре панели (5 записей) отсутствуют ровно те 4 функции набора "Программы и сценарии", что вызываются из `MainActivity`-привязанных менеджеров без собственной автономной точки запуска на момент их добавления в меню: **быстрая камера, быстрый голос, запись экрана, загрузка по ссылке**. Это ровно тот набор, который просит добавить тикет.

## Готовность каждой функции к автономному вызову

Уже есть самодостаточная "точка входа без хоста" (созданная ранее для других вызывающих - edge-gesture dispatcher, home-screen widget):

- Запись экрана: `ScreenRecordingLaunchActivity` (`widget/ScreenRecordingLaunchActivity.kt`) - собственные лаунчеры разрешений, toggle-семантика. Готова к прямому подключению.
- Быстрый голос: `QuickAudioRecorderActivity` + `QuickAudioRecorderLaunchManager` (`widget/QuickAudioRecorderLaunchManager.kt`) - та же форма. Готова к прямому подключению.
- Быстрая камера: `CameraQuickCaptureActivity` + `CameraQuickCaptureLaunchManager` (`widget/CameraQuickCaptureLaunchManager.kt`) - та же форма, но `loadTarget()` требует таргет, привязанный к `appWidgetId` конкретного виджета; у плитки панели такого id нет. Нужен дефолт (`CameraCaptureTarget.CameraFolder`, уже существует как сентинел) вместо отдельного шага выбора цели.
- Загрузка по ссылке: `MainLinkDownloadManager.show()` (`ui/main/helpers/MainLinkDownloadManager.kt`) показывает `MaterialAlertDialogBuilder`, привязанный к окну Activity - **не** отдельная Activity. Автономной формы пока нет - нужна тонкая Activity-обёртка того же образца, что у трёх остальных.

## Прецедент: расширяемость предусмотрена заранее

Архивная стратегическая спека `temp/done/S0663_app-launch-panel-internal-routes.md` в §5.3 явно называет "quick audio recorder, camera-photo, continue reading, scheduled tasks" в числе будущих кандидатов того же реестра. ADR этой спеки (переиспользовать существующие точки входа; без миграции схемы; capability-абстракция вместо `BuildConfig.IS_*` в `src/main`) - обязательный прецедент и для S0912.

## Flavor-гейтинг

- Запись экрана: source-set-монтирование (`screenCaptureStandardEnabled` в `app_v2/build.gradle.kts`), включена в `standard` (при включённом свойстве) и `noLegal`; на `lite/photos/legacy` набор контроллеров пуст, экран самозавершается - как и сегодня в меню "Программы и сценарии".
- Streams: `SUPPORT_STREAMS` через `CapabilityAvailability` - true standard/legacy/noLegal, false lite/photos (не входит в 4 недостающие функции, уже есть в реестре).
- Остальные 3 недостающие функции без специфичного flavor-гейта.

## Риски

- Быстрая камера без дефолтной цели вне модели `appWidgetId` - решается сентинелом `CameraCaptureTarget.CameraFolder`.
- Загрузка по ссылке без готовой автономной формы - нужна новая тонкая Activity-обёртка по образцу трёх остальных.
- `ResolvePanelRouteAvailabilityUseCase.resolve()` - ручной `when`, тихо возвращает "недоступно" при забытой ветке (`domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt:47-55`).
- `InternalRouteCatalog` уже содержит "избранное" - не входит в набор "Программы и сценарии", не трогаем.
- Нет unit-тестов ни на один из затрагиваемых классов (кроме `MainProgramsPanelManagerTest`, покрывающего только `resolveVisibility`).

## Вывод по решённым вопросам (см. §6 стратегической спеки)

Решения приняты по принципу "исследовать и рекомендовать" (см. `.claude/agent-memory/android-rd-specialist/feedback_research_over_owner_question.md`) - все пять развилок закрываются best-practice-прецедентом самого проекта (переиспользование существующих паттернов, минимальный первый релиз), без необходимости уточнения у владельца.
