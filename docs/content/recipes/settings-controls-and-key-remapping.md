---
page_id: settings.controls-and-key-remapping
title: Controls and Key Remapping - Keyboard, TV Remote and Gamepad, Your Way
nav_title: Controls and key remapping
description: Remapping any command to a D-pad, gamepad, keyboard or mouse button, faster D-pad and gamepad navigation in large grids, the travelling focus frame that always shows where you are, readable VR gesture names, and D-pad focus fixes across Permissions, empty lists and custom views.
category: "Settings & Navigation"
category_slug: settings
ticket: S2962
flavor: All editions - remapping, D-pad and gamepad navigation, the focus frame and the automatic initial focus are in Standard, Lite, Photos and Legacy; the VR Only keybinding group needs a build with a VR runtime
recipe_number: "06"
canonical_url: documentation/settings/controls-and-key-remapping.html
why: |
  Not everyone reaches for the screen with a finger - a TV remote, a car head unit's D-pad, a gamepad or a plain keyboard all need to move around the app just as directly. FastMediaSorter lets you remap what each button does, keeps you moving fast through a large grid, and always shows you exactly where focus is, so the remote is never guesswork.
ingredients:
  - "FastMediaSorter, any edition, with **Settings** open. See [Finding your way around Settings](page:settings.settings-overview-and-search) if this is your first visit."
  - "Hardware: a TV remote, a [D-pad](term:d-pad), a gamepad, a physical keyboard or a mouse connected to the device."
steps:
  - number: 1
    id: remap
    title: Remap any command to your remote, gamepad, keyboard or mouse
    text: |
      Open **Settings**, **General**, and tap **Controls & Keybindings** to reach the key-binding screen for navigation and playback. Every command - grouped as **Playback**, **Navigation**, **Browser Actions**, **View & Zoom**, **Audio & Subtitles**, **Sorting Actions**, **System UI** and more - can be reassigned to a D-pad button, a gamepad button, a keyboard key or a mouse button. The **Browser Actions** group is remapped on its own, separate from the player's transport buttons, because the app reads which surface is active before deciding what a button does - the same physical button can browse a file list in one place and skip a track in another. Reset one group at a time from its own menu, or use the reset-all action to put every binding back to its factory defaults.
    image_bookmark:
      shot_id: settings.controls-keybindings-screen
      device_profile: tv
      screen_state: settings-controls-keybindings-command-list
      alt: The Controls and Keybindings screen with a list of command groups and their current key assignments
      caption: "Every command, remapped to what you actually hold."
      title: "Screenshot: Controls & Keybindings"
      desc: Controls & Keybindings screen, command list with group headers, one binding highlighted for capture.
  - number: 2
    id: grid-navigation
    title: Faster D-pad and gamepad navigation in large grids
    text: |
      Hold a direction on the D-pad, remote or keyboard past a short repeat threshold in any large grid, and it jumps a whole page at a time instead of one cell. A gamepad's left stick works the same way, with its own dead zone and hold-to-accelerate; the right stick scrolls the active list, and the shoulder buttons page-jump too - the player keeps its own separate button mapping, so none of this changes how it behaves. If focus ever lands on an empty or bare scrollable area - after data loads late, for instance - the next directional key press moves it straight to the first real control instead of leaving it stuck nowhere.
    image_bookmark:
      shot_id: settings.dpad-grid-navigation-acceleration
      device_profile: tv
      screen_state: browse-grid-dpad-acceleration-demo
      alt: A large media grid with the D-pad focus frame mid page-jump after a held directional key
      caption: "Hold a direction, and the grid jumps a page at a time."
      title: "Screenshot: D-pad and gamepad grid navigation"
      desc: File browser grid view, focus frame visible, page-jump in progress under a held D-pad direction.
  - number: 3
    id: focus-frame
    title: Always know where focus is
    text: |
      Under a remote, keyboard or gamepad, a single highlighted frame - or, on some controls, an accent outline drawn on the control itself - travels with focus across every screen, form and dialog, so you can always see where you are; it only appears under non-touch control, never while you're using your finger. Opening any screen puts focus on a real control straight away, so the first key press does something instead of just waking focus up. In the player, the outline steps aside: arrow keys move focus between the on-screen transport controls instead, and the video or artwork surface itself is never outlined.
    image_bookmark:
      shot_id: settings.tv-focus-indicator-frame
      device_profile: tv
      screen_state: settings-screen-dpad-focus-frame-visible
      alt: A settings screen with the travelling focus frame highlighting the active control under D-pad control
      caption: "One frame, following focus everywhere."
      title: "Screenshot: Travelling focus frame"
      desc: Settings screen, D-pad focus frame around the active row, non-touch mode.
  - number: 4
    id: vr-labels
    title: VR gestures, labeled so you can read them
    text: |
      On a build with a VR runtime, the same **Controls & Keybindings** screen carries a **VR Only** group for headset gestures. Its rows show a readable name - Swipe Left, Double Pinch and the rest of the named gestures - or a clean "VR N" for a code with no name yet, instead of the raw "VR[15]" form the codes arrive as. A build without a VR runtime never shows this group at all.
    image_bookmark:
      shot_id: settings.vr-only-keybinding-group
      device_profile: phone
      screen_state: settings-controls-keybindings-vr-only-group
      alt: The VR Only group in Controls and Keybindings with readable gesture names like Swipe Left and Double Pinch
      caption: "VR gestures, in plain words."
      title: "Screenshot: VR Only keybinding group"
      desc: Controls & Keybindings screen scrolled to the VR Only group, readable gesture labels visible.
  - number: 5
    id: focus-fixes
    title: D-pad focus that stays where it belongs
    text: |
      The Permissions screen and its settings sub-fragments take D-pad focus correctly, so the remote always has something to move onto. An overlay fragment - permissions, extensions, licenses - keeps focus inside itself instead of letting a directional key slip through to the settings screen hidden underneath it. An empty file browser list handles arrow keys and next/previous-file commands without a problem. And the custom views that draw their own UI - the player, the mini-game, the launcher desktop and the gesture settings screen - all answer to the D-pad and the keyboard, and their colors follow the app's theme instead of staying fixed regardless of it.
    image_bookmark:
      shot_id: settings.permissions-screen-dpad-focus
      device_profile: tv
      screen_state: permissions-screen-dpad-focus-frame
      alt: The Permissions screen with the D-pad focus frame on a permission row under non-touch control
      caption: "The Permissions screen, reachable by remote end to end."
      title: "Screenshot: Permissions screen D-pad focus"
      desc: Permissions screen, focus frame on a row, non-touch input active.
outcome: |
  Every command answers to whatever you're actually holding: remapped keys on a D-pad, gamepad, keyboard or mouse, fast page-jumps through large grids, a focus frame that always shows where you are, readable VR gesture names, and a Permissions screen, empty lists and custom views that all take D-pad focus the way the rest of the app does.
tips:
  - '**Reassigning a key already in use?** The capture dialog says so immediately - "Already bound to: .." - pick a different key, or finish the swap and reset the other command later.'
  - "**Using a TV remote for the first time?** See [Keyboard, D-Pad and Android TV Control](page:general.keyboard-dpad-tv-navigation) for the full rundown of moving around the app this way."
  - "**Want the everyday behavior settings next?** See [Playback, power and everyday behavior](page:settings.playback-and-sorting-preferences)."
next_recipes:
  - title: Privacy, saved sign-ins and error reports
    url: page:settings.privacy-and-network-security
    badge: Settings
    badge_type: docs
    description: Saved sign-ins, unused credentials, secure screens and sending a crash report.
  - title: Backing up and syncing your device
    url: page:settings.backup-and-device-sync
    badge: Settings
    badge_type: docs
    description: Exporting and restoring settings, resources and favorites, and keeping devices in step.
  - title: Keyboard, D-Pad and Android TV Control
    url: page:general.keyboard-dpad-tv-navigation
    badge: General
    badge_type: docs
    description: Keyboard, D-pad and remote-control navigation across the app.
---

Remap any command to a [D-pad](term:d-pad), gamepad, keyboard or mouse button, move fast through large grids, and always see where focus is with the travelling focus frame - plus readable VR gesture names and a Permissions screen, empty lists and custom views that all take D-pad focus the way the rest of the app does.
