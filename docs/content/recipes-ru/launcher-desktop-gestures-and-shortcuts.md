---
page_id: launcher.desktop-gestures-and-shortcuts
title: Customizing Navigation Gestures and Shortcuts
nav_title: Navigation gestures and shortcuts
description: How to set up edge-swipe gestures that work over any app, point them at an app or a web address, and use the desktop's own swipes and taps to reach All apps, page between screens, restyle the clock and lock up.
category: "Launcher: Taskbar, Menus and Gestures"
category_slug: launcher
ticket: S2960
flavor: Standard and noLegal
recipe_number: "07"
canonical_url: documentation/launcher/desktop-gestures-and-shortcuts-ru.html
why: |
  A swipe from the edge of the screen can take a screenshot, open the camera or launch your favorite app - from inside any app, not only from the desktop. Add the desktop's own swipes and taps on top of that, and most things you reach for often are one gesture away, with nothing extra to unlock or tap through first.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with **Gesture overlay** turned on in **Settings**, the **Destinations** tab. Turning it on asks for the **Display over other apps** permission, since [edge gestures](term:edge-gesture) work over any app."
  - "The [launcher](term:launcher) [desktop](term:desktop) open for its own swipes and taps - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
steps:
  - number: 1
    id: configure-gestures
    title: Open the gesture map
    text: |
      Go to **Settings**, the **Destinations** tab, and tap **Configure gestures**. A full-screen map shows the four gesture zones - gray where a zone is free, red where it already carries an action - and tapping a zone opens it straight away. Four tabs - **Left top**, **Left bottom**, **Right top** and **Right bottom** - each hold an **Up gesture action**, a **Down gesture action** and a **Right gesture action**, with the target row underneath. A fifth area, **General gesture settings**, holds what every zone shares - where screenshots are saved, whether they also copy to the clipboard, and the quick-access panel editor. Turn the device sideways and the map switches to two columns so both edges stay in view.
    image_bookmark:
      shot_id: gestures.edge-gesture-zone-map
      device_profile: phone
      screen_state: settings-edge-gesture-zone-map
      alt: The Edge gestures full-screen map with four zone tabs, gray and red zone indicators, and the General gesture settings group
      caption: "The edge-gesture map: gray is free, red is already assigned."
      title: "Screenshot: Edge gesture map"
      desc: Configure gestures dialog, zone map with tabs for the four edge bands, portrait.
  - number: 2
    id: action-catalog
    title: Pick from the full list of actions
    text: |
      Each direction opens the same wide catalog, grouped by kind - **Screen capture**, **Camera**, **Launch**, **Device control**, **System** and **Utilities** - with a short line under every choice explaining what it does, from a silent screenshot to setting an alarm, muting the volume or opening the camera in video mode. A slot can also be switched off, listed under its own **Disabled** group.
    image_bookmark:
      shot_id: gestures.action-catalog-picker
      device_profile: phone
      screen_state: settings-gesture-action-picker
      alt: The grouped gesture action picker showing Screen capture, Camera, Launch, Device control, System and Utilities groups with an explanation under each action
      caption: "Every gesture action, grouped and explained."
      title: "Screenshot: Gesture action picker"
      desc: Gesture action picker opened for one zone direction, groups expanded, explanation text visible under each row.
  - number: 3
    id: launch-target
    title: Point a gesture at an app or a web address
    text: |
      Choose **Launch a chosen app** and pick from the installed apps - each of the twelve gesture slots keeps its own choice. The row under the action names the app and clears it in one tap; with nothing chosen, or once the chosen app is gone, the gesture opens FastMediaSorter instead. Choosing **Open a web address** works the same way: a **Web address** row appears under the action, always ready to re-read, correct or clear, so a mistyped address is one tap away from fixed rather than a re-entry from scratch.
    image_bookmark:
      shot_id: gestures.gesture-target-row
      device_profile: phone
      screen_state: settings-gesture-target-row
      alt: A gesture zone's direction row set to Launch a chosen app, with the target app named underneath and a clear control beside it
      caption: "The target row - re-read, change or clear it any time."
      title: "Screenshot: Gesture target row"
      desc: One gesture direction set to Launch a chosen app, target row showing the chosen app and a clear affordance.
  - number: 4
    id: where-bands-sit
    title: Where the gesture bands sit
    text: |
      The two bands per edge sit wherever the phone's own system bars leave the screen free - on the left and right in the usual layout, or across the top and bottom on a device whose bars run down both sides. Turning the phone does not move them by itself: the bands, the swipe directions and the map on screen all turn together, so **Up** still means up from where you are holding the phone.
  - number: 5
    id: cancel-target
    title: Change your mind mid-swipe (sideload only)
    text: |
      In the noLegal edition, an edge gesture opens as a hint menu instead of firing the moment you touch the edge: the action runs only when you lift your finger, and dragging back to the spot you started from - the cancel target - runs nothing at all. This is only in the [sideload (noLegal) version](page:flavors.overview-and-comparison) of the app.
  - number: 6
    id: all-apps-and-paging
    title: Reach All apps or turn the page with a gesture
    text: |
      Assign **All apps** to a gesture slot the same way as any other action, and it brings the running desktop forward with the list already open, instead of starting a second copy. The desktop's own four swipes - set up in the **Gestures** group of the launcher settings - can do the same, plus **Next screen** and **Previous screen** for paging between desktop screens, and in edit mode you can tap a shortcut and send it straight to another screen. Both are covered in full in [Arranging the desktop](page:launcher.desktop-folders-and-pages).
  - number: 7
    id: clock-gestures
    title: Swipe the clock to change how it looks
    text: |
      The **Clock** [gadget](term:gadget) answers its own swipes: hide or bring back the seconds, or swipe for a random dial color and time typeface, or swipe back to the theme color. The date keeps its own typeface while sharing whichever dial color you land on, and the choice survives a launcher restart.
  - number: 8
    id: double-tap-lock
    title: Double-tap to lock the screen
    text: |
      A double tap on empty desktop space locks the device screen, or blacks the screen out with its own overlay where a real device lock is out of reach - any tap or key brings the desktop straight back. Turn it off with **Double tap to lock the screen** in the **Desktop** group of the launcher settings.
    image_bookmark:
      shot_id: launcher.double-tap-lock-overlay
      device_profile: phone
      screen_state: launcher-double-tap-black-overlay
      alt: The launcher's own black-screen overlay shown after a double tap on empty desktop space, on a device with no reachable system lock
      caption: "Double tap locks the screen, or blacks it out."
      title: "Screenshot: Double-tap lock"
      desc: Launcher desktop black overlay after a double tap, portrait, any tap brings the desktop back.
outcome: |
  Every edge gesture does exactly what you chose - a screenshot, an app, a system action - reachable from inside any app, and the desktop's own swipes, the clock and a double tap cover the rest without a single extra icon.
tips:
  - "**Nothing happens when you swipe from the edge?** Check **Gesture overlay** is on in **Settings**, the **Destinations** tab, and that the app still has the **Display over other apps** permission - Android sometimes revokes it after an update."
  - "**Wanted the screenshot side of gestures?** See [Edge gestures and the quick-access panel](page:capture.edge-gestures-and-quick-access-panel)."
  - "**Using a keyboard or a TV remote?** See [Keyboard, D-pad and Android TV control](page:general.keyboard-dpad-tv-navigation)."
next_recipes:
  - title: Arranging the desktop - sections, screens, swipes and the lock
    url: page:launcher.desktop-folders-and-pages
    badge: Launcher
    badge_type: docs
    description: Move, resize and fold sections, spread items over several screens and lock the layout.
  - title: Desktop context menus and fast actions
    url: page:launcher.context-menus-and-actions
    badge: Launcher
    badge_type: docs
    description: What a long press offers on apps, resources, channels and contacts.
  - title: Using the desktop dock and taskbar
    url: page:launcher.taskbar-and-dock
    badge: Launcher
    badge_type: docs
    description: The Start button, recent and pinned apps and the status tray.
---

An [edge gesture](term:edge-gesture) works from inside any app, and the [launcher](term:launcher) [desktop](term:desktop) adds its own swipes and taps on top. This page covers setting up both, plus the small gestures on the Clock gadget and the double tap that locks the screen.
