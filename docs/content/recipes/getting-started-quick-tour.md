---
page_id: getting-started.quick-tour
title: Quick Product Tour
nav_title: Quick product tour
description: Installing FastMediaSorter, what the first launch splash shows you, a fast honest start on a fresh install, bringing in folders from your Windows PC, and a map of where to read next.
category: Getting Started
category_slug: getting-started
ticket: S2946
flavor: All editions - install source depends on the edition
recipe_number: "04"
canonical_url: documentation/getting-started/quick-tour.html
why: |
  This page is the short version of the whole documentation site: where to get the app, what you see in the seconds after you tap its icon for the first time, and a map of which recipe to open next depending on what you actually want to do with it. If the [welcome wizard](page:getting-started.welcome-and-setup) is the conversation FastMediaSorter has with you once, this page is the five-minute walk around the building before that conversation starts.

  Nothing here requires a decision. It is a look around, not a checklist.
ingredients:
  - "An Android phone, tablet, TV box, car head unit or headset - FastMediaSorter runs on all of them, in the [edition](term:edition) built for that hardware."
  - "A way to install an app: the Google Play Store, or a downloaded APK file for the editions that are not sold in a store."
  - "For the Windows-folder step: Fast Media Sorter for Windows running on the same network as your phone."
steps:
  - number: 1
    id: get-the-app
    title: Get the app
    text: |
      The Standard edition is on Google Play, like any other app - search for FastMediaSorter or open its store listing directly and tap **Install**.

      The other editions - noLegal, VR, Lite, Photos and Legacy - carry features a store will not list, so they are not sold there. You install one of these by downloading its APK file directly, either from the project's GitHub Releases page or from the IzzyOnDroid repository, and opening it. Android meets a package it has not seen installed from a store before and shows a one-time warning about it; that warning is about how many times Android has seen the file's signature, not about anything found inside it. [Why Android warns about this APK, and exactly what to tap](../../docs/INSTALL_TRUST.html) walks through both screens.
  - number: 2
    id: first-open
    title: The first thing you see - a splash with a clock
    text: |
      Launching the app opens on its own [startup splash](term:startup-splash) instead of a bare window: the four-arrow logo arrives in the same blue as the launcher icon, growing into place rather than fading, with the app name and the line "..All mine, here!" beneath it, in the language of your device.

      Underneath the logo, the current time counts by the second, in your device's own 12- or 24-hour style and time zone - so it reads exactly like your system clock, not like a stopwatch the app started on its own. The splash lasts exactly as long as the app takes to start: if it is ready sooner, the splash is gone sooner, and it disappears together with the clock the moment the app opens. On a first launch that goes straight to the [welcome wizard](page:getting-started.welcome-and-setup), this splash is skipped - there is nothing yet to jump ahead of.
    image_bookmark:
      shot_id: getting-started.startup-splash
      device_profile: phone
      screen_state: app-startup-splash-clock
      alt: FastMediaSorter startup splash screen showing the four-arrow logo, the app name and the current time with seconds
      caption: "The startup splash, with the clock running underneath it."
      title: "Screenshot: Startup splash"
      desc: Branded splash screen, logo centered, app name and slogan below it, live clock beneath that.
  - number: 3
    id: fast-honest-start
    title: Behind the splash - a fast, honest start
    text: |
      A few things happen while that splash is up, or right after it, that you notice as an absence of problems rather than as a feature: the first thumbnail grid does not stall, because the image loader is built on a background thread instead of on the main thread during your first photo load. The app itself does not stall either, because its file-transfer preferences open on first use instead of while the main screen is still being built.

      And on a completely fresh install, the built-in collections on the main screen - [All Music](term:all-music), [All Videos](term:all-videos), [All Images](term:all-images), Camera Photos and Recent Media - already show their real file counts instead of a row of zeros. FastMediaSorter fills those [virtual resource](term:virtual-resource) counts in on its own, in the background, at startup, so you do not have to open each one or run a manual rescan just to find out there is anything in it.
    image_bookmark:
      shot_id: getting-started.main-screen-fresh-install
      device_profile: phone
      screen_state: main-screen-virtual-resource-counts
      alt: FastMediaSorter main screen right after a fresh install showing real file counts on the All Music, All Videos and All Images built-in resources
      caption: "A fresh install, with real counts already on the built-in collections."
      title: "Screenshot: Main screen, fresh install"
      desc: Main screen resource list, built-in virtual resources showing non-zero file counts.
  - number: 4
    id: map-of-the-app
    title: A quick look around, by role
    text: |
      Page 1 of the welcome wizard pitches FastMediaSorter as a handful of roles rather than a feature list, and this documentation site is organized the same way. Once you have looked around the [main screen](page:getting-started.main-screen-overview), here is where each role's own recipes live:

      - **File manager** - browsing, sorting and file operations start at [grid and list views](page:browsing.grid-and-list-views) and [copy, move and delete](page:storage.file-copy-move-delete).
      - **Player** - photos and GIFs in [the image viewer](page:images.viewer-and-gestures), video in [playback controls](page:player.video-playback-controls), and music in [playing and organizing](page:audio.playing-and-organizing-music).
      - **Any source** - local, network and cloud folders start at [storage sources setup](page:storage.storage-sources-setup) and [Windows network shares](page:network.smb-samba-shares).
      - **Streams**, on the editions that carry it - begins at [the channel catalog](page:streams.channel-catalog-browsing).
      - **More** - the desktop [launcher](page:launcher.desktop-grid-and-icons), the [watch app](page:wear.installation-and-pairing), the [VR headset](page:vr.headset-setup-and-openxr), and the full [Settings map](page:settings.settings-overview-and-search) all branch off from here, alongside the [seven editions compared](page:flavors.overview-and-comparison).
    callout:
      type: tip
      title: The watch trusts what the phone trusts
      text: "If you pinned an [SFTP](term:sftp) server's host key on the phone, the [watch app](term:watch-app) enforces that same pin: a server presenting a different key is refused before your password is ever sent. The pin travels with the source, so there is nothing to set up on the watch itself - see [watch companion data sync](page:wear.companion-data-sync)."
  - number: 5
    id: bring-in-windows-folders
    title: Bring in folders from your Windows PC
    text: |
      If you run Fast Media Sorter for Windows on the same network, you do not have to type in a server address by hand: scan its QR code, or open the `.fmscfg` file it hands you, and FastMediaSorter adds those PC folders as [SFTP](term:sftp) resources in one step.

      Import the same file again later - say, after sharing one more folder on the PC - and it does not create duplicates. It recognizes a resource it already has by its server and folder path and updates that one instead, then shows a short toast telling you what happened: "Added: <resource name>" or "Updated: <resource name>" when exactly one resource changed, a combined count when several did, or "Resources already up to date" when nothing did.
    image_bookmark:
      shot_id: getting-started.companion-import-toast
      device_profile: phone
      screen_state: add-resource-companion-import-toast
      alt: FastMediaSorter add resource screen showing a toast summarizing an updated Windows companion import
      caption: "Re-importing a companion file updates the matching resource instead of duplicating it."
      title: "Screenshot: Companion import toast"
      desc: Add resource screen, a toast at the bottom reading an added/updated summary.
outcome: |
  You have the app installed, you know what that first splash is telling you, your built-in collections already show real numbers, and you have a map of which recipe to read next for whatever you actually came here to do - browse files, watch something, or bring in a folder from another machine.
tips:
  - "**The splash is honest about timing.** It stays up exactly as long as startup actually takes - a faster phone sees it for less time, not a fixed animation length."
  - "**No servers, no analytics.** FastMediaSorter has no backend of its own; nothing about how you use the app is sent anywhere without your say-so. See the [privacy policy](../../docs/PRIVACY_POLICY.html) for the specifics."
  - "**Re-importing a companion file is always safe.** It only adds or updates the resources that changed - it never leaves you with two copies of the same PC folder."
  - "**Skipped the wizard by accident?** It is one tap away from Settings any time - see [first launch and setup wizard](page:getting-started.welcome-and-setup)."
next_recipes:
  - title: First launch and setup wizard
    url: page:getting-started.welcome-and-setup
    badge: Getting Started
    badge_type: docs
    description: Language, theme, device profile, sources, permissions and your default player, page by page.
  - title: Storage and system permissions
    url: page:getting-started.permissions-guide
    badge: Getting Started
    badge_type: docs
    description: Every permission FastMediaSorter can ask for and what it unlocks.
  - title: Navigating the main workspace
    url: page:getting-started.main-screen-overview
    badge: Getting Started
    badge_type: docs
    description: A full tour of the main screen you land on after setup.
  - title: Seven editions compared
    url: page:flavors.overview-and-comparison
    badge: Editions
    badge_type: docs
    description: Which edition carries which capability, and how to tell them apart.
---

From installing FastMediaSorter to the clock on its first splash to a map of which recipe to read next - this is the five-minute tour before the [welcome wizard](page:getting-started.welcome-and-setup) asks you anything.
