---
page_id: launcher.taskbar-and-dock
title: Using the Desktop Dock and Taskbar
nav_title: Taskbar and dock
description: How to place the launcher taskbar, pin and manage the apps on it, jump to the Start menu or the main screen, open the quick-launch dock, and flip the flashlight or a radio on and off with one tap.
category: "Launcher: Taskbar, Menus and Gestures"
category_slug: launcher
ticket: S2960
flavor: Standard and noLegal
recipe_number: "04"
canonical_url: documentation/launcher/taskbar-and-dock.html
why: |
  The [desktop](term:desktop) holds what you look at; the [taskbar](term:taskbar) holds what you reach for. It is the one bar that stays on screen no matter which section, screen or gadget you are looking at, so the apps you use most, the way back to your files, and a couple of one-tap switches are never more than a glance away.

  This page covers the taskbar itself - where it sits, what you can pin to it, and what a long press on one of its buttons offers - plus the quick-launch dock that opens from it.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) turned on - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
  - "A few [apps](term:app) or [programs](term:program) worth keeping one tap away."
steps:
  - number: 1
    id: taskbar-edge
    title: Choose which edge the taskbar sits on
    text: |
      Open the launcher settings - **Launcher settings** in the long-press menu - and in the **Taskbar** group tap **Taskbar position**. Pick **Top** or **Bottom**. The Start button, the pinned and recent apps, and the tray all move together as one bar, and the desktop takes whatever space is left.

      Bottom is the default on every [device profile](term:device-profile) except the [car head unit](term:car-head-unit), which starts at the top.
    image_bookmark:
      shot_id: launcher.taskbar-position-setting
      device_profile: phone
      screen_state: launcher-settings-taskbar-position-dialog
      alt: The Taskbar position dialog in launcher settings with Top and Bottom choices
      caption: "Taskbar position: Top or Bottom."
      title: "Screenshot: Taskbar position"
      desc: Launcher settings, Taskbar group, Taskbar position dialog open with Top and Bottom options.
  - number: 2
    id: pin-and-manage
    title: Pin apps and manage what is already there
    text: |
      Touch and hold any app to pin it, or use **Pin to taskbar** from its menu. Touch and hold a pinned icon on the taskbar itself and a small menu offers **Launch** or **Unpin**.

      The taskbar also keeps a strip of apps you opened recently, without you pinning anything. Touch and hold one of those and the menu offers **Open**, **Pin to taskbar** - so a recent app becomes a permanent one - or **Remove from taskbar**, which only drops it from the recent strip.
    image_bookmark:
      shot_id: launcher.taskbar-recent-context-menu
      device_profile: phone
      screen_state: launcher-taskbar-recent-app-context-menu
      alt: A recent app on the launcher taskbar long-pressed, showing a menu with Open, Pin to taskbar and Remove from taskbar
      caption: "Long press a recent app for Open, Pin to taskbar or Remove from taskbar."
      title: "Screenshot: Recent app menu"
      desc: Launcher taskbar, a recent app icon long-pressed, context menu open with three actions.
  - number: 3
    id: get-around
    title: Get to your files and back again
    text: |
      The **Start** button on the taskbar opens the Start menu - see [The Start menu and the full app list](page:launcher.start-menu-and-all-apps) for what is on it.

      Tapping the FastMediaSorter app icon itself depends on one setting: **Settings**, **General**, **Primary startup window**, with **Device home screen**, **Desktop as primary window** or **Main screen** to choose from - the same choice the [welcome wizard](term:welcome-wizard) offers on first install, and it can be changed later at any time. Whenever the app is not set as your device's actual home screen, tapping its icon opens the [main screen](term:main-screen) with your files and folders directly, instead of a second copy of the desktop.

      And from anywhere the taskbar or desktop sent you, the system Back button brings you straight back to the desktop rather than leaving you stranded in whatever screen you reached.
  - number: 4
    id: quick-launch-dock
    title: Open the quick-launch dock
    text: |
      Wherever a gesture or a button opens the [quick-access panel](term:quick-access-panel), it appears as the **Quick launch** dialog: a grid of your chosen apps and shortcuts, with an explicit close button (an X) beside the title, so a tap closes it as easily as Back or tapping outside.

      Each built-in [program](term:program) on the grid - Calculator, Streams, Favorites and the rest - keeps its own accent colour on its icon in the grid, in its edit picker and in the programs menu, so you can spot the one you want by colour as well as by shape, in either theme. Monochrome icons such as Calculator or Settings stay legible on the light theme too, tinted to read against a bright background, while coloured app icons keep their own colours.

      The function picker for **Edit panel** also offers the same camera and video actions as the screen-edge gestures: **Take a photo and send to..**, **Take a photo and edit**, **Take a photo and OCR-translate**, and **Start video recording** - see [Customizing navigation gestures and shortcuts](page:launcher.desktop-gestures-and-shortcuts) for the gestures themselves.

      The dock itself also exists in the Lite, Photos and Legacy editions, even though the rest of this page is Standard and noLegal only.
    image_bookmark:
      shot_id: launcher.quick-launch-dock-open
      device_profile: phone
      screen_state: launcher-quick-launch-panel-open
      alt: The Quick launch dialog open over the desktop, showing a close button beside the title and a grid of coloured program icons
      caption: "The Quick launch dock, with its own close button."
      title: "Screenshot: Quick launch dock"
      desc: Quick launch dialog open, close X visible before the title, grid of app and program tiles with accent-coloured icons.
  - number: 5
    id: one-tap-switches
    title: Flip a switch without opening a screen
    text: |
      A **Wi-Fi** or **Bluetooth** tile on the desktop or in the quick-launch dock switches that radio directly - the system screen only opens if Android itself refuses the switch. The tile's icon always shows the current state, even when it was changed outside the app.

      **Camera flashlight** works the same way from the programs menu, the quick-launch dock or a desktop shortcut: it turns the physical flash on or off without opening a camera screen. A device with no flash does not offer it, and if the torch is briefly unavailable the app says so and asks you to try again.
    image_bookmark:
      shot_id: launcher.taskbar-radio-and-flashlight-tiles
      device_profile: phone
      screen_state: launcher-desktop-wifi-bluetooth-flashlight-tiles
      alt: Wi-Fi, Bluetooth and Camera flashlight tiles on the launcher desktop, each showing its current state
      caption: "Wi-Fi, Bluetooth and Camera flashlight - one tap, no extra screen."
      title: "Screenshot: One-tap tiles"
      desc: Launcher desktop with Wi-Fi tile on, Bluetooth tile off and Camera flashlight tile, all shown together.
outcome: |
  The taskbar sits on the edge you picked, with the apps you use most pinned to it and your recent ones a long press away. The Start button, the app icon and Back all take you exactly where you expect, the quick-launch dock opens with one tap and closes with another, and a couple of switches never need their own screen.
tips:
  - "**Can't find Unpin?** It only shows on an icon that is already pinned - a recent app's menu offers Pin to taskbar instead."
  - "**Program colours look the same in both themes?** They are deliberately different per theme so they stay readable - only the neutral placeholders and third-party app icons stay plain."
  - "**Looking for the long-press menu on a desktop shortcut instead of the taskbar?** That is covered in [Desktop context menus and fast actions](page:launcher.context-menus-and-actions)."
next_recipes:
  - title: The Start menu and the full app list
    url: page:launcher.start-menu-and-all-apps
    badge: Launcher
    badge_type: docs
    description: Open every installed app, search it, sort it and swipe through it.
  - title: The status area and notifications
    url: page:launcher.status-area-and-notifications
    badge: Launcher
    badge_type: docs
    description: What the taskbar's clock, signal and battery icons show, and how other apps' notifications appear.
  - title: Customizing navigation gestures and shortcuts
    url: page:launcher.desktop-gestures-and-shortcuts
    badge: Launcher
    badge_type: docs
    description: The screen-edge gestures behind the camera actions in the quick-launch dock.
---

The [taskbar](term:taskbar) is the one part of the [launcher](term:launcher) that never scrolls away. This page covers where it sits, pinning and managing the apps on it, the fastest ways back to your files, the quick-launch dock that opens from it, and a couple of switches that need no screen of their own.
