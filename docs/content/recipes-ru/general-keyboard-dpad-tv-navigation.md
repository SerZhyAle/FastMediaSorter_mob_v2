---
page_id: general.keyboard-dpad-tv-navigation
title: Keyboard, D-Pad and Android TV Control - Using the App Without Touching the Screen
nav_title: Keyboard, D-pad and TV control
description: Running FastMediaSorter on an Android TV box with a remote, moving around with a D-pad or the arrow keys, handling every dialog from the keyboard, the default keyboard shortcuts, the mouse and its buttons, selecting many files with a sweep, and typing in text fields with a keyboard attached.
category: "General, Keyboard & TV"
category_slug: general
ticket: S2963
flavor: All editions - keyboard and dialog control everywhere; the automatic first focus on a real control is in Standard, Lite, Photos and Legacy
recipe_number: "02"
canonical_url: documentation/general/keyboard-dpad-tv-navigation-ru.html
why: |
  Not every screen is a phone in your hand. FastMediaSorter also runs on a [TV](term:tv) box in the living room, on a [car head unit](term:car-head-unit), on a tablet with a keyboard cover and on a Chromebook with a mouse. On all of them you should be able to open a folder, look through photos and answer a question in a dialog without ever touching the glass.

  This page shows how: the remote and its [D-pad](term:d-pad), the keyboard and its shortcuts, and the mouse with its buttons and wheel.
ingredients:
  - "FastMediaSorter in any [edition](term:edition)."
  - "One of these: a TV remote, a game controller, a keyboard (Bluetooth, USB or a keyboard cover) or a mouse."
  - "For the TV: an Android TV or Google TV box. The app does not need a touchscreen."
steps:
  - number: 1
    id: tv
    title: Start the app on a TV
    text: |
      On an Android TV or Google TV box, FastMediaSorter appears in the TV's own app row with its own wide banner, like any app made for the big screen - no need to dig through the phone apps list. Open it with the center button of the remote.

      A TV box needs no touchscreen, and the app does not ask for one. To make the app feel at home on the big screen from the start, pick the **TV / media box** [device profile](term:device-profile) in the [welcome wizard](term:welcome-wizard) or later in **[Settings](term:settings)** - it chooses sensible defaults for a remote.
    image_bookmark:
      shot_id: general.tv-app-row-banner
      device_profile: tv
      screen_state: tv-home-app-row-with-banner
      alt: The Android TV home screen with the FastMediaSorter banner in the row of apps
      caption: "FastMediaSorter in the TV's app row."
      title: "Screenshot: App banner on Android TV"
      desc: Android TV home screen, the apps row with the FastMediaSorter banner focused.
  - number: 2
    id: dpad
    title: Move around with the D-pad or the arrow keys
    text: |
      Press the arrows to move the selection, and the center button or **Enter** to open what is selected. **Back** on the remote, or **Escape** on a keyboard, goes back.

      As soon as a screen opens, the selection lands on the first real button or item - never on an invisible list container, where a press of an arrow would seem to do nothing. A frame shows where the selection is, but only when you use a remote or keys; when you touch the screen, the frame stays out of the way.

      Each arrow press moves exactly one step, also in dialogs such as **Choose your device profile**, where a press once used to jump two rows at a time on a TV. Turn the screen or the tablet sideways and the order in which the selection moves stays the same as upright: dialogs and grids keep their order and their compact size in landscape too.
    image_bookmark:
      shot_id: general.dpad-focus-frame-grid
      device_profile: tv
      screen_state: browse-grid-dpad-focus
      alt: A grid of photos in the file browser on a TV with a clear focus frame around the selected thumbnail
      caption: "The frame shows where the D-pad selection is."
      title: "Screenshot: D-pad selection in a grid"
      desc: File browser in grid view on a TV-sized screen, one thumbnail selected with the D-pad and outlined by the focus frame.
  - number: 3
    id: dialogs
    title: Answer every dialog from the keyboard
    text: |
      Every dialog and every panel that slides up from the bottom of the screen works the same way with keys:

      - **Escape** closes it, like **Cancel**.
      - **Enter** presses the main button, such as **OK**, **Save** or **Delete**.
      - The arrows and **Tab** move from one control to the next.
      - When the dialog opens, the selection is already on a sensible control, so one press of **Enter** is often all you need.

      The buttons at the bottom of a dialog get a clear frame when they are selected with a remote, so on a TV you always see whether **OK** or **Cancel** is about to be pressed. Buttons are as wide as their words; in portrait, a long pair of buttons stands one above the other instead of squeezing its words. A dialog also closes by itself when the screen behind it goes away, so it never stays hanging over a screen that is no longer there.
    image_bookmark:
      shot_id: general.dialog-button-focus-frame
      device_profile: tv
      screen_state: confirm-dialog-dpad-focus-on-confirm
      alt: A confirmation dialog on a TV with the confirm button outlined by a focus frame and the cancel button beside it
      caption: "The selected dialog button is outlined."
      title: "Screenshot: Dialog button selected with the D-pad"
      desc: A confirm dialog, for example a delete confirmation, with the confirm button focused by the D-pad and showing its focus frame.
  - number: 4
    id: shortcuts
    title: Use the keyboard shortcuts
    text: |
      With a keyboard attached, many commands have their own key. These are the defaults:

      - **Space** - play or pause.
      - **.** and **,** - one frame forward or back.
      - **]** and **[** - jump 5 seconds forward or back; **Shift+Right** and **Shift+Left** jump 30 seconds.
      - **Page Down** and **Page Up** - the next or the previous file.
      - **Home** and **End** - the start or the end.
      - **F** - full screen; **R** - rotate; **M** - sound on or off.
      - **Ctrl+F** - search; **Ctrl+Z** and **Ctrl+Y** - undo and redo.
      - **Ctrl+C**, **Ctrl+X**, **Ctrl+V** - copy, move and paste files; **Ctrl+R** - rename; **Ctrl+Shift+N** - a new folder; **Ctrl+S** - save; **Backspace** or **Delete** - delete.
      - **Ctrl+B** - add to or remove from [Favorites](term:favorites).
      - **1** to **8** - the eight quick file operation buttons, in order.
      - **Escape** - leave the current screen.

      The media keys of a keyboard or a remote - play, pause, next, previous, fast forward and rewind - work too. Every one of these keys can be changed on the **Controls & Keybindings** screen - type *keybindings* into the Settings search to jump to its row; how to do that is in [Controls and key remapping](page:settings.controls-and-key-remapping).
    callout:
      type: tip
      title: A key for a quick sort
      text: "The number keys **1** to **8** run the eight quick file operation buttons, so sorting a pile of photos into folders with [quick sort](term:quick-sort) becomes: look, press a digit, next."
  - number: 5
    id: mouse
    title: Use a mouse
    text: |
      With a mouse, FastMediaSorter behaves like software on a computer:

      - **Click** selects, a **double click** opens.
      - **Right click** opens the menu of the item under the pointer.
      - **Middle click** (pressing the wheel) adds the item to Favorites or removes it.
      - **The wheel** scrolls.
      - **The back and forward side buttons** go to the previous or the next file.
      - A list item under the pointer lights up, so you see what a click will hit.

      To select many files at once in the [file browser](term:file-browser) or in the duplicate finder, press the mouse button on an item - or on the empty space next to it - and sweep over the others: everything you pass is selected, and the list scrolls by itself when you reach its top or bottom edge. On a touchscreen the same works with a finger once selection is already on: long-press one file to start, then slide sideways over the others. A slide that goes mostly up or down still scrolls the list as usual. More about selecting: [Multi-selection and batch operations](page:browsing.batch-selection).
    image_bookmark:
      shot_id: general.mouse-band-select
      device_profile: tablet
      screen_state: browse-list-mouse-sweep-select
      alt: The file browser list with several consecutive files selected by sweeping the mouse pointer over them
      caption: "Sweep the mouse to select a run of files."
      title: "Screenshot: Selecting files with a mouse sweep"
      desc: File browser in list view on a tablet with a mouse, several rows selected by pressing and sweeping the pointer, the pointer over the last row.
  - number: 6
    id: typing
    title: Type with a keyboard or on the screen
    text: |
      A text field - the filter box, the Settings search, a name or an address - stays in view above the on-screen keyboard also on a wide screen or in landscape, where Android would otherwise cover the whole screen with the keyboard and hide the field you are typing in.

      The small clear icon at the end of a field empties it at once, and you can tap straight back into the field to type again.
    image_bookmark:
      shot_id: general.text-field-above-keyboard-landscape
      device_profile: phone
      screen_state: filter-dialog-landscape-keyboard-open
      alt: The filter dialog in landscape with its text field visible above the on-screen keyboard
      caption: "The field stays visible above the keyboard in landscape."
      title: "Screenshot: Typing in landscape"
      desc: Phone in landscape, the filter dialog open with the on-screen keyboard shown and the filter text field visible above it.
outcome: |
  You can run FastMediaSorter from the sofa with a TV remote, from a desk with a keyboard and mouse, or from a car's D-pad: every screen starts with the selection on something useful, every dialog answers to Escape and Enter, and the most common commands have their own key.
tips:
  - "**Lost the selection frame?** Press any arrow key once - the frame appears on the current control."
  - "**Prefer other keys?** Change any of them in [Controls and key remapping](page:settings.controls-and-key-remapping)."
  - "**Watching videos with a remote?** The player's own keys and panels are in [Watching videos](page:player.video-playback-controls)."
  - "**Official help for your TV remote:** Google's [Android TV help](https://support.google.com/androidtv/) explains the buttons of Google remotes."
next_recipes:
  - title: Controls and key remapping
    url: page:settings.controls-and-key-remapping
    badge: Settings
    badge_type: docs
    description: Put any command on any key, button or mouse click.
  - title: Split-screen, freeform windows and foldables
    url: page:general.multi-window-and-foldables
    badge: General
    badge_type: docs
    description: The app next to another one, on a tablet or on an unfolded phone.
  - title: Multi-selection and batch operations
    url: page:browsing.batch-selection
    badge: Browsing
    badge_type: other
    description: Select many files and act on all of them at once.
---

A remote, a keyboard, a game controller or a mouse - FastMediaSorter answers to all of them. This page shows how to move around, answer dialogs, use the shortcuts and select files without touching the screen.
