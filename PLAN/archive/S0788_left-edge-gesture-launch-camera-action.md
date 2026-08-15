# Спецификация: S0788 - Действие жеста с левого края для запуска фото-видео камеры

**Ticket:** S0788
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:**

Новое действие для жеста с левого края - «запустить фото-видео камеру».

---

## 1. Проблема

Edge-gesture (жест с края экрана, `ScreenshotGestureAction` + `ScreenshotGestureActionDispatcher`) даёт набор действий (silent screenshot, open in player/draw, OCR-translate, send-to, share, crop-and-share, open app/panel). Нет действия «открыть встроенную камеру» - пользователь не может назначить на жест быстрый запуск камеры.

## 2. Цели

1. Новое действие жеста `LAUNCH_CAMERA` - открывает встроенную фото-видео камеру приложения.
2. Действие доступно в picker-е выбора действия жеста наравне с остальными.

**Non-goals:**

- Тихий снимок без UI (это отдельные тикеты S0790+).
- Изменение самого overlay/движка жеста.

## 3. Ограничения

- **Flavor:** edge-gesture overlay ships standard (property `fms.edgeGestureOverlay`) + noLegal; enum/dispatcher/picker в `src/main`.
- **API level:** без API-специфики; запуск через существующий trampoline.
- **Локализация:** EN/RU/UK - добавлена `screenshot_gesture_action_launch_camera`.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0563/S0568 (камера + trampoline виджета), S0672 (edge-gesture).

## 4. Критерии готовности

1. `LAUNCH_CAMERA` в enum, диспетчере (pre-capture, stop) и picker-е с меткой.
2. Жест с назначенным `LAUNCH_CAMERA` открывает камеру (резолвит дефолтный destination + CAMERA-разрешение).
3. Проект компилируется; тесты зелёные.

## Реализация (2026-07-01, Simple-путь)

- `ScreenshotGestureAction`: добавлено значение `LAUNCH_CAMERA` (pre-capture, рядом с OPEN_APP/OPEN_PANEL).
- `ScreenshotGestureActionDispatcher.handlePreCaptureAction`: ветка `LAUNCH_CAMERA -> { launchCamera(context); true }` - переиспользует `CameraLaunchActivity` (S0568 no-UI trampoline: резолвит дефолтный capture-destination, обрабатывает CAMERA-разрешение, открывает камеру), запуск с `FLAG_ACTIVITY_NEW_TASK` (диспетчер в Service). В `runPostSave` добавлено в pre-capture no-op группу (exhaustiveness).
- `ScreenshotGestureActionPickerManager.labelResFor`: `LAUNCH_CAMERA -> R.string.screenshot_gesture_action_launch_camera`; действие автоматически попадает в `availableActions()`.
- Строка `screenshot_gesture_action_launch_camera` (EN "Launch camera" / RU "Запустить камеру" / UK "Запустити камеру").
- `fromName` толерантен: новое значение backward-compatible при парсинге настроек.
- Компиляция `compileStandardDebugKotlin` + `processStandardDebugResources` - BUILD SUCCESSFUL; `ApplyProfilePresetUseCaseTest` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** в настройках edge-gesture назначить на направление действие «Запустить камеру»; выполнить жест - открывается встроенная камера (с дефолтным destination), после снимка возврат как у виджета; проверить на standard-сборке с `fms.edgeGestureOverlay=on` (или noLegal).
