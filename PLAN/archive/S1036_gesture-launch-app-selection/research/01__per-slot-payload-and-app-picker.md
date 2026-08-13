# S1036 research 01 - пер-слотовое хранилище и пикер приложений уже в дереве

**Проведено:** 2026-08-09
**Повод:** §6 п.1-3 спеки S1036 и проверка §3.2 перед написанием тактического плана.
**Метод:** каталог классов (`dev/CATALOG/scripts/query.ps1`), чтение исходников целиком, `docs/FLAVOR_MATRIX.md`, `app_v2/build.gradle.kts`, `docs/settings/settings-manifest.json`, `values*/strings.xml`.

---

## 1. Главная находка: хранилище на слот уже существует

§5.1 столп 1 спеки требует завести двенадцать строковых значений на слот. Заводить нечего - они стоят.

- `domain/model/ScreenshotGestureSettings.kt` - двенадцать полей действий (`leftTopDown`..`rightBottomUp`, строки 25-36) **и двенадцать полей payload** (`payloadLeftTopDown`..`payloadRightBottomUp`, строки 40-51), по умолчанию пустая строка. KDoc строк 37-39 прямо называет этот тикет: payload value-agnostic и общий, он держит URL для действия открытия ссылки из S1038 и пакет приложения для выбора приложения из S1036.
- `domain/model/AppSettings.kt` - резолверы `screenshotGestureAction(zone, direction)` (463-487) и `screenshotGesturePayload(zone, direction)` (494-518); KDoc второго повторяет ту же формулировку.
- `data/repository/settings/ScreenshotSettingsStore.kt` - DataStore Preferences. Ключи действий `gesture_<zone>_<direction>`, ключи payload `gesture_payload_<zone>_<direction>`. Пустой payload не пишется, а удаляется (`setOrRemove`, 185-223), поэтому отсутствие ключа и пустая строка - одно и то же состояние.
- `app_v2/src/test/java/.../settings/ScreenshotSettingsStoreTest.kt` - round-trip payload уже покрыт двумя тестами, написанными под S1038 фазу 02.

**Следствие.** §6 п.1 закрыт не рекомендацией, а фактом: форма хранения выбрана и реализована, тикет её потребляет. Ни одной новой настройки, ни одного нового ключа, ни одной миграции.

## 2. Диспетчер: одна ветка, один запасной путь

`core/screencapture/ScreenshotGestureActionDispatcher.kt` (419 строк):

- Сегодня `OPEN_APP` (107-110) зовёт `launchApp(context)` (199-212), а тот всегда берёт `getLaunchIntentForPackage(context.packageName)` - то есть выводит на передний план сам FastMediaSorter.
- `OPEN_URL` (167-168) уже читает `payloadFor(zone, direction)` (194-197). Это готовый образец: `handlePreCaptureAction(context, action, zone, direction)` (100-104) уже получает и `Context`, и личность слота.
- `core/screencapture/gesture/LaunchActionHandler.kt:82-89` уже несёт `launchPackage(context, packageName, label)` с той же формой - `getLaunchIntentForPackage`, проверка на null, `FLAG_ACTIVITY_NEW_TASK`, `runCatching`. Тот же путь запускает и панель быстрого запуска (`domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt`).

**Следствие.** Ветка запуска - переиспользование, а не новый код запуска. Откат при пустом или неразрешимом пакете - вызов существующего `launchApp(context)`.

## 3. Пикер приложений переиспользуем, и вход для этого уже сделан

`ui/applaunchpanel/edit/AppPickerDialogFragment.kt` (157 строк):

- Две фабрики: `newInstance(slotIndex: Int)` - ключ панели по умолчанию; `newInstance(requestKey: String)` - **сделана ровно для чужих хостов** (KDoc 148-152: хосты вне редактора панели передают свой ключ, чтобы несколько хостов делили один FragmentManager и не получали чужие результаты). У второй перегрузки сегодня ноль вызовов.
- Результат возвращается через Fragment Result API: `setFragmentResult(requestKey, bundleOf(RESULT_SLOT to slotIndex, RESULT_PACKAGE to packageName))`. Константы `RESULT_KEY`, `RESULT_SLOT`, `RESULT_PACKAGE`, `TAG`.
- Перечисление приложений: `domain/usecase/panel/QueryLaunchableAppsUseCase.kt`, читает кэш `InstalledAppsRepository`, сортирует по подписи, работает на `Dispatchers.IO`.
- Видимость пакетов: блок `<queries>` с `MAIN` + `LAUNCHER` уже объявлен в общем `AndroidManifest.xml` (около строк 90-105) под S0623 и не привязан к флейвору. Новых записей тикету не нужно.

**Следствие.** §5.1 столп 2 - подключение, а не реализация. Открытый вопрос при тактике один и он технический: `EdgeGestureConfigDialogFragment` сам является `DialogFragment`, поэтому слушателя результата нужно ставить на тот же менеджер фрагментов, в котором показан пикер, и с собственным ключом запроса.

## 4. Куда встаёт строка выбора

`ui/settings/gesture/EdgeGestureConfigManager.kt` (461 строка):

- Вкладки зон - `setupTabs()` (286-307), показ блока зоны - `showZoneBlock`/`blockFor` (323-332).
- Каждая зона держит три `SettingsSelectionRow` - `bindZone` (172-191), `bindPicker` (193-199).
- `openActionPicker(zone, direction)` (201-219) после выбора действия пишет `applyAction`, а для `OPEN_URL` сразу зовёт `promptUrl(zone, direction)`. **Это точка подключения:** такая же ветка на `OPEN_APP` открывает пикер приложений.
- `applyPayload(settings, zone, direction, payload)` (418-456) уже пишет произвольную строку в payload нужного слота. Нового мутатора не нужно.
- `renderZone` (266-279) **не** показывает значение payload в подписи строки: выбранный URL у `OPEN_URL` пользователю тоже не виден. Это и есть настоящая недостающая часть - §3.2 требует текстовую подпись выбранного приложения, а §11 п.2 требует подпись и сброс рядом с направлением.

Раскладки диалога, проверено на диске: `res/layout/dialog_edge_gesture_config.xml` и `res/layout-land/dialog_edge_gesture_config.xml`. Вариантов `layout-sw*` у этого диалога нет. Идентификаторы строк направлений совпадают в обеих раскладках, и это состояние обязано сохраниться.

**Следствие.** §6 п.3 закрыт: инлайн-строка под направлением - не выбор из двух зол, а единственный вариант, удовлетворяющий §3.2 и §11 п.2. Путь `promptUrl` - временный диалог - им не удовлетворяет, потому что после закрытия не остаётся ничего видимого.

## 5. Судьба payload при смене действия

`applyAction` payload не трогает, и `OPEN_URL` ведёт себя так же: сменил действие - адрес сохранился. Рекомендация §6 п.2 совпадает с уже действующим поведением соседнего действия, поэтому вопрос закрыт не решением, а единообразием: очистка только явным сбросом.

## 6. Флейворный гейт: §3.2 спеки неточен

`docs/FLAVOR_MATRIX.md` о жестах не говорит вовсе, и это не пробел документа: он генерируется из блока `productFlavors` и видит только объявленные там поля `BuildConfig`, а жесты смонтированы условным подключением исходников и Variant API.

По `app_v2/build.gradle.kts`:

- `fms.screenCapture` (по умолчанию **on**) подключает `src/screenCapture/java` - только движок захвата.
- `fms.edgeGestureOverlay` (по умолчанию **off**) подключает `src/standardScreenCapture/java` - тот самый `ScreenGestureOverlayControllerImpl`, который наполняет мультибиндинг `Set<ScreenGestureOverlayController>` и открывает диалог настройки.
- `fms.edgeGestureTile` (по умолчанию **off**) - альтернативный вход через плитку быстрых настроек.
- `noLegal` подключает `src/screenCapture/java` безусловно и биндит собственный контроллер через `@IntoSet`, без всякого свойства.
- `lite`, `photos`, `legacy`, `vr` `src/screenCapture` не подключают вовсе.

**Следствие для проверки.** На обычной сборке `standard debug` без `-Pfms.*` краевой жест выключен на этапе компиляции, значит и диалог, и `OPEN_APP` недостижимы. Проверять на устройстве нужно `noLegal debug`, где возможность есть всегда, либо `standard` с `-Pfms.edgeGestureOverlay=on`. Формулировку §3.2 спеки, называющую гейтом `fms.screenCapture`, надо поправить - она называет не тот флаг.

Гейт в рантайме читается правильно и правило 14 не нарушено: диалог и менеджер смотрят на непустоту внедрённого `Set<ScreenGestureOverlayController>`, а не на поле `BuildConfig`.

## 7. Строки

- `screenshot_gesture_action_open_app` (values/strings.xml:2742) - «Open the main app window», и `gesture_action_explain_open_app` (2987) - «Opens the main app window.» Обе описывают сегодняшнее поведение и после тикета станут ложью, поэтому переписываются.
- `app_picker_title` (2757) - «Choose an app», переиспользуется как есть.
- Ключей под подпись выбранного приложения на слот, под сброс и под состояние «приложение не выбрано» нет - это новые строки, и §3.2 их уже требует в EN/RU/UK.
- Образец пары «заголовок и подсказка» для слотового ввода - `gesture_url_input_title` / `gesture_url_input_hint` (3033-3034).

## 8. Документация настроек

Диалог зарегистрирован в `ui/settings/search/SettingsDocScopeCatalog.kt` (44-49) со scope id `gestures`; в `docs/settings/settings-manifest.json` около двух десятков строк с `"sectionId": "gestures"`. Манифест собирается сканером по идентификаторам вью в раскладке, поэтому временные `AlertDialog` в него не попадают, а новая постоянная строка с `android:id` - попадёт и потребует перегенерации.

Команды: `pwsh -NoProfile -File scripts/quality/reindex-settings.ps1` (обёртка над генерацией манифеста тестом `SettingsManifestExportTest`, рендером `docs/SETTINGS_REFERENCE*.md` и проверкой `assert-settings-doc-sync.ps1`).

## 9. Тесты

- `ScreenshotSettingsStoreTest.kt` - payload на слот покрыт.
- `ScreenshotGestureActionCatalogIconTest.kt` - только полнота иконок.
- Тестов на `ScreenshotGestureActionDispatcher`, `EdgeGestureConfigManager`, `AppPickerDialogFragment`, `QueryLaunchableAppsUseCase` и `LaunchActionHandler` нет ни одного.

**Следствие.** Ветку диспетчера надо покрыть тестом в том же тикете: это единственная часть, которая меняет поведение молча и без экрана.
