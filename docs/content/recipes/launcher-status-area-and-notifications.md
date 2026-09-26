---
page_id: launcher.status-area-and-notifications
title: The Status Area and Notifications
nav_title: Status area and notifications
description: How to choose whether the launcher or Android owns the status bar, pick which clock, network, battery and hotspot icons show, move them to the top of the screen, and see other apps' notifications without opening them.
category: "Launcher: Taskbar, Menus and Gestures"
category_slug: launcher
ticket: S2960
flavor: Standard and noLegal
recipe_number: "06"
canonical_url: documentation/launcher/status-area-and-notifications.html
why: |
  The clock, the signal bars and the battery number are small, but they are the things you check without thinking. This page is about making them yours: choosing whether they live in the [taskbar](term:taskbar) or in Android's own status bar, picking exactly which ones show, and seeing at a glance when another app has something waiting for you - without opening it by accident.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) turned on - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
  - "The taskbar visible - see [Using the desktop dock and taskbar](page:launcher.taskbar-and-dock)."
steps:
  - number: 1
    id: choose-owner
    title: Choose who owns the status area
    text: |
      Open the launcher settings, **Top bar** group, and turn on **Hide system status bar**. Off by default, so the Android status bar stays and the launcher's own tray simply drops whatever it would otherwise duplicate. Turn it on and the launcher takes over: it hides the system bar and draws its own clock, network and battery instead.
    image_bookmark:
      shot_id: launcher.status-area-owner-setting
      device_profile: phone
      screen_state: launcher-settings-hide-system-status-bar
      alt: The Hide system status bar switch in the launcher settings Top bar group
      caption: "Hide system status bar - off by default."
      title: "Screenshot: Status area owner"
      desc: Launcher settings, Top bar group, Hide system status bar switch shown off then on.
  - number: 2
    id: pick-indicators
    title: Pick which icons show, one by one
    text: |
      In the **System tray** settings, switch each indicator on or off by name: **Clock**, **Bluetooth**, **SIM 1 signal**, **SIM 2 signal**, **Network type**, **Battery**, **Wi-Fi Hotspot** and **Transfer speed**, drawn in that order. **Show status tray** turns the whole strip on or off at once.

      An indicator the device cannot report simply stays absent instead of showing blank - a Bluetooth adapter that is off, a SIM slot with no card, or a phone-state permission you declined. Where the device can report more, it does: Wi-Fi shows its generation digit, Bluetooth shows how many devices are connected, a roaming SIM says so next to its data type, and Transfer speed adds live download and upload numbers, updated once a second.
    image_bookmark:
      shot_id: launcher.status-tray-indicator-toggles
      device_profile: phone
      screen_state: launcher-settings-tray-indicator-list
      alt: The System tray settings list with Clock, Bluetooth, SIM signal, Network type, Battery, Wi-Fi Hotspot and Transfer speed switches
      caption: "Every tray icon has its own switch."
      title: "Screenshot: Tray indicator switches"
      desc: Launcher settings, System tray section, list of indicator switches with some on and some off.
  - number: 3
    id: move-to-top
    title: Move the clock and icons to the top
    text: |
      With **Hide system status bar** on, the **Top bar** group also offers **Top status bar**: turn it on and the clock, now showing seconds, moves to the left of the freed top strip while the device indicators move to the right - leaving the Start panel free to show more recent apps instead of the tray. Turn **Hide system status bar** back off and the indicators return to the taskbar on their own.
    image_bookmark:
      shot_id: launcher.top-status-bar-setting
      device_profile: phone
      screen_state: launcher-top-status-bar-enabled
      alt: The launcher top bar with the clock and seconds on the left and device indicators on the right, Start panel widened below
      caption: "Clock left, indicators right, once the top bar is freed."
      title: "Screenshot: Top status bar"
      desc: Launcher desktop with Top status bar on, clock with seconds on the left of the top strip, indicators on the right.
  - number: 4
    id: battery-and-taps
    title: Read the battery, and tap an icon for its settings
    text: |
      The battery number carries a lightning mark while charging, and its color says how: green from the mains, blue over USB, purple on a wireless pad - the low-battery warning color still wins below 15 percent. Tapping any status icon opens the matching Android settings screen directly: the battery icon opens battery settings, the network icon opens network settings, and so on for the rest of the row.
  - number: 5
    id: other-apps-notifications
    title: See other apps' notifications, without reading them
    text: |
      Turn on **Notifications from other apps on the top bar** and grant the notification access it asks for, and the bar adds one icon per app that currently has something waiting, with a count, placed after the app's own icons - a tap opens that app. Their title, text and attachments are never read; declining the access simply leaves the setting off.

      The strip caps itself at five icons plus a counter for whatever does not fit, newest notification first, and every icon stays clear of the display cutout. To dismiss what you see without opening it, open **Active signals**: each row for another app's notifications carries its own **Dismiss** button, and **Dismiss all** clears every one of them at once - the app's own playback, transfer and background-work rows have no dismiss button, because there is nothing there to clear.
    image_bookmark:
      shot_id: launcher.foreign-notifications-and-signals-panel
      device_profile: phone
      screen_state: launcher-active-signals-panel-dismiss
      alt: The Active signals panel listing other apps' notifications with individual Dismiss buttons and a Dismiss all button in the header
      caption: "Active signals: Dismiss one, or Dismiss all."
      title: "Screenshot: Active signals panel"
      desc: Active signals panel open, grid of signal rows, foreign-notification rows showing Dismiss buttons, Dismiss all in the header.
outcome: |
  The status area is exactly as full as you want it - Android's own bar, or the launcher's, with only the icons you chose, wherever you decided to put them. A tap on any of them takes you straight to its Android settings, and other apps' notifications show up as a quiet count you can clear with one tap, never as text you did not ask to read.
tips:
  - "**An icon is missing and you did not turn it off?** The device itself may not be reporting that signal right now - a SIM slot with no card, Bluetooth switched off, or a permission not yet granted all show up as an absent icon rather than an empty one."
  - "**Dimmed screen still shows the time?** The dim screen keeps the same tray signals, folded and simplified - see [Screen dimming and timeout](page:launcher.screen-dimming-and-timeout)."
  - "**Looking for the desktop weather or network gadgets instead of the tray?** Those live on the desktop itself, covered in [Desktop gadgets](page:launcher.built-in-gadgets)."
next_recipes:
  - title: Using the desktop dock and taskbar
    url: page:launcher.taskbar-and-dock
    badge: Launcher
    badge_type: docs
    description: Where the tray sits, and the rest of what the taskbar offers.
  - title: The Start menu and the full app list
    url: page:launcher.start-menu-and-all-apps
    badge: Launcher
    badge_type: docs
    description: What moves into the Start panel when the top bar takes over the tray.
  - title: Screen dimming and timeout
    url: page:launcher.screen-dimming-and-timeout
    badge: Launcher
    badge_type: docs
    description: What the same status signals look like on the dimmed screen.
---

The taskbar's tray can carry as much or as little as you want. This page covers choosing between the launcher's own status area and Android's, picking indicators one by one, moving them to the top of the screen, and seeing other apps' notifications as a count you control.
