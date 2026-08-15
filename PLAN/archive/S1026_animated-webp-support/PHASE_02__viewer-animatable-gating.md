# Phase 02 - viewer-animatable-gating

**Goal:** Gate the animated UI on the actual decoded drawable, not the extension, so static webp behaves correctly and animated webp plays.

## Steps

- [ ] **2.1** In `ui/player/helpers/AnimatedImageController.kt`, set `currentAnimatedDrawable` when the loaded `resource is Animatable` (the drawable-listener already knows this) and start it; drive the badge visibility + play/pause enablement off that actual-Animatable signal rather than `isAnimatedContent()`'s extension guess. Keep GIF behavior identical (GIF is already Animatable via Glide's GifDrawable). Verify: static webp -> no badge, no-op toggle gone; animated webp -> badge + working toggle. Compiles.
- [ ] **2.2** In `ui/player/ImageLoadingManager.kt` / `ImageLoadingGlideListeners.kt`, make the webp/apng load path hand the decoded drawable to `AnimatedImageController` the same way GIF does (so play/pause and PhotoView routing bind to the real animated drawable). Reuse the existing `resource is Animatable` check already logged there. Verify: webp animated drawable reaches the controller.
- [ ] **2.3** Remove the leftover webp diagnostic logging in `ImageLoadingGlideListeners` (the `animatable=..` probe) now that the behavior is resolved - or downgrade to a single Timber.v if still useful. No `Sxxxx` in permanent logs. Verify: no stray probe logging remains.

## Done criteria
- Animated webp/apng animate + toggle; static webp shows no bogus badge; GIF unchanged.
