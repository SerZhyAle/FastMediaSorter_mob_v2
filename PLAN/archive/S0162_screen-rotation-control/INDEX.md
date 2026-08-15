# S0162 — Screen Rotation Control — Tactical Index

**Status:** Tactical  
**Strategic spec:** [S0162_screen-rotation-control.md](../S0162_screen-rotation-control.md)

---

## Phases

| Phase | File | Scope |
|-------|------|-------|
| 01 | [PHASE_01__domain-settings.md](PHASE_01__domain-settings.md) | AppSettings + SettingsRepositoryImpl |
| 02 | [PHASE_02__rotation-manager.md](PHASE_02__rotation-manager.md) | ScreenRotationManager (new class) |
| 03 | [PHASE_03__settings-ui.md](PHASE_03__settings-ui.md) | PlaybackSettingsFragment + layout |
| 04 | [PHASE_04__command-panel.md](PHASE_04__command-panel.md) | CommandPanelLayoutPlanner + layout XML |
| 05 | [PHASE_05__player-wiring.md](PHASE_05__player-wiring.md) | PlayerActivity integration |
| 06 | [PHASE_06__keybinding.md](PHASE_06__keybinding.md) | CommandId + default_bindings.json + PlayerKeyboardHandler |

---

## Architecture Decisions

### ADR-1: No ContentObserver for OS auto-rotate
Re-read `Settings.System.ACCELEROMETER_ROTATION` in `onResume()` and on app-settings change.
Continuous listening adds lifecycle complexity with negligible UX gain — "follow OS" mode
reads the OS state at the moment the player is foregrounded.

### ADR-2: Default = followSystemRotation=true
The manifest already declares `screenOrientation="sensor"` for PlayerActivity and
StandalonePlayerActivity. `SCREEN_ORIENTATION_SENSOR` respects the OS auto-rotate toggle.
Default ON means no behavior change on upgrade.

### ADR-3: playerRotationSensorEnabled default = true
First time the user turns off global delegation, the player trigger starts in "sensor active"
state. Locks on first explicit "lock" tap by the user.

### ADR-4: Draw Mode interaction
`ImageDrawOverlayManager.stopDraw()` currently restores `SCREEN_ORIENTATION_UNSPECIFIED`.
After Phase 05, it must call `ScreenRotationManager.reapply(activity)` instead so S0162 state
is restored correctly after exiting draw mode.

### ADR-5: StandalonePlayerActivity out of scope
`StandalonePlayerActivity` uses `StandaloneVideoControlsManager` (not `CommandPanelController`)
and targets external file-manager intents. Phase 05 covers `PlayerActivity` only.
Standalone follow-up is a separate spec.

### ADR-6: No Toast on toggle
Icon change in the command button is sufficient feedback. Consistent with Fullscreen and
Black Screen toggles which also carry no toast.

### ADR-7: Settings placement
"Player UI" collapsible section in `PlaybackSettingsFragment` — same section as
`switchHideSystemUiInFullscreen` and `switchShowCommandPanel`. Accelerometer guard hides
the row on devices without `FEATURE_SENSOR_ACCELEROMETER`.

### ADR-8: ROTATION_TOGGLE command priority = 490
Placed between SEARCH_YOUTUBE_MUSIC (250) and SLEEP_TIMER (500).
barCapable=true — shows on bar only when all higher-priority commands have fit and
space remains. Effectively overflow-bound on most layouts.
