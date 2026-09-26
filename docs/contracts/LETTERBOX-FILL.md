# Pointer - `LETTERBOX-BARS`, `LETTERBOX-HALO`

| | |
| --- | --- |
| **Id** | `LETTERBOX-BARS`, `LETTERBOX-HALO` |
| **Version** | 0.1 each, draft. Owner: FastMediaSorter_Lite |
| **Home** | `letterbox-fill/README.md` in the shared contracts catalog |
| **Role here** | consumer - the player's edge-extended background behind a fitted photo |

## What this repository must do to stay conformant

- The bars behind a fitted still image follow `LETTERBOX-BARS` rules 1-10; the halo, when offered,
  follows `LETTERBOX-HALO` rules 1-10 and is built on those bars only.
- Every constant of both contracts lives once, in one pure module, under a comment citing
  `LETTERBOX-BARS rule N` / `LETTERBOX-HALO rule N`; "round" means half to even.
- Rung 1 of section 5: the implementation reproduces every value of the catalog's vectors.
- The fill behind a video's first frame is outside version 0.1 (section 6) and is not claimed.
- Open deviations are dated exceptions in the catalog's registry.

## Where it lives here

- The math, once: `app_v2/.../core/letterbox/LetterboxFillMath.kt`; rung 1 is
  `LetterboxFillVectorsTest` over the vendored vectors in `app_v2/src/test/resources/letterbox-fill/`.
- The bars frame: `ui/player/letterbox/LetterboxBarsFrameBuilder.kt`, shown by
  `ui/player/DynamicBackgroundProcessor.kt` in `ivDynamicBackground`; setting `dynamicBackgroundExtension`.
- The halo: `ui/player/letterbox/LetterboxFrameDrawable.kt`, its growth
  `LetterboxHaloGrowthManager.kt`; settings group `AppSettings.letterboxHalo`.
- Host conditions chosen here: zoomed means no bars; no slideshow cut-off; a new request cancels the
  previous build instead of a 50 ms throttle.
