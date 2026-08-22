---
name: wear-rotary-bezel-is-drivable
description: The Galaxy Watch 7 rotating bezel CAN be driven from adb - `input rotaryencoder scroll --axis SCROLL,n` reaches Compose `onRotaryScrollEvent`; sendevent on /dev/input is refused by SELinux.
metadata:
  type: project
---

**A rotary/bezel acceptance point is not automatically INCONCLUSIVE.** Measured 2026-08-20 on the
owner's Galaxy Watch 7 (SM-L310, Wear OS on API 36) while device-testing S1683 and S1701, against the
standing claim - written into S1683's own 2026-08-16 audit - that "вращение безеля нельзя
воспроизвести через adb". That claim had already cost one ticket a device verdict.

The working call, and the two dead ends around it:

- **Works:** `adb -s <dev> shell input rotaryencoder scroll --axis SCROLL,<n>`. The `rotaryencoder`
  source and the `scroll` verb are both in `input -h` on API 36. The event arrives in Compose as a
  real `onRotaryScrollEvent`, so any screen using `Modifier.onRotaryScrollEvent` responds exactly as
  it would to the physical ring.
- **Sign is inverted.** `--axis SCROLL,1` arrives as `verticalScrollPixels = -136.0`. So `SCROLL,-1`
  is a turn "up" (volume raise / seek forward). Check the direction with a probe before scoring, or
  you will report the opposite of what happened.
- **Magnitude:** one unit of `AXIS_SCROLL` = 136 px on this watch (`AXIS_SCROLL` times
  `ViewConfiguration.getScaledVerticalScrollFactor`). The repo's `RotaryStepAccumulator` threshold is
  120 px, so one injected unit = exactly one step. Convenient, but see the caveat below.
- **Dead end - `sendevent`:** the bezel is a real evdev device (`/dev/input/event3`, name
  `detent_bezel`, `REL_WHEEL` + `ABS_MISC` over 60 detents, visible via `getevent -pl`). Shell is in
  group `input` and the node is `crw-rw---- system input`, yet `sendevent` returns
  `Permission denied` - SELinux is `Enforcing` and the shell domain may not write input devices.
  Do not spend time on it.

**Why:** an acceptance point scored INCONCLUSIVE goes back to the owner's wrist and stalls the
ticket; this method closes most of them from the desk, including exact measurements a human cannot
make (S1683's video bezel was proved to seek precisely ±10 s per detent this way).

**How to apply:** reach for `input rotaryencoder scroll` whenever a wear acceptance point mentions
the bezel, crown or rotation. Score INCONCLUSIVE only for what the injection genuinely cannot reach:
the pixels a **physical** detent delivers. If that number falls under the app's step threshold, a real
turn has a dead zone the injection never shows - so a ticket that tunes a step threshold still needs
the owner's wrist, while a ticket that only asks "does rotation do X" does not.

Related: [[project_wear_text_input_is_drivable]], [[index-wear]].
