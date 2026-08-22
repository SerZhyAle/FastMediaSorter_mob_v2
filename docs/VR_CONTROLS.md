---
layout: default
title: "VR Immersive Controls"
permalink: /docs/VR_CONTROLS.html
---

# VR Immersive Controls

*Last updated: 2026-07-29*

## Where things stand today

Today's immersive OpenXR session - opened from the player's VR badge, Browse's
"Open in VR Cinema" menu item, or the "Test Immersive" button in Settings (see
[VR Edition](VR_EDITION.md), `noLegal` sideload build) - has a deliberately
small control scheme:

- The controller **trigger** is the ray click: point at the HUD strip and pull to
  press whatever the ray is crossing.
- **A** on the right controller or **X** on the left leaves the immersive session
  immediately, with no confirmation. A short grace period right after entry stops
  the first frame from being dismissed by accident.
- A clickable HUD strip **is** on screen: a wide, low panel carrying the file
  name, a PREV / PLAY / NEXT transport trio, a volume slider, a stereo-depth
  slider for 3D material, and audio / subtitle rows when the file actually has
  more than one track to choose from. You point at it with the controller ray
  and pull the trigger to press a control.
- The strip's two end buttons are terminal: **HIDE** at the right end takes the
  panel away completely while playback continues, and **EXIT** at the left end
  stops playback and returns you to the flat player at the same position. They
  sit at opposite ends on purpose, so one is never mistaken for the other.
- Once hidden, the strip is summoned back by **pulling the trigger**. That pull
  is consumed - it only brings the panel up, it never presses whatever the ray
  happened to be crossing. While the panel is up the trigger is the ordinary
  click again.
- **Thumbstick left / right seeks** the film by 10 seconds a step. To move to
  the previous or next *file*, hold **grip** and then push the stick - the grip
  is a modifier, and a grip used that way will not drag the panel along with
  it. Live sources with no known length ignore the seek instead of jumping
  somewhere arbitrary.
- The **left controller's menu button (≡)** opens the in-headset **session
  settings panel**, and pressing it again closes the panel and brings the strip
  back. The panel is modal over the strip - they share the one HUD surface - and
  playback keeps running underneath. Five rows, all scoped to the current
  session and reset on exit: **layout** override (Auto / mono / side-by-side /
  over-under), **projection** override (Auto / flat / 180° / 360°), **panel
  distance** and **panel size** sliders for the control strip, **subtitles**
  (same cycle as the strip's row), and **resume position** (continue from the
  flat player's position, or start from the beginning). Auto keeps the
  filename-based detection.
- On the **first immersive entry after install** a controls legend appears by
  itself and lists every binding above. Any controller press closes it, and that
  press does nothing else. The **HELP** button on the strip, immediately left of
  HIDE, brings the legend back at any time.
- The same ray drives the **in-headset browse grid**, which is the other surface
  the session can show (see [VR Edition](VR_EDITION.md)). Point at a tile and
  pull the trigger: a folder opens in place, a file starts playing. There is no
  separate grid control scheme - the ray and the trigger are the whole of it, and
  thumbstick navigation of the grid is not implemented yet.
- Still missing in the headset: the file-operations panel. Zoom is on the
  thumbstick's up/down axis, not a HUD control.

Everything below this point - the full Touch controller layout, Bluetooth
keyboard shortcuts, mouse support, file-operations panel, and detailed HUD -
is the **target design for the dedicated VR Cinema** (epic S0773, still in
development, not shipped). Read it as a design reference, not as what works
in the headset today.

This reference covers every input device planned for the VR flavor's immersive
(headset) playback mode: Meta Quest Touch Plus / Touch Pro controllers, a paired
Bluetooth keyboard, and a paired Bluetooth mouse. All three layers are meant to
share the same command dispatcher, so each action would be available through
whichever input you prefer.

A cheatsheet is designed to auto-appear once on the very first immersive entry
(4 seconds), brought back any time with a long-press of **Y** on the left
controller or **F1** on the keyboard. That is the target design and not what
ships: the legend described above has no timer, waits for a controller press, and
is recalled with the strip's HELP button rather than with **Y** or **F1**.

## 1. Touch controllers (target design, not shipped yet)

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
| **Thumbstick ← / →** | Previous / next file. Edge-trigger only - no auto-repeat. |
| **Thumbstick click** | Re-center the scene in front of your face. |

### Left controller

| Control | Action |
|---|---|
| **X** | Exit to the 2D panel (duplicate of B). |
| **Y (short press)** | Open the **file operations** panel (copy / move / delete / rename / info). |
| **Y (long press ≥ 0.8s)** | Toggle the controls cheatsheet. |
| **≡ (menu)** | Open the playback control dialog (stereo format, audio track, subtitles, speed, brightness). |
| **Trigger** | (Reserved for future ray-pointer.) |
| **Grip** | Zoom - same behavior as the right grip. |
| **Thumbstick ↑ / ↓** | Volume up / down (duplicate of the right stick). |
| **Thumbstick ← / →** | Seek −10s / +10s. Edge-trigger, no auto-repeat. |
| **Thumbstick click** | Toggle immersive ↔ flat screen inside the VR task. |

### Combos

- **Both grips held ≥ 1 second** → reset zoom to 1.0×.

## 2. Bluetooth keyboard (target design, not shipped yet)

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
- **Volume ± / Mute** are **not** intercepted - Android's system keys handle them.

## 3. Bluetooth mouse (target design, not shipped yet)

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

## 4. File operations workflow (target design, not shipped yet)

1. Open the panel with **Y** (left controller), **F7**, or **Ctrl+Y** - or jump
   straight to an action via **F2 / F3 / F5 / F6 / F8**.
2. Playback auto-pauses while the panel is visible and resumes when it closes
   (only if the pause was applied by the panel).
3. For Copy / Move, the "where to?" dialog shows up to ten recent destinations
   with a one-click "Choose another folder.." escape hatch that routes back to
   the 2D panel for the full folder tree.
4. Delete always shows a confirmation with the file name and size.
5. Rename opens a simple text-entry dialog (requires the BT keyboard or the
   Quest system on-screen keyboard).

## 4a. Streams screen navigation

The Streams screen is a 2D panel (not immersive) and is driven by the normal Android input surface - Touch controller ray-pointer, Bluetooth keyboard, or Bluetooth mouse cursor.

- Open Streams from the main window dropdown or from Settings > Media > Streams.
- Select a radio station to start inline audio playback; the sticky mini-player appears at the bottom of the list. The list stays scrollable and interactive while audio plays.
- Select a video or RTSP stream to open the fullscreen player. Press **B** (right controller), **Esc** (keyboard), or **Back** to return to the Streams list; scroll position and last-selected source are preserved.
- Background audio playback behavior is the same as on the phone: with background playback OFF, leaving the Streams screen stops the radio.

## 5. HUD indicators (target design, not shipped yet)

Auto-dismissing toasts overlaid on the scene provide visual feedback:

- Pause / Play icon (center, 0.8 s)
- Volume percent (right side, 1.5 s)
- Seek bar `[pos / total]  ±Ns` (bottom, 1.2 s)
- File name + `3 / 12` (top-right, 2 s)
- Zoom factor `1.2×` (center, 1 s)
- "Recentered" flash (center, 0.4 s)
- "First file" / "Last file" (top-right, 1.5 s)

## 6. Troubleshooting (today)

- **A controller button in immersive does nothing.** Only the inputs listed in
  "Where things stand today" are wired - trigger, thumbstick, grip, A/X and the
  left menu button (session settings panel). B, Y and the thumbstick click are
  deliberately left free. The legend on first entry, or the strip's HELP
  button, lists the live set.
- **The on-screen banner is a blank grey rectangle.** Known bug (S0961) - it's
  meant to show the filename and detected 3D format; a fix is in progress.
- **A Bluetooth keyboard or mouse does not control playback in immersive.**
  Expected today - keyboard/mouse control of the immersive session is part of
  the target design (epic S0773), not shipped yet. Both work normally in the
  flat 2D panel.

The rest of this page - the full Touch controller layout, keyboard, mouse,
file operations, and HUD - describes the troubleshooting surface once the
dedicated VR Cinema (epic S0773) ships.
