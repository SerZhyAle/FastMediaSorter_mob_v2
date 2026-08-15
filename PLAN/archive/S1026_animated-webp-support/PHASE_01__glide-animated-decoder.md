# Phase 01 - glide-animated-decoder

**Goal:** Produce an `AnimatedImageDrawable` for animated webp/apng (API 28+) through Glide, no new dependency.

## Steps

- [ ] **1.1** Add an API-support helper (mirror `HeifSupportUtils`): `isAnimatedImageDecodeSupported()` = `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` (API 28). Verify: helper present; compiles.
- [ ] **1.2** Register a Glide `ResourceDecoder` (webp/apng -> `Drawable`) in `di/GlideAppModule.kt` that, on API 28+, uses `ImageDecoder.decodeDrawable(ImageDecoder.createSource(...))` and returns the result (an `AnimatedImageDrawable` for animated files, a static drawable for static ones). Register for both `InputStream` and `ByteBuffer` model types as the existing Glide decoders do; use `@RequiresApi(28)` on the decode and only append the decoder when supported. Below API 28 the decoder is not registered -> Glide's default static path (unchanged). Verify: `a.ps1 fk` compiles; Glide module still valid.
- [ ] **1.3** Confirm the webp path is NOT forced to `asBitmap`/`asGif` anywhere that would bypass the new decoder. Verify: grep the webp loaders in `ImageLoadingManager` - webp uses the default `Drawable` request (which now yields the animated drawable).

## Done criteria
- Glide returns an Animatable drawable for animated webp/apng on API 28+; static otherwise.
