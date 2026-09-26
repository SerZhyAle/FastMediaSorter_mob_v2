---
page_id: settings.settings-overview-and-search
title: Settings - Finding Your Way Around and Searching It
nav_title: Settings overview and search
description: How the Settings screen is organized into collapsible groups, how the search overlay finds any row by keyword, why every row looks and behaves the same way, and how device profile changes, typed fields and destination pickers stay predictable.
category: "Settings & Navigation"
category_slug: settings
ticket: S2962
flavor: All editions - full-text search and the online settings reference are in Standard, Lite, Photos and Legacy
recipe_number: "01"
canonical_url: documentation/settings/settings-overview-and-search.html
why: |
  [Settings](term:settings) has grown into dozens of groups and well over a hundred individual rows. Folded into collapsible sections and searchable by keyword, it stays a screen you can scan in seconds instead of a list you scroll through by hand - and every row, whatever it controls, opens and reads the same way, so nothing has to be learned twice.
ingredients:
  - "FastMediaSorter, any edition, with **Settings** open."
  - "A wide screen or a rotated device to see the two-column layout mentioned below - it is not required for anything else here."
steps:
  - number: 1
    id: groups
    title: Find your way through collapsible groups
    text: |
      **Settings** is laid out as named groups - **General**, **Images**, **Video**, **Audio**, **Documents**, **Streams**, **Player**, **Destinations** and more, plus a **Launcher** and **Wear OS** section on builds that carry them. Each group header is one animated chevron row with an optional summary of what is collapsed inside it, and whichever groups you leave open stay open the next time you visit. A screen reader announces a group as expanded or collapsed, the same way, everywhere it appears - in Settings, in source editors, in player panels and in dialogs.

      On a wide screen or a tablet held sideways, collapsed groups lay out in two columns instead of one long column; open a group and it spans the full width again while you read it. Every group header and every feature toggle also carries a small monochrome icon of what it controls - images, video, audio, documents, streams, network sources, gestures, scheduled operations and more - so a group is recognizable at a glance even when its label is short.
    image_bookmark:
      shot_id: settings.overview-collapsible-groups
      device_profile: tablet
      screen_state: settings-groups-two-column-landscape
      alt: The Settings screen with several collapsible groups shown in a two-column layout in landscape, each header carrying a small feature icon
      caption: "Settings groups, two columns wide when collapsed."
      title: "Screenshot: Settings groups"
      desc: Settings screen, landscape, several collapsed groups in two columns and one group expanded across the full width.
  - number: 2
    id: search
    title: Search across every setting at once
    text: |
      Tap the search icon at the top of **Settings** to open a search overlay that matches any toggle or option by keyword, scrolls straight to it in the right group and highlights the row - instead of you opening group after group to hunt for it. In landscape the search button sits clear of the system navigation bar, so it opens the overlay instead of the row underneath it being dismissed by an accidental tap.
    image_bookmark:
      shot_id: settings.overview-search-overlay
      device_profile: phone
      screen_state: settings-search-overlay-open
      alt: The Settings search overlay with a keyword typed in and the matching row highlighted in its group below
      caption: "Searching Settings by keyword."
      title: "Screenshot: Settings search"
      desc: Settings screen with the search overlay open, a keyword typed, and the matched row highlighted in the list beneath.
  - number: 3
    id: rows
    title: Every row looks and behaves the same way
    text: |
      Whatever a row does, it follows one pattern: the value sits right next to the caption that names it, not stretched to the far edge of a wide or landscape screen, and adjacent rows in a block line up their values in one shared column sized to the longest caption. A row that opens a list of choices shows its selector arrow immediately after the text instead of a full-width stretch, and a simple value picker - a color, a size, a sort order - opens the same small tap-row-plus-list-dialog everywhere it appears, rather than a different pattern per screen. A dropdown row opens a modal option list that takes window focus, so you can walk the choices with a D-pad or arrow keys, confirm with Enter, and reach every option from a screen reader.
    image_bookmark:
      shot_id: settings.overview-row-etalon
      device_profile: phone
      screen_state: settings-row-value-dropdown-open
      alt: A Settings row with its caption and value aligned in a shared column and its selector arrow right after the text, with a dropdown option list open and focused
      caption: "One row pattern, everywhere in Settings."
      title: "Screenshot: Settings row pattern"
      desc: Settings screen, a block of rows with aligned captions and values, one dropdown row open with its option list focused.
  - number: 4
    id: dialog-settings
    title: Some settings live in a dialog, not a row
    text: |
      Not every setting is a row in this list. The launcher configuration, the edge-gesture zones, default-app registration and the camera capture and OCR overlays each keep their own settings in a dialog reached from a button or a long press, rather than as more rows here. The full picture - every row and every dialog-hosted setting, in English, Russian and Ukrainian - is one page away: the [settings reference](../../docs/SETTINGS_REFERENCE.html) linked from the website lists what each one does, generated straight from the app so it never drifts out of date.
    image_bookmark:
      shot_id: settings.overview-dialog-hosted-and-reference
      device_profile: phone
      screen_state: settings-launcher-dialog-hosted-settings
      alt: The launcher configuration dialog, one of the dialog-hosted settings surfaces documented in the online settings reference
      caption: "Some settings open in their own dialog."
      title: "Screenshot: Dialog-hosted settings"
      desc: Launcher settings dialog open from a Settings link row, illustrating a dialog-hosted settings surface.
  - number: 5
    id: device-profile
    title: Device profile changes tell you exactly what they touch
    text: |
      Changing **Device profile** says exactly how many settings it is about to overwrite, and a profile that changes nothing applies right away, with no confirmation to dismiss. A profile also seeds more than the obvious defaults: the reader's theme and layout, link-download behavior, player interaction, the Streams start-up view and, where the [launcher](term:launcher) ships, the desktop grid, taskbar and lock, so a car head unit, an e-reader or a photo frame starts out feeling different from a phone rather than identically. Whatever you change, the row updates the moment you pick a new value - it never flashes back to the old one for an instant while the write is still in flight.
    image_bookmark:
      shot_id: settings.overview-device-profile-confirmation
      device_profile: phone
      screen_state: settings-device-profile-confirmation-dialog
      alt: The device profile confirmation dialog naming the exact number of settings the new profile will overwrite
      caption: "The device profile confirmation names what it changes."
      title: "Screenshot: Device profile confirmation"
      desc: Settings, General group, Device profile row with its confirmation dialog open showing the affected-settings count.
  - number: 6
    id: typed-fields
    title: Typed numbers are kept, not dropped
    text: |
      **Icon size for grid (pixels)** and **Sync interval (min)**, both in **General**, keep exactly what you type into them: the value commits when you leave the screen or press the keyboard's action key, not only when focus quietly moves away. A grid size off the valid step snaps to the nearest allowed size instead of being thrown out, and a typed sync interval snaps to the nearest whole hour, with the stored value written back into the field so you see what actually took effect.
    image_bookmark:
      shot_id: settings.overview-typed-field-commit
      device_profile: phone
      screen_state: settings-general-typed-numeric-fields
      alt: The Icon size for grid and Sync interval fields in General settings with a typed value about to commit
      caption: "Typed values are kept, snapped to a valid step."
      title: "Screenshot: Typed numeric fields"
      desc: Settings, General group, Icon size for grid and Sync interval rows with the keyboard open over one of them.
  - number: 7
    id: housekeeping
    title: Licenses, scheduled jobs and destination folders find their place
    text: |
      **Open Source Licenses** opens without a stutter, and an entry with nothing to say never shows a stray blank line. **Scheduled file operations**, in **Destinations**, is a single link row that opens the standalone [scheduled operations](term:scheduled-operation) screen instead of embedding the whole card in Settings; an old widget shortcut to the embedded card is redirected there automatically. The destination pickers for downloaded files and for screenshots use the same label-plus-value-plus-**Select** button pattern as the camera and video folder pickers, and any of them can point at a plain [local folder](term:local-folder) picked straight from the system folder browser - kept out of your general [resource](term:resource) list, used only as a write target for capture, screenshots, snapshots and auto-downloads.
    image_bookmark:
      shot_id: settings.overview-destination-rows-and-licences
      device_profile: phone
      screen_state: settings-destination-select-resource-row
      alt: A destination settings row with its label, current value and Select button, matching the camera and video folder picker pattern
      caption: "One picker pattern for every write destination."
      title: "Screenshot: Destination picker row"
      desc: Settings, Destinations group, a download or screenshot destination row with the label, value and Select button visible.
  - number: 8
    id: consistency
    title: Dialogs, copy confirmations and rotation behave the same everywhere
    text: |
      A wide-layout screen triggers by available width as well as by orientation, so a large, high-resolution phone held upright gets the roomier layout instead of everything crowding into the top-left corner. Confirm-and-cancel button pairs across Settings dialogs use the same named button styles everywhere, instead of a one-off style per dialog, and copying text anywhere in the app shows one consistently worded confirmation - on Android 13 and later no message is shown at all, because the system already shows its own clipboard preview. Rotating the device while a settings screen is closing, or right after it opens, is safe, and the [edge-gesture](term:edge-gesture) configuration dialog keeps its layout and the zone you had selected when the device turns.
    image_bookmark:
      shot_id: settings.overview-copy-confirmation-and-rotation
      device_profile: phone
      screen_state: settings-copy-confirmation-toast
      alt: The single copy confirmation message shown after copying text from a Settings screen, with the named dialog button styles visible behind it
      caption: "One confirmation message, one set of dialog buttons."
      title: "Screenshot: Copy confirmation"
      desc: A Settings dialog with named confirm/cancel buttons and the unified copy-confirmation message shown after a copy action.
outcome: |
  Settings stays a screen you can scan and search instead of one you scroll through blind: collapsible groups with icons and summaries, a keyword search that jumps straight to a row, one consistent row pattern everywhere, and device-profile, license, scheduled-job and destination handling that all say clearly what they are doing.
tips:
  - "**Looking for a setting hosted in a dialog, not a row?** The [settings reference](../../docs/SETTINGS_REFERENCE.html) covers dialog-hosted settings too, not only the rows in this list."
  - "**Changing themes, language or units next?** See [Themes, colors, language and units](page:settings.display-and-appearance)."
  - "**Want the everyday behavior settings - screen, power, launcher and quick launch?** See [Playback, power and everyday behavior](page:settings.playback-and-sorting-preferences)."
next_recipes:
  - title: Themes, colors, language and units
    url: page:settings.display-and-appearance
    badge: Settings
    badge_type: docs
    description: Color themes, the language picker, big buttons, compact mode and the app-wide unit system.
  - title: Playback, power and everyday behavior
    url: page:settings.playback-and-sorting-preferences
    badge: Settings
    badge_type: docs
    description: Keeping the screen on, auto-rotate, power saving, default apps, launcher mode and the quick-access panel.
  - title: Privacy, passcodes and network security
    url: page:settings.privacy-and-network-security
    badge: Settings
    badge_type: docs
    description: PIN-protected resources, encryption and keeping remote sources safe.
---

[Settings](term:settings) is where you shape how FastMediaSorter looks and behaves, and it has grown into a lot of ground to cover - which is why it is organized into collapsible groups, searchable by keyword, and built from one consistent row pattern instead of a different one per screen. This page walks through finding your way around it and searching it; the individual settings themselves are covered on the pages that follow.
