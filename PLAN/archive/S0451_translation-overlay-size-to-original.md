# S0451 - Translation block overlay sized to original text

**Status:** Archived

## 1. Problem
- In-player block translation renders the translated text far smaller than the source text and offset to the left, so it neither matches the original size nor covers it.
- Reported on a standard build while verifying S0423: source text at ~54pt, translated block stayed tiny.

## 2. Root cause
- `TranslationOverlayView` hard-caps the translated font at `maxTextSizeSp = 14f`; the per-block size is `coerceIn(min, maxTextSizePx * multiplier)`, so even the 1.5x global multiplier tops out near 21sp regardless of how tall the OCR box is.
- The base height ratio is conservative (0.35..0.45 of the OCR box height), so the text starts smaller than the original line.
- The block shrinks to the actual translated text width, so a short translation cannot cover a longer original.

## 3. Decision (owner, 2026-06-16)
- Auto-size the translated text to roughly the original line height with a small headroom, and let the block cover the original.
- Target font height: about 90% of the OCR box height (single line), with the 14sp ceiling removed so large source text is matched.
- Keep the existing wrap-then-expand fallback for translations that overflow the box.
- Keep manual controls as overrides on top of the new default: per-block swipe resize and the global 0.7..1.5x multiplier.

## 4. Approach
- Treat the OCR `boundingBox` height (scaled) as the original text line height; derive the auto font size from it instead of the fixed 14sp ceiling.
- Replace the `maxTextSizeSp` ceiling with a box-relative target (about 0.9 of usable box height for a single line), keeping a sane absolute floor (`minTextSizeSp`).
- Make the block background cover the original extent: do not shrink the box below the original `boundingBox` width/height; only expand (within current limits) when the translation is longer.
- Preserve the multiline reduce-to-fit path and the per-block / global override paths unchanged in behaviour, layered over the new auto default.

## 5. Scope
- Single shared renderer: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt`.
- Affects all block-overlay translation consumers: image (`PlayerImageTranslationManager`), PDF (`PdfTranslationCoordinator`), Google Lens (`GoogleLensTranslationHelper`).
- EPUB translation uses a separate HTML overlay (`EpubTranslationOverlayHelper`) and is out of scope.
- Translation-capable flavors only (standard/legacy/noLegal/vr); no flavor-specific code.

## 6. Acceptance
- Translated block font visually approximates the original text size (no fixed 14sp ceiling) across small and large source text.
- The block background covers the original text rather than sitting beside it.
- Long translations still wrap and expand within the existing limits without clipping.
- Per-block swipe resize and the global multiplier still override the auto size.

## 7. Related
- S0423 (translation engine bundling) - Verified; surfaced this issue during device test.
