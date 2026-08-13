# Research 02 - Focus-frame offset root cause (§6.2)

**Status:** Resolved
**Date:** 2026-07-04
**Method:** On-device observation (Android TV emulator, Android 16, WelcomeActivity, standard-debug) + coordinate measurement + code read.

## Symptom

Travelling focus frame (S0819) is drawn away from the actually-focused view. Measured, fully settled (no animation in flight):

- Focus on `btnNext`, uiautomator bounds `X[1732..1908] Y[995..1068]` (size 176x73).
- Frame drawn bbox (measured from screenshot, empty-background zone) `X[1579..1745] Y[869..946]` (size 166x77).
- Frame size matches the view (~176x73); frame is translated by `dx=-153, dy=-126` up-left.

Key deduction: the frame is the correct SIZE for the focused view, so it tracks the right view. The error is a pure translation of its ORIGIN, and it is static (persists >2s after the 140ms animation ends). So this is not animation lag - it is a coordinate-computation bug. The offset differs between the bottom-bar button and the mid-screen toggle group, so it is not a constant inset - it scales with an ancestor transform/scroll that the current API ignores.

## Root cause

`FocusFrameController.moveToView` computes bounds with:

    boundsScratch.set(0, 0, view.width, view.height)
    (decorView as ViewGroup).offsetDescendantRectToMyCoords(view, boundsScratch)

`ViewGroup.offsetDescendantRectToMyCoords` walks the ancestor chain using each view's `left/top` and `scrollX/scrollY` ONLY. Per Android docs it explicitly does NOT account for ancestor transforms (`translationX/translationY`, `scaleX/scaleY`, rotation, matrix). When any ancestor of the focused view carries a translation/scale (page transitions, animated containers, insets applied via translation), the returned rect is off by that transform - exactly the observed static translation.

The overlay itself is a `Drawable` in `window.decorView.overlay`, which draws in decorView coordinate space - that part is fine; the input coordinates are wrong.

## Fix direction (feeds Phase 01)

Replace the transform-blind computation with a transform-aware one that maps the view's on-screen rect into decorView-local space:

- Use `view.getLocationInWindow(..)` (accounts for all ancestor transforms and scroll) and subtract `decorView.getLocationInWindow(..)`, or
- Use `view.getBoundsOnScreen(..)` and `decorView.getLocationOnScreen(..)` and subtract.

Both yield decorView-local coordinates that respect transforms, so the frame origin lines up with the focused view.

Secondary (perceptual) fix, separate from the coordinate bug: the 140ms travel animation makes the frame lag visibly during rapid D-pad repeats. Snap-to-target when focus changes faster than the animation can follow (or cap animation distance/duration), so fast navigation does not leave the frame mid-flight.

## Verification plan

After the fix, re-measure on the TV emulator: settled frame bbox origin must equal the focused view's decorView-local origin within a few px, for both a bottom-bar button and a control inside the scrolling page. Frame must visibly hug the focused control during navigation.
