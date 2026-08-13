# Спецификация (compact): S0978 - панель быстрого доступа: добавить жестовые действия камеры/видео

**Ticket:** S0978
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-10
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-10 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-10

**Текст:**

дополнить в список "функций программы", которые можно выбрать для панели быстрого. доступа все функции, которые у нас задаются для жестов слева

---

## 1. Цель

Список «функций программы» для панели быстрого доступа (app-launch panel, реестр `InternalRouteCatalog`) сейчас не содержит части действий, доступных для левых edge-жестов (`ScreenshotGestureAction`). Владелец хочет, чтобы жестовые действия были доступны и как слоты панели. Первая итерация покрывает дёшёвый подмножество - действия, у которых уже есть Context-generic трамплин, переиспользуемый вне Service-контекста жеста.

**Решение владельца (2026-07-10, через `/spec-all`):** вариант «только дёшёвые сейчас». Добавить 4 route, переиспользующих готовые трамплины; 7 «скриншот-комбо» действий (нужны новые MediaProjection/Accessibility consent-трамплины) отложены в non-goals.

Прецедент: **S0912** уже перенёс 4 действия в панель (`quick_camera`, `quick_voice`=START_AUDIO_RECORDING, `screen_recording`=START_SCREEN_RECORDING, `link_download`) по паттерну «запись в `InternalRouteCatalog` + intent-builder в `AppLaunchPanelRouteIntents` + availability-ветка в `ResolvePanelRouteAvailabilityUseCase`». S0978 повторяет этот паттерн.

## 2. Объём (4 новых route)

| Route key | Жестовое действие | Трамплин | Иконка | Лейбл (переиспользуется) |
| --- | --- | --- | --- | --- |
| `take_photo_send_to` | TAKE_PHOTO_SEND_TO | `PhotoCaptureLaunchActivity.intent(ctx, AUTO_ACTION_SEND_TO)` | `ic_camera_send_to` | `screenshot_gesture_action_take_photo_send_to` |
| `take_photo_edit` | TAKE_PHOTO_EDIT | `PhotoCaptureLaunchActivity.intent(ctx, AUTO_ACTION_DRAW)` | `ic_edit_20` | `screenshot_gesture_action_take_photo_edit` |
| `take_photo_ocr_translate` | TAKE_PHOTO_OCR_TRANSLATE | `PhotoCaptureLaunchActivity.intent(ctx, AUTO_ACTION_TRANSLATE)` | `ic_camera_ocr_translate` | `screenshot_gesture_action_take_photo_ocr_translate` |
| `start_video_recording` | START_VIDEO_RECORDING | `CameraLaunchActivity.videoIntent(ctx)` | `ic_video` | `screenshot_gesture_action_start_video_recording` |

`AUTO_ACTION_*` - константы `PhotoVideoStandaloneActivity` (`send_to`/`draw`/`translate`). Каждый intent получает `FLAG_ACTIVITY_NEW_TASK` через `withPanelFlags()`.

**Non-goals:**

- 7 «скриншот-комбо» действий (`SILENT_SCREENSHOT`, `OPEN_IN_PLAYER`, `OPEN_IN_DRAW`, `OCR_TRANSLATE`, `SEND_TO_RECIPIENTS`, `SHARE`, `CROP_AND_SHARE`) - у них нет standalone-формы, каждому нужен новый consent-трамплин. Отложено (владелец выбрал вариант 1, не парковать тикет сейчас).
- `OPEN_APP`, `OPEN_PANEL`, `DO_NOT_USE` - бессмысленны в панели (панель уже моделирует «закрепить приложение» через `OWN_APP`/`EXTERNAL_APP`; «открыть панель из панели» - нонсенс; пустой слот уже = «нет функции»).
- `LAUNCH_CAMERA` / plain `TAKE_PHOTO` - фото-захват уже покрыт route `quick_camera` (S0912); не дублируем.
- Снятие фото-only/fixed-folder ограничения `quick_camera` - вне объёма (своя доработка).

## 3. Реализация

Три файла, механическое расширение по образцу S0912-записей:

1. `core/panel/InternalRouteCatalog.kt` - 4 новых `const KEY_*` + 4 `Route(..)` в списке (после `KEY_LINK_DOWNLOAD`, порядок как в §2 = порядок жестовых действий в enum).
2. `core/panel/AppLaunchPanelRouteIntents.kt` - 4 intent-builder функции (`takePhotoSendTo`, `takePhotoEdit`, `takePhotoOcrTranslate`, `startVideoRecording`), каждая строит intent через существующий трамплин + `withPanelFlags()`. Импорты: `PhotoCaptureLaunchActivity`, `CameraLaunchActivity`, `PhotoVideoStandaloneActivity`.
3. `domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt` - 4 ветки в `resolve(..)`:
   - `take_photo_send_to` / `take_photo_edit`: `availableInBuild = mediaCapabilities.supportsImages`, `enabledAtRuntime = !settings.disableCameraCapture` (как `quick_camera`).
   - `take_photo_ocr_translate`: `availableInBuild = mediaCapabilities.supportsImages && capability.isTranslationAvailable()`, `enabledAtRuntime = !settings.disableCameraCapture`.
   - `start_video_recording`: `availableInBuild = mediaCapabilities.supportsVideo`, `enabledAtRuntime = !settings.disableVideoCapture`.

`InternalRoutePickerDialogFragment` листает `InternalRouteCatalog.all()` с фильтром по availability - новые route подхватываются автоматически, UI-правок нет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0912 (перенёс первые 4 действия - тот же паттерн реестра), S0788-S0797 (жестовые действия камеры/видео/аудио/экрана - источник действий), S0663 (стратегия app-launch panel).
- **Owner decision (2026-07-10):** вариант 1 - только дёшёвый подмножество (4 route с готовыми трамплинами); 7 скриншот-комбо в non-goals, тикет не парковать.
- **Flavor:** standard (+ noLegal); lite/photos/legacy - camera/video действия скрыты availability-гейтом по `supportsImages`/`supportsVideo` (уже так для `quick_camera`).
- **Локализация:** новых строк нет - переиспользуются существующие EN/RU/UK ключи `screenshot_gesture_action_*`.

## 4. Проверка

1. Компиляция standard (`.\a.ps1 fc` - код + ресурсы) - PASS.
2. Реестр: `InternalRouteCatalog.all()` содержит 13 route (было 9 + 4). `ResolvePanelRouteAvailabilityUseCase` имеет ветку для каждого нового key (нет ухода в `else`).
3. Device (BlockNeedUserTest): открыть редактор панели -> «выбрать функцию» -> в списке присутствуют 4 новых пункта с корректными лейблами/иконками; назначить каждый на слот; тап по слоту:
   - take_photo_send_to -> камера снимает фото -> открывается send-to.
   - take_photo_edit -> камера снимает фото -> открывается draw-редактор.
   - take_photo_ocr_translate -> камера снимает фото -> OCR-перевод (если перевод доступен; иначе пункт скрыт).
   - start_video_recording -> открывается камера в видео-режиме.
4. На флейворе без camera/video (lite/photos) новые пункты скрыты в пикере.

## Last Audit

### Manual (device 2026-07-10, emulator-5554 Android 13, standard-debug v2.60.7092.225)

- **Editor reachable:** PASS. Settings search "app panel" -> btnEditAppPanel -> EditAppLaunchPanelActivity -> empty slot -> "Add to panel" -> "FastMediaSorter feature" opens InternalRoutePickerDialogFragment ("Choose a feature").
- **4 new items present:** PASS. Picker lists all 4 with correct labels + icons at the tail after "Download by link": "Take a photo and send to..", "Take a photo and edit", "Take a photo and OCR-translate", "Start video recording" (OCR-translate item shown -> translation capability available on this build).
- **Route count 9 -> 13:** PASS. Full order: Calculator, Mini-game, Photo OCR translate (existing ocr), Streams, Favorites, Camera, Voice recording, Screen video recording, Download by link, + 4 new = 13 total.
- **take_photo_send_to launches camera trampoline:** PASS. Assigned to slot 1, tapped tile in AppLaunchPanelActivity. Probe: `S0978: panel launch route=take_photo_send_to launchable=true`; foreground -> PhotoCaptureLaunchActivity.
- **start_video_recording opens camera in video mode:** PASS. Assigned to slot 2, tapped tile. Probe: `S0978: panel launch route=start_video_recording launchable=true`; foreground -> CameraCaptureActivity, Recorder actively muxing video (video mode confirmed).
- **take_photo_edit / take_photo_ocr_translate launch:** not tapped on device (send_to covers the PhotoCaptureLaunchActivity trampoline path; both share it with a different AUTO_ACTION). Present in picker.
- **lite/photos hiding:** not verified (standard build only).
- **Evidence:** `temp/S0978/device-test-logcat.txt` (both S0978 probes + activity starts).
