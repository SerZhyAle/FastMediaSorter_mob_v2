# Спецификация: S0795 - Действие жеста для запуска видеозаписи

**Ticket:** S0795
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:** /spec-draft Новое действие для жеста с левого края - "Начать видеозапись".

---

## 1. Проблема

Edge-жест умеет открыть камеру (S0788 `LAUNCH_CAMERA`), но всегда в фото-режиме (или switchable). Нет действия, открывающего камеру сразу в режиме видео для быстрой видеозаписи.

## 2. Цели

1. Новое действие жеста `START_VIDEO_RECORDING` открывает встроенную камеру фиксированно в видео-режиме.
2. Действие в picker-е наравне с остальными; клип сохраняется как у камеры-виджета.

**Non-goals:**

- Полностью автоматический старт записи без касания (истинно «headless» видео - отдельная задача; здесь пользователь кадрирует и жмёт запись).
- Изменение самого overlay/движка жеста.

## 3. Ограничения

- **Flavor:** enum/dispatcher/picker в `src/main`; overlay ships standard (`fms.edgeGestureOverlay`) + noLegal.
- **API level:** без API-специфики; запуск через существующий trampoline.
- **Локализация:** EN/RU/UK - добавлена `screenshot_gesture_action_start_video_recording`.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0545, S0563, S0788, S0793.

## 4. Критерии готовности

1. `START_VIDEO_RECORDING` в enum, диспетчере (pre-capture) и picker-е с меткой.
2. Жест открывает камеру в VIDEO-режиме без переключателя; запись сохраняется в Movies.
3. Проект компилируется (standard + noLegal).

## Реализация (2026-07-01, Simple-путь)

- `ScreenshotGestureAction`: добавлено `START_VIDEO_RECORDING` (pre-capture, рядом с `LAUNCH_CAMERA`).
- `CameraLaunchActivity.videoIntent()` + `CameraLaunchWidgetManager.forceVideo`: переиспользуют S0788-trampoline, но открывают камеру фиксированно в `CameraCaptureMode.VIDEO` (`allowModeSwitch=false`), сохранение через существующий `SaveCapturedMediaUseCase` (видео -> Movies).
- `ScreenshotGestureActionDispatcher.handlePreCaptureAction`: ветка `START_VIDEO_RECORDING -> launchVideoCamera(context)` (`FLAG_ACTIVITY_NEW_TASK`, диспетчер в Service); добавлено в pre-capture no-op группу `runPostSave` (exhaustiveness).
- `ScreenshotGestureActionPickerManager.labelResFor`: метка `screenshot_gesture_action_start_video_recording` (EN "Start video recording" / RU "Начать видеозапись" / UK "Почати відеозапис").
- Валидация: `a.ps1 fk` + `a.ps1 fkn` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** назначить на направление «Начать видеозапись»; жест открывает камеру в видео-режиме; запись сохраняется. Проверить на standard (`fms.edgeGestureOverlay=on`) или noLegal.
