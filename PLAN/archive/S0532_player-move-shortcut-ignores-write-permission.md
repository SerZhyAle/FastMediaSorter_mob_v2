# S0532 - Клавиатурная команда MOVE раскрывает скрытую панель «Переместить в..» на ресурсе без права записи

**Ticket:** S0532
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-19
**Tier:** ad-hoc

---

## 0. Захват находки

**Симптом:** клавиатурная команда «переместить» (MOVE) в плеере переключает раскрытие заголовка панели «Переместить в..» даже на ресурсе без права записи, тогда как сама панель в этом случае скрыта. Заголовок открывается, кнопок нет - UI-рассинхрон.

**Доказательство (на момент находки 2026-06-19, research S0531):**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt:62` - `canMoveCurrent()` возвращает `true` безусловно, без проверки `canWrite`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt:155` - видимость `moveToPanel` корректно подавляется при `canWrite == false`.
- Рассинхрон: guard клавиатурной команды и guard видимости панели расходятся.

**Источник:** обнаружено при ресёрче архитектуры для S0531 (цифровые шорткаты операций плеера); к самой S0531 не относится.

**Что предполагается:** выровнять guard клавиатурной команды MOVE с тем же критерием права записи, что и видимость панели. Требует проверки полного пути доступности перемещения (настройка + право записи + наличие направлений).

---

## 1. Корень проблемы

- Видимость панели перемещения: `movePanelVisible = effectiveShowCommandPanel && state.enableMoving && hasMoveButtons && canWrite` (`CommandPanelAvailabilityUpdater`).
- Guard клавиатурной команды MOVE: `canMoveCurrent()` возвращал `true` безусловно (`PlayerKeyboardCallbackImpl`).
- `PlayerDialogAndUiStateManager.toggleMovePanel()` имел guard только по `isReadOnly`, тогда как панель скрывается по более широкому `canWrite` (ресурс может быть `isWritable == false` без `isReadOnly`).
- Итог: на ресурсе без права записи нажатие MOVE переключало состояние заголовка панели, тогда как сама панель оставалась скрыта.

## 2. Решение

- Новый общий резолвер прав `resolvePlayerFilePermissions()` (`CommandPanelPermissions.kt`) - единый источник истины для `canWrite`/`canRead`. Устраняет расхождение guard'ов, из-за которого баг и возник.
- `CommandPanelAvailabilityUpdater.update()` теперь берёт `canWrite`/`canRead` из этого резолвера вместо инлайн-блока.
- `CommandPanelController.isMoveAvailable()` повторяет точный критерий видимости панели: `enableMoving` + наличие назначений (`moveToButtonsGrid.childCount > 0`) + `canWrite`.
- `PlayerKeyboardCallbackImpl.canMoveCurrent()` опрашивает `isMoveAvailable()`; при недоступности команда не консумится и заголовок не переключается.

## 3. Затронутые файлы

- `ui/player/CommandPanelPermissions.kt` (новый)
- `ui/player/CommandPanelAvailabilityUpdater.kt`
- `ui/player/CommandPanelController.kt`
- `ui/player/PlayerActivity.kt` (accessor готовности контроллера)
- `ui/player/callbacks/PlayerKeyboardCallbackImpl.kt`

## 4. Проверка на устройстве

- Открыть в плеере файл из ресурса без права записи (read-only режим ресурса или `isWritable == false`), при включённой настройке перемещения.
- Нажать клавиатурную команду MOVE.
- Ожидание: заголовок панели «Переместить в..» не раскрывается, панель остаётся скрытой; в логах `S0532: keyboard MOVE guard available=false`.
- Контроль: на ресурсе с правом записи и заданными назначениями MOVE по-прежнему раскрывает панель (`available=true`).

## Last Audit

### Manual (device test, 2026-06-19, emulator-5556 standard-debug v2.60.6191.257, Android 13)

- **Verdict: PASS** - both acceptance branches confirmed via injected keyboard MOVE (F6, `key:136:0` -> `sorting.move`) on the player.
- Setup: local folder resource (OCR, 3 files incl. images) toggled `isReadOnly` to exercise both states; "Downloads" registered as destination so the move grid is populated (`moveGrid.childCount=1`) in both cases.

Non-writable branch (resource read-only, `canWrite=false`):
- Expected: MOVE shortcut does NOT toggle the move-panel header; log `S0532 keyboard MOVE guard available=false`; move panel stays hidden.
- Actual: move panel absent from the player (only "Copy to.." visible); F6 reached the handler, resolved to `sorting.move`, guard returned false, no toggle.
- Captured:
  - `KEY: action=DOWN code=136 [KEYCODE_F6] (PlayerKeyboardHandler (type=IMAGE))`
  - `PlayerKeyboardHandler: commandId=sorting.move type=IMAGE`
  - `PlayerKeyboardCallbackImpl: S0532: keyboard MOVE guard available=false`

Writable control branch (resource writable, `canWrite=true`, destinations configured):
- Expected: MOVE shortcut still opens/toggles the move panel; log `available=true`.
- Actual: both "Copy to.." and "Move to.." panels visible; F6 toggled the move-panel header (prefix flipped expanded->collapsed, grid hidden) via `toggleMovePanel()`.
- Captured:
  - `KEY: action=DOWN code=136 [KEYCODE_F6] (PlayerKeyboardHandler (type=IMAGE))`
  - `PlayerKeyboardHandler: commandId=sorting.move type=IMAGE`
  - `PlayerKeyboardCallbackImpl: S0532: keyboard MOVE guard available=true`
  - `PlayerDialogAndUiStateManager: toggleMovePanel()`
- Evidence: `temp/S0532_devtest/` (screenshots + filtered logcat per branch).

## Связанные тикеты

- S0533 - аналогичный рассинхрон для команды COPY (запаркован при реализации).
- S0531 - ресёрч, при котором находка обнаружена.
