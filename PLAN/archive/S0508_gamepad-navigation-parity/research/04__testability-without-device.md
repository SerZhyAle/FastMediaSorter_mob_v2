# Research 04 - Deterministic testability without a device

**Strategic item:** §6.4
**Status:** Resolved

## Question

How to deterministically verify dead-zone, direction, and acceleration on Robolectric without a real gamepad.

## Findings

- `GamepadInputManagerTest` already synthesizes joystick motion events with mockk: `every { e.source } returns InputDevice.SOURCE_JOYSTICK`, `every { e.getAxisValue(axis) } returns value`, `e.action = ACTION_MOVE` - helper `joystickMoveEvent(mapOf(AXIS_Y to 0.8f))`. Dead-zone and direction assertions are already proven this way.
- Rate-limiters use `SystemClock.uptimeMillis()`; Robolectric's `ShadowSystemClock` advances virtual time, so interval-based emission (single-step vs accelerated) is deterministically testable.

## Decision

- The translator takes an injectable time source (a `() -> Long` defaulting to `SystemClock.uptimeMillis()`), so unit tests advance virtual time explicitly without relying on wall-clock.
- Tests synthesize motion events via mockk axis stubs (reuse the `GamepadInputManagerTest` pattern) for: below-dead-zone → no intent; above-dead-zone left stick → directional focus intent; right stick → scroll intent; held direction past the repeat threshold with advanced time → accelerated (more frequent) emissions; return inside dead-zone → counter reset.

## Impact on plan

- A dedicated translator test class mirrors `GamepadInputManagerTest` structure (Robolectric, mockk).
- The injectable clock is a constructor parameter with a production default, keeping Hilt wiring unchanged.
