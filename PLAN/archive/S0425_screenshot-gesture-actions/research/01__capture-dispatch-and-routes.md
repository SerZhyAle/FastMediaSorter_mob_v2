# Research 01 - Capture dispatch point + post-capture routes

**Spec:** S0425 screenshot-gesture-actions
**Date:** 2026-06-15
**Method:** class-catalog query + source read (read-only research agent)

## Key classes

| Class | Path | Role |
|---|---|---|
| `ScreenGestureOverlayManager` | `app_v2/src/screenCapture/java/.../screencapture/ScreenGestureOverlayManager.kt` | Touch → angle detection → `onGestureMatched()` callback (single, no direction payload) |
| `ScreenshotAccessibilityService` | `app_v2/src/noLegal/java/.../screencapture/ScreenshotAccessibilityService.kt` | noLegal API 30+ silent capture; `captureNow()` → `processScreenshotResult()` → `saveBitmap()` |
| `OverlayHostService` | `app_v2/src/screenCapture/java/.../screencapture/OverlayHostService.kt` | noLegal API 26-29 + future Play host; gesture → `ScreenCaptureConsentActivity` |
| `ScreenCaptureService` | `app_v2/src/screenCapture/java/.../screencapture/ScreenCaptureService.kt` | MediaProjection path; `processCapture()` → `SaveScreenshotUseCase` |
| `SaveScreenshotUseCase` | `app_v2/src/main/java/.../domain/usecase/SaveScreenshotUseCase.kt` | Writes PNG; returns `SaveResult.Success(fileName, destinationLabel)` - NO Uri/File |
| `ScreenshotDestinationPolicy` | `app_v2/src/main/java/.../util/ScreenshotDestinationPolicy.kt` | resource id → `Target` (SelectedResource / PublicCollection) |
| `ScreenshotSettingsStore` | `app_v2/src/main/java/.../data/repository/settings/ScreenshotSettingsStore.kt` | DataStore; 3 keys today incl. dead `screenshot_gesture_down_enabled` |
| `AppSettings` | `app_v2/src/main/java/.../domain/model/AppSettings.kt` | L166-168 gesture fields |
| `OperationsSettingsFragment` | `app_v2/src/main/java/.../ui/settings/fragments/OperationsSettingsFragment.kt` | "Screen Gestures" section L628-998; 1172 LOC |
| `ScreenGestureOverlayController` (+Impl) | `core/screencapture/` (iface) + `noLegal/`, `screenCapturePlay/` (impls) | flavor-agnostic contract, Hilt `@IntoSet` |
| `PhotoVideoStandaloneActivity` | `app_v2/src/main/java/.../ui/player/standalone/PhotoVideoStandaloneActivity.kt` | standalone viewer; DRAW via `ensureDrawHelper().enterDrawMode()`; OCR via `TranslationManager` |
| `StandaloneDrawSaveHelper` | `app_v2/src/main/java/.../ui/player/standalone/StandaloneDrawSaveHelper.kt` | `enterDrawMode()` (needs alive Activity + inflated layout) |
| `ImageDrawOverlayManager` | `app_v2/src/main/java/.../ui/player/helpers/ImageDrawOverlayManager.kt` | shared draw canvas (781 LOC) |
| `SystemShareInvoker` | `app_v2/src/main/java/.../core/share/SystemShareInvoker.kt` | `ACTION_SEND` facade; `invokeFiles(uris, mime, preferredPackage)` |
| `TranslationManager` | `app_v2/src/main/java/.../ui/player/helpers/TranslationManager.kt` | in-process OCR+translate from BITMAP; `recognizeAndTranslate(bitmap, src, tgt)` |
| `CameraOcrTranslateActivity` | `app_v2/src/main/java/.../ui/cameraocr/CameraOcrTranslateActivity.kt` | camera-only; NO file/bitmap input - not reusable for screenshot |

## Two parallel capture pipelines (both end at SaveScreenshotUseCase)

1. **noLegal API 30+ (silent a11y):** `ScreenshotAccessibilityService` → `ScreenGestureOverlayManager.onGestureMatched()` → `captureNow()` → `takeScreenshot()` → `processScreenshotResult()` → `saveBitmap()` → `SaveScreenshotUseCase` → toast. No consent dialog.
2. **noLegal API 26-29 / future Play (MediaProjection):** `OverlayHostService` → gesture → `ScreenCaptureConsentActivity` (consent) → `ScreenCaptureService` → `processCapture()` → `SaveScreenshotUseCase` → toast.

Single logical dispatch point per pipeline = right after `SaveScreenshotUseCase.invoke(...)` returns `Success`. The post-capture action must hook here in BOTH services.

## Hard findings driving the tactical plan

- **No locatable file returned.** `SaveResult.Success(fileName, destinationLabel)` - no `Uri`/`File`. Player/DRAW/OCR/SHARE all need a Uri. Fix: extend `SaveResult.Success` with `savedUri: Uri?` (MediaStore insert URI for PublicCollection; FileProvider/SAF Uri for SelectedResource).
- **No direction payload.** `onGestureMatched: () -> Unit` and only ONE angle window (~25-65° down-right diagonal from left-edge strip). Three directions (down/right/up) require: new angle buckets in `ScreenGestureOverlayManager` + change callback to carry a direction enum. BOTH `ScreenshotAccessibilityService` and `OverlayHostService` instantiate the manager independently - both updated.
- **Dead setting.** `screenshotGestureDownEnabled` stored + shown in UI but never read by any capture service. S0425 replaces it with per-gesture action enum keys (migration: drop bool, add 3 enum string keys; bool had no runtime effect so no behavioural migration needed).
- **OCR route mismatch.** `CameraOcrTranslateActivity` always opens camera (no file input). Only in-process bitmap path exists (`TranslationManager` inside standalone viewer). For OCR action: open saved screenshot in `PhotoVideoStandaloneActivity` and auto-trigger translate via a launch extra.
- **DRAW needs live Activity.** `StandaloneDrawSaveHelper.enterDrawMode()` needs inflated layout. For DRAW action: launch `PhotoVideoStandaloneActivity` with an `EXTRA_OPEN_DRAW`-style extra that triggers draw mode on arrival.
- **Background-activity start.** Both services run in service context; launching any Activity (player/DRAW/OCR) or the SHARE chooser needs `FLAG_ACTIVITY_NEW_TASK` and is subject to Android 10+ background-activity-start restrictions. The a11y service path is generally allowed; MediaProjection foreground service must add the flag.
- **Flavor gating.** Mechanism mounted only in `noLegal` today (Hilt multibound `Set<ScreenGestureOverlayController>` empty elsewhere). OCR-translate gated by `CAP_OCR`+`CAP_TRANSLATION` (noLegal has both). `screenCapturePlay` source set exists but is NOT mounted in any flavor (S0418 leftover).

## Risks (for tactical §risks)

- `SaveResult` API change ripples to all call sites (High).
- Three-direction geometry redesign in `ScreenGestureOverlayManager` - angle windows must not overlap (High; UX decision).
- Background activity start from service may fail silently on some OEMs (Med).
- `OperationsSettingsFragment` already 1172 LOC; adding 3 pickers risks 1500 limit → extract a helper manager (Med).

## Untested classes (no unit tests)

`SaveScreenshotUseCase`, `ScreenGestureOverlayManager`, `ScreenshotAccessibilityService`, `ScreenCaptureService`, `OverlayHostService`, `ScreenshotSettingsStore`. Only `ScreenshotDestinationPolicy` has a test.

## /spec-draft candidates

1. Dead `screenshotGestureDownEnabled` - IN-SCOPE for S0425 (replaced here), not parked.
2. Orphaned `src/screenCapturePlay/` source set not mounted in any flavor (S0418 leftover) - OUT-OF-SCOPE, parked.
