# S1026 research 01 - animated-image pipeline + the webp gap

**Date:** 2026-07-14
**Scope:** app_v2, all flavors (shared image code, no flavor guards).

## What already exists (no work needed)

- `AnimatedImageController.isAnimatedContent(file, path)` (`:18-24`) already returns true for `.webp`/`.apng` by extension; flips `usePhotoView`, shows the animated badge, enables PhotoView.
- `PlayerDialogHelper.isAnimatedImagePath()` (`:431-434`) already includes webp/apng (GIF-editor entry gating).
- `GifEditorDialog` already shows extract-frames for webp/apng, hides GIF-only ops (`isGifInput`/`gifOnlyVisibility` `:76-82`).
- `ImageLoadingGlideListeners.createDrawableListener().onResourceReady()` (`:74-108`) already logs `animatable=${resource is Animatable}` for webp - a prior probe left in place.
- OCR/translation extract-bitmap paths already handle any Drawable via `draw(canvas)` - work with any new animated drawable type.

## The single real gap

`ImageLoadingManager`'s three loaders (`loadCloudImage :625`, `loadNetworkImage :723`, `loadLocalImage :838`) compute `isGif = type == GIF || path.endsWith(".gif")` and branch to Glide `.asGif()` ONLY for gif. webp/apng fall into the plain `Glide.load()` path, which decodes them to a static `BitmapDrawable` (Glide 4.16 does not auto-produce an animated drawable for webp). No animated-webp decoder is registered in `di/GlideAppModule.kt`.

Result: webp shows the animated badge but never animates, and `AnimatedImageController.togglePlayback()` is a silent no-op (`currentAnimatedDrawable` never set because the drawable is not `Animatable`).

## Resolved design (from codebase precedent, no owner input)

1. **Decoder:** platform `ImageDecoder.decodeDrawable` -> `AnimatedImageDrawable` for animated webp/apng, gated API 28+ (matches `HeifSupportUtils.isHeicSupported` `:17` and `ExtractGifFramesUseCase.isExtractionSupported` `:99-106`, both API 28). NO new 3rd-party native dependency (rejected `zjupure:webpdecoder` - R8/minified risk, owner-level dep decision). Below API 28: static first frame (today's behavior, harmless).
2. **Wiring:** register a Glide `ResourceDecoder` producing `AnimatedImageDrawable` for webp/apng (API 28+) in `GlideAppModule`, so the existing plain-load path yields an animated drawable; ensure the webp path does not force `asBitmap`.
3. **Animated-UI gating:** key the badge + `currentAnimatedDrawable` + play/pause on the DECODED `resource is Animatable` (already logged in `ImageLoadingGlideListeners`), NOT on extension - so a STATIC webp shows no bogus badge/no-op toggle (fixes the pre-existing paper-cut).
4. **MediaType:** unchanged - webp stays `MediaType.IMAGE`; animation decided at the viewer layer (as GIF-vs-not already is). No enum/bitmask/preset churn.
5. **APNG:** included (same ImageDecoder path; existing sites already lump apng with webp).

## Key files
- `ui/player/ImageLoadingManager.kt` (asGif branch per loader), `ImageLoadingGlideListeners.kt` (Animatable probe), `ui/player/helpers/AnimatedImageController.kt` (badge/toggle), `di/GlideAppModule.kt` (decoder registration), `core/util/HeifSupportUtils.kt` (API-gate precedent), `domain/usecase/ExtractGifFramesUseCase.kt` (Movie/ImageDecoder precedent).

## Sites enumerating gif that already include webp (parity OK): AnimatedImageController, PlayerDialogHelper, GifEditorDialog, MediaExtensions IMAGE set. No MediaType.GIF change needed.

## Verification: device - animated webp/apng animates in the viewer + play/pause works; static webp shows no badge; build green. No unit-test precedent for this Glide/Drawable UI code -> device-gated.
