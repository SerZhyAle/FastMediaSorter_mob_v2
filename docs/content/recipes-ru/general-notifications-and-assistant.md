---
page_id: general.notifications-and-assistant
title: Notifications, the Startup Splash, Colors and Assistant Actions - How the App Shows Itself
nav_title: Notifications and assistant actions
description: What the startup splash shows, which notifications the app posts and how to silence each kind, the one color each type of content and each program keeps everywhere, hiding password screens from the recent-apps list, the app's language on every screen, and the actions a voice assistant can run on Android 16.
category: "General, Keyboard & TV"
category_slug: general
ticket: S2963
flavor: All editions - assistant actions need Android 16 or later; each notification appears only in the editions that have its feature
recipe_number: "04"
canonical_url: documentation/general/notifications-and-assistant-ru.html
why: |
  An app does not live only on its own screens. It greets you when it starts, it tells you about a long copy in the notification shade, it colors a photo differently from a song so you can tell them apart at a glance, and on a new phone it can even answer a voice assistant.

  This page walks through all of these small meeting points, so you know what each one means and how to change it when you want it quieter.
ingredients:
  - "FastMediaSorter in any [edition](term:edition)."
  - "For the assistant actions: a phone with Android 16 or later and a system assistant that supports app actions."
steps:
  - number: 1
    id: splash
    title: Meet the startup splash
    text: |
      For the moment the app takes to start, the [startup splash](term:startup-splash) shows the FastMediaSorter logo and the name **Fast Media Sorter** fading in at the center, with a small author logo at the bottom center and a clock ticking below the logo - in the 24-hour or 12-hour style you chose for units. It disappears by itself as soon as the app is ready; tapping it does nothing, so a stray tap does not press anything behind it.
    image_bookmark:
      shot_id: general.startup-splash
      device_profile: phone
      screen_state: cold-start-splash
      alt: The FastMediaSorter startup splash with the app logo and name at the center, a clock below and the small author logo at the bottom
      caption: "The startup splash."
      title: "Screenshot: Startup splash"
      desc: Phone, cold start of the app, the startup splash with the logo, the name, the ticking clock and the author logo.
  - number: 2
    id: notifications
    title: Read the notifications - and silence the ones you do not need
    text: |
      Every FastMediaSorter notification carries the app logo in the status bar, so you always know where it came from - a file copy or a duplicate search no longer shows the music note of the [audio player](term:audio-player). Android groups the notifications into categories, and you can switch each category off separately. The categories are:

      - **Audio Playback** - what is playing, with play, pause and skip buttons.
      - **Scheduled operations** - progress of a [scheduled operation](term:scheduled-operation).
      - **Network Sync Settings** - background sync of a [network resource](term:network-resource).
      - **Duplicate scanner** - progress of a duplicate search.
      - **Link downloads** - files downloaded from a shared or pasted link; these pop up at the top of the screen.
      - **Browse file transfers** - a copy or a move running in the background.
      - **Element Downloads** - downloadable parts of the app, such as an [extension](term:extension).
      - **Files from the watch** - a file received from the paired [watch](term:watch) being saved.
      - **Voice recording** - the quick voice recorder at work.
      - **Save fallback** - a file could not be saved in the usual place and was saved elsewhere.
      - **Flashlight shortcut** - a notification that turns the flashlight on and off.
      - **SOS signal** - the SOS [program](term:program) is running.
      - **SFTP server** - the app's own SFTP server is running.

      To switch a category off, long-press any FastMediaSorter notification and tap the settings icon, or open the phone's **Settings**, **Apps**, **FastMediaSorter**, **Notifications**. Google's [notification help](https://support.google.com/android/answer/9079661) shows the same on your phone model.
    image_bookmark:
      shot_id: general.notification-categories
      device_profile: phone
      screen_state: android-app-notification-categories
      alt: The Android notification settings of FastMediaSorter listing its notification categories, each with its own switch
      caption: "Each kind of notification has its own switch in Android."
      title: "Screenshot: Notification categories"
      desc: Android Settings, Apps, FastMediaSorter, Notifications, the list of categories with switches.
    callout:
      type: tip
      title: Keep the progress ones
      text: "If you switch off the file transfer or scheduled operation categories, the work still runs - you just do not see its progress. Keep them on if you like to know when a big copy is done."
  - number: 3
    id: colors
    title: Recognize content by its color
    text: |
      Each type of content has one color, and it is the same everywhere: in the statistics chart, on the small badges of each row of the [resource list](term:resource-list) and on the resource icons. Before, a screen could pick its own shade, so one type looked three different ways; now one color always means one type, and every color has a matching shade for the dark theme.

      The same goes for [programs](term:program): each has its own color and keeps it in the programs menu, the [programs panel](term:programs-panel) and its overflow, the quick-launch grid, the [launcher](term:launcher) desktop, the widget picker and the pinned [widget](term:widget) on the home screen.
    image_bookmark:
      shot_id: general.content-type-colors
      device_profile: phone
      screen_state: main-screen-resource-list-type-badges
      alt: The resource list on the main screen with colored media-type badges on each row, one color per type of content
      caption: "One color per type of content."
      title: "Screenshot: Content type colors"
      desc: Main screen, resource list with several resources, each row showing its colored media-type badges.
  - number: 4
    id: privacy
    title: Hide password screens from the recent-apps list
    text: |
      Screens where a password may be visible - adding or editing a network or server [resource](term:resource), the stored-credentials editor in **[Settings](term:settings)**, the sign-in page in the browser and the credentials barcode - show up blank in the recent-apps list and cannot be captured in a [screenshot](term:screenshot). The switch is **Secure sensitive screens** in **Settings**, **General**, **Authorization and accounts**, and it is on from the start. All other screens stay free to capture. More privacy settings: [Privacy, passcodes and network security](page:settings.privacy-and-network-security).
  - number: 5
    id: language
    title: One language on every screen
    text: |
      The app speaks thirteen languages, and the one you choose is used on every screen - also on the few that used to follow the phone's language instead: the [watch](term:watch) companion window, the screens that open from a [widget](term:widget), the barcode import from the [Windows companion](term:windows-companion), the permission screens for camera and screen capture, the launcher's own screens and both VR screens. After a change, every cell and section title of the launcher desktop switches too, and the choice sticks - it does not slip back to the old language. Text that is not translated yet appears in English. In the same settings group, the **Unit system** row shows its value right next to its name, like every other row. How to pick the language and the units: [Choosing the app language and units](page:flavors.multilingual-support).
  - number: 6
    id: assistant
    title: Ask a voice assistant to find and open your media
    text: |
      On Android 16 or later, FastMediaSorter offers a system assistant three actions:

      - **Search your media** - find files by a word from their name across your resources.
      - **Open a media file** - open a found photo, video, song or document in the player.
      - **Open a folder** - open a folder of a resource, for example a shared folder of your computer, in the file browser.

      There is nothing to switch on: on Android 16 an assistant that supports app actions finds these by itself, and on older Android nothing changes. The actions only look and open; they never delete, move or change a file.
outcome: |
  You know what the startup splash, the notifications and the colors are telling you, how to quiet any notification category, how password screens stay private, and that on Android 16 a voice assistant can find and open your media for you.
tips:
  - "**Too many notifications?** Switch off just the categories you do not need - the work behind them keeps running."
  - "**Using the app on a TV?** See [Keyboard, D-pad and TV control](page:general.keyboard-dpad-tv-navigation)."
  - "**Backing up everything before a new phone?** See [Backing up and restoring settings](page:general.backup-and-restore)."
next_recipes:
  - title: Backing up and restoring settings
    url: page:general.backup-and-restore
    badge: General
    badge_type: docs
    description: What a backup carries and how to restore it.
  - title: Choosing the app language and units
    url: page:flavors.multilingual-support
    badge: Languages
    badge_type: other
    description: Thirteen languages, metric or US units, 24-hour or 12-hour clock.
  - title: Split-screen, freeform windows and foldables
    url: page:general.multi-window-and-foldables
    badge: General
    badge_type: docs
    description: The app next to another one, on a tablet or on an unfolded phone.
---

The startup splash, the notifications, the colors of content types and programs, private password screens, one language everywhere, and a voice assistant that can open your media - the ways FastMediaSorter shows itself around the phone.
