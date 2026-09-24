---
page_id: general.multi-window-and-foldables
title: Split-Screen, Freeform Windows and Foldables - The App on Any Screen Size
nav_title: Split-screen and foldables
description: Using FastMediaSorter next to another app in split-screen or in a freeform window, how it lays itself out on a tablet or an unfolded phone, opening a folder or a file in a new window, watching in a small floating window, and what stays put when you turn the device.
category: "General, Keyboard & TV"
category_slug: general
ticket: S2963
flavor: All editions - split-screen, wide layouts and rotation everywhere; picture-in-picture where the video player is present
recipe_number: "03"
canonical_url: documentation/general/multi-window-and-foldables.html
why: |
  Sometimes one app at a time is not enough. You sort photos while a chat is open next to them, you check a video while writing notes, or you unfold your phone and want the extra room to be used rather than wasted.

  FastMediaSorter lets you do all of that: it shares the screen with another app, it spreads out on a wide screen, it can open a folder in a second window, and it keeps its layout in order when the device turns.
ingredients:
  - "FastMediaSorter in any [edition](term:edition)."
  - "For split-screen or a freeform window: a phone, tablet or Chromebook whose Android supports it - most phones since Android 7 do."
  - "Optional: a tablet, a foldable phone, a Chromebook or a [VR headset](term:vr-headset) for the new-window feature."
steps:
  - number: 1
    id: split-screen
    title: Put the app next to another one
    text: |
      Open the recent-apps screen, tap the FastMediaSorter icon above its card and choose split-screen (on some phones the item is called **Split screen** or **Open in split screen view**), then pick the second app. FastMediaSorter resizes itself to its half and keeps working; drag the divider and it follows. On a tablet, a Chromebook or in desktop mode you can also drag it into a freeform window of any size.

      Nothing restarts when the window changes size: the video keeps playing and the folder you were in stays open. How to start split-screen on your particular phone is explained in Google's [split-screen help](https://support.google.com/android/answer/9075928).
    image_bookmark:
      shot_id: general.split-screen-with-another-app
      device_profile: phone
      screen_state: split-screen-browse-top-other-app-bottom
      alt: A phone in split-screen with the FastMediaSorter file browser in the top half and another app in the bottom half
      caption: "FastMediaSorter sharing the screen with another app."
      title: "Screenshot: Split-screen"
      desc: Phone in split-screen mode, FastMediaSorter's file browser in the upper half, another app in the lower half, the divider in the middle.
  - number: 2
    id: wide-layout
    title: Let a wide screen give you more room
    text: |
      When the window is wide - a tablet, an unfolded foldable, a large phone, or any phone turned sideways - FastMediaSorter switches to its roomy layout: the [main screen](term:main-screen), the [file browser](term:file-browser) and **[Settings](term:settings)** spread out, and the collapsed groups of Settings stand in two columns. The switch depends on the width the app actually gets, not only on how the device is held, so a big phone held upright gets the roomy layout too, and a narrow split-screen half gets the compact one.

      On the main screen, the buttons of the top bar, the [programs panel](term:programs-panel), the [streams panel](term:streams-panel) and the [resource type tabs](term:resource-type-tabs) line up along one left edge, in one column of finger-sized buttons.
    image_bookmark:
      shot_id: general.wide-layout-tablet
      device_profile: tablet
      screen_state: main-screen-wide-layout-landscape
      alt: The main screen on a tablet in landscape using the wide layout, with panels aligned along one edge
      caption: "The roomy layout on a wide screen."
      title: "Screenshot: Wide layout on a tablet"
      desc: Main screen on a tablet in landscape, wide layout, the top panels aligned to the same left edge.
  - number: 3
    id: new-window
    title: Open a folder or a file in a new window
    text: |
      On a tablet, a Chromebook, a large display or a VR headset you can keep two places open at once:

      1. Open **Settings**, the **General** tab, **General interface settings**.
      2. Switch on **Allow new windows**. On a Chromebook, an Android XR device or a VR headset it is already on.
      3. In the file browser or the [player](term:player), choose **Open in new window**. The folder or the file opens in a separate Android window that you can place next to the first one.

      Switch the setting off and the action disappears; everything opens in the current window again.
    image_bookmark:
      shot_id: general.open-in-new-window-action
      device_profile: tablet
      screen_state: browse-menu-open-in-new-window
      alt: The file browser menu on a tablet with the Open in new window action
      caption: "Open in new window, available after switching on Allow new windows."
      title: "Screenshot: Open in new window"
      desc: File browser on a tablet with Allow new windows switched on, the menu open with the Open in new window action visible.
  - number: 4
    id: pip
    title: Keep watching in a small floating window
    text: |
      A video can shrink into a small window that floats over other apps while you do something else. Switch it on with **Enable Picture-in-Picture** and leave the player with the home gesture - the video keeps playing in the corner. The details are in [Picture-in-picture and background play](page:player.pip-and-background-play).
  - number: 5
    id: rotation
    title: Turn the device without losing your place
    text: |
      Turning the device from upright to sideways and back keeps things where they belong:

      - Dialogs and item grids keep their order for the D-pad and their compact size in landscape.
      - **Compact elements** and **Sync interval (min)** in the General settings are there in both orientations - each of them used to be missing in one.
      - In the **Management** tab, the two toggles that ask before deleting and before moving, and the two that make FastMediaSorter the phone's media handler and let it accept shared files, each stand as two full-width rows in portrait instead of two squeezed columns.
      - The title of the player settings dialog no longer breaks into two lines in portrait, and its buttons sit in the usual place at the bottom.
      - The edge bands of the [screenshot](term:screenshot) [edge gestures](term:edge-gesture) stay flush with the edges of the screen after a turn.
      - Saving an account after signing in through the browser is safe even if the screen turns while the account is being stored.

      The one screen that stays upright is the quick [camera](term:camera): it always shoots in portrait.
    image_bookmark:
      shot_id: general.rotation-settings-rows-portrait
      device_profile: phone
      screen_state: settings-management-destinations-portrait
      alt: The Management tab of Settings in portrait with the confirm-delete and confirm-move toggles as two full-width rows
      caption: "Toggles stand in full-width rows in portrait."
      title: "Screenshot: Settings rows in portrait"
      desc: Settings, Management tab, Destinations group in portrait, the confirm-delete and confirm-move toggles each on its own full-width row.
outcome: |
  FastMediaSorter shares the screen when you need two apps, spreads out when the screen is wide, opens a second window on a tablet or a Chromebook, floats a video over other apps, and keeps its layout in order whichever way you hold the device.
tips:
  - "**Use a mouse and a keyboard on a big screen** - see [Keyboard, D-pad and TV control](page:general.keyboard-dpad-tv-navigation)."
  - "**Want everything smaller to fit more on screen?** Switch on **Compact elements** in **General interface settings**."
next_recipes:
  - title: Keyboard, D-pad and TV control
    url: page:general.keyboard-dpad-tv-navigation
    badge: General
    badge_type: other
    description: Using the app with a remote, a keyboard or a mouse.
  - title: Picture-in-picture and background play
    url: page:player.pip-and-background-play
    badge: Player
    badge_type: video
    description: Keep a video playing in a corner while you do something else.
  - title: Notifications, the startup splash and assistant actions
    url: page:general.notifications-and-assistant
    badge: General
    badge_type: docs
    description: How the app shows itself around the phone.
---

FastMediaSorter fits the screen it gets: half of a phone in split-screen, a whole tablet, an unfolded foldable or a floating window. This page shows how to use each of them.
