---
layout: doc
title: "VR Immersive Controls"
permalink: /docs/VR_CONTROLS.html
---

<sub class="doc-stamp">26.10.21 15:54</sub>

# VR Immersive Controls

*Last updated: 2026-10-21*

## Controls in the headset

The immersive OpenXR session opens from the player's VR badge, Browse's
"Open in VR Cinema" menu item, or the "Test Immersive" button in Settings (see
[VR Edition](VR_EDITION.md)). It is driven by the Touch controllers:

- The controller **trigger** is the ray click: point at the HUD strip and pull to
  press whatever the ray is crossing.
- **A** on the right controller or **X** on the left leaves the immersive session
  immediately, with no confirmation. A short grace period right after entry stops
  the first frame from being dismissed by accident.
- The clickable **HUD strip** is a wide, low panel carrying the file name, a
  PREV / PLAY / NEXT transport trio, a volume slider, a stereo-depth slider for
  3D material, and audio / subtitle rows when the file actually has more than
  one track to choose from. You point at it with the controller ray and pull the
  trigger to press a control.
- A **position bar** on the strip shows the elapsed and the total time; drag it
  with the ray to seek within a film.
- The strip's two end buttons are terminal: **HIDE** at the right end takes the
  panel away completely while playback continues, and **EXIT** at the left end
  stops playback and returns you to the flat player at the same position. They
  sit at opposite ends on purpose, so one is never mistaken for the other.
- The strip also hides itself after 15 seconds without the ray touching it; any
  hover or click restarts the countdown.
- Once hidden, the strip is summoned back by **pulling the trigger**. That pull
  is consumed - it only brings the panel up, it never presses whatever the ray
  happened to be crossing. While the panel is up the trigger is the ordinary
  click again.
- **Thumbstick left / right seeks** the film by 10 seconds a step. To move to
  the previous or next *file*, hold **grip** and then push the stick - the grip
  is a modifier, and a grip used that way will not drag the panel along with
  it. Live sources with no known length ignore the seek instead of jumping
  somewhere arbitrary. Zoom is on the thumbstick's up/down axis.
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

## The browse grid in the headset

The same controllers drive the **in-headset browse grid**, the other surface
the session can show (see [VR Edition](VR_EDITION.md)):

- Point at a tile and pull the trigger: a folder opens in place, a file starts
  playing in the immersive scene.
- The **thumbstick** steps the highlight across the grid - left and right walk
  the columns, up and down walk the rows, and a step off the side turns the
  page. The trigger opens the highlighted tile even when the ray points away
  from the pane.

File operations - copy, move, rename and delete - are done in the flat panel.

## Streams screen navigation

The Streams screen is a 2D panel (not immersive) and is driven by the normal Android input surface - Touch controller ray-pointer, Bluetooth keyboard, or Bluetooth mouse cursor.

- Open Streams from the main window dropdown or from Settings > Media > Streams.
- Select a radio station to start inline audio playback; the sticky mini-player appears at the bottom of the list. The list stays scrollable and interactive while audio plays.
- Select a video or RTSP stream to open the fullscreen player. Press **B** (right controller), **Esc** (keyboard), or **Back** to return to the Streams list; scroll position and last-selected source are preserved.
- Background audio playback behavior is the same as on the phone: with background playback OFF, leaving the Streams screen stops the radio.

## Troubleshooting

- **A controller button in immersive does nothing.** The bindings are the ones
  listed above - trigger, thumbstick, grip, A/X and the left menu button
  (session settings panel). B, Y and the thumbstick click are deliberately left
  free. The legend on first entry, or the strip's HELP button, lists them.
- **A Bluetooth keyboard or mouse.** Both drive the flat 2D panels - Browse,
  Streams and the flat player. The immersive session is driven by the
  controllers.
