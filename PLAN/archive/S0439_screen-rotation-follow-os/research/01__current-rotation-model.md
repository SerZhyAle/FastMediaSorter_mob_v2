# Research 01 - Current screen-rotation model and migration decision

**Ticket:** S0439
**Resolves:** §6.1 (migration), §6.2 (fresh-install defaults), §6.3 (window scope)
**Date:** 2026-06-16
**Method:** codebase reading (settings model, orientation application, manifest).

## Current persisted settings (S0162)

- `AppSettings.followSystemRotation: Boolean = true` - "delegate to OS auto-rotate".
- `AppSettings.playerRotationSensorEnabled: Boolean = true` - player's own sensor control, active only when `followSystemRotation = false`.
- DataStore keys: `follow_system_rotation`, `player_rotation_sensor_enabled`.
- Absent key resolves to `true` (S0162 chose upgrade-safe defaults: no behaviour change on upgrade).

## How orientation is applied today

- `ScreenRotationManager` (player helper) maps the two flags to `requestedOrientation`:
  - `followSystem = true` -> `SCREEN_ORIENTATION_UNSPECIFIED` (full OS delegation).
  - `followSystem = false`, `sensorEnabled = true` -> `SCREEN_ORIENTATION_SENSOR` (app-driven rotation, ignores OS auto-rotate).
  - `followSystem = false`, `sensorEnabled = false` -> `SCREEN_ORIENTATION_LOCKED`.
- Only player-family activities ever call `setRequestedOrientation`: the player itself and `ImageDrawOverlayManager` (a player overlay that locks during draw).
- `AndroidManifest.xml` declares no `android:screenOrientation` on any activity.

## Consequence for non-player windows

- With no manifest lock and no `setRequestedOrientation`, every non-player activity is effectively `SCREEN_ORIENTATION_UNSPECIFIED`.
- `UNSPECIFIED` already respects the OS auto-rotate system setting. So non-player windows **already follow the OS today** - but they consult no app-level flag.
- The real gap: when a user disables follow-OS and uses the app's own rotation control, that control reaches only the player. Non-player windows cannot be put under app-controlled (OS-independent) orientation at all, and cannot be deliberately locked by the app.

## Decision (corrects the draft's tentative direction)

The draft §6 guessed "map legacy value to the player flag, default the program flag off". Code reading shows that would be a regression: non-player windows already follow OS, so defaulting the program flag off would suddenly stop them rotating on upgrade.

Correct, behaviour-preserving mapping:

- New **program follow-OS** flag <- legacy `followSystemRotation` value (same concept, widened scope; default stays `true`).
- New **player follow-OS** flag defaults `false` on migration (when the program flag ends up off, the player keeps its existing own-control behaviour rather than newly following OS).
- `playerRotationSensorEnabled` unchanged (still the player's own-control sub-setting when not following OS).
- Net upgrade effect: program-follow-OS `true` by default -> all windows follow OS exactly as before; users who had `followSystemRotation = false` get program-off with the player still under its prior sensor/locked control. Zero behaviour change.

## Fresh-install defaults

- program follow-OS = `true` (matches today's `followSystemRotation` default; everything follows OS).
- player follow-OS = `false` (hidden while program flag is on; irrelevant until the program flag is turned off).
- `playerRotationSensorEnabled` = `true` (unchanged).

## Window scope (§6.3)

- All foreground activities should route their orientation decision through one shared policy point so that "program off -> app-controlled / locked" actually reaches non-player windows (today nothing locks them).
- Exact per-activity enumeration (browser/grid, settings, standalone image/video, text, PDF, EPUB viewers, player) is a tactical concern for `/spec-tech`.
