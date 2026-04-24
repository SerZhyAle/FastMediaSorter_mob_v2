# VR Immersive Controls

*Last updated: 2026-04-24*

This reference covers every input device supported in the VR flavor's immersive
(headset) playback mode: Meta Quest Touch Plus / Touch Pro controllers, a paired
Bluetooth keyboard, and a paired Bluetooth mouse. All three layers share the
same command dispatcher, so each action is available through whichever input
you prefer.

A cheatsheet auto-appears once on the very first immersive entry (4 seconds).
To bring it back any time: long-press **Y** on the left controller or press
**F1** on the keyboard.

## 1. Touch controllers

Supported models: Touch Plus (Quest 2, Quest 3) and Touch Pro (Quest Pro).
Both use the same logical binding set; our app registers suggested bindings
for both interaction profiles so it behaves identically on every headset.

### Right controller

| Control | Action |
|---|---|
| **A** | Pause / Play. Main playback key. |
| **B** | Exit to the 2D panel. Always-reachable escape. |
| **Trigger** | (Reserved for future ray-pointer + click on overlay.) |
| **Grip** | Zoom. Hold and move hand forward/back to resize the image. |
| **Thumbstick ↑ / ↓** | Volume up / down. |
| **Thumbstick ← / →** | Previous / next file. Edge-trigger only — no auto-repeat. |
| **Thumbstick click** | Re-center the scene in front of your face. |

### Left controller

| Control | Action |
|---|---|
| **X** | Exit to the 2D panel (duplicate of B). |
| **Y (short press)** | Open the **file operations** panel (copy / move / delete / rename / info). |
| **Y (long press ≥ 0.8s)** | Toggle the controls cheatsheet. |
| **≡ (menu)** | Open the playback control dialog (stereo format, audio track, subtitles, speed, brightness). |
| **Trigger** | (Reserved for future ray-pointer.) |
| **Grip** | Zoom — same behavior as the right grip. |
| **Thumbstick ↑ / ↓** | Volume up / down (duplicate of the right stick). |
| **Thumbstick ← / →** | Seek −10s / +10s. Edge-trigger, no auto-repeat. |
| **Thumbstick click** | Toggle immersive ↔ flat screen inside the VR task. |

### Combos

- **Both grips held ≥ 1 second** → reset zoom to 1.0×.

## 2. Bluetooth keyboard

Four convention layers are stacked so you can keep whatever habit you bring:
Norton Commander F-keys (first-class), Windows / MPC-HC / VLC shortcuts,
browser-YouTube letters, and standard BT media keys.

### F-keys (Norton Commander)

| Key | Action |
|---|---|
| **F1** | Show / hide the cheatsheet. |
| **F2** | Rename the current file. |
| **F3** | About file (opens the info view through the ops panel). |
| **F4** | Playback control dialog. **Alt+F4** = exit. |
| **F5** | Copy to.. |
| **F6** | Move to.. |
| **F7** | Open the file operations panel. |
| **F8** | Delete (confirmation dialog). |
| **F10** | Exit to 2D panel. |

### Windows / VLC / MPC-HC

| Key | Action |
|---|---|
| **Space** | Pause / Play. |
| **← / →** | Seek ±10s. |
| **Shift + ← / →** | Micro-seek ±3s. |
| **Ctrl + ← / →** | Macro-seek ±60s. |
| **↑ / ↓** | Volume ± |
| **Page Up / Page Down** | Previous / next file. |
| **M** | Mute / unmute. |
| **Delete** | Delete (confirmation dialog). |
| **Esc** | Exit. |
| **Ctrl + R** | Rename (duplicate of F2). |
| **Ctrl + C** | Copy to.. (duplicate of F5). |
| **Ctrl + X** | Move to.. (duplicate of F6). |
| **Ctrl + I** | About file (duplicate of F3). |
| **Ctrl + Y** | Open the file operations panel. |
| **+ / −** | Zoom in / out (discrete steps). |
| **0** | Reset zoom to 1.0×. |

### YouTube browser style

| Key | Action |
|---|---|
| **K** | Pause / Play. |
| **J / L** | Seek ±10s. |
| **N / P** | Next / previous file. |
| **C** | Re-center the scene. |
| **V** | Toggle immersive ↔ flat screen. |
| **Tab** | Playback control dialog. |
| **Enter** | Playback control dialog. |

### BT remotes / media keys

- **Media Play/Pause** → Pause / Play.
- **Media Next / Previous** → Next / previous file.
- **Volume ± / Mute** are **not** intercepted — Android's system keys handle them.

## 3. Bluetooth mouse

Mouse input is a parallel surface to the controller ray (this iteration uses
the Android cursor; a 3D ray overlay is future work).

| Control | Action |
|---|---|
| Move | Cursor on the 2D overlay (when rendered). |
| **Left button** | Pause / Play (or click the overlay element under the cursor). |
| **Right button** | Playback control dialog. |
| **Middle button** | Re-center the scene. |
| **Button 4 / Button 5** | Previous / next file (standard gaming-mouse side buttons). |
| **Wheel ↑ / ↓** | Volume ±. |
| **Shift + Wheel** | Seek ±10s. |

## 4. File operations workflow

1. Open the panel with **Y** (left controller), **F7**, or **Ctrl+Y** — or jump
   straight to an action via **F2 / F3 / F5 / F6 / F8**.
2. Playback auto-pauses while the panel is visible and resumes when it closes
   (only if the pause was applied by the panel).
3. For Copy / Move, the "where to?" dialog shows up to ten recent destinations
   with a one-click "Choose another folder.." escape hatch that routes back to
   the 2D panel for the full folder tree.
4. Delete always shows a confirmation with the file name and size.
5. Rename opens a simple text-entry dialog (requires the BT keyboard or the
   Quest passthrough on-screen keyboard).

## 5. HUD indicators

Auto-dismissing toasts overlaid on the scene provide visual feedback:

- Pause / Play icon (center, 0.8 s)
- Volume percent (right side, 1.5 s)
- Seek bar `[pos / total]  ±Ns` (bottom, 1.2 s)
- File name + `3 / 12` (top-right, 2 s)
- Zoom factor `1.2×` (center, 1 s)
- "Recentered" flash (center, 0.4 s)
- "First file" / "Last file" (top-right, 1.5 s)

## 6. Troubleshooting

- **Controllers silent in immersive.** Confirm you're on the `vr` flavor; other
  flavors never load the OpenXR input system. On Quest Pro with Touch Pro
  controllers, the same bindings are active — they share the logical action set.
- **Keyboard works in the 2D panel but not inside VR.** Force-close the
  activity and reopen; some BT keyboards need to be paired after the immersive
  session is focused.
- **Mouse cursor not visible.** The current iteration renders the cursor on the
  2D decor surface beneath the OpenXR composition layer, which is invisible in
  the headset. Controller ray-pointer and full mouse-on-quad rendering are
  planned follow-up work.
