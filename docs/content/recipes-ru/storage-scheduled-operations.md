---
page_id: storage.scheduled-operations
title: Running File Jobs on a Schedule
nav_title: Scheduled file operations
description: How to let the app copy, move or delete files by itself on a schedule - for example move new camera photos to the home computer every night - and how to read the run history.
category: Источники, назначения и операции с файлами
category_slug: storage
ticket: S2949
flavor: Standard, noLegal, Legacy and VR
recipe_number: "09"
canonical_url: documentation/storage/scheduled-operations-ru.html
why: |
  Every evening you plug in the phone and think: "I should copy today's photos to the computer." And every evening you forget. A [scheduled operation](term:scheduled-operation) does it for you: you describe the job once - what to take, where to put it and how often - and the app runs it in the background, even when it is closed.

  Typical jobs: copy new camera photos to a [network folder](term:network-folder) every night, move downloaded videos to the memory card every few hours, or empty a folder of temporary screenshots once a day.
ingredients:
  - "FastMediaSorter in the Standard, noLegal, Legacy or VR [edition](term:edition)."
  - "The source: a [resource](term:resource) or any folder on this device."
  - "For copy and move: a [destination](term:destination), or any folder on this device."
  - "The phone switched on at the scheduled time. Android decides the exact moment, so a job may start a few minutes late."
steps:
  - number: 1
    id: open
    title: Open the scheduled operations screen
    text: |
      There are several doors to the same **Scheduled file operations** screen:

      - **Settings**, the **Management** tab, the row **Scheduled file operations** - "Manage operations and the run history".
      - The programs menu and the quick-access panel, where the scheduled operations program sits among the other [programs](term:program).
      - The **Scheduled Operations** [widget](term:widget) on the Android home screen.
      - In the [file browser](term:file-browser): the [three-dots menu](term:three-dots-menu), **Automation..** - the screen opens with the folder you are in already set as the source.

      At the top of the screen, **Use scheduled operations** - "Copy, move, delete in the background" - switches the whole feature on or off.
    image_bookmark:
      shot_id: storage.scheduled-ops-screen
      device_profile: phone
      screen_state: scheduled-operations-list
      alt: The Scheduled file operations screen with the Use scheduled operations switch, two operations in the list and the Add button
      caption: "The Scheduled file operations screen."
      title: "Screenshot: Scheduled file operations"
      desc: Two operations configured, one enabled, one paused.
  - number: 2
    id: what-where
    title: Say what to take and where to put it
    text: |
      Tap **+** (**Add**). In the new operation:

      - **Source** - pick one of your resources, or the first item **Local Folder** to choose any folder in Android's folder window. Such a folder is used only by this job and does not appear on the main screen.
      - **Operation** - **Copy**, **Move** or **Delete**.
      - **Destination** - for copy and move: one of your destinations, or **Local Folder** again for any folder on the device. If you have exactly one destination, it is filled in for you.

      If the source is read-only, the app allows only **Copy** and says "Source is read-only".
    image_bookmark:
      shot_id: storage.scheduled-op-editor
      device_profile: phone
      screen_state: scheduled-operation-dialog-new
      alt: The new scheduled operation window with Source, Operation and Destination fields, the file type boxes, the time filter, the start time and the interval
      caption: "Describe the job once."
      title: "Screenshot: New scheduled operation"
      desc: Scheduled operation dialog, Source Camera Photos, Operation Copy, Destination a network folder.
  - number: 3
    id: which-files
    title: Choose which files and when
    text: |
      - **File types** - **All files (incl. non-media)**, **Images (incl. GIF)**, **Audio**, **Video** or **Documents (PDF, EPUB, text, Office)**. Ticking **All files** unticks the others. At least one must stay ticked.
      - **Time filter** - **All**, **New since last run**, **New in last hour** or **New in last day**. **New since last run** is the one for "copy only what appeared since yesterday".
      - **Starting at** - the time of the first run.
      - **Every** - how often to repeat, in hours and minutes. The default is every 24 hours. The shortest interval is 15 minutes; a 0-hour, 0-minute interval is corrected to 1 hour.

      Below, **Next run at:** shows when the job will start, updated as you change the time.
  - number: 4
    id: options-save
    title: Set the last details and save
    text: |
      - **Overwrite existing files** - when a file with the same name is already at the destination, replace it. Off means such files are skipped.
      - **Suppress notifications** - run quietly, without a notification.

      Tap **Save operation**. The first time, the app may ask to **Allow background activity**: "On some devices the system may stop background tasks. For reliable scheduled operations, allow this app to run without battery optimization." Tap **Open settings** and allow it - otherwise some phones stop the job while the screen is off.
    callout:
      type: tip
      title: Move and delete need full access
      text: "To remove the original files after a move or a delete in the background, the app needs access to all files. If it is missing, you get the notification 'Scheduled operation needs permission' - tap it and allow the access."
  - number: 5
    id: manage
    title: Pause, run now and read the history
    text: |
      Each operation in the list shows its source, destination and schedule ("At 23:00 · Every 24h") with its own switch to pause it, and **Run now** to start it at once. For all of them together use **Run all now**, **Pause all** and **Resume all**.

      Tap **Log** to open the **Run history**: when each job ran, how many files it handled and what went wrong, if anything. An operation whose last run failed is marked - "Last run failed. Tap Log for details." You can clear the history at any time.

      The job runs by itself even after the phone restarts. If the folder you are looking at in the file browser was changed by a job in the meantime, the list updates the moment you come back to it - no files that were already moved away are left in view.
    image_bookmark:
      shot_id: storage.scheduled-ops-history
      device_profile: phone
      screen_state: scheduled-operations-run-history
      alt: The Run history window listing several runs of a scheduled copy with their times and file counts
      caption: "The run history."
      title: "Screenshot: Run history"
      desc: Run history after three nightly runs, one with an error.
outcome: |
  Your photos land on the home computer every night without you lifting a finger, the downloads folder stays tidy, and the run history tells you in the morning what was done.
tips:
  - "**The main switch follows the list.** On the **Scheduled file operations** screen, the switch for scheduled operations turns off by itself when the list is empty and on again as soon as the list has an operation."
  - "**Start with Copy.** Try a new job as a copy for a day or two; switch it to Move once you trust it."
  - "**A network destination must be reachable** at the scheduled time - for a home computer, choose an hour when it is on."
  - "**Not sure it will work?** Tap **Run now** once and look at the Log."
next_recipes:
  - title: Sorting files into destinations
    url: page:storage.destination-targets-setup
    badge: Storage
    badge_type: other
    description: Set up the destinations your scheduled jobs will fill.
  - title: Adding network folders and cloud storage
    url: page:storage.network-and-cloud-sources
    badge: Storage
    badge_type: other
    description: Add the home computer as a place for nightly copies.
  - title: Built-in utilities
    url: page:programs.built-in-mini-apps
    badge: Programs
    badge_type: docs
    description: The other programs you can open from the same menu.
---

Let the app copy, move or delete files by itself on a schedule - for example copy new camera photos to the home computer every night - and read the run history to see what was done.
