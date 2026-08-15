# Phase 03 - Gamepad routing: browser through resolver

Goal: delete the literal browser tree and route BROWSER gamepad events through the surface-aware resolver, mapping resolved browser commandIds to `BrowserAction`.

## Steps

- [ ] In `core/input/GamepadInputManager.kt` `handleKeyEvent`, replace the `Surface.BROWSER -> mapBrowserButton(event)` arm with the resolver path:
  - `val trigger = InputTrigger.fromGamepadButton(event.keyCode)`
  - `val commandId = keyBindingManager.resolve(trigger, DomainSurface.BROWSER) ?: return null`
  - `mapCommandToGamepadAction(commandId, Surface.BROWSER, trigger, axisValue = null)`
  - Verification: no remaining reference to `mapBrowserButton`.

- [ ] Delete the `mapBrowserButton` function and its `// Browser: legacy literal tree` comment block.
  - Verification: `grep -c mapBrowserButton GamepadInputManager.kt` returns 0.

- [ ] Extend `mapCommandToGamepadAction` with browser branches:
  - `CommandId.BROWSER_SELECT -> GamepadAction.BrowserAction.Select`
  - `CommandId.BROWSER_BACK -> GamepadAction.BrowserAction.Back`
  - `CommandId.BROWSER_MULTI_SELECT -> GamepadAction.BrowserAction.MultiSelect`
  - `CommandId.BROWSER_CONTEXT_MENU -> GamepadAction.BrowserAction.ContextMenu`
  - `CommandId.BROWSER_SEARCH -> GamepadAction.BrowserAction.Search`
  - `CommandId.BROWSER_TAB_NEXT -> GamepadAction.BrowserAction.SwitchTab(forward = true)`
  - `CommandId.BROWSER_TAB_PREV -> GamepadAction.BrowserAction.SwitchTab(forward = false)`
  - Verification: all 7 browser commandIds produce a `BrowserAction`.

- [ ] Update the class KDoc behaviour note: BROWSER now routes through `KeyBindingManager` (drop the "legacy literal tree" wording).
  - Verification: KDoc no longer claims browser uses a literal tree.

## Notes

- The existing surface-branched arms for `NEXT_FILE`/`PREVIOUS_FILE`/`SEARCH` stay - they remain valid for player and are harmless for browser (browser defaults use `browser.*`, so these are not resolved on the BROWSER surface).
- `BrowseActivity` / `MainActivity` need no change: both already call `handleKeyEvent(event, Surface.BROWSER)` and route the returned `BrowserAction` via `routeBrowserGamepadAction`.
- Motion-event browser path already passes `DomainSurface.BROWSER` into `resolve`; with no browser axis defaults it yields null and falls through - acceptable, no analog browser action in scope.
