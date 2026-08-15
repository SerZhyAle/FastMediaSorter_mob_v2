# Спецификация: S0790 - Действие жеста для тихого фото

**Ticket:** S0790
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:** /spec-draft Новое действие для жеста с левого края - "сделать фото" - просто фото с основной камеры без шума, только тост.

---

## 1. Проблема

Edge-жест умеет открыть камеру (S0788), но не сделать быстрый снимок «в один жест». Пользователь хочет назначить на жест мгновенное фото с тостом-подтверждением.

## 2. Цели

1. Новое действие `TAKE_PHOTO` авто-снимает фото основной камерой и сохраняет в публичную папку с тостом.

**Non-goals:**

- Полностью невидимый захват - CameraX неизбежно кратко показывает экран камеры + системный индикатор «камера используется» (Android 12+). Владелец согласовал авто-снимок с кратким показом (2026-07-01).

## 3. Ограничения

- **Flavor:** enum/dispatcher/picker/trampoline + захват в `src/main` (standard и все флейворы с камерой).
- **Permissions:** CAMERA запрашивается трамплином.
- **Локализация:** EN/RU/UK - добавлена `screenshot_gesture_action_take_photo`.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0563, S0568, S0788, S0672.
- **UX-решение (2026-07-01):** авто-снимок (камера открывается, сама делает кадр, закрывается), а не «headless». Краткая вспышка экрана + системный индикатор приняты как неизбежные.

## 4. Критерии готовности

1. `TAKE_PHOTO` в enum, диспетчере (pre-capture) и picker-е с меткой.
2. Жест делает фото и сохраняет в DCIM/Camera; тост подтверждает.
3. Проект компилируется (standard + noLegal).

## Реализация (2026-07-01)

- Новый примитив авто-захвата: `CameraCaptureContract.EXTRA_AUTO_CAPTURE` + `createAutoCaptureIntent()`; `CameraCaptureFlowManager.autoCapture`; `CameraCaptureActivity.maybeAutoCapture()` - как только превью готово (`bind` onReady), один раз спускает затвор (PHOTO, single-shot, без переключателя), затем finish с результатом.
- Новый трамплин `widget/PhotoCaptureLaunchActivity` (тонкий, Rule 3) + `widget/PhotoCaptureLaunchManager`: CAMERA-разрешение -> авто-захват -> сохранение через `SaveCapturedMediaUseCase` (photo -> DCIM/Camera) + тост. Manifest-запись добавлена.
- `ScreenshotGestureAction.TAKE_PHOTO` (pre-capture); `ScreenshotGestureActionDispatcher` ветка `launchPhotoCapture(context, autoAction=null)`; метка `screenshot_gesture_action_take_photo` (EN "Take a photo" / RU "Сделать фото" / UK "Зробити фото").
- Валидация: `a.ps1 fk` + `a.ps1 fkn` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** назначить «Сделать фото»; жест делает headless-снимок (экран камеры НЕ открывается, только системный индикатор камеры); фото в DCIM/Camera, тост. Проверить на standard.

## Исправление (2026-07-04): headless-захват

Device-тест выявил: камера появлялась и закрывалась, но фото не сохранялось и не роутилось - для всех S0790-S0794 (общий трамплин).

- **Корень:** трамплин `PhotoCaptureLaunchActivity` имел `android:noHistory="true"`. Запуск полноэкранной `CameraCaptureActivity` через `startActivityForResult` уводил трамплин на фон -> система немедленно уничтожала его -> результат авто-снимка возвращать было некуда, `onCaptureResult` не вызывался, `SaveCapturedMediaUseCase`/routeToViewer не запускались. Тот же баг ломал CAMERA-диалог на первом запуске. Соседний трамплин `CameraLaunchActivity` (S0568) не страдал: там `multiCapture=true`, host сохраняет каждый кадр сам и не зависит от возврата результата.
- **Решение** (owner GO 2026-07-04, реверс non-goal «CameraX неизбежно показывает экран» от 2026-07-01 - технически неверного: `Preview` это отдельный use case): headless-захват без активити камеры. Новый `HeadlessPhotoCapturer` биндит только CameraX `ImageCapture` (без `Preview`) к lifecycle трамплина и делает `takePicture` в том же процессе - нет второй активности, нет показа экрана камеры, нет межактивити-передачи результата.
- Убран `android:noHistory` с трамплина (нужен, чтобы пережить CAMERA-диалог и async-захват).
- Геотег (S0766) сохранён через `CameraLocationProvider`.
- Неизбежно остаётся: системный индикатор «камера используется» (Android 12+); первый кадр без 3A-сходимости (нет превью) - возможна лёгкая недоэкспозиция в тёмной сцене.
- `createAutoCaptureIntent` в `CameraCaptureContract` осиротел (единственный вызыватель убран); `EXTRA_AUTO_CAPTURE`/`maybeAutoCapture` остаются - их держит видео-авто-запись S0926.
- Валидация: `a.ps1 fc` + `a.ps1 fkn` - BUILD SUCCESSFUL.
