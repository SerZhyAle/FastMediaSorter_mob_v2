---
name: screencapture-gates-gesture-capability
description: fms.screenCapture=on in gradle.properties (default), so a normal standard build DOES compile the standardScreenCapture twin/gesture-overlay; fms.edgeGestureOverlay/Tile are the off flags
metadata:
  type: project
---

`app_v2/build.gradle.kts` reads `screenCaptureStandardEnabled = (gradleProperty("fms.screenCapture").orNull ?: "on") != "off"`. As of 2026-06-26 `gradle.properties:17` sets `fms.screenCapture=on`, so a plain `assembleStandardDebug` (`a.ps1 dq`) **mounts** `src/standardScreenCapture/java` (+ `src/screenCapture/java`) and compiles the Play gesture-overlay/MediaProjection capture twin. A default standard build therefore validates the `standardScreenCapture` `ScreenGestureOverlayControllerImpl` and ships the Operations gesture group.

**Why:** S0630 split the Play-safe gesture controller into `src/standardScreenCapture`, gated by `fms.screenCapture`; the flag has since been turned **on** by default in `gradle.properties`. (Earlier this note said default-off - that was the prior state and is now stale.)

**How to apply:**
- Building/compiling a change to the standard `screencapture` twin: a normal standard debug build covers it (no `-P` needed) while `gradle.properties` keeps `fms.screenCapture=on`. Confirm the line before relying on it - it is a build-config toggle that can flip.
- The truly-off gating flags are separate: `fms.edgeGestureOverlay=off` (line 18) and `fms.edgeGestureTile=off` (line 21). Those gate the edge-gesture overlay/tile UI, not the screenCapture suite - don't conflate them.
- The noLegal flavor always has the capability via its accessibility path (`src/noLegal`), independent of `fms.screenCapture`.
- To validate BOTH gesture-overlay twins after an interface change, build `standard debug` (covers `src/standardScreenCapture` while the flag is on) + `noLegal debug` (covers `src/noLegal`).
