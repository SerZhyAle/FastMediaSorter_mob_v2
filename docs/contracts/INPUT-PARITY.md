<sub class="doc-stamp">26.10.18 19:09</sub>

# Pointer - `INPUT-PARITY`

| | |
| --- | --- |
| **Id** | `INPUT-PARITY` |
| **Version** | 0.4, draft. Owner: shared, one steward per device column |
| **Home** | `input-controls/README.md` in the shared contracts catalog |
| **Role here** | steward of the touch, gamepad and TV remote / D-pad columns |

## What this repository must do to stay conformant

- Every action of the contract's section 3.1 vocabulary has a route on touch, gamepad and remote on
  every screen (rule 1); a screen that cannot meet it is a registry exception naming the screen.
- A default binding in `assets/input/default_bindings.json` for one of those actions uses the key of our
  column in section 4, and a changed default is written in the catalog first - our column is what other
  products copy.
- Focus moved by D-pad or gamepad stays visible (rule 2).
- No system button is bound: gamepad `Guide`, the remote's `Home`, the system back gesture keep their
  platform meaning (rule 3).
- The deviations currently open are recorded as exceptions in the catalog's registry. The 0.4 `VIEWER-KEYBOARD.md`
  player defaults ship in `default_bindings.json` and `PlayerKeyboardHandler` (S4363); the rows still open
  (`G`, `N`, `F4`, frame step, A-B loop, the standalone hosts) are implemented by S4373 and await device
  acceptance. The registry exception stays open until the collection surfaces, numbered-frame stepping,
  A-B repeat and host routes are observed on device; a compile or mocked timestamp test is not that verdict.

`INPUT-CHORD`, the chord notation beside it in the same folder, is not adopted: our bindings are stored as an
internal key-code record that never leaves the app, and in-window shortcuts are outside its scope.

## Where it lives here

- Bindings and routing: `app_v2/.../core/input/` - `KeyBindingManager`, `GamepadInputManager`,
  `GamepadNavigationTranslator`, `TvKeyRouter`; defaults in `app_v2/src/main/assets/input/default_bindings.json`.
- Touch in the viewer: `app_v2/.../ui/player/TouchZoneConfig.kt`.
- Browsing list: `app_v2/.../ui/browse/MediaFileAdapter.kt`, gamepad routing in `BrowseActivity`.
