# Tactical Plan: S1052 - Move "Screenshot test" button into the General-tab debug section

**Ticket:** S1052
**Status:** Tactical
**Strategic spec:** `PLAN/S1052_move-screenshot-test-to-debug.md` (§5 approach + §3.3 owner-resolved decisions authoritative)
**Research:** `research/01__current-location-and-gate.md` (concrete anchors)

## Goal (RU)

Кнопка «Тест снимка экрана» (`btnTakeScreenshotNow`, S0559) переезжает из карточки краевых жестов на вкладке с папками-приёмниками в отладочную группу «Отладочные журналы и тестовые инструменты» на вкладке «Общие». Видимость становится составной: `BuildConfig.DEBUG` (уже гейт группы) И привязанный лаунчер захвата меню. Новых строк нет; поведение тапа сохраняется.

## Decisions (from strategic §3.3, do not re-litigate)

- Гейт типа сборки - `BuildConfig.DEBUG` (build-type, не флейвор-флаг; Rule 14 не нарушается).
- Флейвор-ось видимости остаётся за существующим `Set<MenuScreenshotLauncher>` (пусто везде кроме standard + noLegal).
- Место - внутри `containerDebugSettings` на вкладке «Общие», рядом с прочими отладочными инструментами.
- Строка переиспользуется: `settings_take_screenshot_now`.

## Key insight (why this is Tier-2)

`containerDebugSettings` уже гейтится `BuildConfig.DEBUG` на уровне группы (`GeneralSettingsViewSetupHelper` + `setupCollapsibleSections`). Кнопке достаточно ещё одной оси - наличия лаунчера. `GeneralSettingsFragment` - новый хост: инжектит `Set<MenuScreenshotLauncher>` и проводит тап, зеркаля старую логику `OperationsCaptureManager.setupScreenshotAction`. Поиск (`SettingsSearchCapabilityGate`) читает вёрстку статически, поэтому кнопке нужен явный per-row гейт `BuildConfig.DEBUG && launcher present`, иначе в релизе она «протекает» в результаты поиска (кнопка теряет контейнерный гейт `groupScreenGestures`).

## Phase overview

| Phase | Title | Status |
|-------|-------|--------|
| 01 | Relocate button; move wiring to General fragment; fix search gate | Done |

## Blockers

- None to implement (all decisions resolved from spec §3.3).
- **Device-verification (F3 terminal):** §11 criteria are visual (button in debug section on DEBUG, absent in gestures card, both orientations) + release-absence -> ticket lands `BlockNeedUserTest`; visibility validated on a DEBUG build, release-absence on a release/target variant.

## Completion gate

- `standard debug` compiles green (src/main change + test).
- `fkn` (noLegal Kotlin) green - launcher present on noLegal too.
- Unit test for the new search-gate branch.
- Rule 22 settings-doc-sync: button moves section -> regenerate manifest + reference + annotation.
- One `Timber.d("S1052: …")` probe at the relocated click entry (present only while BlockNeedUserTest).
- `docs/ALL_FEATURES.jsonl` - no ADD (debug-tool relocation, strategic §8 "Без изменений").
