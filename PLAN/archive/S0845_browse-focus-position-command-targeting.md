# Стратегическая спецификация: S0845 - Команды браузера действуют не на сфокусированный элемент (grid)

**Ticket:** S0845
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - parked by /spec-all during S0819 (2026-07-01)

> **Scope:** STRATEGIC skeleton (parked finding). Черновик, дорабатывается через `/spec` или `/spec-quiz`.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-01 (parked during S0819 research)

**Симптом:** В браузере файлов команды с клавиатуры/пульта (delete/copy/move/rename/toggle-selection) действуют не на текущий сфокусированный элемент, а на «первый видимый» - и на жёстко заданную позицию `0` в grid-режиме. После перемещения D-pad-фокуса команда срабатывает по неверному файлу. Баг становится особенно заметен вместе с S0819 (видимая «бегающая рамка» покажет фокус на одном элементе, а действие уйдёт на другой).

**Доказательства:**
- `ui/browse/managers/BrowseStateManager.kt:27-35` - `getCurrentFocusPosition()` возвращает `LinearLayoutManager.findFirstVisibleItemPosition()` для list-режима и захардкоженный `0` для всего остального (включая grid).
- Не читает реальный фокус View. Ожидаемо: `recyclerView.focusedChild -> getChildAdapterPosition()`.
- Нет теста на `BrowseStateManager.getCurrentFocusPosition()`.

**Связь:** S0819 (видимость фокуса) - этот баг ломает согласованность «вижу фокус здесь -> действие здесь». Делать желательно вместе или сразу после S0819.

---

## 1. Проблема

В браузере файлов команды с клавиатуры/пульта (delete/copy/move/rename/toggle-selection, контекстное меню, расширение выделения) таргетят «первый видимый» элемент, а не тот, что реально держит D-pad-фокус. `BrowseStateManager.getCurrentFocusPosition()` возвращает `findFirstVisibleItemPosition()` (и для list, и для grid: grid использует `GridLayoutManager` - подкласс `LinearLayoutManager`), никогда не читая `recyclerView.focusedChild`. После перемещения фокуса действие уходит на неверный файл; особенно заметно вместе с S0819 (видимая рамка на одном элементе, действие на другом).

## 2. Цели

1. `getCurrentFocusPosition()` возвращает adapter-позицию реально сфокусированного элемента (`recyclerView.focusedChild -> getChildAdapterPosition()`) во всех режимах (list/grid).
2. Fallback-цепочка при отсутствии фокуса: first-visible -> `0`; без регрессий для потребителей (перемещение фокуса, context-menu anchor, extend-selection).
3. Unit-тест на `getCurrentFocusPosition()` (focused / no-focus / NO_POSITION / no-layout-manager).

**Non-goals:** изменение самого action-каталога и модели жестов; S0819 (видимость рамки) - отдельный тикет.

## 3. Пожелания и ограничения

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0819

---

## Last Audit

**2026-07-01 - static + unit-test verification (Simple path).**

- Fix: `BrowseStateManager.getCurrentFocusPosition()` now reads `recyclerView.focusedChild -> getChildAdapterPosition()` first, falling back to `findFirstVisibleItemPosition()` then `0`. All browser command consumers (`KeyboardNavigationManager` dispatch/move-focus, `BrowseManagerInitializer` context-menu anchor + extend-selection) target the actually-focused item via this single method.
- Grid confirmed to use `GridLayoutManager` (a `LinearLayoutManager` subclass), so the fallback is valid in grid mode too; the former `else -> 0` branch was dead for browse.
- Test: `BrowseStateManagerFocusPositionTest` (4 cases: focused / no-focus / NO_POSITION / no-layout-manager). `:app_v2:testStandardDebugUnitTest --tests ...BrowseStateManagerFocusPositionTest` -> BUILD SUCCESSFUL (45s), all green.
- Device note: end-to-end remote/D-pad confirmation deferred (no device this run); logic fully covered by the unit test across every fallback branch.
