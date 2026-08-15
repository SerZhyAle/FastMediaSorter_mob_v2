# Phase 03 - Video rotation apply (effect pipeline + aspect-fit)

**Status:** Pending

The crux, de-risked by research: reuse the existing Media3 `setVideoEffects()` GL pipeline in BOTH engines; add a rotation effect; compensate aspect at 90/270. This half is device-verification-gated (crash-resurface, frame-to-frame aspect) - it is the reason the ticket lands `BlockNeedUserTest`.

## Files touched

- `ui/player/VideoColorProcessor.kt` (effect builder - add rotation effect, or a new sibling builder)
- `ui/player/helpers/PlayerSetupHelper.kt` (`applyConfiguredVideoEffects()` `:98-136` - internal engine)
- `ui/player/helpers/StandaloneViewManager.kt` (`:518-528` - standalone engine)
- `ui/player/VideoPlayerLifecycleHelper.kt` (`:51-55` - drain-before-release, verify still covers the new effect)

## Steps

1. Add a rotation effect to the effect composition. Use `androidx.media3.effect.ScaleAndRotateTransformation` (already on classpath via `media3-effect:1.2.1`). Build it from the session angle: rotation applied CLOCKWISE (spec: по часовой) - note Media3 rotation is counter-clockwise-positive, so use `-angle` (or the CW-equivalent) and verify direction on device.
   - Verify: `Grep ScaleAndRotateTransformation` present; effect built only when `angle != 0` (angle 0 -> omit to keep the zero-cost path).
2. Insert the rotation effect into the SAME list built by `applyConfiguredVideoEffects()` (internal) and `StandaloneViewManager` (standalone), composed with the existing stereo/color effects. Preserve ALL THREE Media3 1.2.1 workarounds:
   - 80ms debounce before `setVideoEffects()` (`PlayerSetupHelper.kt:94-96`).
   - Defer until `videoSizeKnown`/`onVideoSizeChanged` (`:111-113`).
   - Drain `setVideoEffects(emptyList())` before `release()` (`VideoPlayerLifecycleHelper.kt:51-55`; standalone `releaseVideoPlayer()`).
   - Verify: none of the three workarounds removed; rotation joins the existing composed list (not a separate `setVideoEffects` call that clobbers color/stereo).
3. Aspect-fit at 90/270: `PlayerView` won't auto-swap w<->h for a post-decode effect. Compensate so the rotated frame fits without crop/stretch - either a compensating scale factor inside the effect chain (`ScaleAndRotateTransformation` scale args using the known video w/h from `onVideoSizeChanged`) or re-measure the player container. Prefer the in-effect scale (no `AspectRatioFrameLayout` reference exists in Kotlin; avoid fighting PlayerView's internal sizing).
   - Verify: at 90/270 the scale basis uses swapped video dimensions; angle 0/180 unchanged.
4. Re-apply on file change: the effect list is already rebuilt per playback setup; ensure the current session angle is read at each `applyConfiguredVideoEffects()` so the next video inherits it.
   - Verify: trace that the angle is sourced from session state at each rebuild.

## Done criteria

- Rotating a video cycles 90/180/270/0 visually via the effect pipeline, aspect preserved, controls unaffected (sibling overlay).
- All three Media3 crash-workarounds intact; rotation composes with existing effects (color/stereo not dropped).
- Both engines covered. Project compiles.

## Device-verification (deferred to /spec-test-device - the BlockNeedUserTest gate)

- No `TexturePool.freeTexture` / `Presentation.createForWidthAndHeight` crash with the 4th concurrent effect.
- Aspect-fit renders clean frame-to-frame at 90/270 (no flicker/crop/stretch).
- PiP / fullscreen / cast interaction with an active rotate effect.
- Rotation direction is clockwise on-screen.
