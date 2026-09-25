---
page_id: launcher.android-widgets-placement
title: Placing, Resizing and Styling Gadgets and Widgets on the Launcher Desktop
nav_title: Placing gadgets and widgets
description: How to put a gadget or one of the app's widgets on the launcher desktop, make room for it, resize it, set how solid its card is, and what happens to gadgets when one fails or when you reset the launcher.
category: "Launcher: Gadgets and Widgets"
category_slug: launcher
ticket: S2959
flavor: Standard and noLegal
recipe_number: "02"
canonical_url: documentation/launcher/android-widgets-placement-ru.html
why: |
  A [gadget](term:gadget) is only useful where your eye falls: the clock big and on top, the weather beside it, the battery small in a corner. This page is about the placing - where a gadget goes, how big it is, and how much of the wallpaper shows behind it.

  The same steps work for the app's own [widgets](term:widget), which can live on the launcher [desktop](term:desktop) as well as on the Android [home screen](term:home-screen).
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) desktop switched on - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
  - "For the app's widgets on the desktop: FastMediaSorter set as the device home screen."
steps:
  - number: 1
    id: add-gadget
    title: Put a gadget on the desktop
    text: |
      Touch and hold an empty square, tap **Add an item..**, choose **Gadget** and pick one. It lands where you pressed, at its starting size. What each gadget does is described in [Desktop gadgets](page:launcher.built-in-gadgets).

      No empty square in sight? Tap **Edit the desktop** in the long-press menu, then the **+** button next to **Done** on the [taskbar](term:taskbar): the new item goes into the first free place, or into a new row below everything.
  - number: 2
    id: make-room
    title: Let the desktop make room
    text: |
      A gadget often needs more than one square. When you add it to a spot that is too small, the squares below slide down to make room - nothing is covered and nothing is lost.

      If there is truly no way to fit it, you are told so instead of nothing happening: **No room on the launcher desktop. Free a spot and try again.** Remove or move something, or add another screen - see [Arranging the desktop](page:launcher.desktop-folders-and-pages).
  - number: 3
    id: add-widget
    title: Bring one of the app's widgets to the desktop
    text: |
      The app's [widgets](term:widget) - calculator, camera, photo frame, music, recorder, favorites, scheduled operations and others - can sit on the launcher desktop too. Open **Settings**, the **Management** tab, and tap **Add a gadget to the launcher desktop**; the chosen one goes into the first free place and a short message says **Added to the launcher desktop**. The button is shown only while the launcher is on.

      Some widgets need to be set up first. **Random Photo Frame** asks which [resource](term:resource) to take photos from, and **Quick capture** asks which folder to save to. The square is placed only after you confirm; if you cancel, nothing is placed and nothing is left behind.

      A widget on the desktop and the same widget on another home screen keep their own settings - two photo frames can show two different albums. Removing a square also forgets its settings. All widgets are described in [FastMediaSorter widgets for your home screen](page:launcher.home-screen-widgets).
    image_bookmark:
      shot_id: launcher.widgets-add-from-settings
      device_profile: phone
      screen_state: settings-management-add-launcher-gadget
      alt: The Management tab of Settings with the Add widget to the Android home screen and Add a gadget to the launcher desktop buttons
      caption: "Add a gadget to the launcher desktop, in Settings."
      title: "Screenshot: Adding a widget to the desktop"
      desc: Settings, Management tab, scrolled to the two widget buttons, launcher on, portrait.
  - number: 4
    id: resize
    title: Make a gadget bigger or smaller
    text: |
      Tap **Edit the desktop** in the long-press menu. Every square now shows a card, and each gadget has a handle in its corner. Drag the handle to make the gadget bigger or smaller - from its starting size up to the whole screen. Tap **Done** when it looks right.

      The size is remembered, also after a restart. A few gadgets keep a sensible minimum: **Clock** goes down to two squares by one, and **Translator** always stays at least two rows high so its text and result fit.
    image_bookmark:
      shot_id: launcher.widgets-resize-handle
      device_profile: phone
      screen_state: launcher-edit-mode-resize-gadget
      alt: The launcher desktop in edit mode with a Weather gadget being enlarged by its corner handle, the squares below moved down
      caption: "Dragging the corner handle to resize."
      title: "Screenshot: Resizing a gadget"
      desc: Desktop in edit mode, a Weather gadget mid-resize, portrait.
  - number: 5
    id: backdrop
    title: Choose how solid the gadget card is
    text: |
      Behind each gadget there is a light card. To make it lighter or heavier, open the launcher settings - from the long-press menu, from the Start menu, or through **Settings**, **General**, **System launcher settings** - and in the **Appearance** group tap **Widget backdrop opacity**. Pick from **0% (Transparent)**, which lets the wallpaper show through, to **100% (Opaque)**; **25% (Default)** is the starting value.

      The choice stays after a restart. While you arrange the desktop every square shows the full card anyway, so its edges are easy to see.
    image_bookmark:
      shot_id: launcher.widgets-backdrop-opacity
      device_profile: phone
      screen_state: launcher-settings-backdrop-opacity-dialog
      alt: The Widget backdrop opacity choice with 0% (Transparent), 25% (Default), 50%, 70%, 85% and 100% (Opaque)
      caption: "Widget backdrop opacity."
      title: "Screenshot: Widget backdrop opacity"
      desc: Launcher settings, Appearance group, the opacity list open, portrait.
  - number: 6
    id: reset
    title: Start again with a fresh set of gadgets
    text: |
      In the **System** group of the launcher settings, **Reset launcher settings** returns the desktop to its first state after a confirmation. It asks how densely to lay the icons out (**Grid density**: **Sparse**, **Standard**, **Dense** or **Very dense**) and opens on the choice that suits your [device profile](term:device-profile).

      The starter **Gadgets** section is filled for your kind of device too: a photo frame gets an **Image window**, an e-book reader a **Document window**, a [VR headset](term:vr-headset) a **Battery** square. The settings of widgets you had placed are cleared with the reset, so nothing is left behind.
outcome: |
  Every gadget and widget sits where you want it, at the size you chose, with as much or as little card behind it as you like - and it stays that way after a restart.
tips:
  - "**A gadget could not start?** The rest of the desktop stays usable, and a message names the square: for example **Map could not start. The rest of your desktop is available.** Remove that square and add it again, or leave it for now."
  - "**Want the layout to stay put?** Lock the desktop so a stray touch cannot move a gadget - see [Arranging the desktop](page:launcher.desktop-folders-and-pages)."
  - "**A gadget swallows your swipes?** Gadgets keep their own gestures: a map still pans and a list still scrolls. Start the swipe on an empty spot instead."
next_recipes:
  - title: Desktop gadgets
    url: page:launcher.built-in-gadgets
    badge: Launcher
    badge_type: docs
    description: What each gadget shows - clocks, weather, map, sensors, device status and the translator.
  - title: FastMediaSorter widgets for your home screen
    url: page:launcher.home-screen-widgets
    badge: Launcher
    badge_type: docs
    description: Calculator, camera, photo frame, music, recorder and more on any home screen.
  - title: Arranging the desktop - sections, screens, swipes and the lock
    url: page:launcher.desktop-folders-and-pages
    badge: Launcher
    badge_type: docs
    description: Move, resize and fold, spread items over several screens and lock the layout.
---

A [gadget](term:gadget) needs a good place and the right size. This page shows how to put gadgets and the app's widgets on the launcher [desktop](term:desktop), let the desktop make room, resize them, choose how solid their card is, and start again with a fresh set.
