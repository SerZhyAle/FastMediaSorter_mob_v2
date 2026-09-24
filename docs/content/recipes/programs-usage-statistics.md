---
page_id: programs.usage-statistics
title: Usage Statistics - What You Have Sorted, Viewed and Freed
nav_title: Usage statistics
description: How to switch usage statistics on or off, what the Statistics screen shows - files sorted, space freed, time in the player, and every kind of work you did - and how to save the summary or send it to the author yourself.
category: "Programs, Statistics and Diagnostics"
category_slug: programs
ticket: S2961
flavor: All editions; the Sources section only in editions with network folders or cloud storage
recipe_number: "04"
canonical_url: documentation/programs/usage-statistics.html
why: |
  After a few weeks of sorting it is nice to see the result: how many files you put in order, how many gigabytes the deletions gave back, how many hours of video and music you played. FastMediaSorter counts all of this on your phone and shows it on one screen.

  The numbers never leave the device by themselves. If you want to help the author understand how the app is used, you send the summary yourself, from your own email app, and you see every line of it before it goes.
ingredients:
  - "FastMediaSorter in any [edition](term:edition) - see [The seven editions of FastMediaSorter](page:flavors.overview-and-comparison)."
  - "Some time spent in the app - a new install shows **No activity yet. Your stats will fill in as you sort, view and capture files.**"
  - "For **Send to author**: an email app on the phone. For **Export**: any app that can save or send a file."
steps:
  - number: 1
    id: switch-on
    title: Check that statistics are collected
    text: |
      Open **[Settings](term:settings)**, the **General** tab, and find **Statistics collection** in **General interface settings**. It is on after installation, with the note **Collected on your device. Nothing is sent automatically.** You can also turn it on or off on the first-run setup page - see [Welcome and first setup](page:getting-started.welcome-and-setup).

      Turning the switch off erases the detailed counters at once. Three facts are always kept, even with the switch off: how many times the app was launched, the date of the first launch and the version you first installed.
    image_bookmark:
      shot_id: programs.statistics-switch
      device_profile: phone
      screen_state: settings-general-statistics-rows
      alt: The General tab of Settings with the Statistics collection switch turned on and the Statistics row below it
      caption: "Statistics collection and the Statistics row."
      title: "Screenshot: Statistics switch"
      desc: Settings, General tab, General interface settings, Statistics collection on, Statistics row visible, portrait.
  - number: 2
    id: open
    title: Open the Statistics screen
    text: |
      While collection is on, a **Statistics** row appears next to the switch. Tap it. The row disappears the moment you switch collection off, because there is nothing new to show.

      At the top four cards show your all-time totals:

      - **Sorted (copied + moved)** - how many files you copied or moved into place;
      - **Freed space** - how much space your deletions gave back;
      - **Player time** - how long you watched video and listened to audio in the app, together;
      - **Viewed** - how many files you opened to look at.

      If you open the screen right after a copy or a move, the new files are already counted - there is no moment where the card shows 0.
    image_bookmark:
      shot_id: programs.statistics-dashboard
      device_profile: phone
      screen_state: statistics-summary-cards-and-bar
      alt: The Statistics screen with the four summary cards Sorted, Freed space, Player time and Viewed, and the By type bar underneath
      caption: "Your totals at a glance."
      title: "Screenshot: Statistics screen"
      desc: Statistics screen after some weeks of use, summary cards and By type bar visible, portrait.
  - number: 3
    id: by-type
    title: See which kinds of files you work with - By type
    text: |
      Under the cards the **By type** bar splits the files you worked with into **Images**, **Video**, **Audio**, **Documents** and **Other**, each with its share in percent and its count. The bar appears only once there is something to show.
  - number: 4
    id: sections
    title: Open the sections for the details
    text: |
      Below the bar the details sit in sections you can fold and unfold with a tap on their title. A row that is still at zero is not shown at all, so each section lists only what you really did.

      - **Operations** - **Copied**, **Moved**, **Deleted** with the space freed, **Renamed**, **Archived**, **Extracted**, **Folders created**, **Duplicate scans** and **Duplicates removed**, **Operations undone**, **Scheduled runs** and **Files processed on schedule**.
      - **Capture** - **Photos taken**, **Videos recorded**, **Voice notes** and **Screenshots** made with the in-app camera, recorder and screen-edge gestures.
      - **Viewing** - **Images viewed**, **Videos watched** and **Audio played** with the time spent, **Documents opened** with their pages, **Slideshows started** and **Slides shown**, **Audio streams played** and **Video streams played**, **Frames exported** and **GIF frames saved**, **Added to favorites** and **Removed from favorites**.
      - **Editing** - **Drawings**, **Notes** written in the text editor, **Image edits** and **Text recognized** with [text recognition](page:tools.ocr-text-recognition).
      - **Sources** - **Sources connected**: how many network and cloud folders you added, **Streams added** and **Imported from playlists**. Editions without network folders and cloud storage do not show this section.
      - **Usage** - **App launches**, **First launch**, **First installed version**, and, while collection is on, **Sessions** and **Active time**.

      Each row uses the full width of the screen, so long values such as the installed version read on one line.
    image_bookmark:
      shot_id: programs.statistics-sections
      device_profile: phone
      screen_state: statistics-operations-and-viewing-expanded
      alt: The Statistics screen scrolled down with the Operations and Viewing sections unfolded and their counters listed
      caption: "Unfolded sections list only what you did."
      title: "Screenshot: Statistics sections"
      desc: Statistics screen, Operations and Viewing expanded, Capture and Editing folded, portrait.
  - number: 5
    id: send-or-export
    title: Save the summary or send it to the author
    text: |
      At the bottom the screen reminds you: **This data stays on your device. You send the summary to the author yourself, from your own email app.** Two buttons build the same short text report - the totals, the app version, the [edition](term:edition), the phone model and the Android version, and nothing that identifies you:

      - **Send to author** opens your email app with the author's address, the subject **FastMediaSorter statistics** and the report attached. Read it, and send it only if you want to. If no app can send it, the app says **No app can send this - install a mail or sharing app and try again.**
      - **Export** opens the Android share menu with the same report as a text file, so you can save it to a folder or send it anywhere.
outcome: |
  You know how much work the app has done for you - files sorted, space freed, hours played - and you decide yourself whether anyone else ever sees those numbers.
tips:
  - "**Starting from zero.** Switch **Statistics collection** off and on again: the detailed counters start over, and the launch count and first-launch date stay."
  - "**Why is a row missing?** A counter at zero is hidden. Do the thing once - take a photo, rename a file - and its row appears."
  - "**Freed space and cleaning up.** The biggest gains usually come from duplicates and old videos - see [Cleaning up space](page:storage.cleaning-up-space)."
  - "**Something went wrong in the app?** The statistics report does not help the author there; send the debug log instead - see [System information and the debug log](page:programs.device-diagnostics-and-logs)."
next_recipes:
  - title: System information and the debug log
    url: page:programs.device-diagnostics-and-logs
    badge: Programs
    badge_type: docs
    description: A full report about your device, and the log that helps the author fix a problem.
  - title: Cleaning up space
    url: page:storage.cleaning-up-space
    badge: Storage
    badge_type: other
    description: Find duplicates and large files and free space on the phone.
  - title: Built-in programs
    url: page:programs.built-in-mini-apps
    badge: Programs
    badge_type: docs
    description: Switch on the lights, the mirror, the SOS signal and the other programs.
---

FastMediaSorter keeps a private tally of what you do with it: files sorted and deleted, photos taken, videos watched, documents read. This page shows where to switch the tally on or off, how to read the Statistics screen, and how to save the summary or send it to the author yourself.
