# Спецификация: S0829 - `EXTRA_HIGHLIGHT_SETTING` объявлен, но не обрабатывается

**Ticket:** S0829
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-01
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-01 (parked during S0780 research)

**Симптом / evidence:**

- `SettingsActivity.EXTRA_HIGHLIGHT_SETTING` (`"extra_highlight_setting"`) и `HIGHLIGHT_EMBEDDED_GAME` объявлены, но нигде в `src/main/java` не читаются.
- Единственный, кто его ставит - `GameLaunchIntents.settingsGameToggle()` (`core/game/GameLaunchIntents.kt:13`), с намерением проскроллить Настройки к строке embedded-game toggle.
- В `SettingsActivity` нет обработчика этого extra: переход открывает таб Playback, но к нужной строке не скроллит/не подсвечивает.
- Найдено при исследовании S0780 (общий механизм deep-link в секцию настроек). S0780 ввёл `EXTRA_EXPAND_SECTION` для раскрытия группы; `EXTRA_HIGHLIGHT_SETTING` остаётся не реализован.

**Предлагаемое направление:** либо реализовать обработчик (`SettingsActivity` -> фрагмент -> `navigateToTarget(viewId)` + highlight), либо удалить мёртвый extra и переключить `settingsGameToggle()` на `EXTRA_EXPAND_SECTION`/прямой viewId.

---

## 1. Проблема

Deep-link из mini-game entry в Настройки не подсвечивает/не скроллит к целевой строке: extra объявлен, но не потребляется. Мёртвый код вводит в заблуждение и оставляет UX-намерение нереализованным. Хуже: `settingsGameToggle()` открывал **не тот таб** (`TAB_PLAYBACK`), тогда как toggle embedded-game живёт в табе Operations.

## 2. Цели

1. Deep-link открывает верный таб (Operations) и раскрывает + скроллит к группе Additional Programs, где находится toggle embedded-game.
2. Удалить мёртвые `EXTRA_HIGHLIGHT_SETTING` + `HIGHLIGHT_EMBEDDED_GAME`.

**Non-goals:**

- Новый механизм подсветки конкретной строки (highlight) - переиспользуем готовый S0780 `EXTRA_EXPAND_SECTION`.

## 3. Решение

Переключить `GameLaunchIntents.settingsGameToggle()` на `SettingsActivity.openProgramsSectionIntent()` (S0780: `TAB_OPERATIONS` + `EXTRA_EXPAND_SECTION=SECTION_ADDITIONAL_PROGRAMS`), потребляемый `OperationsSettingsFragment.checkAndExpandSectionFromIntent()`. Удалить оба мёртвых const.

## 4. Критерии готовности

1. `settingsGameToggle()` не ставит `TAB_PLAYBACK`/`EXTRA_HIGHLIGHT_SETTING`; использует `openProgramsSectionIntent`.
2. `EXTRA_HIGHLIGHT_SETTING` + `HIGHLIGHT_EMBEDDED_GAME` отсутствуют в `src` (кроме исторического комментария).
3. Проект компилируется.

## Last Audit

**2026-07-01 - статическая верификация (Simple-путь `/spec-all`).**

- `GameLaunchIntents.settingsGameToggle()` -> `SettingsActivity.openProgramsSectionIntent(context)`.
- Удалены `EXTRA_HIGHLIGHT_SETTING`, `HIGHLIGHT_EMBEDDED_GAME` из `SettingsActivity`; grep по `src` - остаточных ссылок нет (только исторический комментарий).
- Детерминированная корректность подтверждена по коду: `rowEmbeddedGame` вложен в `containerAdditionalPrograms` (layout portrait+land); `openProgramsSectionIntent` раскрывает `SECTION_ADDITIONAL_PROGRAMS`, а `OperationsSettingsFragment` его потребляет (header `setExpanded` + `requestRectangleOnScreen`). Механизм S0780 уже device-проверен для programs-panel «Configure».
- Компиляция: `compileStandardDebugKotlin` - BUILD SUCCESSFUL.
- Устройство не требуется (переиспользование проверенного детерминированного механизма).

Итог: критерии 1-3 выполнены. **Verified** статически.
