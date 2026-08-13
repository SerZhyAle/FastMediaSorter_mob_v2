# S0760 research 01 - Font-resize swipe usage map + centralization options

**Date:** 2026-06-28
**Scope:** app_v2, text/epub/translation viewers
**Goal:** Map every place the horizontal swipe-resizes-font gesture lives, pin current step/bounds/toast, and lay out the cheap vs dynamic vs centralized options.

---

## Headline

There are **two independent engines** for the same gesture, each with its own `onFling`, its own step
(both `2`), its own bounds, and its own per-step toast. The owner's "this is in several places" is
accurate. The two engines also use **different unit systems** (SP float vs Int px), which is the main
complication for true centralization.

## Engine A - TextViewerManager (+ TextViewerGestureDetectors)

Covers **three surfaces** through one manager:
- open text file content
- OCR text
- translation overlay

- Gesture: `TextViewerGestureDetectors.buildTextDetector` and `buildTranslationDetector`, both
  `SimpleOnGestureListener.onFling`; horizontal fling -> `onIncrease/DecreaseTextFontSize` /
  `...TranslationFontSize` (`ui/player/helpers/TextViewerGestureDetectors.kt:29-41,75-86`). Thresholds:
  `SWIPE_THRESHOLD_PERCENT = 0.05f`, `SWIPE_VELOCITY_THRESHOLD = 100`.
- Sizes: SP float. Constants in `TextViewerManager` companion: `MIN_FONT_SIZE_SP = 6f`,
  `MAX_FONT_SIZE_SP = 72f`, `DEFAULT_TEXT_FONT_SIZE_SP = 14f`, `FONT_SIZE_STEP_SP = 2f`
  (`TextViewerManager.kt:67-71`).
- Apply: `increaseTextFontSize`/`decreaseTextFontSize` add/sub `FONT_SIZE_STEP_SP`, coerce to bounds,
  `applyTextFontSize()` -> `setTextSize(SP, ...)` on `tvTextContent` + `etTextContent`
  (`:451-479`). Translation mirror at `:482-493`.
- Toast: `showFontSizeToast(sizeSp)` called on every step (`:461,474,486,493`, body at `:505`). This is
  the laggy indicator the owner wants gone.
- Interplay: an `autoFitFontManager` can own the size; manual swipe calls `notifyManualOverride`.

## Engine B - EpubViewerManager (duplicate)

- Gesture: own `onFling` (`ui/player/helpers/EpubViewerManager.kt:231-244`) -> `increaseFontSize()` /
  `decreaseFontSize()` (`:655-667`).
- Sizes: **Int px**. `currentFontSize` default `18`, step `+= 2` / `-= 2`, own `MIN_FONT_SIZE` /
  `MAX_FONT_SIZE` (`:86,177,656-666`).
- Toast: own, `R.string.epub_font_size` with the current size (`:247`).
- Persistence: `saveFontSize()` (epub has its own persisted font size, unlike Engine A whose sizes are
  session-scoped until the player exits).

## Gesture type - why dynamic drag is a rework

Both engines use `onFling` = a single discrete event at the end of a fast swipe. The owner's "font grows
live as you drag your finger" requires `onScroll` (continuous, fires repeatedly during the drag) instead,
mapping accumulated horizontal distance to a size delta, with throttling/coalescing so each
`setTextSize` (which forces a TextView relayout) does not stutter on long documents. That is a different
gesture model, not a constant tweak - hence the fork.

## Options

- **Cheap (recommended v1):** raise the step in both engines (`FONT_SIZE_STEP_SP 2f -> 10f`;
  Engine B `+= 2` -> `+= 10`) and delete the per-step toasts (`showFontSizeToast` calls +
  `epub_font_size` toast). Pure constant/removal change, both surfaces, no architecture change.
- **Dynamic drag:** replace `onFling` with `onScroll`-based continuous resize + throttle; needs a perf
  guard on long docs. Larger, UX-tuning required.
- **Shared engine:** extract a reusable `FontResizeGestureController` + size policy used by both managers.
  Complication: Engine A is SP-float/session-scoped, Engine B is Int-px/persisted - unifying needs a
  common unit + persistence story. Worth doing only if the owner wants real centralization.

## Step-value sanity

Step 10 on the SP range 6..72 = ~7 stops; reasonable but coarse near the small end. Epub uses Int px on a
different range. A flat 10 is not necessarily uniform across the two unit systems - confirm, or use a
proportional step.

## Owner decisions (cannot be inferred from code)

1. **Variant:** cheap step bump, dynamic drag, or shared dual-mode engine? (Owner leaned cheap-first.)
2. **Centralization depth:** just fix both call sites, or extract a true shared controller (bigger;
   blocked by the SP-float vs Int-px mismatch)?
3. **Toast:** remove entirely (owner leaned this), or replace with a responsive inline/overlay indicator
   shown during the gesture?
4. **Step value:** flat 10 across both engines, or proportional given the two unit systems?

## Touched files (for any variant)

- `ui/player/helpers/TextViewerGestureDetectors.kt`
- `ui/player/helpers/TextViewerManager.kt`
- `ui/player/helpers/EpubViewerManager.kt`
- strings: `epub_font_size` (and whatever `showFontSizeToast` formats) if toast removed.
