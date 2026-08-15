# Research 01 - TV focus-indicator approach: platform standard vs our overlay (§6.1)

**Status:** Resolved
**Date:** 2026-07-04
**Method:** Official Android TV docs + community practice (Leanback, Compose-for-TV) web review.

## Question

Should app-wide non-touch focus visibility be driven by our single window-level travelling overlay frame (S0819 `FocusFrameController`/`FocusFrameOverlay`), or by the platform-standard per-view focus indicator? I.e. is our current approach correct?

## Finding: the platform standard is PER-VIEW, not a global overlay

Android's official "Focus system" guidance for TV says the focus indicator is a per-element visualization, drawn by each focusable itself, mixing:
- **Scale** (1.025x-1.1x on focus),
- **Glow** (2-32dp elevation shadow),
- **Outline/border** (drawn by the element, `state_focused`),
- **Color** (surface/content color change on focus).

Implementation is a `state_focused` StateListDrawable (selector) as the view's background/foreground, optionally plus a StateListAnimator for scale/elevation. Leanback's `FocusHighlightHandler` does exactly this per item (scale + dim). Compose-for-TV models focus as per-composable state. None of the platform paths use a single window overlay that computes the focused view's coordinates and travels to it.

## Why our overlay approach is fragile

`FocusFrameController` computes the focused view's rect and draws one shared frame in `decorView.overlay`. Because the highlight is drawn in a DIFFERENT coordinate space from the view, every position depends on correct coordinate mapping - and that is exactly where the bugs live:
- Offset `-153,-126` on the welcome bottom bar (Research 02 - `offsetDescendantRectToMyCoords` ignored transforms; fixed via `getLocationInWindow`).
- Residual left offset (~177px) still observed on the right column of the two-column functionality page after the fix (the row's window rect vs the frame differ for that container).

A per-view indicator can NEVER be offset: the view draws its own highlight in its own bounds. It also matches what TV users expect (scale/glow), and is accessibility-consistent.

## Universal, app-global mechanisms (no per-control code, covers generated controls)

Hand-styling hundreds of controls (many generated at runtime) is untenable and unnecessary. Android offers app-wide mechanisms that require zero per-instance work:

1. **Framework default focus highlight (API 26+).** Every focusable view whose background has no `state_focused` automatically gets a default focus highlight (a ripple based on the theme). It is global and automatic; tune visibility app-wide by raising contrast of `?attr/colorControlHighlight` in the app theme. Opt-out per view is `android:defaultFocusHighlightEnabled=false`. This is the zero-code baseline and it already covers generated controls.
2. **Theme-wide widget default styles.** Set a `state_focused` selector as the DEFAULT style per widget TYPE in the app theme (`materialButtonStyle`, `checkboxStyle`, ..). Every instance of that type - including runtime-generated ones - inherits the focus outline with no per-instance code.
3. **One global focus-change decorator (single place).** A single `ViewTreeObserver.OnGlobalFocusChangeListener` at the window decorates the view that GAINS focus in-place (scale + elevation + a focus foreground drawable) and reverts the one that loses it. This is Leanback's `FocusHighlightHandler` idea generalized to arbitrary views. One place, no per-control code, works for generated controls - and crucially the highlight is drawn BY the focused view in its own coordinates, so it can never be offset.

## Recommendation (feeds S0943 pillar E and the §6.1 fork)

Keep the "single global place" instinct behind S0819 - that part was right - but stop drawing a separate overlay at computed coordinates. Instead:
- Baseline: turn on / tune the framework's global focus highlight via theme `colorControlHighlight` (covers everything, incl. generated controls, for free).
- Branded/stronger: one global focus-change decorator that applies scale + elevation + a `state_focused` foreground to the focused view IN PLACE (no overlay, no coordinate math), configured once at the window/decor level.
- Retire `FocusFrameController`/`FocusFrameOverlay` (the decorView-overlay-at-computed-coordinates design) - it is the sole source of the offset bugs.

This answers the §6.1 fork: the indicator is one global mechanism (a window-level focus listener decorating the focused view), NOT per-instance styling and NOT a coordinate-computing overlay. Reachability/order stay a separate concern (initial focus, no-trap), handled by the same window-level listener + `nextFocus`/`FocusFinder`, not by drawing.

Trade-off: in-place scale/elevation mutates the focused view briefly (must be reverted on blur and must not disturb layout - use scale/elevation, not size). This is the standard TV pattern and is far more robust than a floating overlay.

## Sources

- Focus system | TV | Android Developers - https://developer.android.com/design/ui/tv/guides/styles/focus-system
- TV navigation | Android Developers - https://developer.android.com/training/tv/get-started/navigation
- Developing for Android TV - keeping focused (Egeniq) - https://egeniq.com/blog/developing-for-android-tv-keeping-focused/
- Focus as a state - TV focus with Compose (A. Zaitsev) - https://alexzaitsev.substack.com/p/focus-as-a-state-new-effective-tv
