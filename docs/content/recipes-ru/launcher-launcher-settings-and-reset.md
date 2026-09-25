---
page_id: launcher.launcher-settings-and-reset
title: Launcher Settings - Finding Your Way Around and Starting Fresh
nav_title: Launcher settings and reset
description: How the launcher settings dialog is organized into groups, what a full reset changes and leaves alone, how a fresh desktop is seeded for your device, and how sections and icons stay tidy and readable.
category: "Launcher: Taskbar, Menus and Gestures"
category_slug: launcher
ticket: S2960
flavor: Standard and noLegal
recipe_number: "10"
canonical_url: documentation/launcher/launcher-settings-and-reset-ru.html
why: |
  A dozen-odd settings are a lot to hunt through one at a time. Folded into a few groups they are quick to scan, and when the desktop stops feeling right - too many test shortcuts, an experiment that went wrong - one button puts it back exactly where it started, without touching your resources or favorites.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) [desktop](term:desktop) open - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
steps:
  - number: 1
    id: groups
    title: Find your way around the launcher settings
    text: |
      Open **Launcher settings** from the long-press menu, the Start menu, or **Settings**, **General**, **System launcher settings**. Its rows sit under collapsible groups - **Taskbar**, **Top bar**, **Desktop** and **System**, plus **Gestures**, **Appearance** and **System tray** for the more specialized rows - and whichever groups you leave open stay open the next time you visit.
    image_bookmark:
      shot_id: launcher.settings-collapsible-groups
      device_profile: phone
      screen_state: launcher-settings-groups-overview
      alt: The System launcher settings dialog with its collapsible groups - Taskbar, Top bar, Desktop, System, Gestures, Appearance and System tray
      caption: "The launcher settings, folded into groups."
      title: "Screenshot: Launcher settings groups"
      desc: System launcher settings dialog, several groups expanded and collapsed, scroll position near the top.
  - number: 2
    id: reset
    title: Start the desktop over
    text: |
      In the **System** group, tap the labeled **Reset launcher settings** button. After a confirmation, the desktop layout, the taskbar pins, the recent-apps history, every launcher setting and the wallpaper return to the state they had right after installation. Your resources, favorites and the app's general settings are never touched.
    image_bookmark:
      shot_id: launcher.settings-reset-button
      device_profile: phone
      screen_state: launcher-settings-reset-confirm
      alt: The System group of launcher settings showing the labeled Reset launcher settings button and its confirmation dialog
      caption: "Reset launcher settings, in the System group."
      title: "Screenshot: Reset launcher settings"
      desc: System launcher settings, System group, Reset launcher settings button and confirmation dialog.
  - number: 3
    id: what-survives
    title: What comes back on its own
    text: |
      A shortcut another app pinned to your desktop - through its own "Add to home screen" - reappears by itself after a reset, laid out after the starter set in both orientations, instead of you pinning it again one app at a time. A shortcut whose app is gone, or which no longer offers pinning, is simply left out. More on bringing in outside shortcuts is in [Your launcher desktop](page:launcher.desktop-grid-and-icons).
  - number: 4
    id: fresh-seed
    title: How a fresh desktop is seeded for your device
    text: |
      The first desktop - and every reset - is laid out for the [device profile](term:device-profile) FastMediaSorter detected: a car head unit, a tablet, a TV, a phone or a photo frame each get their own section order and item count, and a third-party app only gets a cell when it is actually installed. A fresh smartphone starts at icon density 1.5, a two-row taskbar and the top status strip on; other profiles start dense on a phone, sparse on a car head unit, tablet or TV, and standard elsewhere, and every section is auto-sorted right after the re-seed. Only a feature that is both built into your build and switched on gets a cell at first seed - turn one on later and its cell appears by itself, without another reset. On a device with Google Play services, a **Google** section carries the installed apps from an approved list - Play Store, Chrome, Gmail, Search, Photos, Drive, Maps, YouTube, YouTube Music, Calendar - placed after your content and before **App functions**; without Play services, no section appears at all. The very first group of widgets opens with one cell chosen for your device - an interactive Google Maps frame for a car head unit, or YouTube in its own group on a tablet.
    image_bookmark:
      shot_id: launcher.reset-fresh-desktop-seed
      device_profile: phone
      screen_state: launcher-desktop-fresh-seed
      alt: A freshly reset launcher desktop laid out for a phone profile, with the Google section and the profile's own signature widget visible
      caption: "A fresh desktop, seeded for your device."
      title: "Screenshot: Fresh desktop seed"
      desc: Launcher desktop right after a reset, phone profile, Google section and signature widget visible, portrait.
  - number: 5
    id: tidy-sections
    title: Sections stay tidy as they collapse
    text: |
      A [section](term:section) heading now takes up two grid cells instead of a whole row, so its own shortcuts can start in the same row right after it; collapsing it hides only the cells that shared its row and the rows below. Several collapsed sections in a row pack their headers together into one shared row instead of one each, shortening the desktop - a header is never split across rows, and a section still showing its content always ends the chain. While you are arranging the desktop, sections stay unpacked so a drop lands exactly where it looks like it will. Titles keep their own outline so they stay readable over a light wallpaper too.
    image_bookmark:
      shot_id: launcher.collapsed-sections-shared-row
      device_profile: phone
      screen_state: launcher-desktop-collapsed-sections-row
      alt: A launcher desktop with several collapsed section headers sharing one row instead of a row each
      caption: "Collapsed sections sharing a row."
      title: "Screenshot: Collapsed sections"
      desc: Launcher desktop, multiple sections collapsed and packed into one row, one section expanded below, portrait.
  - number: 6
    id: reorder-sections
    title: Move a whole section at once
    text: |
      While arranging the desktop, dragging a section heading moves the section and every cell under it together, one orientation at a time. The same **Section Actions** menu that offers **Rename Section**, **Re-sort Section** and **Delete Section** also carries **Move up** and **Move down**, for a keyboard, a D-pad or a screen reader. More on arranging sections is in [Arranging the desktop](page:launcher.desktop-folders-and-pages).
  - number: 7
    id: legible-surfaces
    title: Icons and surfaces that stay legible
    text: |
      Desktop icons, the gadget picker and the feature chooser keep their own light and dark versions, so nothing turns invisible when you switch the app's [color theme](term:color-theme). One **Widget backdrop opacity** setting, in the **Appearance** group, sets how see-through the card behind widgets, labels, section headers and the Start panel is - from **0% (Transparent)** to **100% (Opaque)**, defaulting to **25% (Default)**. Switching the system theme while you are arranging the desktop no longer interrupts you - edit mode stays exactly where you left it.
  - number: 8
    id: web-portal
    title: A guided tour online
    text: |
      An in-app link in the launcher settings opens an online walkthrough of the desktop, in your own language, with screenshots and - on a car head unit or a tablet - a tour built for that device.
outcome: |
  The launcher settings are a few taps to scan instead of a long list, a reset puts the desktop back to day one without touching your resources or favorites, and a fresh desktop already looks right for the device it is running on.
tips:
  - "**Reset removed a shortcut you added by hand?** Only automatic and pinned shortcuts come back on their own - add your own squares again the way you did the first time. See [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
  - "**Section heading hard to read?** Titles are already drawn to stay legible over a light wallpaper; if a custom photo still fights with it, try a different **Widget backdrop opacity**."
  - "**Using a keyboard or a TV remote?** See [Keyboard, D-pad and Android TV control](page:general.keyboard-dpad-tv-navigation)."
next_recipes:
  - title: Your launcher desktop
    url: page:launcher.desktop-grid-and-icons
    badge: Launcher
    badge_type: docs
    description: Turn the desktop on and put apps, folders, channels and gadgets on it.
  - title: Arranging the desktop - sections, screens, swipes and the lock
    url: page:launcher.desktop-folders-and-pages
    badge: Launcher
    badge_type: docs
    description: Move, resize and fold sections, spread items over several screens and lock the layout.
  - title: Customizing navigation gestures and shortcuts
    url: page:launcher.desktop-gestures-and-shortcuts
    badge: Launcher
    badge_type: docs
    description: Edge swipes, the clock's own gestures, and the double tap that locks the screen.
---

Fourteen-odd rows of launcher settings are easier to use folded into a few groups than laid out in one long list, and a single **Reset launcher settings** button undoes an experiment without touching anything outside the [launcher](term:launcher). This page covers both, plus how a fresh [desktop](term:desktop) is seeded and how its sections and icons stay readable.
