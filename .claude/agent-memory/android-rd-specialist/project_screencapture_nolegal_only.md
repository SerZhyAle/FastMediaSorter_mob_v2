---
name: screencapture-nolegal-only
description: Gesture screenshot capture (screenCapture source set) is mounted only into the noLegal flavor, not standard
type: project
---

The edge-gesture screenshot capture feature (overlay + MediaProjection FGS) lives in the `src/screenCapture/` source set, which `app_v2/build.gradle.kts` mounts **only into the `noLegal` flavor**. The `ScreenGestureOverlayController` multibinding (`@IntoSet`) that activates it exists only in `src/noLegal/java/.../di/ScreenCaptureModule.kt`. So in standard/lite/photos/legacy/vr the injected `screenGestureControllers` set is empty and the whole gesture-settings group in `OperationsSettingsFragment` (rendered inside `if (screenGestureControllers.isNotEmpty())`) is hidden.

**Why:** S0418/S0423 kept it noLegal-only - the SPECIAL_USE / SYSTEM_ALERT_WINDOW manifest declarations are a Play-review risk, so they're not mounted into store flavors.

**How to apply:** Any work on screenshot-gesture capture is noLegal-scoped. Place capture-side code in `src/screenCapture/` (or `src/noLegal/`); shared contracts/utilities go in `src/main`. Don't add `BuildConfig.IS_*` guards - the feature gates itself via the empty-vs-nonempty injected controller set. Note: `docs/FEATURES.md` labels this `[Standard]`, which does not match the current runtime gating (possible staged-toward-standard intent or a doc lag).
