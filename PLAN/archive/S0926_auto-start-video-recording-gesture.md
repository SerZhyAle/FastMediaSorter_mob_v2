# Спецификация: S0926 - Авто-старт видеозаписи для жеста «Начать видеозапись»

**Ticket:** S0926
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-04

---

## 1. Проблема

Жест «Начать видеозапись» (S0795) открывает встроенную камеру фиксированно в видео-режиме, но запись не стартует - пользователь всё равно должен нажать кнопку затвора. S0795 явно вынес авто-старт в non-goals как «отдельную задачу»; это она.

## 2. Цель

Жест «Начать видеозапись» стартует запись сам, как только превью готово. Пользователь только останавливает её (клип сохраняется как раньше).

**Non-goals:**

- Авто-старт для обычного запуска камеры (виджет/overflow) - только для именованного жеста.
- Новые настройки/переключатели поведения.

## 3. Первопричина и решение

- Механизм авто-действия уже есть: `EXTRA_AUTO_CAPTURE` -> `CameraCaptureActivity.maybeAutoCapture()` -> `triggerCapture()`, а `triggerCapture()` для видео-режима вызывает `toggleRecording()` (старт записи). До S0926 флаг ставился только на фото-пути (S0790), поэтому видео-жест им не пользовался.
- `CameraCaptureContract.createSwitchableIntent`: добавлен параметр `autoCapture` (default false) -> `EXTRA_AUTO_CAPTURE`. Комментарий `EXTRA_AUTO_CAPTURE` расширен: PHOTO = один снимок, VIDEO = авто-старт записи.
- `CameraLaunchWidgetManager.launchCaptureIntent`: передаёт `autoCapture = forceVideo`. Только S0795-жест (`forceVideo`) авто-стартует; обычный запуск виджета сохраняет ручной затвор.
- `maybeAutoCapture()`: комментарий обновлён; для видео пишет probe `S0926: ...` перед `triggerCapture()`.
- Сохранение не менялось: `multiCapture` хост сам сохраняет каждый клип в Movies (`persistMultiCapture` -> `SaveCapturedMediaUseCase`), на закрытии пакует результат для виджета.

## 4. Критерии готовности

1. Жест «Начать видеозапись» стартует запись без касания затвора, как только готово превью.
2. Остановка и сохранение в Movies работают как в S0795 (регресса нет).
3. Обычный запуск камеры (не жест) по-прежнему ждёт ручного затвора.
4. Проект компилируется (standard + noLegal).

## 5. Реализация (2026-07-04)

- `CameraCaptureContract.kt`: `createSwitchableIntent(autoCapture = false)` + `EXTRA_AUTO_CAPTURE` doc.
- `CameraLaunchWidgetManager.kt`: `autoCapture = forceVideo` в build-е intent-а.
- `CameraCaptureFlowManager.kt`: doc `autoCapture` (video auto-start).
- `CameraCaptureActivity.kt`: `maybeAutoCapture()` doc + probe `S0926` для видео.

**Device-проверка (BlockNeedUserTest):** назначить жест «Начать видеозапись»; запись должна пойти сама; остановить -> клип в Movies. Проверить на standard (`fms.edgeGestureOverlay=on`) или noLegal. Probe в logcat: `S0926: auto-start video recording on preview ready`.
