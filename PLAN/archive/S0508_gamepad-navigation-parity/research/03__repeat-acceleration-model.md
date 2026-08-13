# Research 03 - Repeat-acceleration model

**Strategic item:** §6.3
**Status:** Resolved

## Question

Which acceleration curve to apply when a stick is held, so the feel matches the existing D-pad acceleration in large grids.

## Findings

- `FocusManager` is the existing source of truth: `DPAD_ACCEL_REPEAT_THRESHOLD = 6`. When a D-pad key's `event.repeatCount` exceeds the threshold, the move escalates from single-step `InputAction.MoveFocus` to `InputAction.PageJump(±1)`.
- `InputAction.PageJump(deltaPages)` is the shared page-jump abstraction, also consumed by `KeyboardNavigationManager.scrollPage`.
- The player's analog handling already rate-limits emissions (`ANALOG_SEEK_INTERVAL_MS`, etc.) via `SystemClock.uptimeMillis()` deltas.

## Decision

- The stick translator mirrors the D-pad model: it tracks how long a direction has been held (a synthesized repeat counter incremented per emitted step while the stick stays deflected past the dead-zone), and resets the counter when the stick returns inside the dead-zone or reverses direction.
- Below the repeat threshold: emit single-step focus moves at a base interval (rate-limited like the player's analog emitters).
- At/above the threshold (reuse the `DPAD_ACCEL_REPEAT_THRESHOLD` value as the shared constant): shorten the emit interval (faster repeat) so held navigation accelerates, matching the D-pad grid feel.
- Dead-zone reuses `GamepadInputManager.DEADZONE` (0.15).

## Impact on plan

- The threshold constant is shared, not re-invented; the translator references the same value used by `FocusManager`.
- Acceleration is expressed as interval shortening past the threshold, deterministic and unit-testable with a virtual clock.
