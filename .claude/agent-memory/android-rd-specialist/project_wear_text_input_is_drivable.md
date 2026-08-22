---
name: wear-text-input-is-drivable
description: The watch IME CAN be driven - `input text` reaches a Compose BasicTextField once the field itself is tapped; the T9 window hides the value, so read it back with uiautomator dump.
metadata:
  type: project
---

`adb shell input text` **does** reach a Compose `BasicTextField` on the owner's Galaxy Watch 7.
Measured 2026-08-20 on S1554, against the standing claim that the watch IME cannot be driven and
that any acceptance point needing typed input is automatically INCONCLUSIVE. That claim is wrong,
and it had already cost one ticket a device verdict.

The trap that produces the false negative, and the three facts that beat it:

- **Focus needs two taps, not one.** Tapping the settings row opens the edit overlay, but the field
  inside it does not take focus. `input text` sent at that moment goes nowhere. Tap the field
  rectangle itself (its centre, ~y 170 on a 480x480 round face) - `dumpsys input_method` flips
  `mInputShown=false` to `true` - and the very next `input text` lands.
- **The screenshot lies.** Samsung's T9 IME draws a full-screen editor over the app, and it does not
  mirror what `input text` committed - the field looks empty in every `screencap`. Read the value
  back with `uiautomator dump` instead; the app's own node carries the real text.
- **Editing keys work too.** `KEYCODE_DEL` (67) and `KEYCODE_FORWARD_DEL` (112) both land, so a
  wrong value is repairable without retyping. The cursor sits where you tapped, so a tap in the
  middle of existing text makes backspace delete only the left half - forward-delete clears the rest.
  The IME's check button (top-right, ~412,192) commits and closes both the IME and the overlay.

The T9 keyboard is also tappable directly (`input tap` on a key raises its candidate bar), but that
path is predictive and needs a second tap on the candidate per character - use it only if
`input text` is genuinely refused.

**Why:** declaring an acceptance point INCONCLUSIVE for "the IME cannot be driven" retires a
verdict the stand can actually produce. On S1554 the whole ticket hinged on filling one field.

**How to apply:** before writing INCONCLUSIVE on any watch scenario that needs typed input, tap the
field, confirm `mInputShown=true`, send `input text`, and verify with `uiautomator dump`. See
[[index-wear]] for the other watch device traps.
