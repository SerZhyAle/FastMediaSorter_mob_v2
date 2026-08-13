# S0184 Tactical Plan - Multi-Window Capability

Strategic spec: `PLAN/S0184_multi-window-capability.md`

## Scope

- Enable the Standard-version setting that allows separate windows.
- Default the setting on for ChromeOS, Android XR/VR, freeform-window capable devices, and desktop-mode capable devices.
- Show "Open in new window" in the resource overflow menu when the setting is enabled.
- Show "Open in new window" in the file overflow menu when the setting is enabled.
- Reactivate the player command panel entry for separate-window playback.

## Phases

1. Capability default and settings wiring.
2. Browse resource and file menu entry points.
3. Player command reactivation and launch-state preservation.
4. Strings, catalog, validation, and BlockNeedUserTest handoff.

