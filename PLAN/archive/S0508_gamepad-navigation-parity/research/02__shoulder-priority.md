# Research 02 - Shoulder-button priority vs screen consumers

**Strategic item:** §6.2
**Status:** Resolved

## Question

How a generic L1/R1 page-jump coexists with screens that already consume L1/R1 (browser tab switch, player transport).

## Findings

- `BrowseActivity.dispatchKeyEvent` and `MainActivity.dispatchKeyEvent` call `gamepadInputManager.handleKeyEvent(event, Surface.BROWSER)` first; `KEYCODE_BUTTON_L1/R1` map to `GamepadAction.BrowserAction.SwitchTab(forward=…)` and are consumed (return true) before `super.dispatchKeyEvent`.
- `PlayerActivity.dispatchKeyEvent` routes everything through `inputDispatcher` and consumes its own transport keys before `super`.
- `BaseActivity.dispatchKeyEvent` is reached only when the subclass did NOT consume the event (subclass overrides call `super` last).

## Decision

- Generic L1/R1 page-jump lives in `BaseActivity.dispatchKeyEvent` (after the existing S0506 focus guard and `TvKeyRouter`), so any subclass that already consumes L1/R1 pre-empts it - no regression to browser tab switch or player transport.
- Player family additionally opts out via the gamepad-navigation opt-out hook (same pattern as S0506 `shouldGuardContainerFocus`).
- The page-jump is a fallback action: it fires only on unconsumed `KEYCODE_BUTTON_L1/R1` from a gamepad source.

## Impact on plan

- Page-jump handling is added to `BaseActivity` key dispatch, not to a new global interceptor.
- Screens expose an overridable page-jump target hook so list/pager screens opt in; default is no-op (safe fallback).
