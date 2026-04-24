# Specification: GAMEPAD-SUPPORT — Comprehensive Gamepad Control

**Status:** Backlog
**Date:** 2026-04-24
**Tier:** TBD upon activation
**Roadmap entry:** Ad-hoc — user request 2026-04-24. Covers the entire application: file browser, player (standard and VR), and settings.

---

## 1. Problem Statement

Standard gamepads (Xbox, DualSense, 8BitDo, etc.) can be connected to Quest and Android devices via Bluetooth or USB OTG. Currently, the application does not meaningfully process any `KEYCODE_BUTTON_*` or `AXIS_*` events, except for a few codes in `VrPlayerActivity.dispatchKeyEvent` (which are not invoked in immersive mode — see `spec_vr-immersive-controls.md` §1). When a user presses gamepad buttons, there is no response from the app.

---

## 2. Goals & Event Mapping

### Player (`PlayerActivity` + `VrPlayerActivity`)

1. **A / Cross** (`KEYCODE_BUTTON_A`) — Play / Pause.
2. **B / Circle** (`KEYCODE_BUTTON_B`) — Exit player (Back).
3. **X / Square** (`KEYCODE_BUTTON_X`) — Next file.
4. **Y / Triangle** (`KEYCODE_BUTTON_Y`) — Previous file.
5. **L1 / LB** (`KEYCODE_BUTTON_L1`) — Seek −10s.
6. **R1 / RB** (`KEYCODE_BUTTON_R1`) — Seek +10s.
7. **Left Stick ↑↓** (`AXIS_Y`) — Volume + / −.
8. **Right Stick ↑↓** (`AXIS_RZ` / `AXIS_Z`) — Analog seek (speed depends on stick deflection).
9. **Start / Options** (`KEYCODE_BUTTON_START`) — Toggle `PlaybackControlDialog` (HUD).
10. **Select / Share** (`KEYCODE_BUTTON_SELECT`) — Toggle control hints overlay.

### File Browser (`BrowseActivity` / `MainActivity`)

1. **D-pad** (`KEYCODE_DPAD_*`) & **Left Stick** — UI focus navigation across the file list.
2. **A** — Open / Select file (equivalent to tap).
3. **B** — Back / Up folder hierarchy.
4. **X** — Toggle item selection (multi-select mode).
5. **Y** — Open file context menu.
6. **Start** — Global search.
7. **L2/R2** (Triggers) or `LB/RB` — Switch tabs / Toggle view (List ↔ Grid).

### Settings & Dialogs

1. **D-pad / Left Stick** — Navigate between items.
2. **A** — Confirm / Apply.
3. **B** — Cancel / Close.

---

## 3. Architecture & Technical Complexity: MEDIUM

**Key Technical Decisions:**

- **Activity Logic Prohibited:** In strict adherence to the architecture rules, input handling MUST NOT be placed inside `dispatchKeyEvent` of the Activities. A dedicated `GamepadInputManager` (or an extension of an existing `InputManager`) must be implemented to intercept `KeyEvent` and `MotionEvent`, translating them into domain actions (`PlayerAction`, `NavigationAction`).
- **Analog Sticks:** Processing `onGenericMotionEvent` requires implementing a deadzone (approx. `0.15`) and rate-limiting for smooth volume and seek control, preventing erratic jumps.
- **Focus Visuals:** For proper `RecyclerView` and menu navigation, all interactive elements must have `android:focusable="true"` and an appropriate focus highlight (e.g., `android:background="?selectableItemBackground"` or a custom `StateListDrawable`) to clearly indicate the currently focused item.
- **VR Immersive Mode:** In `VrPlayerActivity`, gamepad events must be routed in parallel with OpenXR controllers without causing conflicts.

---

## 4. UI AMBIGUITY GATE

Before starting implementation, the following UX decisions must be resolved:

- [ ] **Focus Indication:** Is the standard system highlight (grey/semi-transparent background) sufficient for list items, or do we need a more prominent visual cue (like a TV-style border/scaling) for couch/VR readability?
- [ ] **On-screen Hints:** Should floating gamepad hints (e.g., "A - Select | B - Back | Y - Menu") appear at the bottom of the screen upon gamepad detection?
- [ ] **Empty State Focus:** Where does the focus land when the last file in a folder is deleted via gamepad?
- [ ] **Player HUD Focus:** When opening the HUD via the Start button, should the focus automatically snap to the Play/Pause button, or remain hidden until the first D-pad interaction?

---

## 5. Flavors

Support spans all flavors (`standard`, `lite`, `photos`, `legacy`, `vr`). In the `vr` flavor, the player mapping is unified through base classes/interfaces (`PlayerActionReceiver`), ensuring consistent behavior even though `VrPlayerActivity` is used instead of the standard activity.

---

## 6. References

- [Android — Game controller input](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input)
- [Android — Supporting controllers](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/multi-controller)
- [Android — Handling MotionEvent for gamepad axes](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input#process-axis)

---

## 7. Out of Scope (Non-goals)

- Gamepad as the primary OS navigation method (this is handled by Meta/Android).
- Custom gamepad remapping (this is covered by `spec_player-keybinding-remapping.md`).
- Haptic feedback (Rumble) via gamepad — technically possible via `InputDevice.getVibrator()`, but deferred to a separate polishing task.
- Simultaneous support for multiple gamepads (e.g., for local split-screen).
- Using the gamepad for text input (the on-screen keyboard remains standard).
- Gamepad support in the Wear OS companion app.
