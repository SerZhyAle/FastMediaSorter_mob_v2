# Design Brief: Google Play Store Assets - Fast Media Sorter

**Version:** 2.0 (S2570 - supersedes 1.0, S0135, 2026-05-13)
**Date:** 2026-09-05
**Scope:** App icon, feature graphic, and the visual standard every store screenshot must meet

---

## 1. App Icon - 512 × 512 px

**Format:** PNG, 32-bit RGBA, transparent background
**Style:** Symbol-only - no text, no wordmark inside the icon
**Core motif:** Rising arrows (upward/diagonal, 2-3 arrows) in a blue gradient
  - Base colour: #1565C0 (deep blue)
  - Highlight colour: #42A5F5 (light blue)
  - Arrow direction: bottom-left → top-right (growth, sorting, upward movement)
**Background:** Solid rounded-rect or circle, dark navy (#0D1B2A) or deep blue (#0A2463)
**Accessibility test:** Icon must be recognisable in greyscale at 48 dp (≈ 192 × 192 device px)
**Do not include:** text "Fast", "Media", or any wordmark; camera lens; film strip; unrelated imagery

### Acceptance criteria
- Passes readability check at 48 dp (export at 3× and inspect at 64 px)
- Passes greyscale conversion - arrows remain distinguishable from background
- No text or letterforms visible inside the icon boundary

---

## 2. Feature Graphic - 1024 × 500 px

**Format:** PNG or JPG, sRGB
**Layout (left to right):**
  - Left 30%: app icon (centred vertically, ~200 px)
  - Centre 40%: headline text (see copy below)
  - Right 30%: phone mockup showing the Browse / Sort screen (portrait phone frame, cropped if needed)
**Background:** dark theme - #0D1B2A (navy) or a subtle dark gradient
**Headline copy:**
  - EN: "Sort photos & videos in seconds"
  - RU: "Сортируй фото и видео за секунды"
  - UK: "Сортуй фото та відео за секунди"
**Sub-headline (optional):** "Local · NAS · Cloud" (same across locales)
**Typography:** sans-serif (Roboto or Inter), bold headline ≥ 48 pt in source file
**Contrast:** headline text contrast ratio ≥ 4.5:1 (WCAG AA) against the dark background

### Acceptance criteria
- All three locale headlines fit without truncation at 1024 × 500 px
- No Google, Apple, Microsoft, or competitor logos/trademarks

---

## 3. Screenshots - Caption Overlay Standard

**Which screens are shot, in what order, and what each caption says is `play/listing/captions.json`.**
This brief deliberately carries no slot list of its own: a second list is what let the store listing
sit at three languages while the app grew to thirteen (S2340, recorded in `play/listing/README.md`),
and the same drift ran here from 2026-05-13 until S2570 - six slots described against eight in the
registry, with not one slot name in common. Read the registry for *what* to shoot; read this section
for what the raw shot must be and what the finished frame must never violate.

**The overlay itself is executed, not described here** (S2633). Band colour, font, point size and
band geometry live in `scripts/release/compose-play-screenshots.py` and are read from there. This
section carried a font name, a point size and a semi-transparent black plaque for months after
S2573 replaced all three with an opaque brand band placed *above* the frame - a second copy of an
executed parameter drifts the same way a second slot list did, so there is no longer one.

**Raw shot - what the composer does not check, so it is decided here:**

- Format: PNG, sRGB, no alpha.
- Size: portrait phone at 1080 × 1920 px minimum, up to 1440 × 2560. The tablet set is captured in
  landscape and composed by the same rule into `tenInchScreenshots`.
- Device frame: optional, and consistent across every slot of a set.

**Invariants of the composed frame - the constraints any future change to the composer's constants
must still satisfy:**

- The caption occupies at most 20% of the finished image. This is Google's ceiling for a tagline,
  and it is the reason the band sits above the frame with a height derived from the canvas height
  rather than the short edge: the short-edge rule spent 23% of a landscape tablet frame while
  spending 11% of a portrait phone one (S2573).
- The band never overlaps the screenshot's own pixels. Whatever the app draws at the top edge of a
  capture survives the compose.
- Caption text over the band keeps a contrast ratio of at least 4.5:1 (WCAG AA).
- Caption text is at most two lines. This is a rule for the text authored in `captions.json`; the
  composer will happily wrap a third.

---

## 4. Where the Files Live

The screenshot pipeline owns its paths and is documented once, in `play/listing/README.md`: raw shots
are captured to `temp/play-shots/<locale>/<slot>.png`, `scripts/release/compose-play-screenshots.py`
composes them into `play/listing/<locale>/images/phoneScreenshots/<NN>.png` in registry order, and
`scripts/release/publish-play-listing.ps1 -Mode commit` uploads them. No screenshot is stored under
`store_assets/`.

The single images this brief specifies:

- App icon: `play/listing/en-US/images/icon.png`, the same artwork as `store_assets/icon_512.png` and
  the fastlane copy
- Feature graphic: `play/listing/<locale>/images/featureGraphic.png`, written by
  `scripts/release/compose-feature-graphic.py` for the three locales that carry an `images/` folder;
  the same run writes `fastlane/metadata/android/en-US/images/featureGraphic.png`, because the split
  between the two publication trees is about text and not about pictures (S2597)

---

## 5. Acceptance Checklist (reviewer)

- [ ] Icon passes greyscale test at 48 dp
- [ ] Icon contains no text
- [ ] Feature graphic headline legible at 1024 × 500 native resolution. **Truncation is no longer a
      reviewer's job (S2597):** the composer measures every string against the real column and
      refuses to write a file it cannot fit, because the graphic this one replaced failed exactly
      this criterion - its wordmark ran off the right edge as "FastMediaSorte" - and shipped for
      months on IzzyOnDroid with nobody checking. What is left here is a judgement about legibility,
      not about whether the text fits
- [ ] Every slot in `play/listing/captions.json` has a composed frame for the default language
- [ ] Overlay text contrast ≥ WCAG AA (use browser dev-tools or Figma accessibility plugin)
