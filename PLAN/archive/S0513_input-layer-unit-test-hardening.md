# Draft: S0513 - Input-layer unit-test hardening (JVM/Robolectric)

**Status:** Archived
**Priority:** 45
**Date:** 2026-06-18
**Tier:** 2 - Easy (ad-hoc)
**Origin:** owner request 2026-06-18 (multi-device input improvements)

> **Scope:** Compact spec (Simple path). Review-mode: the enumerated targets are already covered by existing green tests authored after this draft.

---

## 0. Идея

Input-слой широкий (`TvKeyRouter`, `GamepadInputManager`, `KeyBindingManager`, `BaseActivity` focus-layer), но покрыт тестами частично: есть `TvKeyRouterTest`, `SettingsKeyboardNavigationManagerTest`, `KeyboardShortcutHandlerTest`, `FocusManagerTest`, и новый `FocusTargetResolverTest` (S0504). Не покрыты `GamepadInputManager` (dead-zone/repeat/маппинг), `KeyBindingManager` (per-surface резолв, конфликты), edge-cases `TvKeyRouter` (gamepad-source фильтр).

## Проблема

Multimodal-логику сейчас можно надёжно проверить только на устройстве; регрессии в маппинге/резолве проходят незаметно. Это мешает «фиксить в коде без устройства».

## Цель (RU)

Поднять JVM/Robolectric-покрытие input-слоя по образцу S0504: unit-тесты на `GamepadInputManager` (dead-zone, повтор, AXIS→action), `KeyBindingManager` (per-surface резолв, дефолты, конфликты), edge-cases `TvKeyRouter`. Сделать input-слой проверяемым без устройства.

## Acceptance criteria

1. `GamepadInputManager` покрыт JVM-тестами: dead-zone (выше/ниже), AXIS→action, per-surface dispatch; повтор - в `GamepadNavigationTranslatorTest`.
2. `KeyBindingManager` покрыт: override-резолв, default-резолв, unbound→null (precedence/конфликты разрешаются в `InputBindingRepository`, не в менеджере).
3. `TvKeyRouter` edge-cases покрыты, включая gamepad-source фильтр (владелец у `GamepadInputManager`).
4. Целевые `--tests` зелёные (per-class).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0504 (pure-helper test pattern), S0289 (multimodal parity), S0506/S0508 (input logic providers).

## Грубый объём

- Тесты `GamepadInputManagerTest`, `KeyBindingManagerTest`, дополнить `TvKeyRouterTest`.
- При необходимости — лёгкий рефактор для тестируемости (вынести чистую логику маппинга, как `FocusTargetResolver`).
- Не менять поведение — только покрытие (+ извлечение чистых функций).

## Верификация

- Целевые `--tests` зелёные (per-class XML отчёты; не весь suite — есть pre-existing failures). Сборка.

## Связь

- S0504 (паттерн чистого тестируемого helper'а), S0289, S0506/S0508 (поставщики новой логики под тесты).

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (Simple, review-mode)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 0

The draft predated the tests: the enumerated targets are already covered by existing green JVM tests (drift - tests authored after the inbox capture, spec never updated). No new code needed.

- [PASS §1] `app_v2/src/test/java/com/sza/fastmediasorter/core/input/GamepadInputManagerTest.kt` (5 @Test): dead-zone above/below, AXIS_Y→volume, BUTTON_A per-surface, BROWSER dispatch. Button-repeat lives in `GamepadNavigationTranslatorTest`.
- [PASS §2] `KeyBindingManagerTest.kt` (3 @Test): override-resolve, default-resolve, unbound→null. Conflict/precedence is resolved upstream in `InputBindingRepository.observeResolvedBindings()` (mocked here), not in the manager.
- [PASS §3] `TvKeyRouterTest.kt` (9 @Test): dpad/enter/back/media/hardware mappings + the gamepad-source filter that keeps `GamepadInputManager` owning gamepad events.
- [PASS §4] `gradlew :app_v2:testStandardDebugUnitTest --tests *GamepadInputManagerTest --tests *KeyBindingManagerTest --tests *TvKeyRouterTest --tests *GamepadNavigationTranslatorTest` -> BUILD SUCCESSFUL (all green).
- Test-only scope - no ALL_FEATURES capability. Zero `Timber.d("S0513:` tags.
