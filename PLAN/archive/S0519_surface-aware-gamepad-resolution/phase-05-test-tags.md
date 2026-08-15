# Phase 05 - Test inversion + verification tags

Goal: flip the unit test that asserted the old literal-tree behaviour, and land the device-test probes for the BlockNeedUserTest gate.

## Test

- [ ] In `src/test/java/com/sza/fastmediasorter/core/input/GamepadInputManagerTest.kt`, rewrite test #5 (`BROWSER surface dispatches BrowserAction without calling resolver`):
  - New name: `BROWSER surface resolves BrowserAction via resolver`.
  - Stub `resolve(GamepadButton(BUTTON_A), InputSurface.BROWSER)` returns `CommandId.BROWSER_SELECT`; catch-all returns null.
  - Assert result is `GamepadAction.BrowserAction.Select`.
  - Remove the `throws AssertionError("resolver called for BROWSER surface")` stub - the resolver is now expected to be called.
  - Verification: test references `InputSurface.BROWSER` and `CommandId.BROWSER_SELECT`; no `throws AssertionError`.

- [ ] Optionally add test #6: same trigger resolves to different actions per surface (A -> PlayPause for PLAYER, A -> Select for BROWSER) to lock the surface-aware contract.
  - Verification: both assertions pass under the resolver.

## Verification tags (BlockNeedUserTest gate)

- [ ] Insert `Timber.d("S0519: <entry desc>")` at the two changed-flow entry points, as the final code edits:
  - `GamepadInputManager.handleKeyEvent` BROWSER arm: `Timber.d("S0519: browser gamepad keyCode=%d -> resolve", event.keyCode)`.
  - `KeyBindingManager.resolve` multi-candidate branch: `Timber.d("S0519: multi-candidate trigger surface=%s candidates=%d", surface, candidates.size)`.
  - Add `import timber.log.Timber` where missing.
  - Verification: exactly the S0519 tags exist while status is BlockNeedUserTest; remove on the transition out.

## Device-test instructions (status note)

- On a device with a gamepad, open a browse list: A selects, B goes back, X multi-selects, Y opens context menu, START opens search, L1/R1 switch tabs.
- Remap a browser action (e.g. Select -> a different button) in the keybinding screen; confirm the new button drives browser select and the player binding for the same physical button is unchanged.
