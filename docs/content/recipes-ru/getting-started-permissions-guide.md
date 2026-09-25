---
page_id: getting-started.permissions-guide
title: Understanding App Permissions
nav_title: App permissions explained
description: What each permission FastMediaSorter can ask for actually unlocks, how to grant one permission at a time or all of them at once, and how to change your mind later in Android's own settings.
category: Первые шаги и настройка
category_slug: getting-started
ticket: S2946
flavor: Все 7 редакций
recipe_number: "02"
canonical_url: documentation/getting-started/permissions-guide-ru.html
why: |
  FastMediaSorter only sees and touches what Android lets it. Every [permission](term:permission) it can ask for exists because some feature needs it - reading your photos, opening a shared folder over the network, writing a geotag into a picture you just took. Nothing is requested "just in case".

  You already met a short version of this list on the welcome screen when you first opened the app. This page is the full picture: every permission this edition can ask for, in plain words, in one place, with a way to grant them one at a time or all together.
ingredients:
  - "FastMediaSorter installed, in any [edition](term:edition): Standard, noLegal, Lite, Photos, Legacy, VR or FOSS. The exact list of rows differs a little by edition - a permission only appears if this build can actually use it."
  - "A few rows only appear from certain Android versions onward - each one names the version it needs, so there is nothing to look up."
steps:
  - number: 1
    id: open-permissions-screen
    title: Find the full list
    text: |
      Open **Settings**, the **General** tab, and tap **Permissions & Access**. The screen lists every permission this edition can request, grouped under headings: **Storage**, **Network**, **Microphone**, **Notifications**, **Camera**, **Location**, **System**, and - on editions that ship the launcher - **Contacts**. Each row shows a short label, a one-line description, and whether it is currently granted.

      The same list, with the same wording, is also the last page of the first-launch setup wizard - see [First launch and setup wizard](page:getting-started.welcome-and-setup).
    image_bookmark:
      shot_id: getting-started.main-screen-permissions-list
      device_profile: phone
      screen_state: settings-permissions-list
      alt: The Permissions and Access screen in Settings, showing grouped permission rows with their granted status
      caption: "Settings, General, Permissions & Access."
      title: "Screenshot: Permissions & Access screen"
      desc: Settings screen scrolled to the permission groups, several rows granted and a few not yet requested.
  - number: 2
    id: grant-one
    title: Grant one permission at a time
    text: |
      Tap any row that is not yet granted. Most rows show Android's own request dialog right there - tap **Allow** and you are back on the list. A few permissions cannot be granted this way at all: Android insists on a dedicated screen for them, for example **Full storage management** or **Display over other apps**. For those, tapping the row opens that screen; make your choice there and press Back to return to FastMediaSorter.
    image_bookmark:
      shot_id: getting-started.permissions-dialog
      device_profile: phone
      screen_state: permissions-dialog
      alt: The Android system permission prompt asking to allow FastMediaSorter access to photos and media on the device
      caption: "The system's own request dialog for one permission."
      title: "Screenshot: Android permission prompt"
      desc: Android's storage/media permission dialog shown over the app, Allow and Deny buttons visible.
  - number: 3
    id: grant-all
    title: Grant everything in one go
    text: |
      Tap **Grant All** at the top of the screen instead of working through the rows one by one. The app answers one ordinary Android dialog for the permissions that support it, then walks you to each special-screen permission in turn, one system screen at a time, so you always know which one you are looking at.

      Changed your mind about a specific one afterwards? Deny it the normal way in Android, or come back to this screen and revisit that single row.
    image_bookmark:
      shot_id: getting-started.main-screen-permissions-grant-all
      device_profile: phone
      screen_state: settings-permissions-grant-all
      alt: The Permissions and Access screen with the Grant All button highlighted above the permission group list
      caption: "Grant All walks through every permission for you."
      title: "Screenshot: Grant All button"
      desc: Top of the permissions screen, Grant All and Open App Settings buttons visible.
  - number: 4
    id: what-each-group-means
    title: What each group actually unlocks
    text: |
      - **Storage** - reading your photos, video and music, and, on the sideload noLegal edition only, full access to any folder for bulk media edits.
      - **Network** - reaching SMB, SFTP, FTP and DLNA servers on your local network. On newer Android versions this is its own permission, separate from Wi-Fi being on.
      - **Microphone** - recording voice notes and capturing sound inside screen recordings, on editions that record audio.
      - **Notifications** - showing progress while a long job runs in the background, on editions that keep audio playing after you leave the app.
      - **Camera** - taking photos and video inside the app, reading text and scanning QR codes, including the code used to pair a companion device.
      - **Location** - writing coordinates into the photos and videos you shoot in the app. On editions with a launcher desktop or a Network Monitor, the same permission also drives the compass, speed, altitude and map desktop gadgets and the network monitor's GNSS and Wi-Fi details - the row on those editions names all of that, not only the geotag.
      - **System** - background items such as an exemption from battery optimization for scheduled jobs, drawing the gesture strip over other apps, installing an APK you opened from a file (noLegal edition only), and reporting phone signal strength or step count on the launcher desktop.
      - **Contacts** - reading a pinned contact's current name and photo so a launcher shortcut stays up to date, on editions with a launcher desktop.

      A row you do not see simply does not apply to this edition or this Android version - the list never hides a permission the app could still use.
    callout:
      type: tip
      title: One wording, everywhere
      text: "Whichever screen asks for a permission - this list, the welcome wizard, or a dialog shown while you work - it explains that permission in the same words. Nothing changes meaning depending on where you happened to be asked."
  - number: 5
    id: open-app-settings
    title: Change a decision later
    text: |
      Tap **Open App Settings** to jump straight to Android's own application details screen for FastMediaSorter. That is the only place to grant a permission Android has stopped asking about in-app - typically one you denied twice in a row - and it is also where you go to revoke something you granted earlier.
    image_bookmark:
      shot_id: getting-started.main-screen-permissions-open-settings
      device_profile: phone
      screen_state: android-app-settings-screen
      alt: The Android application details screen for FastMediaSorter with the Permissions entry visible
      caption: "Android's own app settings screen."
      title: "Screenshot: App settings, opened from the permissions page"
      desc: System app-info screen for FastMediaSorter, Permissions row visible.
outcome: |
  You know what every permission this edition can ask for is actually for, you can grant them one at a time or all in one pass, and you know exactly where to go in Android when you want to revisit a choice later.
tips:
  - "**Audio permissions are always offered where they belong.** In the editions that record or play sound in the background, the microphone and audio permissions are listed and can be granted - they are never hidden by mistake."
  - "**A row named 'Physical activity' or 'Install apps from files'?** Those only appear on the noLegal sideload edition, because only its manifest declares them - the Play Store edition never shows a permission it could not act on."
  - "**Location reads differently depending on your edition.** On Standard and noLegal it explains the desktop gadgets and Network Monitor too; on the others it stays a plain geotag explanation, because that is all those editions use it for."
  - "**A denied permission is not a dead end.** Most features that need one keep working in a reduced form, and a row you skip during setup stays visible here for whenever you change your mind."
next_recipes:
  - title: First launch and setup wizard
    url: page:getting-started.welcome-and-setup
    badge: Getting Started
    badge_type: docs
    description: The short version of this same permissions list, on the last page of first setup.
  - title: Navigating the main screen
    url: page:getting-started.main-screen-overview
    badge: Getting Started
    badge_type: docs
    description: What the resource list, command bar and panels do once permissions are granted.
  - title: Navigating and searching Settings
    url: page:settings.settings-overview-and-search
    badge: Settings
    badge_type: settings
    description: Find any setting fast, including Permissions & Access.
---

FastMediaSorter only asks for the permissions a feature actually needs, explains each one in the same plain words everywhere it is asked, and lets you grant them one at a time or all at once from **Settings, General, Permissions & Access** - with Android's own app settings always one tap away for changing your mind later.
