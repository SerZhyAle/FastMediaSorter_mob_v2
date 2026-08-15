# Спецификация: S0830 - Overflow панели программ теряет per-item меню

**Ticket:** S0830
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-01
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-01 (parked during S0780 research)

**Симптом / evidence:**

- `MainProgramsPanelManager.showOverflowPopup()` (`ui/main/helpers/MainProgramsPanelManager.kt:148-160`) показывает не поместившиеся в строку элементы простым `PopupMenu.add()` с одним click-listener.
- Эти overflow-элементы НЕ проходят через `showItemMenu()`, поэтому для них недоступны "Открыть в новом окне", "Удалить", а после S0780 - и "Настроить".
- Видимые элементы получают полное per-item меню (long-press / three-dots), overflow - нет. UX-паритет нарушен.

**Предлагаемое направление:** для overflow-элементов открывать тот же `PanelItemContextMenu` (через `showItemMenu`) вместо плоского `PopupMenu`, либо строить вложенное меню.

---

## 1. Проблема

Элементы панели программ, ушедшие в overflow, не имеют полного контекстного меню (new-window / remove / configure), в отличие от видимых элементов. Поведение зависит от ширины экрана и числа элементов, что непредсказуемо для пользователя.

## 2. Цели

1. Overflow-элементы получают тот же per-item контекст (Open / Open-in-new-window / Configure / Remove), что и видимые.

**Non-goals:**

- Отказ от overflow в пользу горизонтального скролла (owner-решение S0755: фиксированный набор + overflow).

## 3. Решение

`showOverflowPopup()` строит overflow-список как anchored `PopupWindow` из тех же строк `item_main_program`, что и видимые элементы (single source of truth). Каждая строка получает те же жесты, что видимый элемент: короткий тап -> `onItemSelected(id)` (запуск), long-press и three-dots -> `showItemMenu` (per-item меню). Плоский `PopupMenu` для этого не годится - он даёт один click-жест на строку.

## 4. Критерии готовности

1. Короткий тап overflow-элемента запускает программу (паритет с видимым элементом), а не открывает меню.
2. Long-press или three-dots на overflow-строке открывают per-item меню; набор действий идентичен видимым (переиспользование `showItemMenu`).
3. Проект компилируется.

## Реализация (2026-07-01, Simple-путь `/spec-all`) - ПРОВАЛ device-теста

- `MainProgramsPanelManager.showOverflowPopup()`: click-listener резолвил `PanelItem` по id и вызывал `showItemMenu(model, anchor)` вместо `onItemSelected`.
- Device-тест: тап overflow-элемента открывал вложенное меню вместо запуска - пользователь терял возможность запустить программу («теряют способность быть запущенными», «что-то перепутано»). Паритет с видимыми элементами (у которых короткий тап = запуск) нарушен.
- Причина: плоский `PopupMenu` даёт один click-жест на строку, поэтому свести к «тап = запуск + отдельный жест на меню» на нём нельзя.

## Реализация (2026-07-03, исправление)

- `showOverflowPopup()` переписан на anchored `PopupWindow` из строк `item_main_program` - тот же layout и wiring, что у видимых элементов (single source of truth).
- Overflow-строка = копия видимой: короткий тап -> `onItemSelected(id)` (запуск), long-press и three-dots -> `showItemMenu` (Open / Open-in-new-window / Configure / Remove).
- Popup берёт тему `popupMenuBackground`, закрывается по тапу вне; `rebuild()` закрывает открытый popup при повороте / смене ширины.
- Компиляция: `pwsh -NoProfile -File a.ps1 fk` (`compileStandardDebugKotlin`) - BUILD SUCCESSFUL.
- Остаётся `BlockNeedUserTest` - проверить два жеста overflow-строки на устройстве.
