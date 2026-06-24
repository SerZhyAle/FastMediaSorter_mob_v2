---
name: screencapture-split-standard-vs-nolegal
description: screenCapture source set + MenuScreenshotLauncher are standard+noLegal; only the edge-gesture overlay controller and accessibility path stay noLegal-only
type: project
---

The `src/screenCapture/` source set is NO LONGER noLegal-only. Since S0559 (verified 2026-06-22) `app_v2/build.gradle.kts` mounts it into BOTH `standard` (line ~574) and `noLegal` (line ~592). The split now runs through DI bindings, not the source-set mount:

**standard + noLegal (capture ENGINE shared):**
- `MenuScreenshotLauncher` (bound in `src/screenCapture/di/ScreenCaptureLauncherModule.kt`) → the store-safe confirmable MediaProjection menu-screenshot (`ScreenCaptureConsentActivity` + `ScreenCaptureService`). The settings test button `btnTakeScreenshotNow` is gated on this launcher set being non-empty (`OperationsCaptureManager.setupScreenshotAction`).

**Standard (Play-safe gesture controller, since S0630 Verified):**
- A Play-safe `ScreenGestureOverlayControllerImpl` `@IntoSet` now lives in `src/standardScreenCapture/.../screencapture/` + `.../di/ScreenCaptureModule.kt`, mounted into the standard variant. So `screenGestureControllers` is NO LONGER empty on standard and `OperationsGesturesManager.setup()` shows the "Жесты с левого края" group. S0621 (`BlockNeedUserTest`) is the device-test hotfix for this standard wiring.

**noLegal-only (always-on strip + silent path):**
- The SYSTEM_ALERT_WINDOW always-on edge strip + AccessibilityService silent screenshots stay declared/mounted ONLY in `src/noLegal` (Play-review risk, S0418/S0423). `src/main/.../di/ScreenGestureOverlayModule.kt` still declares the empty `@Multibinds Set`; bindings come from the flavor buckets.

**How to apply:** Don't assume "screenshot capture = noLegal-only" anymore. Menu/MediaProjection capture is standard-shipping; only the always-on edge-overlay + accessibility silent capture are noLegal-gated. Gate UI on the matching injected set (launchers vs controllers), never on `BuildConfig.IS_*`. Capture-side code still goes in `src/screenCapture/` (shared) or `src/noLegal/` (overlay/accessibility); shared contracts in `src/main`.
