# Спецификация: S0797 - Действие жеста для запуска записи экрана

**Ticket:** S0797
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:** /spec-draft Новое действие для жеста с левого края - "Начать запись экрана" видео с экрана.

---

## 1. Проблема

Запись экрана (S0774) запускается только из главного окна. Нет действия edge-жеста для быстрого старта/остановки записи экрана.

## 2. Цели

1. Новое действие жеста `START_SCREEN_RECORDING` стартует запись экрана (disclosure + системный consent + foreground-сервис).
2. Повторный жест во время записи останавливает её (toggle).
3. Действие скрыто там, где движок захвата отсутствует (lite/photos/legacy).

**Non-goals:**

- «Тихий» старт без системного диалога - MediaProjection consent обязателен на уровне ОС, обойти нельзя.
- Изменение самого движка записи экрана (S0774).

## 3. Ограничения

- **Flavor:** enum/dispatcher/picker/trampoline в `src/main`; реальный контроллер только на standard (`fms.screenCapture=on`) + noLegal (пустой multibinding в остальных).
- **Permissions:** RECORD_AUDIO + (API 33+) POST_NOTIFICATIONS + системный MediaProjection consent - через трамплин.
- **Локализация:** EN/RU/UK - добавлена `screenshot_gesture_action_start_screen_recording`.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0774, S0672, S0793, S0798 (дубликат).

## 4. Критерии готовности

1. `START_SCREEN_RECORDING` в enum, диспетчере (pre-capture), picker-е с меткой и гейтингом видимости.
2. Жест стартует запись (consent), повторный - останавливает.
3. Действие отсутствует в picker-е на флейворах без движка захвата.
4. Проект компилируется (standard + noLegal).

## Реализация (2026-07-01)

- `ScreenshotGestureAction`: добавлено `START_SCREEN_RECORDING` (pre-capture).
- Новый трамплин `widget/ScreenRecordingLaunchActivity` (`src/main`, `@AndroidEntryPoint`, прозрачный `noHistory`): инжектит `Set<ScreenVideoRecordingController>` + `ScreenRecordingStateController`, хостит RECORD_AUDIO/POST_NOTIFICATIONS launchers, тоглит запись - `requestStop` если активна, иначе `controller.launch(this)` (нужен FragmentActivity, которого нет у Service-диспетчера). Manifest-запись добавлена.
- `ScreenshotGestureActionDispatcher.handlePreCaptureAction`: ветка `START_SCREEN_RECORDING -> launchScreenRecording(context)` (`FLAG_ACTIVITY_NEW_TASK`); добавлено в pre-capture no-op группу `runPostSave`.
- `ScreenshotGestureActionPickerManager`: метка `screenshot_gesture_action_start_screen_recording` (EN "Start screen recording" / RU "Начать запись экрана" / UK "Почати запис екрана") + `availableActions()` скрывает действие при `screenRecordingAvailable=false`; `OperationsSettingsFragment` передаёт `screenVideoRecordingControllers.isNotEmpty()`.
- Валидация: `a.ps1 fk` + `a.ps1 fkn` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** назначить «Начать запись экрана»; жест показывает disclosure + системный consent, стартует запись; повторный жест останавливает и сохраняет. Проверить скрытие действия на lite/photos/legacy.
