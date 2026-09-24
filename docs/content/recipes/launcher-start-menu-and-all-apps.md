---
page_id: launcher.start-menu-and-all-apps
title: The Start Menu and the Full App List
nav_title: Start menu and All apps
description: How to size the Start panel, open the full list of every installed app, search and sort it, get an app's own shortcuts with a long press, and swipe through the list instead of tapping.
category: "Launcher: Taskbar, Menus and Gestures"
category_slug: launcher
ticket: S2960
flavor: Standard and noLegal
recipe_number: "05"
canonical_url: documentation/launcher/start-menu-and-all-apps.html
why: |
  The taskbar's **Start** button is the short list - pinned apps, recent ones, a few quick rows. When that is not enough, **All apps** is the long one: everything installed on the device, searchable, sortable, and reachable by letter.

  Both grow with you: the Start panel can be made taller as your pinned row fills up, and All apps stays fast to open and fast to search even with a hundred apps on the device.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) turned on - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
  - "The taskbar visible - see [Using the desktop dock and taskbar](page:launcher.taskbar-and-dock)."
steps:
  - number: 1
    id: start-panel-height
    title: Make the Start panel as tall as you need
    text: |
      Open the launcher settings and, in the **Taskbar** group, set **Taskbar rows**. A taller Start panel shows proportionally more recent apps, keeps the pinned icons in their own corner block, and stacks the tray indicators one above another instead of running them off the edge; the **Start** and **All apps** buttons themselves always stay one row tall, on the bottom row.

      When the taskbar sits at the bottom, the Start panel opens as a sheet with a drag handle that respects the navigation bar and moves out of the way of the keyboard; with the taskbar at the top it opens as before. Its **Start** button and every row on it also keep enough contrast against the background to stay readable in both the light and the dark theme, under any colour theme you pick.
    image_bookmark:
      shot_id: launcher.start-panel-rows-setting
      device_profile: phone
      screen_state: launcher-settings-taskbar-rows
      alt: The Taskbar rows setting in launcher settings, with the Start panel shown two rows tall behind it
      caption: "Taskbar rows: a taller Start panel."
      title: "Screenshot: Taskbar rows"
      desc: Launcher settings, Taskbar group, Taskbar rows control, Start panel preview two rows tall.
  - number: 2
    id: open-all-apps
    title: Open every app on the device
    text: |
      Tap the **All apps** icon button next to Start, or swipe up on empty desktop space while the desktop sits at the top of its scroll. The list opens from a cache the launcher keeps current on its own - through every install, update and removal - so it is ready even right after a restart.
    image_bookmark:
      shot_id: launcher.all-apps-entry-point
      device_profile: phone
      screen_state: launcher-taskbar-all-apps-button
      alt: The All apps icon button on the launcher taskbar next to Start
      caption: "All apps sits right next to Start."
      title: "Screenshot: All apps button"
      desc: Launcher taskbar, Start button and the All apps icon button beside it, both highlighted.
  - number: 3
    id: search-sort-browse
    title: Find one app fast
    text: |
      Type anywhere in the list to search - the **Search apps** field narrows the results as you type, matching the app's name or its package name. Tap **Sort order** to change how the list is arranged: **By name**, **By install date**, **By update date**, **By how often you open it** or **By category**, each with a **Reverse order** switch; by default the list opens sorted by how often you actually open things from it, with the extra space under the letter tiles filled by a bigger preview instead of a fixed two rows.

      Scroll without a search term and the apps are grouped alphabetically, each letter its own small heading with a short preview of what is under it - tap a letter and its group expands.
    image_bookmark:
      shot_id: launcher.all-apps-search-and-sort
      device_profile: phone
      screen_state: launcher-all-apps-search-active
      alt: The All apps screen with the Search apps field active and results narrowed, sort order button visible
      caption: "Search narrows the list as you type."
      title: "Screenshot: Searching All apps"
      desc: All apps screen, search field focused with a partial query, filtered results below, Sort order button visible.
  - number: 4
    id: long-press-shortcuts
    title: Long-press an app for its own shortcuts
    text: |
      A long press on any app opens one menu with **Open**, **Put on desktop**, **Pin to taskbar**, **App info** and **Uninstall**, together with whatever quick actions that app itself publishes - a messenger's "New chat", say, or a browser's "New tab". An action that cannot work on this device is simply left out rather than shown greyed out. An app with no shortcuts of its own still opens normally on a plain tap.
    image_bookmark:
      shot_id: launcher.all-apps-long-press-menu
      device_profile: phone
      screen_state: launcher-all-apps-context-menu
      alt: An app long-pressed in the All apps list, showing Open, Put on desktop, Pin to taskbar, App info, Uninstall and the app's own shortcuts
      caption: "One menu: the launcher's actions plus the app's own shortcuts."
      title: "Screenshot: App long-press menu"
      desc: All apps screen, one app long-pressed, context menu listing launcher actions above the app's published shortcuts.
  - number: 5
    id: swipe-actions
    title: Swipe the list instead of tapping
    text: |
      In the launcher settings, open **All apps swipe action** and set **All apps: swipe up**, **swipe down**, **swipe left** and **swipe right** from the same list each direction offers: **Back to desktop**, **Expand all apps** (shows the full list instead of the letter groups), **Launch a chosen app**, **Lock screen**, or leave the direction unused. By default swiping down at the top of the list returns to the desktop and swiping up at the end expands the full list; **Lock screen** only appears in builds that can actually lock the device.
    image_bookmark:
      shot_id: launcher.all-apps-swipe-settings
      device_profile: phone
      screen_state: launcher-settings-all-apps-swipe-picker
      alt: The All apps swipe action picker in launcher settings with Back to desktop, Expand all apps, Launch a chosen app and Lock screen
      caption: "One of four swipes, each with its own action."
      title: "Screenshot: All apps swipe actions"
      desc: Launcher settings, All apps swipe action picker open, action list visible.
outcome: |
  The Start panel is exactly as tall as your pinned row needs, All apps opens instantly and finds anything by name, install date, use or category, a long press reaches an app's own shortcuts, and all four swipes do what you chose instead of nothing.
tips:
  - "**Picking an app to pin to the desktop or the taskbar?** The picker sheet used there is taller too, with more than one column in portrait, so you are not scrolling a single narrow list."
  - "**All apps looks different from the old Start menu list?** It replaced the expanding grid that used to live inside Start - the full list now has a screen of its own."
  - "**Using a keyboard or a TV remote?** See [Keyboard, D-pad and Android TV control](page:general.keyboard-dpad-tv-navigation)."
next_recipes:
  - title: Using the desktop dock and taskbar
    url: page:launcher.taskbar-and-dock
    badge: Launcher
    badge_type: docs
    description: Where the Start button lives, pinning apps, and the quick-launch dock.
  - title: Customizing navigation gestures and shortcuts
    url: page:launcher.desktop-gestures-and-shortcuts
    badge: Launcher
    badge_type: docs
    description: The screen-edge gesture that can open All apps from any app.
  - title: Your launcher desktop
    url: page:launcher.desktop-grid-and-icons
    badge: Launcher
    badge_type: docs
    description: The starter layout the Start menu and All apps sit alongside.
---

Start is the short list, [All apps](term:all-apps) is the long one. This page covers sizing the Start panel, opening the full app list, searching and sorting it, an app's own shortcuts from a long press, and the four swipes that move through the list without a single tap.
