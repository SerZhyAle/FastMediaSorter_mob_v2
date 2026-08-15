# Research 01 - Axis distribution between focus navigation and scroll

**Strategic item:** §6.1
**Status:** Resolved

## Question

Which stick drives focus, which drives scroll, and which axes act as the D-pad equivalent alongside hat axes.

## Findings

- `GamepadInputManager.TRACKED_AXES` already iterates: `AXIS_X`, `AXIS_Y` (left stick), `AXIS_Z`, `AXIS_RZ` (right stick), `AXIS_HAT_X`, `AXIS_HAT_Y` (D-pad hat).
- Android synthesizes `KEYCODE_DPAD_*` key events from the hat switch (`AXIS_HAT_X/Y`) on standard gamepads. Those D-pad key codes already flow through the existing key path (`TvKeyRouter` + framework focus traversal, plus the S0506 container guard).
- The player consumes `AXIS_X/Y/Z/RZ` for seek/volume via `KeyBindingManager`; that path is unchanged and gated to the player surface.

## Decision

- Left stick (`AXIS_X`, `AXIS_Y`) → focus navigation (D-pad equivalent) in the generic layer.
- Right stick (`AXIS_Z`, `AXIS_RZ`) → continuous scroll of the active scrollable container.
- Hat axes (`AXIS_HAT_X/Y`) are NOT consumed by the new analog layer - they remain handled as synthesized `KEYCODE_DPAD_*` key events on the existing key path. This prevents double-stepping (one step from the hat key event, another from analog translation).
- `AXIS_Y` inversion convention (up = positive) mirrors the existing `handleMotionEvent` handling.

## Impact on plan

- Translator consumes only the four analog-stick axes; phase steps must explicitly exclude hat axes.
- No change to `TvKeyRouter` or the framework D-pad path.
