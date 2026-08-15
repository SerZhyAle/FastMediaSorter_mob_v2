**Status:** Archived

<!-- auto-approved by /spec-all - 2026-07-13 -->

# S1042 - Route screenshot (and all OCR entry points) into the photo crop + OCR/translate flow

## Goal

Пользователь видит на экране английский текст и хочет одним жестом получить его перевод на русский - так же, как это работает для фото. Сейчас жест-скриншот открывает полноэкранный просмотрщик и делает «немой» авто-перевод в диалоге, без обрезки и без выбора языков. Нужно, чтобы жест-скриншот вёл в **тот же экран, что и фото-OCR** ([CameraOcrTranslateActivity]): превью с рамкой обрезки, смена языков (источник/цель), кнопки «оцифровка» (только OCR) и «оцифровка с переводом». Источник - скриншот вместо камеры. Все точки входа OCR сводятся к одному экрану.

## Problem

Два разных UX для одной задачи:

- **Фото** ([CameraOcrTranslateActivity] + [CameraOcrFlowManager]): камера -> экран обрезки с draggable-рамкой -> кластер языков (источник OCR + цель перевода, тап меняет язык) -> «оцифровка» / «оцифровка с переводом» -> результат (перевод сверху, оригинал снизу) с повторной сменой языков и re-OCR. Богатый, управляемый.
- **Скриншот** (жест `OCR_TRANSLATE` / `TAKE_PHOTO_OCR_TRANSLATE`): `ScreenCaptureService` сохраняет сырой PNG в галерею -> `PhotoVideoStandaloneActivity` открывает его на весь экран -> `AUTO_ACTION_TRANSLATE` разово запускает перевод в диалоге. Ни обрезки, ни выбора языка. Плюс хрупкая гонка готовности картинки (S1041).

## Proposed solution

Сделать вход в фото-OCR-flow источник-независимым и направить туда все OCR-точки входа.

- **Новый вход в flow «начать с готового изображения».** В [CameraOcrFlowManager] камера-специфичны только `startCapture()` -> `launchCaptureInternal()` -> `CameraCaptureActivity` -> `onPhotoCaptured()`, где снимок декодируется в `orientedBitmap`. Всё после (`showCropStep` -> языки -> `onCropConfirmed` -> `runRecognition` -> результат) от источника не зависит. Добавить `startWithImage(source)` (temp-файл/URI скриншота), который декодирует его в `orientedBitmap` и сразу вызывает `showCropStep` + `emitCropLanguages`, минуя камеру.
- **Активити принимает источник.** [CameraOcrTranslateActivity] получает опциональный extra (например `EXTRA_SOURCE_IMAGE`); при его наличии в `onCreate` идёт `flowManager.startWithImage(..)` вместо `startCapture()`.
- **Роутинг жеста.** [ScreenshotGestureActionDispatcher] для `OCR_TRANSLATE` и `TAKE_PHOTO_OCR_TRANSLATE` запускает `CameraOcrTranslateActivity` c источником-скриншотом, а не `PhotoVideoStandaloneActivity` + `AUTO_ACTION_TRANSLATE`.
- **Только обрезанный кадр в галерею.** Для OCR-действий `ScreenCaptureService` не коммитит сырой скриншот в галерею (`saveScreenshotUseCase`), а стейджит его во временный файл (app cache) и передаёт в crop-flow; в галерею пишется только обрезанный результат (`CameraOcrFlowManager.onCropConfirmed` -> `saveBitmapToGallery`), как в фото-flow.
- **Сразу в crop.** Полноэкранный `PhotoVideoStandaloneActivity` из OCR-пути убирается - жест ведёт прямо в crop+OCR.

## Scope

In:

- `startWithImage` entry point in [CameraOcrFlowManager] + [CameraOcrTranslateActivity] intent contract.
- Reroute screenshot gesture `OCR_TRANSLATE` and `TAKE_PHOTO_OCR_TRANSLATE` to the unified screen.
- OCR-action capture path in [ScreenCaptureService]: temp-stage instead of gallery-save the raw frame.
- Confirm OCR widgets ([CameraOcrTranslateWidgetProvider], [CaptureOcrPanelWidgetProvider]) land on the same unified screen (camera source already routes there - audit for parity).
- Retire the now-unused `AUTO_ACTION_TRANSLATE` gesture path in [PhotoVideoStandaloneActivity] if no other caller remains (see S1041 note).

Out:

- The in-viewer manual translate button (`menu_translate_image` -> `translateCurrentImage`) on normally-opened images stays as-is.
- OCR engine / translation model availability and quality (separate concern; existing download prompts apply).
- Redesign of the crop screen itself.

## Owner inputs (resolved 2026-07-13)

- **Gesture scope:** all OCR entry points (screenshot OCR/translate, take-photo OCR/translate, OCR widgets) route to the unified crop+OCR screen.
- **Saved artifacts:** only the cropped OCR frame is written to the gallery; the raw full-screen screenshot is temp-only.
- **Intermediate screen:** straight to the crop/OCR screen, no full-screen `PhotoVideoStandaloneActivity` first.

## Affected components

- [CameraOcrFlowManager](../app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt) - add `startWithImage`.
- [CameraOcrTranslateActivity](../app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt) - source extra + onCreate branch.
- [ScreenshotGestureActionDispatcher](../app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt) - reroute OCR actions.
- [ScreenCaptureService](../app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt) - temp-stage for OCR actions.
- [CameraOcrStorageManager](../app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrStorageManager.kt) - accept an external source file.
- Widgets: [CameraOcrTranslateWidgetProvider], [CaptureOcrPanelWidgetProvider] - parity audit.
- [PhotoVideoStandaloneActivity](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt) - retire gesture `AUTO_ACTION_TRANSLATE` path.

## Relationship to S1041

S1041 fixed the drawable-ready race for the standalone launch auto-actions generally. S1042 removes only the `AUTO_ACTION_TRANSLATE` branch (translate now reroutes to the crop screen), but S1041's `onImageReady` deferral **stays** - it still protects the surviving `AUTO_ACTION_DRAW` and `AUTO_ACTION_CROP_AND_SHARE` auto-actions, which also read the freshly-shown image. So S1041 is **not** archived; its device-test scenario shifts from "gesture -> OCR/translate" to a draw / crop-and-share gesture (its status note updated accordingly).

## Flavor / capability notes

- OCR/translate is `ENABLE_TRANSLATION`; screenshot gestures are `fms.screenCapture` (standard). Both already satisfied where `CameraOcrTranslateActivity` runs. No `BuildConfig.IS_*` guards in `src/main` (Rule 14) - the screenshot capture entry lives in the `screenCapture` source set.

## Resolved design decisions (2026-07-13)

- **Clipboard on OCR actions:** keep `copyScreenshotToClipboard` firing from the live bitmap (still useful), but suppress the "saved to gallery" toast for OCR actions since the raw frame is temp-only.
- **`TAKE_PHOTO_OCR_TRANSLATE`:** route straight to `CameraOcrTranslateActivity` (camera source) and drop the intermediate `PhotoCaptureLaunchActivity` trampoline + `AUTO_ACTION_TRANSLATE` - one unified path.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1041 (supersedes the screenshot auto-translate path it fixed)
- **UI/UX:** screenshot OCR gesture now opens the crop + language + OCR/translate screen directly (no full-screen viewer first); result and controls mirror the existing photo-OCR screen. No new screen designed - the photo-OCR UI is reused verbatim.
- **Flavor:** entry lives in the `screenCapture` source set; OCR/translate gated by `ENABLE_TRANSLATION`. No `BuildConfig.IS_*` guards in `src/main` (Rule 14).
- **Data/storage:** OCR actions no longer commit the raw screenshot to the gallery destination - only the cropped OCR frame is saved (raw frame staged to app cache, temp-only).
