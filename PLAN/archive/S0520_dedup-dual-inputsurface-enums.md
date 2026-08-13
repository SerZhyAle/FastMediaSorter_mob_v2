# S0520 - Deduplicate the InputSurface enums

**Status:** Archived
**Priority:** 30
**Date:** 2026-06-18
**Tier:** 3 - Moderate (ad-hoc)
**Origin:** parked during S0509 research (gamepad button remap)

> **Scope:** Re-assessed by `/spec-all` research (2026-06-18). Premise valid (3 real enums). The core dedup carries an architecture fork entangled with S0519; this file records the finding + recommendation and defers the model decision to the owner.

---

## 0. Идея (исходная)

В проекте несколько enum'ов «surface» с пересекающейся семантикой - источник путаницы при импорте и резолве биндов.

## Research finding (2026-06-18) - подтверждено

Три surface-enum'а:
- `domain/input/InputBinding.kt:11` - `enum class InputSurface { PLAYER, BROWSER, DIALOG, VR }` (4). **Resolve-surface** - ключ `KeyBindingManager.resolve(trigger, surface)`.
- `ui/common/input/InputSurface.kt:13` - `enum class InputSurface { MAIN, BROWSE, PLAYER, SETTINGS, ADD_RESOURCE, .. }` (~12). **Screen-id** UI-слоя.
- `core/input/GamepadInputManager.kt:45` - `enum class Surface { PLAYER, BROWSER }` (2). Уже маппит на `DomainSurface.PLAYER`/резолв (стр. 54) - **чистый removable дубль**.

Наблюдения:
- Headline-баг (путаница импорта) - именно два **одноимённых** `InputSurface` (domain + ui). `GamepadInputManager.Surface` назван иначе (`Surface`) - не источник import-конфликта, просто избыточен.
- domain (4) и ui (12) - **разные концепты разных слоёв** (resolve-bucket vs screen-id), разной мощности. Слияние в один enum архитектурно неверно: сконфлюутит «какой экран» с «какой резолв-бакет».
- Часть UI-поверхностей не имеет domain-эквивалента (`MAIN`, `SETTINGS`, `ADD_RESOURCE` отсутствуют в 4-значном domain-наборе) -> маппинг UI->domain для них требует решения (резолвить в `DIALOG`? в новый domain-surface? не биндить вовсе?).

## Архитектурный форк + сцепление с S0519

- **merge vs disambiguate-rename:** конвенция Clean Architecture - не сливать разные концепты; держать оба типа раздельно, но **переименовать**, чтобы исключить wrong-import, + явный UI->domain маппинг.
- **Сцепление с S0519 (owner-gated):** S0519 - per-surface binding storage (Room schema column vs device/commandId namespace). Surface-enum это **ключ** этого хранилища; canonical-набор и маппинг no-equivalent поверхностей определяют key-space S0519. Решение по S0520 должно приниматься **когерентно** с S0519, иначе переделка.

## Recommendation (research-driven)

- НЕ сливать в один enum (разные концепты). Канонический resolve-surface - **доменный** `InputSurface`.
- Переименовать UI-enum для дизамбигуации (напр. `UiSurface`/`ScreenSurface`) + ввести явную `fun UiSurface.toResolveSurface(): InputSurface?` (null = поверхность не участвует в bind-резолве).
- Удалить `GamepadInputManager.Surface` -> использовать доменный `InputSurface` (PLAYER/BROWSER уже там).
- Выполнять как **один когерентный change вместе с S0519** (или сразу после фиксации его surface-key модели).

## Решение владельца (resolved 2026-06-19)

- Канонический resolve-surface = доменный `InputSurface`. Путь = rename UI-enum + explicit mapping, **не merge** (разные концепты слоёв).
- UI-поверхности без domain-эквивалента (`MAIN`/`SETTINGS`/`ADD_RESOURCE`) -> `toResolveSurface()` возвращает **null** (не участвуют в bind-резолве). Это задаёт минимальный key-space S0519 без фантомных domain-значений.

### Quiz decisions (2026-06-19)
- Канонический resolve-surface и путь дедупликации -> Domain canonical + rename UI-enum + explicit mapping (Clean Architecture: не сливать разные концепты слоёв).
- Маппинг no-equivalent UI-поверхностей -> null / не bind-резолвятся (минимальный key-space S0519, без фантомных domain-surface).

## Реализация (2026-06-19, NO-BUILD)

- UI-enum `ui/common/input/InputSurface` переименован в `UiSurface` (новый файл `UiSurface.kt`); доменный `InputSurface` оставлен каноническим resolve-surface.
- Введена `fun UiSurface.toResolveSurface(): InputSurface?` в `UiSurface.kt`: PLAYER->PLAYER, VR_PLAYER->VR, BROWSE->BROWSER, DIALOG->DIALOG.
- No-equivalent UI-поверхности (MAIN, SETTINGS, ADD_RESOURCE, CLOUD_PICKER, DUPLICATES, RESOURCE_EDITOR, RECEIVE_SHARE, WIDGET_CONFIG, WELCOME) -> `toResolveSurface()` возвращает null.
- `KeyboardShortcutHandler` резолвит player-поверхности через `surface.toResolveSurface()` (заменил ad-hoc `if VR_PLAYER .. else PLAYER`); удалён импорт `DomainSurface`.
- Удалён `GamepadInputManager.Surface`; `handleKeyEvent`/`handleMotionEvent`/`mapCommandToGamepadAction` принимают доменный `InputSurface`, non-player/browser -> null.
- Обновлены вызовы: `PlayerInputDispatcher`, `PlayerActivity` (удалено мёртвое поле `gamepadSurface`), `MainActivity`, `BrowseActivity`, тесты `GamepadInputManagerTest` / `KeyboardShortcutHandlerTest`.
- Поведение сохранено: на player-поверхностях резолв тот же (PLAYER/VR), gamepad-роутинг по-прежнему только PLAYER/BROWSER.
- Build не выполнялся (запрошен NO BUILD); требуется компиляция + smoke gamepad/keyboard на устройстве.

## Верификация (2026-06-19)

- `assembleStandardDebug` (`.\a.ps1 d`) BUILD SUCCESSFUL - NO-BUILD флаг снят, компиляция подтверждена машинно (APK v2.60.6191.257).
- `testStandardDebugUnitTest --tests *GamepadInputManagerTest --tests *KeyboardShortcutHandlerTest` BUILD SUCCESSFUL (зелёные) - резолв surface и gamepad-роутинг сохранены.
- Debug-тегов `Timber.d("S0520:` в `.kt` нет (статус не BlockNeedUserTest - инвариант соблюдён).
- Поведение-сохраняющий рефактор; device-smoke не требуется для Verified (owner: «только сборка + sanity»).

## Связь

- S0519 (per-surface binding storage - owner-gated; surface-enum это его ключ; решать когерентно).
- S0509 (gamepad remap - выигрывает от единого surface-понятия), S0289 (multimodal input parity).
