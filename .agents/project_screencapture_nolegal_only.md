---
name: screencapture-split-standard-vs-nolegal
description: Two flags - fms.screenCapture=on ships menu-capture in standard; fms.edgeGestureOverlay=off keeps left-edge gesture actions noLegal-only in shipped builds
type: project
---

The `src/screenCapture/` source set is NO LONGER noLegal-only. Since S0559 (verified 2026-06-22) `app_v2/build.gradle.kts` mounts it into BOTH `standard` (line ~574) and `noLegal` (line ~592). The split now runs through DI bindings, not the source-set mount:

**standard + noLegal (capture ENGINE shared):**
- `MenuScreenshotLauncher` (bound in `src/screenCapture/di/ScreenCaptureLauncherModule.kt`) → the store-safe confirmable MediaProjection menu-screenshot (`ScreenCaptureConsentActivity` + `ScreenCaptureService`). The settings test button `btnTakeScreenshotNow` is gated on this launcher set being non-empty (`OperationsCaptureManager.setupScreenshotAction`).

**Standard (Play-safe gesture controller, since S0630 Verified):**
- A Play-safe `ScreenGestureOverlayControllerImpl` `@IntoSet` now lives in `src/standardScreenCapture/.../screencapture/` + `.../di/ScreenCaptureModule.kt`, mounted into the standard variant. So `screenGestureControllers` is NO LONGER empty on standard and `OperationsGesturesManager.setup()` shows the "Жесты с левого края" group. S0621 (`BlockNeedUserTest`) is the device-test hotfix for this standard wiring.

**noLegal-only (always-on strip + silent path):**
- The SYSTEM_ALERT_WINDOW always-on edge strip + AccessibilityService silent screenshots stay declared/mounted ONLY in `src/noLegal` (Play-review risk, S0418/S0423). `src/main/.../di/ScreenGestureOverlayModule.kt` still declares the empty `@Multibinds Set`; bindings come from the flavor buckets.

**TWO-FLAG SPLIT (as of working tree 2026-06-25):** the capture suite and the edge-gesture overlay are now controlled by SEPARATE gradle properties:
- `fms.screenCapture` (build.gradle.kts ~170, default "on") - mounts `src/screenCapture` (MediaProjection consent/capture suite, menu screenshot) into standard.
- `fms.edgeGestureOverlay` (build.gradle.kts ~172, default "off") - mounts `src/standardScreenCapture` (the standard Play-safe `ScreenGestureOverlayControllerImpl`, i.e. the left-edge swipe strip + gesture-action dispatch) into standard.

**SHIPPED-RELEASE REALITY (critical for release notes / showcase / 4pda attribution):** committed `gradle.properties` now has `fms.screenCapture=on` (line 17) and `fms.edgeGestureOverlay=off` (line 18) - this CHANGED from the earlier S0630 "Release-A FGS-free" state where screenCapture itself was off. Net for the shipped standard Play APK:
- Menu-screenshot capture suite: PRESENT (screenCapture=on) - `MenuScreenshotLauncher` set is non-empty, the settings test button + confirmable MediaProjection menu screenshot ship in standard.
- Left-edge GESTURE trigger + gesture-action dispatch: ABSENT (edgeGestureOverlay=off) - `src/standardScreenCapture` is NOT mounted, so `screenGestureControllers` stays empty on standard and the "Жесты с левого края" group/dispatch hides. Therefore every gesture-action feature (including new ones like S0680 crop-and-share) is effectively noLegal-only in shipped builds; standard gets it only when built with `-Pfms.edgeGestureOverlay=on`.
- noLegal (sideload): always mounts both `src/screenCapture` + `src/noLegal`, so capture AND edge gestures are unconditionally present.

**Inventory flavor != shipped flavor:** `docs/ALL_FEATURES.jsonl` lists `standard` for S0559/S0621/S0622/S0623/S0663/S0662 because it records the build-script capability-at-flag-on, NOT the shipped reality. When writing WHATS_NEW / FEATURES showcase `[Standard]` labels / 4pda "Что нового", treat these six as effectively noLegal-only until a Release-B flips `fms.screenCapture=on`. (Confirmed 2026-06-24 during v2.60.6242.232 release: a workflow audit moved screen-capture/edge-gesture bullets from the main Play post into the noLegal spoiler for exactly this reason. The public `docs/FEATURES.md` currently labels App panel / Screen capture / Edge-gesture actions `[Standard]` - a likely over-claim pending owner decision on Release-B timing.)

**How to apply:** Distinguish the two flags. The MENU-screenshot capture suite DOES ship in standard now (`fms.screenCapture=on`); the LEFT-EDGE GESTURE trigger does NOT (`fms.edgeGestureOverlay=off`). So "screen capture" is shipped-standard, but "edge-gesture actions" are still effectively noLegal-only until a release flips `fms.edgeGestureOverlay=on`. Gate UI on the matching injected set (launchers vs gesture controllers), never on `BuildConfig.IS_*`. Device-test standard gesture wiring with `-Pfms.edgeGestureOverlay=on` (and capture with `-Pfms.screenCapture=on`). Capture-side code still goes in `src/screenCapture/` (shared) or `src/noLegal/` (overlay/accessibility); standard gesture controller in `src/standardScreenCapture/`; shared contracts + action enum/dispatcher/picker in `src/main`.
