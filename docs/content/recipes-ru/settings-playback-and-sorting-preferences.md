---
page_id: settings.playback-and-sorting-preferences
title: Playback, Power and Everyday Behavior - Screen, Rotation, Default Apps and the Launcher
nav_title: Playback, power and everyday behavior
description: Keeping the screen on where it matters, letting the app follow the device's auto-rotate, automatic power saving, Picture-in-Picture, becoming the device's default media app, turning the app into your home screen, opening things in their own window, remote-source and Send-to toggles, the programs panel, the quick-access panel and the one shared list behind every program menu.
category: "Settings & Navigation"
category_slug: settings
ticket: S2962
flavor: All editions - launcher mode and the quick-access panel's programs-menu entry are Standard and noLegal only; most other rows are in Standard, Lite, Photos and Legacy
recipe_number: "03"
canonical_url: documentation/settings/playback-and-sorting-preferences-ru.html
why: |
  The everyday defaults matter more than any single feature: whether the screen stays lit while you are actually looking at something, whether the battery is respected when it runs low, whether FastMediaSorter answers when another app hands it a file, and how quickly you reach your [programs](term:program) and resources. This page covers the [Settings](term:settings) rows that shape that everyday behavior.
ingredients:
  - "FastMediaSorter, any edition, with **Settings** open. See [Finding your way around Settings](page:settings.settings-overview-and-search) if this is your first visit."
steps:
  - number: 1
    id: screen-and-rotation
    title: Keep the screen on, and let it follow you, not the OS lock
    text: |
      **Prevent sleep**, in **General**, holds the display on across every screen of the app, including widget configuration, the Network Monitor and the transparent app launch panel. **Keep screen on while player is active** does the same job just for the player, useful when you leave the global option off. On the launcher desktop, an idle timeout raises the app's own black screen instead of letting the device lock, so the desktop dims without the system going to sleep; either hold stands down once power saving kicks in.

      **Rotate screen with OS auto-rotate (except player)** makes every window but the player follow the system auto-rotate setting; **Rotate player screen with OS auto-rotate** is a second toggle that lets the player alone follow it while the first one is off. Both are hidden on a device with no accelerometer.
    image_bookmark:
      shot_id: settings.playback-screen-and-rotation
      device_profile: phone
      screen_state: settings-general-prevent-sleep-and-rotate
      alt: The Prevent sleep and auto-rotate toggles in Settings, with the player-only keep-screen-on and follow-rotate rows shown below them
      caption: "Prevent sleep and auto-rotate, program-wide and player-only."
      title: "Screenshot: Screen and rotation settings"
      desc: Settings screen with Prevent sleep, program-wide auto-rotate and the player-only screen and rotation toggles visible.
  - number: 2
    id: power-saving
    title: The app quiets itself down when the battery runs low
    text: |
      **Power saving mode**, in **General**, turns the app quiet when the battery runs low: pick a threshold - off, always on, or below 10, 15, 20 or 30 percent - and below it every continuous animation stops, the audio visualizer freezes to a still frame, and the screen stops being held awake outside playback; a video still keeps the screen on. Turning on Android's own battery saver has the same effect at any charge level. The phone and the [watch](term:watch) each judge their own battery, and the threshold set on the phone travels to the watch. Your own [Disable animations](page:settings.display-and-appearance) choice is never overwritten by this - it returns exactly as you left it once the charge recovers, and both it and the screen hold come back the moment the device is put on charge, even while Android's own battery saver keeps its own effects off until you switch it off yourself. On a device that reports no battery level at all, the setting says so under its threshold options instead of leaving them silently inert.
    image_bookmark:
      shot_id: settings.playback-power-saving-mode
      device_profile: phone
      screen_state: settings-general-power-saving-thresholds
      alt: The Power saving mode picker in General settings with the off, always-on and percentage threshold choices
      caption: "Power saving mode, with its battery thresholds."
      title: "Screenshot: Power saving mode"
      desc: Settings, General group, Power saving mode row with its threshold picker open.
  - number: 3
    id: pip-and-default-apps
    title: Picture-in-Picture, and becoming the device's default player
    text: |
      **Enable Picture-in-Picture** appears in the Streams settings group as well as in the playback section - one setting shown twice, so flipping it in either place changes the other immediately, and playback behaves the same for a stream as for a local file. It is hidden, on either device generation, below Android 12.

      **Set as default**, in **Destinations**, opens the Default app dialog, where buttons register FastMediaSorter as the device's handler for images, audio, video and documents - only the buttons for capabilities your edition actually has are shown.
    image_bookmark:
      shot_id: settings.playback-pip-and-default-apps
      device_profile: phone
      screen_state: settings-destinations-default-app-dialog
      alt: The Enable Picture-in-Picture toggle in the Streams settings group and the Default app dialog with its per-capability buttons
      caption: "Picture-in-Picture, and setting default apps."
      title: "Screenshot: PiP and default apps"
      desc: Settings, Streams group, Enable Picture-in-Picture row, with the Destinations group's Default app dialog shown alongside.
  - number: 4
    id: launcher-mode-and-exit
    title: Turn the app into your device's home screen
    text: |
      **Primary startup window**, in **General**, offers **Device home screen** among its choices: pick it, confirm it in the system chooser, and pressing Home opens a desktop you build yourself - a grid of shortcuts plus live gadgets, with a taskbar carrying a Start menu, recent launches, pinned apps and a tray. The first time you switch it on, the desktop is pre-filled to match your device profile, and you can always leave [launcher](term:launcher) mode and return to your previous [home screen](term:home-screen). More on the desktop itself is in [Themes, colors, language and units](page:settings.display-and-appearance) and the launcher documentation.

      Separately, the exit button at the top left of the main window minimizes the app - keeping background music, recording, scheduled file operations and the edge-gesture overlay alive - instead of closing it outright, whenever one of those is actually running; it only fully closes when nothing is active in the background, and a long press always force-closes after a brief "background stopped" message.
    image_bookmark:
      shot_id: settings.playback-launcher-mode-startup
      device_profile: phone
      screen_state: settings-general-primary-startup-window-device-home
      alt: The Primary startup window picker in General settings with Device home screen selected
      caption: "Primary startup window, choosing the device home screen."
      title: "Screenshot: Launcher mode"
      desc: Settings, General group, Primary startup window row with the Device home screen choice selected.
  - number: 5
    id: separate-window
    title: Open something in its own window
    text: |
      **Allow new windows**, in **General**, turns on the "Open in new window" action offered from Browse and from the player. Choosing it opens as its own independent window, so multi-window on a VR headset or a desktop-class device (DeX, ChromeOS) genuinely opens a second window instead of staying inside the current one.
    image_bookmark:
      shot_id: settings.playback-separate-window
      device_profile: tablet
      screen_state: browse-open-in-new-window-action
      alt: The Open in new window action opening a folder as a second, independent window
      caption: "Open in new window, as its own independent window."
      title: "Screenshot: Open in new window"
      desc: File browser with the Open in new window action selected, a second independent window opening beside it.
  - number: 6
    id: remote-and-send-to
    title: Switch remote source groups and Send-to targets on or off
    text: |
      Three toggles in **General** - **Local network (Ethernet) SMB**, **Computer on the internet (S)FTP** and **In cloud resources** - turn the [network resource](term:network-resource) and cloud groups on or off; switching one off while it still has saved resources asks for confirmation first. **Send file to..**, in **Destinations**, builds one toggle per possible destination for the player's "Send file to.." menu; a target that is not installed shows a label saying so instead of just disappearing.
    image_bookmark:
      shot_id: settings.playback-remote-source-and-send-to
      device_profile: phone
      screen_state: settings-general-remote-source-toggles
      alt: The SMB, FTP/SFTP and cloud source toggles in General settings, with the Send file to.. per-target toggles shown below
      caption: "Remote source groups and Send-to targets, each its own switch."
      title: "Screenshot: Remote sources and Send-to"
      desc: Settings, General group, the three remote-source toggles, with the Destinations group's Send file to.. toggles shown alongside.
  - number: 7
    id: widgets-and-quick-launch
    title: Add a widget to the home screen, and reach the quick-access panel from the menu
    text: |
      **Add widget to the Android home screen..**, in **Destinations**, opens a picker of the [widgets](term:widget) available for your edition and the settings you have on, filtered to what can actually be pinned, and starts the system's own pin request. **Edit quick-access panel**, also in **Destinations**, opens the editor for the [quick-access panel](term:quick-access-panel) - and that panel can be opened from the main window's programs (three-dots) menu too, alongside the edge gesture, the Quick Settings tile and the widget. Its own catalog of Android settings tiles includes Developer options, Battery saver, Auto-rotate, Accessibility, Wireless networks, Data usage, NFC and VPN - each one appearing only when its settings screen actually resolves on the device.
    image_bookmark:
      shot_id: settings.playback-widget-and-quick-launch
      device_profile: phone
      screen_state: main-window-menu-quick-access-panel-entry
      alt: The programs three-dots menu on the main window with an entry that opens the quick-access panel, and the Add widget picker in Destinations settings
      caption: "The quick-access panel, reachable from the main menu."
      title: "Screenshot: Quick-access panel entry point"
      desc: Main window programs menu open with a quick-access panel entry, Destinations settings Add widget row shown alongside.
  - number: 8
    id: programs-panel
    title: A programs panel above your resources, with its own menu
    text: |
      **Programs panel**, in **General**, shows an optional horizontal strip above the [resource list](term:resource-list) on the main window, mirroring the programs three-dots menu - icons only in portrait, icons with labels in landscape, with overflow for anything that does not fit; it hides the three-dots button while shown, and is off by default. Every item on the [programs panel](term:programs-panel) and the streams panel carries its own three-dots button, or a long-press menu where labels are hidden, to open it, open it in a separate window where multi-window is available, or remove it from the panel; a plain tap still opens or plays the item directly. That menu also carries a **Configure** entry that jumps straight into **Settings** on the relevant group, already expanded and scrolled into view.
    image_bookmark:
      shot_id: settings.playback-programs-panel
      device_profile: phone
      screen_state: main-window-programs-panel-item-menu
      alt: The programs panel above the resource list on the main window with an item's context menu open, showing a Configure entry
      caption: "The programs panel, and its per-item menu."
      title: "Screenshot: Programs panel"
      desc: Main window with the programs panel shown above the resource list, one item's context menu open with Configure visible.
  - number: 9
    id: one-list-everywhere
    title: One list of programs, everywhere it's offered
    text: |
      The programs menu, the programs panel, the quick-access panel, the widget picker and the launcher desktop all read from one program registry, in one order - so the flashlight, the mirror and every other switched-on program are worded and drawn once and appear the same way on all of them; turning a program off in Settings removes it from all of them together. Long-pressing the app's own icon shows recently used programs and resources among the dynamic shortcuts, ranked by how recently you used them and capped by however many shortcuts the platform allows; Favorites and Slideshow stay as fixed shortcuts regardless. On the launcher desktop, the top signal strip caps itself at five icons plus a counter while **Top status bar** is on, or eleven plus a counter with it off - a narrow screen still shows only what actually fits.
    image_bookmark:
      shot_id: settings.playback-one-program-registry
      device_profile: phone
      screen_state: launcher-app-icon-long-press-shortcuts
      alt: The long-press shortcut menu on the FastMediaSorter app icon showing recently used programs and resources ranked by recency
      caption: "One program list, reflected everywhere it appears."
      title: "Screenshot: App-icon shortcuts"
      desc: Android home screen, long-press menu on the FastMediaSorter icon, recent programs and resources listed as dynamic shortcuts.
outcome: |
  The everyday behavior stays predictable: the screen stays lit where you need it and lets go where you do not, the battery is respected automatically, the app answers when handed a file, the launcher and its exit button behave the way you set them up, and every program you have switched on shows up the same way wherever you reach for it.
tips:
  - "**Not sure which group a setting lives in?** The search overlay covered in [Finding your way around Settings](page:settings.settings-overview-and-search) finds it by keyword."
  - "**Want the color theme and language settings first?** See [Themes, colors, language and units](page:settings.display-and-appearance)."
  - "**Navigating with a keyboard, D-pad or remote?** See [Keyboard, D-Pad and Android TV Control](page:general.keyboard-dpad-tv-navigation)."
next_recipes:
  - title: Finding your way around Settings
    url: page:settings.settings-overview-and-search
    badge: Settings
    badge_type: docs
    description: Collapsible groups, keyword search and the consistent row pattern behind every setting.
  - title: Themes, colors, language and units
    url: page:settings.display-and-appearance
    badge: Settings
    badge_type: docs
    description: Color themes, the language picker, big buttons, compact mode and the app-wide unit system.
  - title: Backing Up and Restoring Settings
    url: page:general.backup-and-restore
    badge: General
    badge_type: docs
    description: Exporting and restoring settings, resources and favorites, and keeping devices in step.
---

Beyond how [Settings](term:settings) looks lies how the app actually behaves day to day - whether the screen stays on, whether it plays nicely with the battery, whether it answers when another app hands it a file, and how quickly your [programs](term:program) and resources are within reach. This page covers that everyday behavior, from screen and power to the [launcher](term:launcher) and the programs panel.
