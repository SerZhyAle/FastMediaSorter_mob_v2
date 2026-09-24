---
page_id: settings.backup-and-device-sync
title: Backing Up Your Settings and Keeping Devices in Sync
nav_title: Backing up and syncing your device
description: Exporting and restoring every app setting to a file or to Google Drive, exporting and importing just your Favorites, moving one kind of data at a time with the unified import and export menu, and keeping a paired watch's settings and resources in step with the phone.
category: "Settings & Navigation"
category_slug: settings
ticket: S2962
flavor: All editions - Google Drive backup needs Standard, noLegal, Photos, Legacy or VR with a Google account connected; Wear OS sync needs Standard or noLegal with Wear Companion
recipe_number: "05"
canonical_url: documentation/settings/backup-and-device-sync.html
why: |
  Setting everything up the way you like it - sources, [Favorites](term:favorites), every toggle - takes a while, and you don't want to do it twice. FastMediaSorter can pack the whole thing into a file or a Google Drive backup, move just one kind of data on its own, and keep a paired [watch](term:watch) matching the phone without you tapping the same setting twice.
ingredients:
  - "FastMediaSorter, any edition, with **Settings**, the **General** group, open. See [Finding your way around Settings](page:settings.settings-overview-and-search) if this is your first visit."
  - "For the Google Drive backup: a Google account connected in the app."
  - "For the watch steps: a paired watch with [Wear Companion](term:wear-companion) turned on."
steps:
  - number: 1
    id: settings-to-file
    title: Back up every setting to a file
    text: |
      In **Settings**, **General**, expand **Backups, restore and settings export**, then tap **Export All Settings to File**. It saves everything the app has stored - not a hand-picked subset - so restoring on an older or a newer version of the app still brings back everything both versions share. On another phone, or after a reinstall, tap **Import Settings from File** and pick the file; it replaces the current configuration.

      A restore also carries your measurement system forward instead of quietly resetting it to metric, and, separately, the SOS program switch, the SOS mode it starts in and the launcher's taskbar row count - a backup taken before any of those existed simply leaves each one as the device already has it.
    image_bookmark:
      shot_id: settings.export-import-settings-file
      device_profile: phone
      screen_state: settings-general-backup-card-file-buttons
      alt: The Backups, restore and settings export card with the Export All Settings to File and Import Settings from File buttons
      caption: "Every setting, packed into a file."
      title: "Screenshot: Export and import settings"
      desc: Settings, General tab, backup card, Export All Settings to File and Import Settings from File buttons.
  - number: 2
    id: google-drive
    title: Keep a full backup in Google Drive
    text: |
      *Standard, noLegal, Photos, Legacy and VR editions, with a Google account connected.* In the same card, tap **Backup settings to Google Drive** and the app saves every setting, together with your [resources](term:resource) and their network passwords and saved web sign-ins, your Favorites and your launcher [desktop](term:desktop), straight to your [Google Drive](term:google-drive) - reporting back with something like "Backed up 12 resources, 40 favorites and all settings to you@gmail.com." The card remembers the date of the last backup and the account.

      Tap **Restore settings from Google Drive** to bring it back: settings are replaced, while resources and Favorites are added to what you already have, with exact duplicates skipped.
    image_bookmark:
      shot_id: settings.google-drive-backup-restore
      device_profile: phone
      screen_state: settings-general-google-drive-buttons
      alt: The Backup settings to Google Drive and Restore settings from Google Drive buttons with the last backup date and account shown
      caption: "A full backup, one tap away in Google Drive."
      title: "Screenshot: Google Drive backup"
      desc: Settings, General tab, Google Drive backup buttons, last backup date and account expanded.
  - number: 3
    id: favorites-file
    title: Export and import just your Favorites
    text: |
      If you only want the starred list, not the whole settings file, the same card has its own pair: **Export Favorites** saves your starred files and channels to a file for sharing or safekeeping, and **Import Favorites** brings a list back in, asking whether to skip or overwrite anything that already exists.
    image_bookmark:
      shot_id: settings.export-import-favorites
      device_profile: phone
      screen_state: settings-general-favorites-buttons
      alt: The Export Favorites and Import Favorites buttons in the backup card
      caption: "Just the starred list, in and out."
      title: "Screenshot: Favorites export and import"
      desc: Settings, General tab, backup card, Export Favorites and Import Favorites buttons.
  - number: 4
    id: import-export-data
    title: Move exactly one kind of data, to a file or to Drive
    text: |
      For anything more selective, tap **Import and export data** - "*Settings, favorites, pinned streams and resources - to a file or to Google Drive*." Choose what (**Settings**, **Favorites**, **Pinned streams** or **Resources**), which way (**Export** or **Import**), and where (**Device file** or **Google Drive**). A file that holds another kind of data, or comes from a newer app version, is refused cleanly and nothing changes. The Google Drive half needs a connected account; the pinned-stream option only appears where streams are supported and switched on. The full backup and export picture, in one place, is in [Backing up and restoring settings](page:general.backup-and-restore).
    image_bookmark:
      shot_id: settings.import-export-data-menu
      device_profile: phone
      screen_state: settings-general-import-export-data-menu
      alt: The Import and export data menu with the data kind, direction and medium choices for settings, favorites, pinned streams and resources
      caption: "One kind of data at a time, either direction."
      title: "Screenshot: Import and export data"
      desc: Import and export data menu open, data kind, direction and medium pickers visible.
  - number: 5
    id: wear-sync
    title: Keep watch settings in step with the phone
    text: |
      *Standard and noLegal editions, with Wear Companion.* Open **Wear Companion** and tap **Sync settings**. Every watch setting can also be changed directly on the watch, and either side's **Sync settings** button brings both to the same state, keeping whichever side changed a given field most recently. Two settings always stay watch-only by design - auto rotation and voice note delivery - and the background picture always stays a phone choice. Both sides show when they last agreed, or "Never synced" if they haven't yet.
    image_bookmark:
      shot_id: settings.wear-companion-sync-settings
      device_profile: phone
      screen_state: settings-wear-companion-sync-button
      alt: The Wear Companion window with the Sync settings button and the Last synced timestamp
      caption: "Sync settings, from either side."
      title: "Screenshot: Wear settings sync"
      desc: Wear Companion settings screen, Sync settings button, Last synced label.
  - number: 6
    id: push-resources
    title: Choose which resources ride along to the watch
    text: |
      Resources don't sync automatically. Inside **Wear Companion**, tap **Push to Watch** and pick which of your registered resources should travel to the watch on the full-screen picker that opens. Leaving the selection empty sends nothing, and says so, instead of quietly pushing your entire resource list.
    image_bookmark:
      shot_id: settings.push-to-watch-resource-picker
      device_profile: phone
      screen_state: settings-wear-companion-push-to-watch-picker
      alt: The Push to Watch full-screen picker listing registered resources with checkboxes
      caption: "Pick exactly what goes to the watch."
      title: "Screenshot: Push to Watch"
      desc: Push to Watch resource picker, several resources listed, some selected.
outcome: |
  Everything you set up travels with you: a settings file or a Google Drive backup that carries the whole configuration, a Favorites list you can move on its own, a menu that moves exactly the one kind of data you want, and a paired watch that stays in step - settings synced both ways, and only the resources you actually chose.
tips:
  - "**Not sure which group a setting lives in?** The search overlay in [Finding your way around Settings](page:settings.settings-overview-and-search) finds it by keyword."
  - "**Back up before a big change.** A settings file or a Google Drive backup takes a few seconds; do one before resetting the phone or moving to a new one."
  - "**Looking for the resource side of sharing?** [Sharing and Backing Up Your Resources](page:storage.sharing-and-backing-up-resources) covers exporting resources to a file, PINs included, and importing a ready configuration from the Windows companion."
next_recipes:
  - title: Privacy, saved sign-ins and error reports
    url: page:settings.privacy-and-network-security
    badge: Settings
    badge_type: docs
    description: Saved sign-ins, unused credentials, secure screens and sending a crash report.
  - title: Controls and key remapping
    url: page:settings.controls-and-key-remapping
    badge: Settings
    badge_type: docs
    description: Remapping keys, D-pad and gamepad navigation, and the travelling focus frame.
  - title: Backing up and restoring settings
    url: page:general.backup-and-restore
    badge: General
    badge_type: docs
    description: Every backup and export option of the app on one page.
---

Pack every setting into a file or a Google Drive backup, export and import just your Favorites, move exactly one kind of data with the unified menu, and keep a paired [watch](term:watch)'s settings and resources in step with the phone - synced both ways, with only the resources you chose along for the ride.
