---
name: screencapture-split-standard-vs-nolegal
description: Two flags - fms.screenCapture=on ships menu-capture in standard; fms.edgeGestureOverlay=on (since ~2026-06-26, confirmed 2026-07-01) NOW ships left-edge gesture actions in standard too
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

**SHIPPED-RELEASE REALITY (UPDATED 2026-07-01, critical for release notes / showcase / 4pda attribution):** committed `gradle.properties` now has `fms.screenCapture=on` (line 17) AND `fms.edgeGestureOverlay=on` (line 18). The edge-overlay flag FLIPPED from `off` to `on` between 2026-06-25 and 2026-07-01 - so the earlier "edge-gesture actions are effectively noLegal-only in shipped builds" claim is NO LONGER TRUE. Net for the shipped standard Play APK:
- Menu-screenshot capture suite: PRESENT (screenCapture=on) - `MenuScreenshotLauncher` set is non-empty, the settings test button + confirmable MediaProjection menu screenshot ship in standard.
- Left-edge GESTURE trigger + gesture-action dispatch: PRESENT (edgeGestureOverlay=on) - `src/standardScreenCapture` IS mounted, `screenGestureControllers` is non-empty on standard, the "Жесты с левого края" group + dispatch show, and every gesture-action feature (S0680, S0788-S0798 ..) ships in the standard Play APK. (`build.gradle.kts` PROPERTY default is still "off"; the shipped value comes from the `gradle.properties` override = "on".)
- noLegal (sideload): always mounts both `src/screenCapture` + `src/noLegal`, so capture AND edge gestures are unconditionally present.
- Device-test standard gesture wiring builds default-on now; no `-P` flag needed unless a build overrides it off.

**Inventory flavor != shipped flavor (RESOLVED):** `docs/ALL_FEATURES.jsonl` lists `standard` for S0559/S0621/S0622/S0623/S0663/S0662 because it records the build-script capability-at-flag-on, NOT the shipped reality. This mattered while the flags were off (2026-06-24, v2.60.6242.232: a workflow audit moved capture/edge-gesture bullets into the noLegal spoiler for exactly this reason). Since release 2.60.6270.802 (2026-06-27) shipped with both flags on, `[Standard]` labels for the capture family are accurate - the general lesson stands: before labeling a flag-gated feature `[Standard]` in showcase/release notes, check the committed `gradle.properties` value, not the inventory flavor.

**How to apply:** Distinguish the two flags but note BOTH ship in standard now (`fms.screenCapture=on` + `fms.edgeGestureOverlay=on`): the MENU-screenshot capture suite AND the LEFT-EDGE GESTURE trigger + gesture-action dispatch all ship in the standard Play APK as of 2026-07-01. So new gesture actions (enum/dispatcher/picker/strings/trampolines in `src/main`) are genuine STANDARD features - do not frame them as noLegal-only. Gate UI on the matching injected set (launchers vs gesture controllers), never on `BuildConfig.IS_*`. Standard device-test needs no `-P` flag now (defaults ship on); only pass `-Pfms.edgeGestureOverlay=off` if you deliberately want the pre-flip state. Capture-side code still goes in `src/screenCapture/` (shared) or `src/noLegal/` (overlay/accessibility); standard gesture controller in `src/standardScreenCapture/`; shared contracts + action enum/dispatcher/picker in `src/main`.
