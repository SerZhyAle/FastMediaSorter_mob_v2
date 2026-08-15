# S0512 - Mouse interaction enhancements

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-18
**Tier:** 3 - Moderate (ad-hoc)
**Origin:** owner request 2026-06-18 (multi-device input improvements)

> **Scope:** Re-scoped after `/spec-all` research (2026-06-18). The raw Draft assumed XButton1/2 navigation and middle-click were missing; they already ship in S0289. This spec now tracks only the genuine gaps and the owner decisions gating them.

---

## 0. Идея (исходная, raw)

`MouseEventHandler` / `ActivityMouseDispatchHelper` уже дают primary-click, wheel-scroll и right-click context (S0289). Идея захвата: дополнить desktop/Chromebook/TV-trackpad сценарии - hover-подсветка, XButton1/2 back/forward, middle-click, drag-select.

## Что уже реализовано (S0289) - не делать заново

Research-проверка кода (`MouseEventHandler.kt`, `ActivityMouseDispatchHelper.kt`, `BaseActivity.kt`) показала, что бóльшая часть исходной «Проблемы» уже закрыта:

- **XButton1 = Back - готово глобально.** `BaseActivity.onMouseNavigateBack` -> `onBackPressedDispatcher.onBackPressed()`. Работает на любом `BaseActivity`-экране без доработок.
- **XButton2 = Forward - инфраструктура готова.** `BaseActivity.onMouseNavigateForward` - overridable hook, по умолчанию no-op («forward» осмыслен только на части экранов). `MouseEventHandler` уже диспетчит `BUTTON_FORWARD` и резолвит `CommandId.NEXT_FILE` через `KeyBindingManager`.
- **Middle-click - инфраструктура готова.** `MouseEventHandler` диспетчит `BUTTON_TERTIARY` -> `InputAction.ToggleFavourite`; `BaseActivity.onMouseMiddleClick` - overridable hook (по умолчанию no-op).
- **Hover enter/exit - колбэки есть.** `MouseEventHandler.onHoverEnter/onHoverExit` + `handleGenericMotionEvent(ACTION_HOVER_*)`. Но визуально никто не подсвечивает (callbacks не используются для highlight).
- **Right-click context - готово глобально.** `BaseActivity.onMouseContextClick` -> `performLongClick()`.

Подтверждение: ни один экран не переопределяет `onMouseNavigateForward/onMouseMiddleClick/onMouseNavigateBack/onMouseContextClick` - базовый контракт обслуживает всех.

## Реальные пробелы (остаток скоупа)

1. **Hover-highlight (визуал).** Колбэки `onHoverEnter/Exit` существуют, но нигде не красят наводимый элемент. Нужна визуальная разводка без кражи фокуса (focus-ring не навязывать - S0289 §2 goal 9). Затрагивает списки/адаптеры (Browse, Duplicates, возможно прочие).
2. **Drag-select (новое, 0 кода).** Ни `SelectionTracker`, ни band/rubber-select, ни drag-to-select не реализованы. Мульти-выбор протяжкой в списках Browse/Duplicates - самостоятельная UX-фича.

## Открытые вопросы владельцу (blocking)

Оба остаточных пункта - UI/UX-решения, не выводимые из кода (CLAUDE.md Rule 10, UI Ambiguity Gate). Нужны до реализации:

1. **Drag-select - строить ли и как?**
   - Стоит ли вообще делать drag-select при том, что 3/4 исходной идеи уже отгружены?
   - Если да: какие списки (Browse-grid, Duplicates-list, оба)?
   - Модель взаимодействия: rubber-band (рамка выделения мышью по пустому полю) vs press-and-drag-over-items (протяжка по элементам)?
   - Только мышь или также тач (press-drag после входа в режим мульти-выбора)?
2. **Hover-highlight - визуальная трактовка и поверхности.**
   - Стиль подсветки: достаточно ли штатного `?attr/selectableItemBackground` (даёт hovered-state на API 21+) или нужен явный кастомный фон?
   - На каких поверхностях включать (только мульти-выбор списки или все списки/кнопки)?

### 3.3 Owner inputs (Approval gate)

- **Drag-select scope:** build minimally - basic drag without full canonical `SelectionTracker` contract, integrated with the existing multi-select mode.
- **Target surfaces:** all existing multi-select lists (Browse-grid, Duplicates-list, and any other list already exposing a multi-select mode).
- **Interaction model:** mouse band-select over empty area plus finger press-drag while in multi-select mode.
- **Hover-highlight:** stock `?attr/selectableItemBackground` (hovered-state on API 21+); no custom hover background, `onHoverEnter/Exit` highlight not needed.
- **Related tickets:** S0289 (base input infra), S0510, S0511, S0519 (adjacent input improvements).

## Решения владельца (2026-06-18)

1. **Drag-select - делать, минимально.** Базовая протяжка без полного развёртывания канонического `SelectionTracker`-контракта; интеграция с уже существующим режимом мульти-выбора.
2. **Списки - все мульти-выбор поверхности.** Единый контракт выделения везде, где уже есть режим мульти-выбора (Browse-grid, Duplicates-list и прочие).
3. **Модель - mouse band + touch drag.** Рамка-выделение мышью по пустому полю плюс протяжка пальцем в режиме мульти-выбора.
4. **Hover-highlight - штатный `?attr/selectableItemBackground`.** Без кастомного фона; hovered-state бесплатно на API 21+. Кастомная подсветка через `onHoverEnter/Exit` не нужна.

## Решения реализации (2026-06-19, research-driven)

- **Hover list-mode фон.** `ListViewHolder.bind()` затирает `item_focus_selector` вызовом `root.setBackgroundColor(..)`. Решение: перенести selector в `android:foreground`, чередование строк (alternating-row) оставить на `android:background`. Grid/GridNoThumb не затронуты (используют `CardView.setCardBackgroundColor`).
- **Duplicates drag-select - только внутри группы.** Список - двухуровневый nested RecyclerView (группы -> файлы); cross-group протяжка архитектурно заблокирована. Drag-select работает внутри одной раскрытой группы.
- **Browse touch drag-select - неявный режим.** Вход в режим определяется по `selectedFiles.isNotEmpty()`; новый флаг `isMultiSelectMode` в `BrowseState` не добавляется.
- **Артефакт research:** `PLAN/S0512_mouse-interaction-enhancements/research/01__multiselect-and-mouse-infra.md`.
- **Запаркованные находки:** S0524 (dead `BrowseSelectionManager` stub), S0525 (`DuplicateGroupAdapter` full rebind).

## Рекомендации (research-driven, для ускорения решения)

- **Drag-select:** канонический путь - AndroidX `androidx.recyclerview:recyclerview-selection` (`SelectionTracker` + `ItemKeyProvider` + `ItemDetailsLookup` + `withSelectionPredicate`), который из коробки даёт mouse band-select и touch drag. Рекомендация: band-select для мыши + интеграция с уже существующим режимом мульти-выбора Browse/Duplicates. Требует ресёрча текущего multi-select контракта этих списков (как хранится выбор, есть ли ActionMode).
- **Hover-highlight:** дешевле всего - убедиться, что item-фоны используют `?attr/selectableItemBackground` (hovered-state бесплатно). Кастомная подсветка через `onHoverEnter/Exit` - только если штатного state недостаточно.

## Last Audit

- **2026-06-19** - `/spec-test-device` on emulator-5556 (standard debug v2.60.6191.257, Android emulator)
- Evidence: `temp/S0512_devtest/` (22 screenshots, 2 logcat captures, EVIDENCE_SUMMARY.txt)
- Test media registered as a local resource over `/storage/emulated/0/Download/FastMediaSorter_Test/DCIM` (21 files) and `DupBig` (10 identical files) for the Duplicates group.

Manual sub-check verdicts (mouse-source gating read from `DragSelectTouchListener.onInterceptTouchEvent`: empty-area band-select and hover tint both require `TOOL_TYPE_MOUSE` / `ACTION_HOVER`, which a stock emulator cannot inject - `adb`/mobile-mcp produce only `TOOL_TYPE_FINGER`):

1. Browse-grid mouse drag over empty area draws band-select rectangle - INCONCLUSIVE (mouse-source-gated, emulator-unverifiable). Expected: rubber-band rectangle on mouse drag over empty area. Actual: empty-area path is reached only when `getToolType(0)==TOOL_TYPE_MOUSE`; finger injection returns early at the `position==NO_POSITION && !isMouse` guard, so the behavior cannot be honestly driven on the emulator. Needs a mouse-capable host.
2. Browse, items selected, finger drag extends selection - PASS. Expected: drag over rows grows the selection. Actual: long-press selected 1 item, finger drag fired `S0512: Browse drag-select start at position=0` and the header count went 1 -> 2 selected. No crash.
3. Browse list+grid item under mouse shows hover tint, no focus-ring stolen - INCONCLUSIVE (mouse-hover-gated). Expected: `?attr/selectableItemBackground` hovered-state under the pointer. Actual: hover tint depends on `ACTION_HOVER` from a mouse source, not injectable on the emulator. Same listener/layout drives both list and grid, so grid adds no separately touch-verifiable behavior. Needs a mouse-capable host.
4. Duplicates mouse/touch drag within an expanded group selects file rows - PASS (touch path). Expected: drag inside one expanded group selects its file rows. Actual: scan found 1 group (10 files), group expanded, finger drag over the inner `rvFiles` fired `S0512: Duplicates within-group drag-select start at position=1`; additive range-select kept the dragged rows selected. No crash. Mouse-source band-select within the group remains emulator-unverifiable.
5. Duplicates hovered file row shows tint - INCONCLUSIVE (mouse-hover-gated). Expected: hovered file row tinted. Actual: same `ACTION_HOVER` limitation as sub-check 3. Needs a mouse-capable host.

Verdict: 2/5 PASS via touch (items 2, 4); 3/5 INCONCLUSIVE because they are mouse-source / hover gated and a stock emulator cannot inject `SOURCE_MOUSE` / `ACTION_HOVER` (items 1, 3, 5). No code regression observed; status left at `BlockNeedUserTest` - the three mouse-only sub-checks still need owner verification on a mouse-capable host. Probes retained.

## Revision History

- **2026-06-19** - by `/spec-test-device` (SM-S731B, device R5CY9070WNB, Android 16)
  - Scenario: temp/S0512_mobile_test_scenario_20260619_0039.md · PASS/SKIPPED 1/3 · log errors 0
  - Browse touch drag-select EXERCISED on-device (probe `S0512: Browse drag-select start` fired, no crash).
  - Mouse band-select + hover-highlight SKIPPED (no mouse on a phone) - owner manual test on a mouse-capable host required.
  - Duplicates within-group drag-select SKIPPED (no duplicate set on device).
  - Status kept `BlockNeedUserTest`: novel mouse/hover surfaces still need on-device owner verification; probes retained.

## Связь

- S0289 (multimodal input parity) - база `MouseEventHandler`/`ActivityMouseDispatchHelper`, уже покрывает XButton1/2 + middle-click + hover-колбэки.
- S0510 (global F1 input help), S0511 (dialog keyboard consistency), S0519 (per-surface binding) - смежные input-улучшения.
