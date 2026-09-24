---
page_id: launcher.context-menus-and-actions
title: Desktop Context Menus and Fast Actions - What a Long Press Opens
nav_title: Context menus and fast actions
description: What a long press opens on a launcher square, and which squares - resources, app functions, contacts, places, channels and a few helpers - build or update themselves on their own.
category: "Launcher: Taskbar, Menus and Gestures"
category_slug: launcher
ticket: S2960
flavor: Standard and noLegal
recipe_number: "08"
canonical_url: documentation/launcher/context-menus-and-actions.html
why: |
  A long press on the FastMediaSorter desktop rarely does just one thing. Depending on what you're holding, it can open a rich menu, start creating something new, or turn a shared contact, place or channel into a square that keeps itself current from then on.

  This page is a tour of what each long press offers, and of the squares that build or update themselves on their own - so nothing on the desktop ever feels like a mystery.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) [desktop](term:desktop) open - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
  - "A few empty squares to try the menus and the new squares on."
  - "Optional: a contact, a channel, or a place shared from a map app, if you want to try those specific squares."
steps:
  - number: 1
    id: cell-menus
    title: Long-press menus on cells
    text: |
      Touch and hold any square on the desktop and a menu opens that matches what you're holding.

      A [resource](term:resource) or [channel](term:channel) square opens the same menu the main app offers for that item: for a resource, open it, play it, add another [shortcut](term:shortcut), edit it, copy it, export it, share access to it, rescan it or delete it; for a channel, pin it, add a shortcut, share its link, or remove it. Both menus come from one shared list the app keeps for each kind of item, so the desktop and the main window never drift apart, and the same confirmation guards a destructive action either way.

      An App functions square - Streams, Quick Camera, Network Monitor and the rest - opens a shorter menu: **Open**, **Settings** where the feature has one, **Pin to taskbar**, and **Remove from desktop**.

      Some squares reach further than opening themselves. A square pinned to a [quick-access panel](term:quick-access-panel) route opens that panel directly, and a square pinned to a [scheduled operation](term:scheduled-operation) asks you to confirm, then runs the job in the background - a backup you can start with one long press instead of digging through settings.
    image_bookmark:
      shot_id: launcher.cell-action-menu
      device_profile: phone
      screen_state: launcher-cell-action-menu
      alt: A long-press menu open on a resource square on the launcher desktop, listing Open, Edit, Copy, Export, Rescan and Delete
      caption: "The same menu the main window offers, opened from the desktop."
      title: "Screenshot: Cell action menu"
      desc: Launcher desktop, long press on a resource square, the full action menu open, portrait.
  - number: 2
    id: resources-from-home
    title: Resources straight from home
    text: |
      Starting a brand-new resource - a folder, a cloud account, a network share - no longer means leaving the desktop first.

      Touch and hold an empty square and tap **Add resource**, or, while adding **My resource** from the ordinary add-item picker, tap **Add resource..** at the bottom of the list. Either opens the app's usual resource-creation screen; finish it and the new resource's shortcut lands on the desktop by itself. Cancelling leaves nothing behind, and on a home screen that won't accept a pinned shortcut the resource is still created - the app just says so.

      Every resource you create this way, or from the main window, lands in the desktop's [Resources](term:section) [section](term:section) automatically, and that section keeps its own **Add resource** square at the end, so you're never short of an empty one to start the next.

      A resource's square shows its real face on the desktop and the [taskbar](term:taskbar): the icon you picked for it, or its virtual-folder or cloud-provider glyph, layered with a small badge for how it connects - cloud, network or local - instead of one generic icon per resource type. Turn on a media type later, say [All Images](term:all-images), and its own aggregate square appears in Resources by itself, without touching anything else you've arranged.

      The App functions squares keep themselves current too: switching a feature on adds its square there at once, in both the upright and sideways layout, and switching it off removes that square again - one you removed by hand stays gone either way. Installing a real app adds one automatic square of its own to the desktop; uninstalling it removes only that automatic square, never one you placed yourself.
    image_bookmark:
      shot_id: launcher.resources-section-auto-add
      device_profile: phone
      screen_state: launcher-resources-section-add-tile
      alt: The Resources section of the launcher desktop showing two resource squares with their own icons and an Add resource square at the end
      caption: "Resources add themselves, with their own Add resource square."
      title: "Screenshot: Resources section"
      desc: Launcher desktop, Resources section, two resource squares with composed logos and the Add resource square, portrait.
  - number: 3
    id: people
    title: Squares for people
    text: |
      Share a contact with the launcher and it becomes a square that keeps working for that person, without asking for your address book up front.

      Pin a contact from the add-item picker and choose what the square does: open their **Contact card**, **Call** them, **Send SMS**, or open **Message in an app** to jump straight into a messenger conversation. Call and SMS are hidden on a device with no telephony. The app reads the contact once, from the entry you pick in the system contact picker, and keeps a snapshot on the square - no contacts permission is needed just to pin one, and calling always goes through the number you actually chose. Until a photo is on file, or if you never grant the permission below, the square shows the person's initials over a colour that stays theirs.

      Pinning a messenger square first asks **Which app should open the chat?** so you pick the app before the person; an **Any app** row keeps the older order of person-then-app, and the question is skipped when only one messenger is installed. If the person turns out to have no chat in the app you picked, the square's error message says which app that was.

      Want the square to stay current when someone changes their photo or gets renamed? Grant the contacts permission when the app asks, right as you pin the square. Refuse and the square keeps working from the snapshot taken at pin time, which also stands in if the contact is later deleted or can't be read.
    image_bookmark:
      shot_id: launcher.contact-cell-live-photo
      device_profile: phone
      screen_state: launcher-contact-cell-pinned
      alt: A pinned contact square on the launcher desktop showing the person's current photo and name, next to another showing coloured initials as a fallback
      caption: "A pinned contact square, kept current from the address book."
      title: "Screenshot: Contact square"
      desc: Launcher desktop, a pinned contact square with a live photo next to one showing initials on a colour block, portrait.
  - number: 4
    id: places
    title: A place from the map
    text: |
      Share a location from any map app while the launcher is turned on, and **Add place to launcher** appears in the share sheet next to your other sharing choices. Tap it, and a square lands on the desktop that opens turn-by-turn navigation to that spot, ready to start driving the moment you tap it.

      A shared short link is resolved to real coordinates while the square is created, so opening it later needs no network. When a link can't be resolved, the square is still created and simply shows the place as shared.
    image_bookmark:
      shot_id: launcher.place-shortcut-share-sheet
      device_profile: phone
      screen_state: share-sheet-add-place-to-launcher
      alt: Android's share sheet open from a map app with an Add place to launcher entry among the sharing targets
      caption: "Add place to launcher, right in the share sheet."
      title: "Screenshot: Add place to launcher"
      desc: Share sheet opened from a maps app, the Add place to launcher target visible, portrait.
  - number: 5
    id: live-windows
    title: Live windows onto radio, video and maps
    text: |
      A channel can sit on the desktop as more than a shortcut. Choose **Stream** in the add-item picker, or long-press a channel square and choose **Add window to desktop**, and you get a **Stream window**: a radio channel shows its name, icon and a play/pause button; a video channel gets a larger square, at least 3x2, with the picture itself paused on the last frame until you press play - nothing starts on its own.

      Tap a playing video window and corner controls fade in for a moment: play/pause, mute, stop, fullscreen and picture-in-picture. Fullscreen and picture-in-picture both hand off to the full player, which can collapse back into a small floating window on request.

      Adding a channel square before you've saved any channels opens **Settings**, [Streams](term:streams-screen) instead of an empty list, so there's always somewhere to go. Picking a channel for any of these squares stays quick even with a very long list: only the icons on screen get loaded, a big catalog asks for a search word or a topic first, and an empty result says so instead of leaving a spinner on screen. The same fast picker also serves the channel choosers of the Legacy and VR [editions](term:edition), which use it outside the launcher desktop.

      **YouTube channel window** works the same idea for a channel you follow there: name it or paste its address, and its square - at least 3x2 - shows the channel name and the cover of its latest upload, silent until you tap it, then plays that video right inside the square through YouTube's own player. Where the device can't host that player, the square opens the channel in the YouTube app instead.

      **Google Maps Live Frame** puts an interactive map, not a screenshot, on the desktop: pan and zoom it like the map app itself, right from the square.
    image_bookmark:
      shot_id: launcher.stream-window-overlay-controls
      device_profile: phone
      screen_state: launcher-stream-window-overlay-controls
      alt: A video Stream window square on the desktop with auto-hiding corner controls for play, mute, stop, fullscreen and picture-in-picture
      caption: "Tap a playing video window for its corner controls."
      title: "Screenshot: Stream window controls"
      desc: Launcher desktop, a 3x2 video Stream window square, overlay controls visible after a tap, portrait.
  - number: 6
    id: helpers
    title: "Helpers: translation, playback and weather"
    text: |
      A few squares do small jobs well, right where you are.

      The **Translator** square translates typed or pasted text fully offline, through the bundled engine; its caption shows the language pair and doubles as a button to change it. It says plainly when a language pack is still missing rather than failing quietly.

      The [Now Playing](term:now-playing) [gadget](term:gadget) starts out showing this app's own playback, the same as ever. Turn on notification access from a button on the gadget itself - behind a plain-language explanation of what that grants - and it follows and controls whatever is playing in any app instead, falling back to FastMediaSorter's own playback whenever nothing else is. It draws the cover art the playing app publishes, falls back to that app's icon when there is none, and a tap on the card opens whichever app is currently playing.

      The weather square's **Unit system** - metric or US measurements - lives in **Settings**, **General**, and without you touching it the square already shows Fahrenheit on a device set to the United States, Liberia or Myanmar, and Celsius everywhere else, read from the device's region rather than its language. The city you picked for it is remembered on its own, so resetting the launcher to defaults brings the weather back already showing your city instead of asking again.
    image_bookmark:
      shot_id: launcher.now-playing-artwork
      device_profile: phone
      screen_state: launcher-now-playing-artwork
      alt: The Now Playing gadget on the launcher desktop showing cover art from a different app's playback with playback controls
      caption: "Now Playing, following another app's music."
      title: "Screenshot: Now Playing artwork"
      desc: Launcher desktop, Now Playing gadget showing an external app's cover art and controls, portrait.
outcome: |
  Every square on your desktop opens the right menu for what it holds, new resources and app squares add themselves as you create or install things, and people, places, channels and a handful of small helpers all turn into squares that stay live and current - without you hunting through settings first.
tips:
  - "**Interrupted while picking something for a new square?** If Android needs to close the app in the background while you're still in a picker - choosing a contact, an app or a resource - the square you started keeps its place. Finish the picker when you come back and it drops in exactly where you began."
  - "**Using a keyboard or a TV remote?** See [Keyboard, D-pad and Android TV control](page:general.keyboard-dpad-tv-navigation) for how to reach these same menus without a long press."
next_recipes:
  - title: Your launcher desktop
    url: page:launcher.desktop-grid-and-icons
    badge: Launcher
    badge_type: docs
    description: Turn the desktop on and put apps, folders, channels and gadgets on it.
  - title: Desktop gadgets
    url: page:launcher.built-in-gadgets
    badge: Launcher
    badge_type: docs
    description: The full gadget catalog, clocks to weather to device status.
  - title: Using the desktop dock and taskbar
    url: page:launcher.taskbar-and-dock
    badge: Launcher
    badge_type: docs
    description: The Start button, recent and pinned apps and the status tray.
---

A long press on the [launcher](term:launcher) [desktop](term:desktop) is rarely just one thing. This page walks through what each long press opens - on a resource, a channel, an app function - and through the squares that build or update themselves on their own: new resources, app shortcuts, contacts, a shared place, live channel windows and a few small helpers.
