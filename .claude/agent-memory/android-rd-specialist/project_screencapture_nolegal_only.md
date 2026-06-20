---
name: screencapture-nolegal-only
description: Gesture screenshot capture (screenCapture source set) is mounted only into the noLegal flavor, not standard
type: project
---

The edge-gesture screenshot capture feature lives in the `src/screenCapture/` source set, which `app_v2/build.gradle.kts:583` mounts **only into the `noLegal` flavor**. The `ScreenGestureOverlayController` multibinding (`@IntoSet`) that activates it exists only in `src/noLegal/java/.../di/ScreenCaptureModule.kt`. So in standard/lite/photos/legacy/vr the injected `screenGestureControllers` set is empty and the whole gesture-settings group in `OperationsSettingsFragment` (rendered inside `if (screenGestureControllers.isNotEmpty())`) is hidden.

**Two capture mechanisms, both noLegal-only - there is no "plain" screenshotter in standard:**
- MediaProjection FGS path (`src/screenCapture/`): `ScreenCaptureService` (foregroundServiceType=mediaProjection), `ScreenCaptureConsentActivity` (consent dialog), `OverlayHostService` (SPECIAL_USE FGS hosting the SYSTEM_ALERT_WINDOW edge strip). This is the API 26..29 fallback (S0418).
- AccessibilityService path ("через инвалида", `src/noLegal/`): `ScreenshotAccessibilityService` (TYPE_ACCESSIBILITY_OVERLAY, no SYSTEM_ALERT_WINDOW, silent screenshots) + `screenshot_accessibility_service_config.xml`. This is the dialog-free API 30+ primary path (S0405).
- `src/standard/AndroidManifest.xml` and `src/photos/...` carry only a comment explaining why the feature is NOT declared (a bare grep for "MediaProjection" hits that comment, not a real declaration).

**Why:** S0418/S0423 kept it noLegal-only - the SPECIAL_USE / SYSTEM_ALERT_WINDOW manifest declarations are a Play-review risk, so they're not mounted into store flavors.

**How to apply:** Any work on screenshot-gesture capture is noLegal-scoped. Place capture-side code in `src/screenCapture/` (or `src/noLegal/`); shared contracts/utilities go in `src/main`. Don't add `BuildConfig.IS_*` guards - the feature gates itself via the empty-vs-nonempty injected controller set. Note: `docs/FEATURES.md` labels this `[Standard]`, which does not match the current runtime gating (possible staged-toward-standard intent or a doc lag).
