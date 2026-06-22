---
name: screencapture-split-standard-vs-nolegal
description: screenCapture source set + MenuScreenshotLauncher are standard+noLegal; only the edge-gesture overlay controller and accessibility path stay noLegal-only
type: project
---

The `src/screenCapture/` source set is NO LONGER noLegal-only. Since S0559 (verified 2026-06-22) `app_v2/build.gradle.kts` mounts it into BOTH `standard` (line ~574) and `noLegal` (line ~592). The split now runs through DI bindings, not the source-set mount:

**standard + noLegal (capture ENGINE shared):**
- `MenuScreenshotLauncher` (bound in `src/screenCapture/di/ScreenCaptureLauncherModule.kt`) → the store-safe confirmable MediaProjection menu-screenshot (`ScreenCaptureConsentActivity` + `ScreenCaptureService`). The settings test button `btnTakeScreenshotNow` is gated on this launcher set being non-empty (`OperationsCaptureManager.setupScreenshotAction`).

**noLegal-only (overlay strip + silent path):**
- `ScreenGestureOverlayController` `@IntoSet` binding exists ONLY in `src/noLegal/.../di/ScreenCaptureModule.kt`; `src/main/.../di/ScreenGestureOverlayModule.kt` declares just an empty `@Multibinds Set`. So the injected `screenGestureControllers` set is empty on standard, and `OperationsGesturesManager.setup()` hides the whole "Жесты с левого края" settings group (`binding.groupScreenGestures` GONE). The SYSTEM_ALERT_WINDOW edge strip + AccessibilityService silent screenshots are not declared/mounted on standard (Play-review risk, S0418/S0423).

**S0621 (Draft, parked 2026-06-22):** owner reports the edge-gesture group SHOULD be partially available on standard (S0418 intent, Play-safe MediaProjection path) but the settings UI "didn't pull through" - because the controller binding was never added to the standard source set. Fix = expose a Play-safe `ScreenGestureOverlayController` in `src/standard`.

**How to apply:** Don't assume "screenshot capture = noLegal-only" anymore. Menu/MediaProjection capture is standard-shipping; only the always-on edge-overlay + accessibility silent capture are noLegal-gated. Gate UI on the matching injected set (launchers vs controllers), never on `BuildConfig.IS_*`. Capture-side code still goes in `src/screenCapture/` (shared) or `src/noLegal/` (overlay/accessibility); shared contracts in `src/main`.
