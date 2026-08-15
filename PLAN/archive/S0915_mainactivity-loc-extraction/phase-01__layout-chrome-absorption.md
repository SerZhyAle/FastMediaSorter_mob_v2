# Phase 01 - MainLayoutChromeManager absorption (#1-3)

**Status:** Done (MainActivity 1483 -> 1422; MainLayoutChromeManager 132 -> 196; `a.ps1 fk` PASS)
**Target:** `MainActivity.kt` 1483 -> 1425 (-58). Приёмник: `ui/main/helpers/MainLayoutChromeManager.kt` (132 LOC).

## Files Touched

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt` (+3 метода)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` (-3 метода, обновить call-sites)

## Steps

1. **#1 `restitchControlBarFocusChain()`** - перенести тело (MainActivity ~647-667) в `MainLayoutChromeManager` как `fun restitchControlBarFocusChain()`. Зависимость: только `binding` (уже в ctor). Заменить 4 call-site в MainActivity (~684, 887, 1106, 1230) на `layoutChrome.restitchControlBarFocusChain()`. Удалить приватный метод из MainActivity.
   - Verification: `Grep "fun restitchControlBarFocusChain" MainLayoutChromeManager.kt` = 1 hit; в MainActivity - 0 объявлений, только вызовы `layoutChrome.restitchControlBarFocusChain`.
2. **#2 `applyEdgeToEdgeInsets()`** - перенести тело (MainActivity ~1071-1081) в менеджер как `fun applyEdgeToEdgeInsets()`. Зависимость: `binding.rvResources`. Заменить call-site на `layoutChrome.applyEdgeToEdgeInsets()`. Порядок ok (layoutChrome построен в onCreate до setupViews).
   - Verification: метод в менеджере = 1; в MainActivity 0 объявлений.
3. **#3 `updateFilterWarning(state: MainState)`** - перенести тело (MainActivity ~1238-1263) в менеджер. Зависимости: import `MainState` + `R.string.filters_active` (строка уже есть). Заменить call-site (~1113) на `layoutChrome.updateFilterWarning(state)`.
   - Verification: метод в менеджере = 1; в MainActivity 0 объявлений.

## Constraints

- Detekt-clean-first: строки логов/кода <=120; без новых числовых литералов; не добавлять `@Suppress` к baselined-методам.
- Ни один player/stream-символ не тронут (методы #1-3 их не касаются - подтверждено картой §4).
- Порядок вызовов не меняется; тела переносятся дословно (только `this@MainActivity`/`binding` -> `activity`/`binding` менеджера при необходимости).

## Done Criteria

- [ ] 3 метода в `MainLayoutChromeManager`, 0 их объявлений в MainActivity.
- [ ] `.\a.ps1 fk` PASS.
- [ ] `MainActivity.kt` ~1425 LOC.
