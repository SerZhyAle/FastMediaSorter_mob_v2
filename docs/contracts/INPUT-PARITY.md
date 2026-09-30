# Pointer - `INPUT-PARITY`

| | |
| --- | --- |
| **Id** | `INPUT-PARITY` |
| **Version** | 0.3, draft. Owner: shared, one steward per device column |
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
- The deviations currently open are recorded as exceptions in the catalog's registry.

`INPUT-CHORD`, the chord notation beside it in the same folder, is not adopted: our bindings are stored as an
internal key-code record that never leaves the app, and in-window shortcuts are outside its scope.

## Where it lives here

- Bindings and routing: `app_v2/.../core/input/` - `KeyBindingManager`, `GamepadInputManager`,
  `GamepadNavigationTranslator`, `TvKeyRouter`; defaults in `app_v2/src/main/assets/input/default_bindings.json`.
- Touch in the viewer: `app_v2/.../ui/player/TouchZoneConfig.kt`.
- Browsing list: `app_v2/.../ui/browse/MediaFileAdapter.kt`, gamepad routing in `BrowseActivity`.
