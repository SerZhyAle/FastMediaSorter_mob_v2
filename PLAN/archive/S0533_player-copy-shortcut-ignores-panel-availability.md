# S0533 - Клавиатурная команда COPY раскрывает скрытую панель «Копировать в..»

**Ticket:** S0533
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-19
**Tier:** ad-hoc

---

## 0. Захват находки

**Симптом:** клавиатурная команда «копировать» (COPY) в плеере переключает раскрытие заголовка панели «Копировать в..», даже когда сама панель скрыта (копирование выключено в настройках или нет назначений). Заголовок открывается, кнопок нет - UI-рассинхрон, аналогичный S0532, но по другому критерию.

**Доказательство (на момент находки 2026-06-19, при реализации S0532):**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt` - `canCopyCurrent()` возвращает `true` безусловно.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` - `copyPanelVisible = effectiveShowCommandPanel && state.enableCopying && hasCopyButtons`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt` - `toggleCopyPanel()` без guard'а вообще.

**Источник:** обнаружено при реализации S0532 (аналог для MOVE); COPY не относится к праву записи, поэтому вынесено отдельно.

**Что предполагается:** выровнять guard клавиатурной команды COPY с критерием видимости панели (`enableCopying` + наличие назначений), по образцу `CommandPanelController.isMoveAvailable()` из S0532.

---

## 1. Корень проблемы

- Видимость панели копирования: `copyPanelVisible = effectiveShowCommandPanel && state.enableCopying && hasCopyButtons` (`CommandPanelAvailabilityUpdater`); права записи здесь не участвуют - копирование читает исходник.
- Guard клавиатурной команды COPY: `canCopyCurrent()` возвращал `true` безусловно (`PlayerKeyboardCallbackImpl`).
- `PlayerDialogAndUiStateManager.toggleCopyPanel()` не имел guard'а вообще.
- Итог: при выключенном копировании или без заданных назначений нажатие COPY переключало состояние заголовка панели, тогда как сама панель оставалась скрыта.

## 2. Решение

- `CommandPanelController.isCopyAvailable()` повторяет критерий видимости панели: `enableCopying` + наличие назначений (`copyToButtonsGrid.childCount > 0`). Проверка права записи намеренно опущена (в отличие от `isMoveAvailable()` из S0532).
- `PlayerKeyboardCallbackImpl.canCopyCurrent()` опрашивает `isCopyAvailable()`; при недоступности команда не консумится и заголовок не переключается.

## 3. Затронутые файлы

- `ui/player/CommandPanelController.kt`
- `ui/player/callbacks/PlayerKeyboardCallbackImpl.kt`

## 4. Проверка на устройстве

- Открыть в плеере файл при выключенной настройке копирования (или без заданных назначений копирования).
- Нажать клавиатурную команду COPY.
- Ожидание: заголовок панели «Копировать в..» не раскрывается, панель остаётся скрытой; в логах `S0533: keyboard COPY guard available=false`.
- Контроль: при включённом копировании и заданных назначениях COPY по-прежнему раскрывает панель (`available=true`).

## Связанные тикеты

- S0532 - аналогичный рассинхрон для команды MOVE (по праву записи); общий паттерн `isMoveAvailable()`/`isCopyAvailable()`.

## Last Audit

### Manual (device test) - 2026-06-19

- Verdict: PASS
- Device: emulator-5556 (Pixel 6, Android 13), standard debug v2.60.6191.257 (`com.sza.fastmediasorter.debug`).
- Method: keyboard COPY shortcut injected via `adb shell input keyevent 135` (KEYCODE_F5, the no-meta keyboard trigger of `sorting.copy` per `default_bindings.json` key:135:0). Copying capability toggled via Settings -> Management -> Copy/move -> Allow copying; copy destination ("Downloads") stayed configured (badge 1) throughout.

- Branch available=true (copying enabled + destinations configured):
  - expected: COPY toggles the copy-panel header; log `available=true`.
  - actual: panel header collapsed from "▼ Copy to.." to "▶ Copy to.." after the keyevent; log fired.
  - log: `D PlayerKeyboardCallbackImpl: S0533: keyboard COPY guard available=true`

- Branch available=false (copying disabled, destinations still configured):
  - expected: COPY does NOT toggle the hidden copy-panel header; log `available=false`.
  - actual: copy panel header absent before and after the keyevent (only "▶ Move to.." shown); nothing toggled; log fired.
  - log: `D PlayerKeyboardCallbackImpl: S0533: keyboard COPY guard available=false`

- Guard debug log fired in BOTH branches (`Timber.d("S0533: keyboard COPY guard available=...")` at `PlayerKeyboardCallbackImpl.canCopyCurrent()`).
- Note: actual log text is `S0533: keyboard COPY guard ...` (colon after id); the status-note wording omits the colon. Same line, content matches.
- Evidence: `temp/S0533_devtest/` (log_available_true.txt, log_available_false.txt, available_true_after_copy_collapsed.png, available_false_no_copy_header.png).
- Device left in original state: copying re-enabled.
