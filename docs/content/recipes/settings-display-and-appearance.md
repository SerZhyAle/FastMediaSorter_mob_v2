---
page_id: settings.display-and-appearance
title: Themes, Colors, Language and Units - Making the App Look and Read Like Yours
nav_title: Themes, colors, language and units
description: Choosing a color theme or a custom accent, reading the app in your own language, switching to Big Buttons or Compact mode, turning decorative motion off, themed icons, the app-wide unit system, colour-coded panels and how the watch keeps the same look on a round screen.
category: "Settings & Navigation"
category_slug: settings
ticket: S2962
flavor: All editions - color theme, custom accents, language, Big Buttons and Compact mode are in Standard, Lite, Photos and Legacy; the watch appearance rows need Standard or noLegal with a paired watch
recipe_number: "02"
canonical_url: documentation/settings/display-and-appearance.html
why: |
  The right look is not the same for everyone: a dark [color theme](term:color-theme) at night, a bold accent by day, big controls on a car dashboard, a compact list on a small screen, your own language everywhere including on a locale that writes its own digits. FastMediaSorter keeps all of it in a handful of [Settings](term:settings) rows instead of scattering it across screens.
ingredients:
  - "FastMediaSorter, any edition, with **Settings**, the **General** group, open. See [Finding your way around Settings](page:settings.settings-overview-and-search) if you have not opened it before."
  - "Optional: a paired [watch](term:watch) to see the appearance settings that reach it."
steps:
  - number: 1
    id: color-theme
    title: Pick a color theme, or one of the accent themes
    text: |
      **Color theme**, in **General**, switches the whole app between **Auto**, **Light** and **Dark**. Underneath it sit six custom accent themes - a dark and a light version each of green, blue and red - applied app-wide and kept across restarts. Secondary text adapts its color to whichever of the six you pick, so captions and hints stay readable against every accent instead of only the two plain themes.
    image_bookmark:
      shot_id: settings.appearance-color-theme-picker
      device_profile: phone
      screen_state: settings-general-color-theme-dialog
      alt: The Color theme picker in General settings showing Auto, Light, Dark and the six accent color themes
      caption: "Color theme, with six accent options underneath."
      title: "Screenshot: Color theme picker"
      desc: Settings, General group, Color theme row with its picker dialog open showing the base and accent theme choices.
  - number: 2
    id: language
    title: Read the app in your own language
    text: |
      **Language/Язык/Мова**, in **General**, opens a searchable list of every interface language. The app reads in thirteen of them: English, Russian and Ukrainian are complete, and Arabic, Bengali, Chinese (Simplified), French, German, Hindi, Italian, Portuguese, Spanish and Urdu carry roughly 98 percent of the text, flavor-specific screens included. A caption with no translation yet falls back to English instead of showing blank, so no screen is ever half-empty, and the system's own per-app language picker offers the same thirteen. On a device set to a language that writes its own digits - Arabic, Bengali or Urdu - creating a resource works normally, and the icon picker grid fills in as expected.
    image_bookmark:
      shot_id: settings.appearance-language-picker
      device_profile: phone
      screen_state: settings-general-language-search-list
      alt: The searchable interface language list in General settings with thirteen languages available
      caption: "Choosing an interface language."
      title: "Screenshot: Language picker"
      desc: Settings, General group, Language row with the searchable language list open.
  - number: 3
    id: big-buttons-compact
    title: Bigger buttons for a car dashboard, smaller elements for a busy screen
    text: |
      **Big Buttons Mode**, in **Playback**, enlarges the player's control buttons and stretches them to the full width, with the toolbar showing five to nine buttons depending on how much room there is - built for a [car head unit](term:car-head-unit) or anywhere you need larger targets. **Compact elements**, in **General**, goes the other way: it shrinks list rows and player controls to fit more on screen at once, and needs a restart to take effect.
    image_bookmark:
      shot_id: settings.appearance-big-buttons-and-compact
      device_profile: phone
      screen_state: settings-playback-big-buttons-toggle
      alt: The Big Buttons Mode toggle in Playback settings and the Compact elements toggle in General settings
      caption: "Big Buttons Mode and Compact elements, opposite ends of the same idea."
      title: "Screenshot: Big Buttons and Compact elements"
      desc: Settings, Playback group, Big Buttons Mode row, with the General group's Compact elements row shown alongside.
  - number: 4
    id: animations
    title: Turn off the decorative motion, keep the motion that means something
    text: |
      **Disable animations**, in **General**, stops screen transitions, group expanding and collapsing, settings-tab effects and image crossfades, plus the branded wave-and-particle backdrop behind the [desktop](term:desktop) when the app is used as a [launcher](term:launcher) and the same backdrop under every watch screen. Motion that carries meaning is kept on purpose: the audio visualizer under a playing track keeps moving, and a progress spinner keeps spinning, because a frozen one reads as a hang rather than as something being saved. With the switch on, the brand backdrop shows a still frame instead of going black after a rotation.
    image_bookmark:
      shot_id: settings.appearance-disable-animations
      device_profile: phone
      screen_state: settings-general-disable-animations-toggle
      alt: The Disable animations toggle in General settings, with the brand backdrop showing a still frame behind it
      caption: "Disable animations, and what keeps moving anyway."
      title: "Screenshot: Disable animations"
      desc: Settings, General group, Disable animations row, brand backdrop visible behind it as a still frame.
  - number: 5
    id: icons
    title: Icons that match your wallpaper and stay recognizable
    text: |
      On Android 13 and newer, turning on the system's own **Themed icons** option - outside FastMediaSorter, in the device's own wallpaper and style settings - recolors the app icon to match your wallpaper the same way the built-in system apps do; below Android 13 the icon stays as it is. Everywhere an icon needs a color instead of just a shape - app shortcuts, the quick-access panel's program, resource and OS tiles - it sits on a flat circle of its own hue, the program's accent or the source's color, instead of a bare glyph on a plain white disc, so the same glyph reads consistently across every surface it appears on.
    image_bookmark:
      shot_id: settings.appearance-themed-and-decorated-icons
      device_profile: phone
      screen_state: home-screen-themed-app-icon
      alt: The FastMediaSorter app icon recolored to match the device wallpaper next to a set of decorated app-shortcut icons on colored circles
      caption: "A themed app icon, and decorated shortcut icons."
      title: "Screenshot: Themed and decorated icons"
      desc: Android home screen with the themed FastMediaSorter icon and a long-press shortcut menu showing decorated colored-circle icons.
  - number: 6
    id: units
    title: One unit system for the whole app, phone and watch
    text: |
      **Unit system**, in **General**, lives in the main app settings and applies everywhere, on every flavor, and syncs to a paired watch. It decides more than temperature: the metric system gives a 24-hour clock, year-month-day dates, kilometres per hour, metres and kilometres, while the US system gives a 12-hour clock with AM/PM, month-day-year dates, miles per hour, feet and miles - on every surface of the phone and the watch, desktop gadgets and home-screen widgets included, overriding the device's own clock-format switch.
    image_bookmark:
      shot_id: settings.appearance-unit-system
      device_profile: phone
      screen_state: settings-general-unit-system-dialog
      alt: The Unit system picker in General settings choosing between the metric and US measurement systems
      caption: "Unit system, app-wide and synced to the watch."
      title: "Screenshot: Unit system"
      desc: Settings, General group, Unit system row with its picker dialog open.
  - number: 7
    id: accents
    title: Panels get their own color, so you spot them at a glance
    text: |
      The collapsible panels on the main window - the programs panel, the streams panel, the resource-type filter - each carry their own accent background, shown both expanded and collapsed, with a recognizable icon on the collapsed chip. It is the same color logic behind the decorated icons in the previous step, just applied to a whole panel instead of one glyph.
    image_bookmark:
      shot_id: settings.appearance-main-panel-accents
      device_profile: phone
      screen_state: main-window-panel-accent-colors
      alt: The main window with the programs panel and streams panel each shown in their own accent color, expanded and collapsed
      caption: "Colour-coded panels on the main window."
      title: "Screenshot: Panel accent colors"
      desc: Main window with the programs and streams panels visible, one expanded and one collapsed, both in their accent colors.
  - number: 8
    id: watch-appearance
    title: The watch keeps the same look, fitted to a round screen
    text: |
      Every screen on the [watch](term:watch) keeps content inside the visible glass of a round [Wear OS](term:wear-os) display, shows the clock, and puts the scroll indicator beside the content instead of over it - the inset comes from the shape and size the watch itself reports, so it is correct on any model rather than tuned to one. In two-column settings cells at large font sizes, toggle captions wrap only at word boundaries.
    image_bookmark:
      shot_id: settings.appearance-wear-round-safe-layout
      device_profile: phone
      screen_state: wear-settings-round-safe-layout
      alt: A Wear OS settings screen with content kept clear of the round glass edge and a two-column toggle caption wrapping at a word boundary
      caption: "Round-safe layout on the watch."
      title: "Screenshot: Wear round-safe layout"
      desc: Wear OS settings screen, round watch face, content and scroll indicator kept inside the visible glass, toggle captions intact.
outcome: |
  The app looks and reads the way you want it to: a color theme or an accent that suits the time of day, your own language down to the icon picker, buttons sized for the situation, motion that only moves when it means something, icons that match your wallpaper, one unit system everywhere including the watch, and panels you can tell apart at a glance.
tips:
  - "**Switching a color theme did not seem to take?** Some accent changes apply immediately; the base Color theme choice needs a restart, which the app offers right away."
  - "**Want the search and row layout behind these settings explained?** See [Finding your way around Settings](page:settings.settings-overview-and-search)."
  - "**Setting up default playback, power and the launcher next?** See [Playback, power and everyday behavior](page:settings.playback-and-sorting-preferences)."
next_recipes:
  - title: Finding your way around Settings
    url: page:settings.settings-overview-and-search
    badge: Settings
    badge_type: docs
    description: Collapsible groups, keyword search and the consistent row pattern behind every setting.
  - title: Playback, power and everyday behavior
    url: page:settings.playback-and-sorting-preferences
    badge: Settings
    badge_type: docs
    description: Keeping the screen on, auto-rotate, power saving, default apps, launcher mode and the quick-access panel.
  - title: Keyboard, D-Pad and Android TV Control
    url: page:general.keyboard-dpad-tv-navigation
    badge: General
    badge_type: docs
    description: Keyboard, D-pad and remote-control navigation across the app.
---

A theme, a language and a size that fit you make every other screen easier to use, and FastMediaSorter keeps all three in [Settings](term:settings), **General**, together with the icon and unit choices that follow from them. This page covers picking a [color theme](term:color-theme) or an accent, reading the app in your own language, switching button size, turning decorative motion off, themed icons, the app-wide unit system, colour-coded panels and how the same look carries over to the [watch](term:watch).
