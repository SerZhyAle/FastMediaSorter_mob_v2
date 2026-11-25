<sub class="doc-stamp">26.11.25 02:51</sub>

# Pointer - `INPUT-PARITY`

| | |
| --- | --- |
| **Id** | `INPUT-PARITY` |
| **Version** | 0.9, draft. Owner: shared, one steward per device column |
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
- A touch-locked screen (0.7, section 3.3, items K and L) - a full-screen state that keeps running while touch cannot
  be trusted - is named in the registry row, is ended or changed by no touch, and is left only by a non-touch input:
  its hardware button held 1500 ms, or three hardware events inside 2000 ms, or the platform's task-lock exit. Its
  exit is the one action exempt from section 3.2 rules 1 and 3, a permanent supported variant and not an exception.
  The water flashlight (`WaterFlashlightActivity`, `WaterFlashlightLockdownManager`) is this product's touch-locked
  screen and a permanent supported variant, named in the registry row. Its exit is `WaterFlashlightExitGate`: one of
  the six hardware exit keys held 1500 ms, or three presses of them with the third at most 2000 ms after the first,
  read against item K.3 from the code and not run. `Back` is deliberately not an exit event (under gesture navigation
  it arrives from a swipe across the glass, which K.2 forbids from ending the screen), so the gate is stricter than
  K.3's "platform's back". Item K.4 (help and listing wording, `ask: off`) is a separate read.
- The watch Tourist lock (`TouristLockOverlay`, the dashboard touch lock) is this product's second touch-locked screen
  and a permanent supported variant: it keeps the telemetry readable while swimming or running, and no touch or crown
  turn reaches the dashboard under it. Its exit is the watch `WaterFlashlightExitGate`, shared with the watch water
  flashlight: a key held 1500 ms, or three backs or key releases with the third at most 2000 ms after the first; one
  key release or one back does not leave. Read from the code, not run on a watch.
- 0.8 (items M and N): "applicable" is the scope of sections 3 and 4 as a whole (section 3.2 rule 6): a product with no
  search function writes `search` as not applicable and a product that has one binds `Ctrl+F`. This product has one,
  and `system.search` is bound to `Ctrl+F` in `default_bindings.json`; no filled cell and no canonical spelling changes.
- The deviations currently open are recorded as exceptions in the catalog's registry. The 0.4 `VIEWER-KEYBOARD.md`
  player defaults ship in `default_bindings.json` and `PlayerKeyboardHandler` (S4363); the rows once open
  (`G`, `N`, `F4`, frame step, A-B loop, the standalone hosts) are bound and routed in the tree and await device
  acceptance. The watch video player binds the crown to volume with no touch volume route (0.6 item J), carrier S4898. The registry exception stays open until the collection surfaces, numbered-frame stepping,
  A-B repeat and host routes are observed on device; a compile or mocked timestamp test is not that verdict.

`INPUT-CHORD`, the chord notation beside it in the same folder, is not adopted: our bindings are stored as an
internal key-code record that never leaves the app, and in-window shortcuts are outside its scope.

## Where it lives here

- Bindings and routing: `app_v2/.../core/input/` - `KeyBindingManager`, `GamepadInputManager`,
  `GamepadNavigationTranslator`, `TvKeyRouter`; defaults in `app_v2/src/main/assets/input/default_bindings.json`.
- Touch in the viewer: `app_v2/.../ui/player/TouchZoneConfig.kt`.
- Browsing list: `app_v2/.../ui/browse/MediaFileAdapter.kt`, gamepad routing in `BrowseActivity`.
