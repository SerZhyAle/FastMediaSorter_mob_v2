# S0915 - Тактический план: разгрузка MainActivity

**Status:** Tactical
**Strategic:** `PLAN/S0915_mainactivity-loc-extraction.md`
**Research:** `research/01__mainactivity-decomposition-map.md` (карта декомпозиции, номера строк - живые, файл стабилен)

## Цель

`MainActivity.kt` 1483 -> ~1342 LOC (перенос ~141 строки, ~158 запаса до потолка 1500) через чистые LOW-risk кандидаты #1-7 карты. Без новых классов, без имперфект-фитов, без касания player/stream-поверхности. Поведение неизменно (чистый перенос проводки).

## Phase overview

| Phase | Приёмник | Кандидаты | Removed | LOC после |
|-------|----------|-----------|:-------:|:---------:|
| 01 | `MainLayoutChromeManager` | #1-3 (focus-chain, insets, filter-warning) | 58 | 1425 |
| 02 | `MainEventHandler` + `KeyboardNavigationHandler` | #4-7 (showError/showInfo, gamepad-routing, delete-confirm) + удаление 3 dead-функций | 83 | 1342 |

## Blockers

- Нет. Файл стабилен (mtime ~23 ч), локи свободны, player/stream-поверхность обходится.

## Completion Gate

- `MainActivity.kt` < 1350 LOC.
- `.\a.ps1 fk` (standard debug compile) PASS после каждой фазы.
- detekt-гейт scoped на тронутые файлы PASS (`post-change.ps1 -ScopeToFile`).
- Статик-ревью: каждый перенос сохраняет порядок и семантику; ни один player/stream-символ не тронут.

## Deferred (вне тикета)

- Phase 3 карты (#8 version-toast, #9 widget-pin, #10 settings-return) - имперфект-фит/новый класс, нарушают §11.
- Phase 4 карты (#11-13, streams-coupled кластер) - единственный путь к `<=1200`, ревизия после мерджа S0936/S0937/S0938.
