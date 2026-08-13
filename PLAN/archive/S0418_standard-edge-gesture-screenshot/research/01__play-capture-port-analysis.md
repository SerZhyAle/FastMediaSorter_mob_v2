# 01 - Play-capture port analysis (code surface + decomposition)

**Research item:** §5 (approach), §6.1 (Play declarations)
**Date:** 2026-06-15
**Status:** Resolved - decomposition fixed; ready for tactical phases.

## Owner decision (2026-06-15)

- `standard` + `photos` (Play): capture via **MediaProjection only** (per-shot consent + system indicator). No accessibility service declared.
- `noLegal` (sideload): keep the silent `AccessibilityService.takeScreenshot` path (API 30+) with MediaProjection fallback (API 26..29). **Unchanged** by this port.

## Already flavor-agnostic (src/main - reused as-is, not touched)

- `core/screencapture/ScreenGestureOverlayController.kt` - capability interface.
- `core/screencapture/ScreenGestureOverlayStartupCoordinator.kt` - cold-start restore; iterates the multibound `Set<ScreenGestureOverlayController>`; no-ops on empty set.
- `di/ScreenGestureOverlayModule.kt` - `@Multibinds` empty-set default (a flavor with no impl compiles + the set is empty).
- `domain/usecase/SaveScreenshotUseCase.kt`, `util/ScreenshotDestinationPolicy.kt` - save + destination resolution.
- `data/repository/settings/ScreenshotSettingsStore.kt`, `domain/model/AppSettings.kt` fields - settings persistence.
- `ui/settings/fragments/PlaybackSettingsFragment.kt` - gates the "Системные приложения" section on `screenGestureControllers.isEmpty()` (runtime, not BuildConfig). Adding a bound controller to a flavor makes the entry appear automatically.

Consequence: no BuildConfig flag is needed. The capability is gated purely by whether a flavor contributes a `@IntoSet` controller binding.

## Current noLegal implementation (src/noLegal/java/.../screencapture)

- `ScreenGestureOverlayControllerImpl.kt` - API routing: a11y path (>=R, service enabled) vs MediaProjection (`canDrawOverlays`). a11y-aware.
- `ScreenGestureOverlayManager.kt` - transparent edge strip view + diagonal gesture recognizer. No a11y reference.
- `OverlayHostService.kt` - FGS (`specialUse`) hosting the strip on the MediaProjection path; launches consent. a11y only in a comment.
- `ScreenCaptureConsentActivity.kt` - launches `MediaProjectionManager.createScreenCaptureIntent()`. No a11y reference.
- `ScreenCaptureService.kt` - FGS (`mediaProjection`); VirtualDisplay + ImageReader, one frame, save. No a11y reference.
- `ScreenshotAccessibilityService.kt` + `ScreenshotAccessibilityServiceHolder.kt` - a11y path. **noLegal-exclusive.**
- `di/ScreenCaptureModule.kt` - `@Binds @IntoSet` controller.

Verified: a11y references (`ScreenshotAccessibilityService*`) exist only in the a11y classes and the controller impl. The four machinery classes are a11y-free (one stale comment in `OverlayHostService`).

## Decomposition (follows project shared-source-set convention)

The project already mounts shared pseudo-source-sets into each flavor via `kotlin.directories.add("src/<set>/java")` (e.g. `streamingEnabled`, `cloudEnabled`, `vrStub`). Reuse that pattern.

**New shared set `src/screenCapture/` (machinery, a11y-free).** Mounted into `standard`, `photos`, `noLegal`.
- Move from `src/noLegal/java/.../screencapture/`: `ScreenGestureOverlayManager`, `OverlayHostService`, `ScreenCaptureConsentActivity`, `ScreenCaptureService`.
- Move from `src/noLegal/res/drawable/`: `ic_notification_screen_capture.xml`.

**New Play set `src/screenCapturePlay/java` (controller + binding).** Mounted into `standard`, `photos` only.
- `ScreenGestureOverlayControllerImpl` - MediaProjection-only variant: `isOverlayPermissionGranted = canDrawOverlays`; `permissionSettingsIntent = ACTION_MANAGE_OVERLAY_PERMISSION`; `permissionRationaleResId = screenshot_overlay_permission_rationale`; `isFallbackCaptureAvailable = false`; `setEnabled` toggles `OverlayHostService` only (no a11y holder). Same FQN/package as the noLegal impl - no per-variant collision because the two sets never compile together.
- `di/ScreenCaptureModule` - `@Binds @IntoSet` for the Play controller.

**Stays in `src/noLegal` (a11y-exclusive).** `ScreenshotAccessibilityService`, `ScreenshotAccessibilityServiceHolder`, the a11y-aware `ScreenGestureOverlayControllerImpl`, the noLegal `ScreenCaptureModule`. noLegal mounts `src/screenCapture` for the moved machinery; its a11y service still sees `ScreenGestureOverlayManager` from the shared set.

## Manifests

- `src/standard/AndroidManifest.xml` and `src/photos/AndroidManifest.xml` already exist - edit directly (auto-detected + merged; no `manifest.srcFile` override on these flavors).
- Add to both: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `FOREGROUND_SERVICE_SPECIAL_USE`; `ScreenCaptureConsentActivity`; `OverlayHostService` (`specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`); `ScreenCaptureService` (`mediaProjection`). **No accessibility service** (the Play-risk vector stays out by construction).
- noLegal manifest unchanged.

## Open / deferred

- Play Console declaration forms for `SYSTEM_ALERT_WINDOW` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` - release-time gate, not a build blocker (§6.1).
- `screenshotGestureDownEnabled` is a persisted+rendered but unread setting (S0405 legacy) - out of scope; park as a separate ticket (§6.3).

## Sources

- `app_v2/build.gradle.kts` sourceSets block (lines ~548-602), onVariants manifest injection (~888-907).
- `app_v2/src/noLegal/AndroidManifest.xml` (services + perms).
- `temp/done/S0405_always-on-top-overlay-screenshot/` (parent tactical + research).
