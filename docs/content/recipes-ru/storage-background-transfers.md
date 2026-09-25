---
page_id: storage.background-transfers
title: Background Transfers and Their Progress
nav_title: Background transfers
description: What happens while a big copy or move runs in the background - the notification, the thin progress line at the bottom of every screen, files shared into the app from other apps, unreachable servers, expired cloud sign-ins, and files saved on the phone when their destination is out of reach.
category: Источники, назначения и операции с файлами
category_slug: storage
ticket: S2949
flavor: All editions (details per step)
recipe_number: "06"
canonical_url: documentation/storage/background-transfers-ru.html
why: |
  Copying two hundred holiday videos to the computer at home can take a while. You do not want to stare at a progress window for that long - you want to keep sorting photos, answer a message, or just put the phone down.

  FastMediaSorter runs copies and moves as background tasks that carry on when you leave the screen or close the app. This page shows how to keep an eye on them, and what the app does when something gets in the way.
ingredients:
  - "FastMediaSorter in any [edition](term:edition). Receiving files shared from other apps: Standard, Lite, Photos and Legacy. Network servers and the cloud: see [Copying, moving and deleting files](page:storage.file-copy-move-delete) for which editions have them."
  - "Permission to show notifications, so Android can display the progress while the app is closed."
steps:
  - number: 1
    id: send-to-background
    title: Send a transfer to the background
    text: |
      Start a copy or move in the [file browser](term:file-browser), and in the progress window tap **Background**. The app says "Transfer continues in the background." and you are free: open another folder, sort photos in the player, or leave the app.

      Transfers started with [Quick Sort](term:quick-sort) in the player run in the background from the start. One transfer runs at a time; if you start another while one is still going, the app says "A file transfer is already running."
  - number: 2
    id: notification
    title: Follow it in the notification
    text: |
      While the transfer runs, the notification shade shows "Copying files" or "Moving files" with the percentage and the current file, and a **Cancel** button. At the end you get "File transfer complete" with the number of files, and "Folders transferred: 2" if folders were part of it. If something failed, the notification says "File transfer failed" with the reason.

      Tap the notification to go back to the folder you started from.
    image_bookmark:
      shot_id: storage.transfer-notification
      device_profile: phone
      screen_state: notification-shade-browse-transfer
      alt: The Android notification shade with a FastMediaSorter notification reading Copying files, 42 percent and the current file name, with a Cancel button
      caption: "A copy running in the background."
      title: "Screenshot: Transfer notification"
      desc: Notification shade pulled down during a background copy to an SMB share.
  - number: 3
    id: hairline
    title: The thin progress line at the bottom of the screen
    text: |
      As long as a transfer runs, a thin line along the very bottom edge of the app fills up from left to right, on every screen - so you always know that something is still being copied. In the file browser it also shows a short text such as "Copying 42% - IMG_2031.jpg". Tap it to open the full progress window again.
  - number: 4
    id: folder-updates
    title: Folders stay up to date by themselves
    text: |
      When a background task changes a folder while you are looking at something else - a [scheduled operation](term:scheduled-operation) moved files away, or the watch asked to delete a file - the folder's list catches up the moment you return to it. You do not see files that are no longer there, and you do not have to refresh by hand.
  - number: 5
    id: share-in
    title: Receive files from other apps
    text: |
      *Standard, Lite, Photos and Legacy editions.*

      In any app - a messenger, the gallery, a browser - tap **Share** and choose FastMediaSorter. The same window as for **Copy** opens straight away: tap a [destination](term:destination) or **Select Folder**. The copying then continues in the background with the usual notification, even after the window has closed, and a final notification tells you when it is done.
    image_bookmark:
      shot_id: storage.share-in-destination
      device_profile: phone
      screen_state: receive-share-destination-dialog
      alt: The FastMediaSorter copy window opened over a messenger after sharing a photo, with destination buttons and Select Folder
      caption: "Photos shared from another app, ready to be filed."
      title: "Screenshot: Receiving a shared photo"
      desc: A photo shared from the gallery app, destination dialog over it.
  - number: 6
    id: unreachable
    title: When the server cannot be reached
    text: |
      Before copying to a network folder, the app checks in a couple of seconds whether the computer or NAS answers. If it does not - it is switched off, or the phone left the home Wi-Fi - the transfer stops right away with "Destination server is unreachable - transfer aborted", instead of hanging for minutes. Your files stay where they were. Wake the computer and try again.
  - number: 7
    id: cloud-sign-in
    title: When the cloud asks you to sign in again
    text: |
      Cloud services end a sign-in from time to time. If that happens during a copy or move, the app shows **Authentication Required** - "Cloud authentication required. Please go to Resources and sign in to this Cloud resource." Tap **Sign In** to sign in to that provider right there, then start the transfer again. In the background the notification says "Sign in required - Open FastMediaSorter to sign in and continue".
  - number: 8
    id: fallback
    title: A new file is never lost
    text: |
      When the app saves a new file - a photo from its camera, a voice note, a download - into a network folder or the cloud, and that place cannot be reached, it saves the file on the phone instead and tells you where: "Saved to DCIM/Camera - Home PC is unavailable". Photos go to the camera folder, videos to Movies, other files to the matching standard folder. Move them to their place later - see [Photos, videos and voice notes into a folder](page:storage.capture-to-destination).
outcome: |
  The two hundred videos arrived on the home computer while you kept sorting photos, the notification told you when they were done, and nothing got lost on the way - not even when the Wi-Fi dropped for a moment.
tips:
  - "**Allow notifications.** Without them the transfer still runs, but you only see its progress inside the app."
  - "**Big transfers and battery saving.** For very long copies, keep the phone on the charger; some phones slow down background work on a low battery."
  - "**A permission is missing?** On Android 11 and newer the notification may say 'Permission required' - open the app, allow the access and start again."
next_recipes:
  - title: Copying, moving and deleting files
    url: page:storage.file-copy-move-delete
    badge: Storage
    badge_type: other
    description: Start copies and moves, and undo them.
  - title: Running file jobs on a schedule
    url: page:storage.scheduled-operations
    badge: Storage
    badge_type: other
    description: Background copies that start by themselves.
  - title: Sharing files to nearby devices and apps
    url: page:tools.fast-sharing-and-export
    badge: Tools
    badge_type: docs
    description: The other direction - send files from the app to others.
---

What happens while a big copy or move runs in the background - the notification, the thin progress line at the bottom of every screen, files shared into the app, unreachable servers, expired cloud sign-ins, and new files saved on the phone when their destination is out of reach.
