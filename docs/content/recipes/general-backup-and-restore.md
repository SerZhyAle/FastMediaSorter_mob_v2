---
page_id: general.backup-and-restore
title: Backing Up and Restoring Settings - What Travels, What Stays and How to Find It
nav_title: Backing up and restoring settings
description: What exactly a FastMediaSorter backup carries, the few settings it leaves behind on purpose, how a backup made by an older version restores, and where to find every backup, export and import button.
category: "General, Keyboard & TV"
category_slug: general
ticket: S2963
flavor: All editions - backup and restore to a file work everywhere; the Google Drive backup needs Standard, noLegal, Photos, Legacy or VR with a Google account connected
recipe_number: "01"
canonical_url: documentation/general/backup-and-restore.html
why: |
  A backup is only worth something if it gives you back what you had. You spent time on the [color theme](term:color-theme), the screenshot gestures, the swipe commands of the [file browser](term:file-browser), the [launcher](term:launcher) desktop and a dozen [streams](term:stream) - and after a new phone or a reset you want all of it back, not half of it.

  This page tells you exactly what a FastMediaSorter backup keeps, the handful of things it leaves behind on purpose and why, and what happens when you restore a backup made months ago by an older version of the app.
ingredients:
  - "FastMediaSorter in any [edition](term:edition), with **[Settings](term:settings)** open on the **General** tab."
  - "For the Google Drive backup: the Standard, noLegal, Photos, Legacy or VR edition and a Google account connected in the app."
  - "For a backup to a file: a place to keep the file - a folder on the phone, a memory card or a [cloud storage](term:cloud-storage) folder."
steps:
  - number: 1
    id: find-card
    title: Find every backup button in one card
    text: |
      Open **Settings**, stay on the **General** tab and expand **Backups, restore and settings export**. Everything that saves or brings back your data is here:

      - **Export All Settings to File** and **Import Settings from File** - every setting, to a file you keep yourself.
      - **Backup settings to Google Drive** and **Restore settings from Google Drive** - settings, [resources](term:resource), [Favorites](term:favorites) and more, in your Google Drive.
      - **Export Favorites** and **Import Favorites** - just your Favorites.
      - **Export resources to file** and **Import resources from file** - just your list of folders and servers, ready to hand to another phone.
      - **Import and export data** - one kind of data at a time: **Settings**, **Favorites**, **Pinned streams** or **Resources**, to a **Device file** or to **Google Drive**.

      The small help icon next to the card opens **Backup Information**, a short reminder of what a backup is for.
    image_bookmark:
      shot_id: general.backup-card-all-buttons
      device_profile: phone
      screen_state: settings-general-backup-card-expanded
      alt: The Backups, restore and settings export card in the General settings, expanded, with the settings file, Google Drive, Favorites and resource buttons and the Import and export data row
      caption: "Every backup and export button lives in one card."
      title: "Screenshot: Backup card"
      desc: Settings, General tab, the Backups, restore and settings export card expanded with a Google account connected.
  - number: 2
    id: search
    title: Or just search for it
    text: |
      Do not remember which group holds the card? Tap the search icon at the top of **Settings** and type **backup**, **restore** or **export**. The search opens the right tab, unfolds the group that holds the row and highlights it - backup and restore always lead to this card, not to an unrelated destinations tab, and there are no phantom results that lead nowhere.

      The same search finds rows that open a list of choices too, such as **Language/Язык/Мова**, the color theme, the video pre-cache size, the default sort mode, the [device profile](term:device-profile), saved authorizations and statistics. In the Lite and Photos editions it hides the rows of features those editions do not carry - microphone recording, background audio, cloud sources, text recognition and translation, and the downloadable extensions screen - so every match you tap leads to a setting you can actually see. More about searching: [Navigating and searching Settings](page:settings.settings-overview-and-search).
    image_bookmark:
      shot_id: general.backup-settings-search
      device_profile: phone
      screen_state: settings-search-overlay-backup-query
      alt: The Settings search overlay with the word backup typed in and the matching backup row highlighted inside its unfolded group
      caption: "Searching for backup jumps straight to the card."
      title: "Screenshot: Searching Settings for backup"
      desc: Settings with the search overlay open, the query backup typed, and the backup card unfolded with the matching row highlighted.
  - number: 3
    id: what-travels
    title: Know what a backup carries
    text: |
      A settings backup - to a file or to Google Drive - carries practically every setting in the app. Among them:

      - the color theme and the look of every screen;
      - the whole block of stream settings;
      - the [camera](term:camera), microphone and [screen recording](term:screen-recording) options;
      - both swipe commands of the file browser and your overrides of the **Send to..** menu;
      - the sixteen swipe slots of the launcher, the network speed readout in its tray and the color palette of its animated waves and particles;
      - all the fields of the [screenshot](term:screenshot) gestures - every zone and every gesture you set up.

      The Google Drive backup adds your resources with their network passwords and saved site sign-ins, your Favorites, your [scheduled operations](term:scheduled-operation) and your launcher desktop. Moving your list of places to someone else's phone is described step by step in [Sharing and backing up your resources](page:storage.sharing-and-backing-up-resources).
    callout:
      type: warning
      title: Keep the backup to yourself
      text: "A backup that carries resources carries their passwords too. Keep it in your own Google Drive or your own folder, and do not send it to anyone."
  - number: 4
    id: what-stays
    title: Know the few settings that stay behind - on purpose
    text: |
      Eight settings are deliberately not carried, because on another phone they would be wrong or even harmful:

      - **Camera lens memory** - the exposure remembered for each lens belongs to that phone's cameras.
      - **The step counter's reset point** - it belongs to that phone's sensor.
      - **Your consent to screen capture and to screen recording** - Android wants you to give it again on each device, so the app asks again.
      - **The last folder picked on the phone, the last opened resource and an old slideshow music setting** - they point at things that no longer exist after a restore, so carrying them would only lead nowhere.

      Everything else comes back.
  - number: 5
    id: restore-older
    title: Restore a backup made by an older version
    text: |
      A backup made months ago, by an older version of FastMediaSorter, still restores. Settings that did not exist when it was made are simply not in the file - and for each of them the app keeps whatever your phone already has, instead of silently resetting it.

      When the file has no value for a setting that it should have had, the app uses exactly the default a fresh install uses. Before, some settings could come back different from a fresh install - text recognition and translation switched on, [picture-in-picture](page:player.pip-and-background-play) switched off, the [trash](term:trash) switched on, or launcher values the app no longer ships. That no longer happens.

      One safety rule is always respected: an old backup does not switch **Open downloaded file in player** back on. A file you download from a link is not opened by itself unless you switch that on again yourself.
    image_bookmark:
      shot_id: general.backup-restore-confirmation
      device_profile: phone
      screen_state: settings-restore-confirm-dialog
      alt: The Restore from Backup confirmation showing the backup date, the device it came from, and how many resources and favorites it holds, with the note that settings will be replaced
      caption: "The restore confirmation says where the backup came from."
      title: "Screenshot: Restore from Backup confirmation"
      desc: Settings, General, Restore settings from Google Drive tapped, the Restore from Backup confirmation open.
outcome: |
  You know where every backup button is, what a backup brings back - practically everything, from the color theme to the last screenshot gesture - what it leaves behind on purpose, and that even an old backup restores without quietly changing your settings.
tips:
  - "**Back up before a big change.** A backup takes seconds; do one before you reset the phone, move to a new one or try a very different [device profile](term:device-profile)."
  - "**Keep two copies.** A file in your own folder and a copy in Google Drive protect you against losing either one."
  - "**Syncing a watch?** Keeping a paired watch in step with the phone is covered in [Backing up your settings and keeping devices in sync](page:settings.backup-and-device-sync)."
next_recipes:
  - title: Sharing and backing up your resources
    url: page:storage.sharing-and-backing-up-resources
    badge: Storage
    badge_type: other
    description: Hand your list of folders and servers to another phone in one file.
  - title: Backing up your settings and keeping devices in sync
    url: page:settings.backup-and-device-sync
    badge: Settings
    badge_type: docs
    description: Settings to a file or Google Drive, Favorites on their own, and a watch in step with the phone.
  - title: Keyboard, D-pad and TV control
    url: page:general.keyboard-dpad-tv-navigation
    badge: General
    badge_type: docs
    description: Moving around the app without touching the screen.
---

Your settings, Favorites and resources can all go into a backup and come back again. This page is about the backup itself: where its buttons are, what it carries, what it leaves behind on purpose, and how an old backup restores.
