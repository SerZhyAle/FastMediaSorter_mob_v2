# S0630 - Gate screen-capture out of standard for a Play-fast release (no new FGS declaration)

**Status:** Archived

> Owner-directed release sequencing after v2.60.6221.755 was blocked by the Play
> Foreground-service-permissions gate. This is "Release A": ship the bug fixes fast with the
> screen-capture features hidden, so the standard manifest carries no new FGS type and Google needs
> no new declaration. "Release B" (re-enable + declare) follows and uses S0629 materials.

## 0. Problem

The standard bundle declares two NEW foreground-service types the approved Play declaration
(MEDIA_PLAYBACK + MICROPHONE) does not cover, so the production commit returns HTTP 403:

- `FOREGROUND_SERVICE_MEDIA_PROJECTION` - menu/Operations screenshot (S0559), service
  `screencapture.ScreenCaptureService`.
- `FOREGROUND_SERVICE_SPECIAL_USE` - left-edge gesture overlay (S0621), service
  `screencapture.OverlayHostService` (high Play-rejection risk).

## 2. Goal

Produce a standard build whose merged manifest carries NEITHER new FGS permission, WITHOUT deleting
code, with a one-line clean revert for Release B. The settings rows for both features hide and
disable themselves (empty Hilt multibind sets), matching the existing photos/lite steady state.

## 3. Mechanism (implemented)

- New gradle flag `fms.screenCapture` read in `app_v2/build.gradle.kts` (default `on`; `off` for
  Release A, set in `gradle.properties` so the release builder picks it up with no `-P` plumbing).
- When `off`, the standard flavor does NOT mount `src/screenCapture/java|res` nor the relocated
  `src/standardScreenCapture/java`, and the `onVariants` block injects neither
  `src/screenCapture/AndroidManifest.xml` nor `src/standardScreenCapture/AndroidManifest.xml` for
  standard. Result: no MEDIA_PROJECTION, no SPECIAL_USE, no SYSTEM_ALERT_WINDOW, no capture
  components in the standard merged manifest.
- The SPECIAL_USE block + the standard-only `ScreenGestureOverlayControllerImpl` +
  `di/ScreenCaptureModule` were relocated from `src/standard` into a new `src/standardScreenCapture`
  source set (mounted only when enabled). `src/standard/AndroidManifest.xml` is now a bare overlay.
- Empty-set tolerance: `@Multibinds Set<ScreenGestureOverlayController>` /
  `Set<MenuScreenshotLauncher>` resolve empty; consumers early-return on empty, so the gesture card
  and the menu-screenshot button hide and no code path starts an undeclared FGS. No src/main edit.
- noLegal/photos/lite/legacy/vr untouched (flag is standard-only; noLegal keeps its own impl).

## 4. Acceptance

- `assembleStandardDebug` (flag off) compiles; merged standard manifest contains neither
  MEDIA_PROJECTION nor SPECIAL_USE nor the capture components.
- `assembleStandardDebug -Pfms.screenCapture=on` restores both permissions + components (revert-clean
  default).
- noLegal release still carries both FGS types + accessibility service.
- On device (Release A QA): the left-gesture settings block and the Operations screenshot action are
  not visible; the app runs normally; bug fixes present.

## 5. Reversal (Release B)

- Remove `fms.screenCapture=off` from `gradle.properties` (or set `on`) -> full feature returns.
- All gating is one build.gradle.kts flag + three guards + the relocated source set; nothing deleted.

## Related

- S0559 (menu screenshot / MEDIA_PROJECTION), S0621 (left-edge gesture overlay / SPECIAL_USE).
- S0629 (Google declaration materials for Release B).
- S0628 (publisher attach-existing-bundle), Play FGS gate.

## Last Audit

**Date:** 2026-06-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 16 · WARN 0 · FAIL 0 · MANUAL 4 · EXEMPT 1

### Notes

- Gate verified static: `screenCaptureStandardEnabled` flag (build.gradle.kts:171), `fms.screenCapture=off` in gradle.properties, source-set mount (build.gradle.kts:583-589) and both manifest injections (build.gradle.kts:960-969) all gated on the flag for standard; noLegal mounts/injects unconditionally.
- Relocation clean: `src/standardScreenCapture/{ScreenCaptureModule.kt, ScreenGestureOverlayControllerImpl.kt, AndroidManifest.xml}` present; `src/standard/AndroidManifest.xml` is a bare overlay; no orphaned duplicates left in `src/standard` (Rule 21 PASS). `src/main/AndroidManifest.xml` declares no MEDIA_PROJECTION / SPECIAL_USE / SYSTEM_ALERT_WINDOW, so the gate is not bypassed via main.
- Empty-set tolerance: `@Multibinds Set<ScreenGestureOverlayController>` (ScreenGestureOverlayModule) and `Set<MenuScreenshotLauncher>` (MenuScreenshotLauncherModule); consumers `OperationsGesturesManager.setup()` and `OperationsCaptureManager.setupScreenshotAction()` early-return / hide on empty.
- FEATURES trilingual EXEMPT: internal release-engineering gate (hides capability for Release A), no showcase addition.
- The surviving `Timber.d("S0621:` tag in OperationsGesturesManager.kt is legitimate (S0621 is BlockNeedUserTest), not stale - left in place.

### Manual / on-device

- [ ] `assembleStandardDebug` (flag off via gradle.properties) compiles; merged standard manifest carries neither MEDIA_PROJECTION nor SPECIAL_USE nor SYSTEM_ALERT_WINDOW nor capture components.
- [ ] `assembleStandardDebug -Pfms.screenCapture=on` restores both permissions + components (revert-clean default).
- [ ] noLegal release still carries both FGS types + accessibility service.
- [ ] On device (Release A QA): left-gesture settings block + Operations screenshot action not visible; app runs normally; bug fixes present.
