---
page_id: launcher.screen-dimming-and-timeout
title: Screen Dimming and Timeout on the Launcher Desktop
nav_title: Screen dimming and timeout
description: How to set when the launcher desktop's screen turns off, what the warning dim and the dim screen itself show, keep the desktop lit while charging, and where each device profile starts.
category: "Launcher: Taskbar, Menus and Gestures"
category_slug: launcher
ticket: S2960
flavor: Standard and noLegal
recipe_number: "09"
canonical_url: documentation/launcher/screen-dimming-and-timeout-ru.html
why: |
  A tablet left as a kitchen photo frame, a car head unit that should never go dark, or a phone on the nightstand you would rather not have glowing all night: the desktop's own screen timeout decides which of these you get, without you having to remember to lock the device yourself every time.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) [desktop](term:desktop) open - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
steps:
  - number: 1
    id: set-timeout
    title: Choose when the screen turns off
    text: |
      Open the launcher settings - **Launcher settings** in the long-press menu, or **Settings**, **General**, **System launcher settings** - and in the **Desktop** group find **Screen timeout**. Pick **Off (never)**, one of the presets from **5 seconds** to **300 seconds (5 min)**, or type your own number of seconds. Where the device supports it, the countdown locks the device the same way pressing the power button would, and a device with an Always On Display keeps showing it; where a lock is out of reach, the desktop instead falls back to its own black screen, and the setting's own subtitle says so. On by default, at 30 seconds.
    image_bookmark:
      shot_id: launcher.screen-timeout-dialog
      device_profile: phone
      screen_state: launcher-settings-screen-timeout
      alt: The Screen timeout dialog in the Desktop group of System launcher settings, with Off and preset second choices
      caption: "Screen timeout, in the Desktop group."
      title: "Screenshot: Screen timeout"
      desc: System launcher settings, Desktop group, Screen timeout dialog open with presets from 5 to 300 seconds.
  - number: 2
    id: dim-warning
    title: A brief dim before it goes dark
    text: |
      A few seconds before the countdown ends, the desktop dims instead of cutting straight to black, so a glance at the screen is enough to notice it is about to go, and a tap keeps it awake.
  - number: 3
    id: dim-face
    title: What the dimmed screen shows
    text: |
      While the screen is dimmed or already blacked out, it can still show the time: turn on **Show clock and status while dimmed**, in the **Desktop** group, to draw the clock together with the same status indicators the tray shows - SIM signal, network type and roaming, Bluetooth device count, transfer speed and Wi-Fi generation - folding extra notifications into a plain count once there are too many to fit. The whole face turns to landscape by the device's own sensor, and a larger tap ring with four compass sparks marks where to tap or swipe to wake the desktop.
    image_bookmark:
      shot_id: launcher.dim-screen-clock-face
      device_profile: phone
      screen_state: launcher-dim-screen-clock-face
      alt: The launcher's dimmed screen showing a live clock, folded status indicators and a tap ring with four compass sparks
      caption: "The dim screen: clock, status and a tap ring."
      title: "Screenshot: Dim screen clock face"
      desc: Launcher dim screen, clock and status indicators visible, tap ring with four sparks, landscape orientation.
  - number: 4
    id: charging-timeout
    title: Keep it lit while charging
    text: |
      **Screen timeout when on charge**, in the same **Desktop** group, takes over for as long as a wired or wireless charger is connected, replacing the ordinary screen timeout while power stays attached. It ships **Off (never)**, so a charging device keeps its desktop lit unless you set a time for this row too.
    image_bookmark:
      shot_id: launcher.screen-timeout-charging-dialog
      device_profile: phone
      screen_state: launcher-settings-screen-timeout-charging
      alt: The Screen timeout when on charge dialog in the Desktop group, defaulting to Off
      caption: "Screen timeout when on charge - off by default."
      title: "Screenshot: Screen timeout when on charge"
      desc: System launcher settings, Desktop group, Screen timeout when on charge dialog open, Off selected.
  - number: 5
    id: profile-defaults
    title: Where each device profile starts
    text: |
      A car head unit or a photo-frame [device profile](term:device-profile) starts with **Screen timeout** already set to **Off (never)**, since those devices are meant to stay lit and visible. Every other profile starts at the default 30 seconds. Change either default any time from the same **Screen timeout** row.
  - number: 6
    id: lock-now
    title: Lock it before the countdown ends
    text: |
      A double tap on empty desktop space locks the screen straight away without waiting out the countdown - the same decision point the idle timeout uses, so it locks the device where possible and blacks the screen out where it cannot. Audio you started earlier keeps playing either way. See [Double-tap to lock the screen](page:launcher.desktop-gestures-and-shortcuts) for how to turn the gesture off.
outcome: |
  The desktop turns off exactly when you want it to - never, on a fixed clock, or on its own schedule while charging - with a warning dim first and a clock you can still glance at once it goes dark.
tips:
  - "**Nothing on the dim screen?** Turn on **Show clock and status while dimmed** in the **Desktop** group - it is off until you switch it on."
  - "**Device without a real lock?** The subtitle under **Screen timeout** says so, and the desktop uses its own black overlay instead - any tap or key brings it back."
  - "**Want a gesture that locks it instantly?** See [Customizing navigation gestures and shortcuts](page:launcher.desktop-gestures-and-shortcuts)."
next_recipes:
  - title: Your launcher desktop
    url: page:launcher.desktop-grid-and-icons
    badge: Launcher
    badge_type: docs
    description: Turn the desktop on and put apps, folders, channels and gadgets on it.
  - title: Customizing navigation gestures and shortcuts
    url: page:launcher.desktop-gestures-and-shortcuts
    badge: Launcher
    badge_type: docs
    description: Edge swipes, the clock's own gestures, and the double tap that locks the screen.
  - title: Launcher settings and starting fresh
    url: page:launcher.launcher-settings-and-reset
    badge: Launcher
    badge_type: docs
    description: Find your way around the launcher settings, and reset the desktop back to its first state.
---

The [launcher](term:launcher) [desktop](term:desktop) can turn its own screen off after a while of inactivity, warn you first with a dim, and show a readable clock even once it has gone dark. This page covers setting the timeout, reading the dim screen, keeping it lit while charging, and what each device profile starts with.
