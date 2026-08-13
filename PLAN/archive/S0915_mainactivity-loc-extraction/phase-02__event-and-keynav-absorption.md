# Phase 02 - MainEventHandler + KeyboardNavigationHandler absorption (#4-7)

**Status:** Done, reduced scope - only #4-5 (`showError`/`showInfo` -> `MainEventHandler`) выполнены. #6 (`routeBrowserGamepadAction`) и #7 (`showDeleteConfirmation`) + dead-code отложены: чтение живого кода показало плохой семантический фит для `KeyboardNavigationHandler` (gamepad/Activity-coupling, раздувание ctor) - не меняем дизайн помощника ради ~40 LOC. См. strategic Last Audit.
**Target:** `MainActivity.kt` 1425 -> 1342 (-83). Приёмники: `MainEventHandler.kt` (133), `KeyboardNavigationHandler.kt` (242). **Факт:** 1422 -> 1383 (только #4-5 + Rule 7 detekt-fixups).

## Files Touched

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainEventHandler.kt` (+showError/showInfo, +settingsRepository в ctor)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt` (+routing +delete-confirm, +ctor-параметры/@Suppress, -3 dead-функции)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` (-4 метода, обновить ctor-construction + call-sites)

## Steps

1. **#4-5 `showError()` + `showInfo()`** -> `MainEventHandler`. Тела MainActivity ~1279-1300 / ~1302-1319. Добавить `settingsRepository` в ctor `MainEventHandler` (диалог-vs-toast по настройке). Убрать ctor-параметры `onShowError`/`onShowInfo` (8 -> ~7 параметров). Обновить конструирование `MainEventHandler` в MainActivity (~1151-1161) и все внутренние вызовы `showError`/`showInfo` в handler на локальные методы.
   - Порядок: `MainEventHandler` строится local `val` в `observeData()` - показ ошибок/инфо идёт только реактивно через `handle()`, ordering ok.
   - Verification: `fun showError`/`fun showInfo` в `MainEventHandler` = 1 каждый; в MainActivity 0 объявлений; ctor `MainEventHandler` содержит `settingsRepository`.
2. **#6 `routeMainCommandId()` + `routeBrowserGamepadAction()`** -> `KeyboardNavigationHandler`. Тела MainActivity ~1354-1358 / ~1360-1386. Класс уже владеет command-dispatch. Новые ctor-зависимости передать как lambda/ref (currentFocus getter, onBackPressedDispatcher, tab-switch, reuse `onFilterClick` для Search). Ctor 9 -> ~11-12 параметров пересекает `LongParameterList.constructorThreshold:10` -> добавить `@Suppress("LongParameterList")` + KDoc-обоснование на ctor (прецедент `MainProgramsPanelManager`). Обновить call-sites `dispatchKeyEvent`/`onKeyDown` в MainActivity на делегаты handler.
   - Verification: 2 функции в handler; ctor несёт `@Suppress("LongParameterList")`; `.\a.ps1 fk` PASS (новая ctor-находка покрыта suppress).
3. **#7 `showDeleteConfirmation()`** -> `KeyboardNavigationHandler`. Тело MainActivity ~1321-1331. Уже есть `viewModel.deleteResource(resource)` (MainViewModel:500) + `context`. Оба call-site (~450 keyboardNav ctor lambda, ~926 resourceAdapter ctor lambda) строятся после конструирования handler (446) - ordering ok. Заменить `onDeleteConfirmation`-lambda на прямой `keyboardNavigationHandler.showDeleteConfirmation(resource)`.
   - Verification: `fun showDeleteConfirmation` в handler = 1; в MainActivity 0 объявлений.
4. **Dead-code cleanup (Rule 20)** - удалить 3 baselined `UnusedPrivateMember` функции в `KeyboardNavigationHandler.kt` (`navigateUp`, `navigateDown`, `scrollPage`, baseline 12018-12020). Baseline-строки станут stale-no-op (безвредны); опц. убрать их из `config/detekt/baseline-app_v2.xml` (тогда `gradlew --stop` перед detekt, чтобы демон не отдал stale-baseline).
   - Verification: `Grep "fun navigateUp|fun navigateDown|fun scrollPage" KeyboardNavigationHandler.kt` = 0.

## Constraints

- Detekt-clean-first: `@Suppress("LongParameterList")` только на ctor `KeyboardNavigationHandler` (не имеет baselined ctor-находки - коллизии нет; см. карту #6). Строки <=120; без новых числовых литералов.
- Player/stream-поверхность не тронута (#4-7 её не касаются - карта §4).
- Тела переносятся дословно; логика не меняется.

## Done Criteria

- [ ] `showError`/`showInfo` в `MainEventHandler`; `routeMainCommandId`/`routeBrowserGamepadAction`/`showDeleteConfirmation` в `KeyboardNavigationHandler`; 0 их объявлений в MainActivity.
- [ ] 3 dead-функции удалены.
- [ ] `.\a.ps1 fk` PASS; detekt scoped PASS.
- [ ] `MainActivity.kt` ~1342 LOC.
