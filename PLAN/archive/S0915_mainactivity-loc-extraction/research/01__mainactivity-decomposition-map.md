# Research: MainActivity LOC-extraction decomposition map (S0915)

**Дата:** 2026-07-04. **Режим:** read-only (subagent android-solution-researcher). Все номера строк - из прямого чтения живого файла; файл стабилен (mtime 2026-07-03 20:47, ~23 ч назад), поэтому диапазоны актуальны.

## Ключевая поправка по данным

`MainActivity.kt` = **1483 LOC** (`Get-Content.Count` = newline-count = 1483; каталог сообщает 1484 с учётом финального EOF). Это опровергает более раннюю заметку §0 спеки о «1330 LOC» - та цифра была артефактом ошибочного замера (`Get-Content | Measure-Object -Line` недосчитывает массив строк; верно `.Count`). Реальность: до потолка 1500 остаётся **17 строк**, острота исходной находки сохраняется. Кадрирование «запас восстановлен, проактивная гигиена» - неверно.

## 1. Текущая форма MainActivity (1483 LOC)

`MainActivity : BaseActivity<ActivityMainBinding>()`. Импорты 1-101, тело класса 103-1483. Три template-метода (`onCreate` 267-475, `setupViews` 758-1069, `observeData` 1083-1232) - **671 из 1483 строк (45%)**, каждый смешивает wiring/UI/intent/observer инлайн -> нужна экстракция на уровне диапазонов строк, а не целых функций. Класс уже baselined `LongMethod`+`LargeClass`/`TooManyFunctions` (`config/detekt/baseline-app_v2.xml:3307,3404-3406,11951`) - известный отслеживаемый oversize, не новая находка.

## 2. Инвентарь существующих менеджеров (кандидаты-цели)

35 файлов в `ui/main/helpers/`. Реальные цели-приёмники (конструируются внутри MainActivity):

- **`MainLayoutChromeManager` (132 LOC; ctor `activity, binding, isResourceGridMode`)** - лучший приёмник: владеет «layout chrome», минимальный detekt-баласт; `restitchControlBarFocusChain`/`applyEdgeToEdgeInsets`/`updateFilterWarning` не требуют новых зависимостей.
- **`MainEventHandler` (133 LOC; ctor 8 параметров, `internal class`)** - хорош для `showError`/`showInfo` (KDoc прямо: «extracted to keep host below LOC budget»); после переноса `onShowError`/`onShowInfo` ctor-параметры уходят (8 -> ~7).
- **`KeyboardNavigationHandler` (242 LOC; ctor 9 параметров)** - владеет command-dispatch (`dispatchCommandId`), подходит для gamepad-routing + delete-confirmation. **3 baselined dead private-функции** (`navigateUp`/`navigateDown`/`scrollPage`, baseline 12018-12020) - убрать в этой же правке (Rule 20).
- Прочие - узкие/single-purpose или player/stream-adjacent (исключены).

**Не тронуты в MainActivity вовсе** (out of scope, `MainViewModel`-side): `ResourceFilterManager`, `ResourceItemTouchCallback`, `ResourceOrderManager`, `ResourceNavigationCoordinator`, `ResourceScanCoordinator`.

## 3. Кандидаты на вынос (ранжировано; LOW-risk сначала)

| # | Строки | Блок | Приёмник | Removed LOC | Зависимости | Risk |
|---|---|---|---|---|---|---|
| 1 | 647-667 | `restitchControlBarFocusChain()` | `MainLayoutChromeManager` | 21 | только `binding`; 4 call-site (684,887,1106,1230) | LOW |
| 2 | 1071-1081 | `applyEdgeToEdgeInsets()` | `MainLayoutChromeManager` | 11 | `binding.rvResources`; порядок ok (layoutChrome построен в onCreate:369 до setupViews) | LOW |
| 3 | 1238-1263 | `updateFilterWarning(state)` | `MainLayoutChromeManager` | 26 | тип `MainState` + `R.string.filters_active`; call-site 1113 | LOW |
| 4 | 1279-1300 | `showError()` | `MainEventHandler` | 22 | `settingsRepository` в ctor; вызывается реактивно через `handle()` | LOW |
| 5 | 1302-1319 | `showInfo()` | `MainEventHandler` | 18 | как #4; вместе #4+#5 ctor-параметры 8 -> ~7 | LOW |
| 6 | 1354-1358, 1360-1386 | `routeMainCommandId()` + `routeBrowserGamepadAction()` | `KeyboardNavigationHandler` | 32 | новые lambda/ref (currentFocus, onBackPressedDispatcher, tab-switch); **ctor 9 -> ~11-12, пересекает `LongParameterList.constructorThreshold:10`** -> `@Suppress("LongParameterList")` + KDoc-обоснование (прецедент `MainProgramsPanelManager`/`MainStreamsPanelManager`); ctor этого класса не имеет baselined-находки, коллизии нет | LOW-MED |
| 7 | 1321-1331 | `showDeleteConfirmation()` | `KeyboardNavigationHandler` | 11 | `MediaResource`, `R.style.*Destructive`, `R.string.delete_resource_*`; уже есть `viewModel.deleteResource` (MainViewModel:500) + `context`; оба call-site (450,926) после конструирования handler (446) - порядок ok (в отличие от маршрута через `MainEventHandler`, чей local `val` строится позже) | LOW |
| 8 | 1046-1068 | App-version-check Toast | `MainChromeOsBannerManager` (имя не идеально) **или** новый мелкий класс | ~22 | `packageManager`, prefs, `R.string.app_updated_to`; чистого семантич. приёмника нет | LOW |
| 9 | 1429-1452 | `pinResourceLaunchWidget()` | чистого приёмника нет; новый stateless-класс (как `object PanelItemContextMenu`) **или** оставить | 24 | `resourceLaunchWidgetPinManager`, `R.string.widget_*` | LOW (нарушает §11 «no new classes») |
| 10 | 500-513, 515-517 | `routeToSettingsIfRequested()` + `isReturnToSettingsIntent()` | `MainStoragePermissionsHelper` (слабый фит) | 18 | `Intent`, `SettingsActivity`; call-site 318,480 | LOW (низший yield) |
| 11 | 669-739 (−#1, ~64) | dropdown-menu + panel-refresh кластер | `MainProgramsMenuCoordinator` (частично) | ~64 | `refreshPanels()` зовёт `streamsPanelManager.setVisible`; `currentProgramsMenuGate` читает `isStreamsEnabled` | **MED/HIGH - defer (streams)** |
| 12 | 1168-1231 | settings-reconciliation collector (11 флагов) | нужен новый/расширенный координатор | 64 | мутирует `isStreamsEnabled/*PanelEnabled`, зовёт `refreshPanels()`; zero test | **HIGH - defer (streams, shared-state)** |
| 13 | 762-883 | блок конструирования менеджеров в `setupViews` | LOC-нейтрально без реструктуризации (factory - новый паттерн) | ~122 (net ~0) | сильно порядко-зависимая цепочка (streams) | **MED - defer** |

### Бюджет LOC (старт 1483)

- #1-3 -> 1425
- +#4-5 -> 1385
- +#6-7 -> **1342**
- +#8 -> 1319; +#10 -> ~1301 (граница <=1300); +#9 -> 1277 (<=1300, но новый класс)
- <=1200 **недостижимо** только LOW-risk-кандидатами (#1-10 = 206 LOC); требует Phase 4 (#11-13, streams-coupled) - отложено.

## 4. Player/stream контактная поверхность (ОБХОДИТЬ - параллельная сессия S0936/S0937/S0938)

- **Импорты:** `AudioPlaybackService`(83), `PlayerActivity`(84), `StreamsActivity`(89), `ObservePinnedStreamSourcesUseCase`(50), `UnpinStreamSourceUseCase`(51), `FaviconAtlasStore`(37), `MainStreamsMenuManager`(78), `MainStreamsPanelManager`(79), `StreamsPanelMenuActions`(82), `MainResumePlaybackHelper`(76).
- **Поля:** `resumeHelper`(113), `streamsMenuManager`(119), `programsPanelManager`/`streamsPanelManager`(129-130), `isStreamsEnabled`/`isStreamsPanelEnabled`(142,149), `observePinnedStreamSources`/`faviconAtlasStore`/`networkContextAnalyzer`/`unpinStreamSource`(251,254,257,261), `getResumeStateUseCase`/`clearResumeStateUseCase`(191,194).
- **onCreate:** AudioPlaybackService->PlayerActivity redirect(335-353), resumeHelper(356-363,375-378), ACTION_START_SLIDESHOW/RANDOM_MUSIC(381-391), ACTION_RESUME_PLAYER(420-424). **onNewIntent:** 485-487.
- **Функции целиком:** `openAudioPlayerFromNotification()`(519-532), `restoreFocusToLastPlayedResource()`(741-756), `performFullExit()`/`stopAudioPlaybackService()`(1409-1427).
- **Состояние:** `recordLastPlayedResource()`/`lastPlayedResourceId`(616-617,642-645), onSave/RestoreInstanceState(627-640).
- **Menu/panel:** `refreshPanels()`(693-700), `currentProgramsMenuGate()`(707-717).
- **setupViews:** весь блок 762-883; `btnStartPlayer` listener(979-981). **observeData:** nav-progress->resumeHelper(1144), settings-collector toggling streams(1182-1229). **onLayoutConfigurationChanged:** 1275-1276.

**Вывод:** кандидаты #1-10 безопасны (ни один не ссылается на player/stream-символы). #11-13 - defer.

## 5. Фазировка

- **Phase 1** - `MainLayoutChromeManager` (#1-3, 58 LOC). 0 новых зависимостей, 0 изменения порядка, 0 player/stream. Checkpoint `a.ps1 fk`. -> 1425.
- **Phase 2** - `MainEventHandler` (#4-5, +`settingsRepository` в ctor) + `KeyboardNavigationHandler` (#6-7, +@Suppress LongParameterList, + удаление 3 dead-функций). 83 LOC. Checkpoint `a.ps1 fk`/`fc`. -> 1342.
- **Phase 3 (нужно решение §11 «no new classes»)** - #8 (version-toast) + #10 (settings-return) + #9 (widget-pin, новый класс). -> 1277 (<=1300). **Отложено в этом тикете** (имперфект-фит/новый класс).
- **Phase 4 - deferred, вне тикета** - #11-13 (streams-coupled). Ревизия только после мерджа S0936/S0937/S0938. Единственный путь к <=1200. Любой сдвиг lifecycle-порядка -> device smoke-test (§7 спеки).

## 6. Тесты

Нулевое покрытие `MainActivity.kt` (нет `MainActivityTest`). Есть тесты `MainStreamsPanelManagerTest`/`MainProgramsPanelManagerTest`/`MainResourceTabsCollapseManagerTest`/`ResourceFilterManagerTest`, но **нет** для 4 целей Phase 1-3 (`MainLayoutChromeManager`/`MainEventHandler`/`KeyboardNavigationHandler`/`MainChromeOsBannerManager`). Перенос логики - возможность добавить unit-тесты (additive scope).

## 7. Разрешение открытых вопросов (для /spec-all)

1. §0-заметку обновить: 1483, не 1330. -> сделано в стратегической спеке.
2. Цель: **этот тикет - чистые #1-7 -> ~1342 (~158 запаса), без новых классов и имперфект-фитов**. <=1300 требует #8/#10 (имперфект) или #9 (новый класс); <=1200 требует Phase 4 (streams). -> взят чистый вариант.
3. Новые классы vs имперфект-фит: §11 запрещает новые классы без обоснования; #8/#9 не имеют чистого приёмника -> оба отложены (Phase 3), тикет их не берёт.
4. Phase 4 (streams-coupled) - явно вне тактики этого тикета; ревизия после S0936/7/8.

## /spec-draft кандидаты

Нет. Единственная смежная находка (3 baselined dead-функции в `KeyboardNavigationHandler.kt`) - in-scope cleanup Phase 2 (Rule 20), не отдельный тикет.
