# S0503 - CameraCaptureActivity onError touches released ViewBinding (crash)

**Ticket:** S0503
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-18

> **Scope:** Compact spec (Simple path). Strategic goal + inline phase. Refined from the /spec-draft skeleton in §0.

---

## Goal

Устранить FATAL-краш `IllegalStateException: Binding is only valid..` в `CameraCaptureActivity`. CameraX доставляет `takePicture` колбэки (`onSaved`/`onError`) асинхронно на main looper; если пользователь закрыл экран камеры пока съёмка «висела», активность уже уничтожена (`_binding = null` в `BaseActivity.onDestroy`), и доступ к `binding` в колбэке падает. Защитить оба колбэка проверкой `isFinishing`/`isDestroyed` перед обращением к `binding`.

## Acceptance criteria

1. `capturePhoto()` колбэки `onSaved` и `onError` не обращаются к `binding`/UI, если активность `isFinishing` или `isDestroyed`.
2. Стандартная debug-сборка компилируется.
3. Закрытие экрана камеры во время висящего `takePicture` (эмуляторная fake-camera) больше не приводит к `CrashActivity` (device repro - MANUAL, гонка по времени).

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-18 (during /spec-test-device S0469 on emulator-5554)

**Симптом:** FATAL crash when an in-app camera capture errors out (or is cancelled while a `takePicture` is still in flight). The app jumps to `CrashActivity`.

**Стек (logcat, emulator-5554, standard debug):**

```
java.lang.IllegalStateException: Binding is only valid between onCreateView and onDestroyView
  at com.sza.fastmediasorter.core.ui.BaseActivity.getBinding(BaseActivity.kt:57)
  at com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity.capturePhoto$lambda$7(CameraCaptureActivity.kt:142)
  at com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager$capture$1.onError(CameraCaptureSessionManager.kt:84)
  at androidx.camera.core.imagecapture.TakePictureRequest.lambda$onError$0(TakePictureRequest.java:232)
```

**Корень:** в `CameraCaptureActivity.capturePhoto()` колбэк `onError` (строка 140-145) обращается к `binding.btnCapturePhoto.isEnabled` после того, как активность уже финишируется/уничтожена (binding освобождён). CameraX доставляет `onError` асинхронно на main looper; если активность к этому моменту закрыта (пользователь нажал Close, пока `takePicture` висел), `getBinding()` бросает `IllegalStateException`.

**Как воспроизведено:** на эмуляторе виртуальная камера (`vendor.qemu.sf.fake_camera=front`, Stream 0 timestamps `not increasing`) подвешивает `ImageCapture.takePicture()` - JPEG-колбэк не приходит. Закрытие экрана камеры (`btnCloseCamera`) триггерит `onError`, который падает на доступе к binding.

**Воспроизводимость на реальном устройстве:** вероятна в любом сценарии, где `takePicture` завершается ошибкой/отменой после ухода с экрана (нет файла для записи, отказ камеры, быстрый back во время съёмки). Эмулятор лишь делает окно гонки 100%-ным.

**Кандидат на фикс:** в обоих колбэках (`onSaved`, `onError`) проверять `isFinishing`/`isDestroyed` (или lifecycle `STARTED`) перед доступом к `binding`; либо снимать колбэки в `onDestroy`. `onSaved` (строки 136-139) трогает только `setResult/finish` - безопаснее, но тоже стоит защитить.

**Связанные файлы:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt:140-145`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt:84`

**Связанные тикеты:** обнаружено во время теста S0469 (save-photos-to-clipboard); S0022-CAM - подсистема съёмки.

**Вложения:** `temp/S0469_run3/crash.txt`, `temp/S0469_run3/logcat_capture.txt`.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0022-CAM (capture subsystem), discovered during S0469.

---

## Phase 01 - Guard capture callbacks against released binding

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`

**Prompt for developer:**

> In `capturePhoto()`, guard both the `onSaved` and `onError` callbacks: at the start of each, return early when the activity is already gone (`isFinishing || isDestroyed`) so the async CameraX callback never touches the released `binding` (or calls `setResult`/`finish` on a dead activity). Add one short comment explaining the async-after-destroy race. Do not change capture logic otherwise.

**Verification:**

- `Grep` - `isFinishing || isDestroyed` present inside `capturePhoto`.
- `Grep` - both `onSaved =` and `onError =` lambdas in `capturePhoto` contain the guard before any `binding` access.
- Standard debug compiles (`.\a.ps1 fk` / `dq`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-18 - Guards added to `onSaved` + `onError` in `capturePhoto()` (`if (isFinishing || isDestroyed) return@capture` before any binding/finish access). Verification 3/3 PASS (`.\a.ps1 fk` BUILD SUCCESSFUL). post-change Kotlin gates PASS.

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (Simple)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 2 - WARN 0 - FAIL 0 - MANUAL 1 - EXEMPT 0

Static checks PASS: both `capturePhoto()` callbacks (`onSaved`, `onError`) start with `if (isFinishing || isDestroyed) return@capture` before any `binding`/`setResult`/`finish` access, eliminating the async-after-destroy `IllegalStateException`. `.\a.ps1 fk` BUILD SUCCESSFUL. Fix is provably correct: `_binding` is nulled only in `BaseActivity.onDestroy`, after which `isDestroyed` is true, so the guard always short-circuits before a null-binding access. Internal defect fix - no ALL_FEATURES capability change.

### Manual / on-device

- [ ] Reproduce the camera close-during-in-flight-takePicture race on the emulator fake-camera and confirm no `CrashActivity` - MANUAL: timing-dependent race; the guard removes the root cause by construction, so a scripted repro is redundant.
