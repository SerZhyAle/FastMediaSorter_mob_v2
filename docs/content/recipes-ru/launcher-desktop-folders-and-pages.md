---
page_id: launcher.desktop-folders-and-pages
title: Arranging the Desktop - Sections, Screens, Swipes and the Lock
nav_title: Arranging the desktop
description: How to move, resize and remove squares on the launcher desktop, group them into sections you can fold, spread them over several screens, choose what each swipe does, and lock the layout.
category: "Launcher: Desktop"
category_slug: launcher
ticket: S2958
flavor: Standard and noLegal
recipe_number: "03"
canonical_url: documentation/launcher/desktop-folders-and-pages-ru.html
why: |
  A desktop is only comfortable when things are where your hand expects them. Maybe you want the radio in the top corner, the family photos on a second screen, and the rarely used apps folded away under a heading. And once it is right, you want nobody - including a curious child - to move anything by accident.

  Everything on this page changes only the layout. No app, folder or file is ever deleted by rearranging the desktop.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) desktop open - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
  - "A few squares on the [desktop](term:desktop) to move around."
steps:
  - number: 1
    id: edit-mode
    title: Start arranging
    text: |
      Touch and hold an empty square and tap **Edit the desktop**. The same is in the Start menu on the [taskbar](term:taskbar) as **Edit desktop contents**.

      Every square now shows a card with clear edges, and the taskbar shows **Done** and a **+** button. While arranging:

      - Touch and hold a square, then drag it to another place.
      - Drag the corner handle of a [gadget](term:gadget) to make it bigger or smaller.
      - Tap the remove mark on a square to take it off the desktop.
      - Tap **+** to add something. It goes into the first free place, or into a new row below everything when the desktop is full.
      - Tap **Done** when you are finished.

      The upright and the sideways screen keep separate layouts, so you can arrange each one the way it suits best.
    image_bookmark:
      shot_id: launcher.desktop-edit-mode
      device_profile: phone
      screen_state: launcher-desktop-edit-mode
      alt: The launcher desktop in edit mode, every square drawn as a card with a remove mark, and the taskbar showing Done and a plus button
      caption: "Arranging the desktop: cards, remove marks, Done and +."
      title: "Screenshot: Edit mode"
      desc: Launcher desktop in edit mode, portrait, a gadget with its resize corner visible, taskbar with Done and +.
  - number: 2
    id: long-desktop
    title: Move things further than the screen shows
    text: |
      When the desktop is longer than the screen, a scroll bar appears along its right edge. Swipe to scroll, or grab the bar and drag it to jump quickly.

      While you drag a square, hold it near the top or bottom edge and the desktop scrolls - the closer to the edge, the faster - so you can drop it in a row that was out of sight. Wide gadgets placed against the right edge are fitted inside the grid, so they never cover their neighbours.
  - number: 3
    id: sections
    title: Group squares into sections and fold them
    text: |
      A [section](term:section) is a heading with the rows under it, such as **Main**, **Apps** or **App functions**. Tap a heading to fold its rows away; tap it again and every square comes back to exactly where it was. A small arrow beside the name shows whether the section is folded, so a folded section is never mistaken for an empty one. Folding is remembered separately for the upright and the sideways screen and survives a restart.

      Touch and hold a heading to open **Section Actions**: **Rename Section**, **Re-sort Section** or **Delete Section**. Deleting asks first, and removes the heading together with the shortcuts under it - you can add them again later. While arranging, the same menu opens from the button on the heading, and holding the heading drags the whole section.

      To start a new section, choose **Create a section..** when adding an item and give it a name, for example "Work".
    image_bookmark:
      shot_id: launcher.desktop-section-folded
      device_profile: phone
      screen_state: launcher-desktop-section-folded
      alt: The launcher desktop with the Apps section folded, its heading showing a turned arrow and the next section moved up
      caption: "A folded section - the arrow shows it is folded."
      title: "Screenshot: Folded section"
      desc: Launcher desktop, Apps section folded, Google section expanded, portrait.
  - number: 4
    id: screens
    title: Spread the desktop over several screens
    text: |
      Touch and hold an empty square, tap **Wallpaper**, and under **Screens** set **Number of screens**. Swipe left and right to move between them. Turn on **Show screen number** to see which screen you are on while you page.

      To move a square to another screen while arranging, tap it and choose **Move to screen 2** (or whichever screen you want). Or drag it to the left or right edge and hold it there a moment: the desktop turns to the previous or next screen and you drop the square where you like.
    image_bookmark:
      shot_id: launcher.desktop-move-to-screen
      device_profile: phone
      screen_state: launcher-edit-move-to-screen
      alt: A square tapped in edit mode on a three-screen desktop, its menu offering Move to screen 2 and Move to screen 3
      caption: "Move a square to another screen."
      title: "Screenshot: Move to screen"
      desc: Edit mode on a desktop with three screens, one shortcut tapped, the move menu open.
  - number: 5
    id: swipes
    title: Choose what each swipe does
    text: |
      Open the launcher settings - **Launcher settings** in the long-press menu - and find the **Gestures** group. Tap **Swipe up**, **Swipe down**, **Swipe left** or **Swipe right** and pick an action from the list: the same actions the edge gestures offer, plus the launcher's own **All apps**, **Next screen** and **Previous screen**. For **Open app** or **Open URL**, the row **App to launch** below it asks which app or web address.

      At the start, swiping up opens [All apps](term:all-apps) - once you have reached the bottom of the desktop - and swiping down opens the Android notification shade. Left and right have no action, so on a desktop with several screens they turn the pages.

      A swipe works when it starts on an empty spot, on a shortcut, on a section heading or on the taskbar. On a gadget, the gadget keeps its own gestures - a map still pans and a list still scrolls. More gestures are in [Customizing navigation gestures and shortcuts](page:launcher.desktop-gestures-and-shortcuts).
    image_bookmark:
      shot_id: launcher.settings-gestures
      device_profile: phone
      screen_state: launcher-settings-gestures-group
      alt: The Gestures group of System launcher settings with Swipe up, Swipe down, Swipe left and Swipe right rows
      caption: "The four desktop swipes in the launcher settings."
      title: "Screenshot: Desktop swipes"
      desc: System launcher settings scrolled to Gestures, Swipe up set to All apps, Swipe down to the notification shade.
  - number: 6
    id: grid-density
    title: Make the squares bigger or smaller
    text: |
      In the launcher settings, **Desktop** group, **Grid density** sets how many squares fit in a row: **Sparse**, **Standard**, **Dense** or **Very dense**. Sparse gives big squares, easy to hit on a car screen; very dense fits the most on a tablet.
  - number: 7
    id: lock
    title: Lock the layout
    text: |
      When the desktop is the way you like it, touch and hold an empty square and tap **Lock changes**. From now on a long press does not open the menu or start moving things. If someone tries, a short message says **The desktop is locked. Unlock it in launcher settings.**

      To unlock, open the launcher settings, **Desktop** group, and turn off **Lock desktop**. The same switch locks it too. **Edit desktop contents** in the Start menu keeps working even while the desktop is locked, so you are never shut out of your own layout.
    image_bookmark:
      shot_id: launcher.settings-lock-desktop
      device_profile: phone
      screen_state: launcher-settings-desktop-group
      alt: The Desktop group of System launcher settings with Grid density, Lock desktop turned on and Double tap to lock the screen
      caption: "Lock desktop in the Desktop group."
      title: "Screenshot: Lock desktop"
      desc: System launcher settings scrolled to Desktop, Lock desktop on.
outcome: |
  Every square sits where you want it, in sections you can fold, spread over as many screens as you need. Your swipes do what you chose, and a lock keeps the whole layout safe from accidental changes.
tips:
  - "**Double tap on an empty spot** locks the device screen, or blacks it out where the device lock is not available. Turn this off with **Double tap to lock the screen** in the **Desktop** group of the launcher settings."
  - "**Rotated the device and everything moved?** Nothing moved - the sideways screen simply has its own layout. Arrange the one you use, or both."
  - "**A section heading got removed while arranging?** Add it back with **Add an item..** like any other square."
  - "**Using a keyboard or a TV remote?** See [Keyboard, D-pad and Android TV control](page:general.keyboard-dpad-tv-navigation)."
next_recipes:
  - title: Your launcher desktop
    url: page:launcher.desktop-grid-and-icons
    badge: Launcher
    badge_type: docs
    description: Turn the desktop on and put apps, folders, channels and gadgets on it.
  - title: Choosing a desktop wallpaper
    url: page:launcher.wallpapers-and-live-backgrounds
    badge: Launcher
    badge_type: image
    description: Animated waves, a still frame, your own photo or a live camera picture.
  - title: Desktop context menus and fast actions
    url: page:launcher.context-menus-and-actions
    badge: Launcher
    badge_type: docs
    description: What a long press offers on apps, channels and gadgets.
---

Once the [desktop](term:desktop) holds the things you use, arranging them is a matter of a few long presses. This page covers moving and resizing squares, sections you can fold, several screens, the four swipes and the lock that keeps it all in place.
