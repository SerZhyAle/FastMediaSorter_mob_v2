---
name: check-animator-scale-before-diagnosing
description: Always READ animator_duration_scale before judging an animation - never assume its value; a 0 makes a working animation look broken and invites a fix for a non-bug
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

**How to apply:** the scale check belongs in the same breath as `device-ready.ps1` for any ticket whose acceptance is "does this animate". More generally: when two successive fixes both fail to change the symptom, stop fixing and re-examine the test rig - two failed fixes is the signal that the diagnosis, not the patch, is wrong. Note the scales are per-device settings that survive `adb.ps1 wipe-data` but not necessarily an AVD wipe, so re-check on a fresh emulator.

**Read the value, never assume it - in either direction.** This note used to claim the AVDs ship at `0`. On 2026-07-31 `emulator-5556` answered `1.0` on all three, so that claim is not a standing fact about the fleet: someone (a sibling session, a prior sweep, an AVD wipe) had already restored them. Assuming `0` is as wrong as assuming `1.0` - it would mean "reproducing" an animations-off defect on a device where animations are on, and reporting a green result that proves nothing. When a ticket's repro *requires* animations off (S1277), set the three scales explicitly, verify with a `get`, and restore them afterwards; do not rely on any default.

Everything was reverted in the end, including the "surely this is still useful" one-liner - measured with animations off, restarting the animator from the host repaints nothing, because a zero-duration `ValueAnimator` fires no update callback. Keeping it would have left two files carrying a comment that claims a fix that does not fix anything. The residual real defect was parked as its own ticket instead.
