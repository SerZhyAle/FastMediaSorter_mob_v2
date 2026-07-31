---
name: check-animator-scale-before-diagnosing
description: AVDs here run with animator_duration_scale=0 - a working animation looks broken and invites a fix for a non-bug
metadata:
  type: feedback
---

Before diagnosing any animation as broken on an emulator, run `adb shell settings get global animator_duration_scale`. If it is `0`, stop - nothing you see about that animation is evidence. Set all three scales to 1.0 and re-test:

```
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0
```

**Why:** 2026-07-29, wiring `AudioWaveParticleView` into the welcome pages (S1234). Both project AVDs ship with all three scales at `0`, so every `ValueAnimator` completes instantly. The backdrop rendered exactly one frozen frame, and after a rotation - where `onSizeChanged` re-allocates and clears the buffer - nothing was left running to repaint it. The screen went black and stayed black. That read as a clean lifecycle bug: the host declares `configChanges`, so no `onStart`, so no restart. I wrote the fix, then a second stronger fix, and shipped a matching "same bug in the launcher" fix and status-note edit on someone else's open ticket. With the scales at 1.0 the animation ran correctly through rotation **with no code change at all**. All of it was fixing a non-bug, and the second fix (`stopAndReset()` before `startAnimation()`) would have re-randomised the visuals on every rotation - a real regression introduced to cure an imaginary one.

**How to apply:** the scale check belongs in the same breath as `device-ready.ps1` for any ticket whose acceptance is "does this animate". More generally: when two successive fixes both fail to change the symptom, stop fixing and re-examine the test rig - two failed fixes is the signal that the diagnosis, not the patch, is wrong. Note the scales are per-device settings that survive `adb.ps1 clear` but not necessarily an AVD wipe, so re-check on a fresh emulator.

Everything was reverted in the end, including the "surely this is still useful" one-liner - measured with animations off, restarting the animator from the host repaints nothing, because a zero-duration `ValueAnimator` fires no update callback. Keeping it would have left two files carrying a comment that claims a fix that does not fix anything. The residual real defect was parked as its own ticket instead.
